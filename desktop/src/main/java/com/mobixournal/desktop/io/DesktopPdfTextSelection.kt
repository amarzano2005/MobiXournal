package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.ink.warmInk
import com.mobixournal.desktop.ink.withElement
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.audio.withAudio
import com.mobixournal.format.XoppColor.withAlpha
import com.mobixournal.format.model.Stroke

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.format.model.Background
import com.mobixournal.render.PdfTextAvailability
import com.mobixournal.render.PdfTextPhase
import com.mobixournal.render.PdfWord
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection

/**
 * A **view-only** text selection on a PDF page: two word indices into the page's own reading order.
 *
 * It lives on the tab ([DesktopTab.pdfTextSelection]) and never in the document, for the reason the
 * app states in `docs/pdf.md`: selecting text is reading, so there is no page edit and no undo step,
 * and selecting-copying-saving leaves the file byte-identical. [anchor] is where the press landed and
 * [focus] is where the drag is now, so a selection can be grown and shrunk in either direction.
 */
internal data class DesktopPdfTextSelection(
    /** The **document** page it is on — a page change clears it, because it names words of one sheet. */
    val page: Int,
    val anchor: Int,
    val focus: Int,
) {
    val first: Int get() = minOf(anchor, focus)
    val last: Int get() = maxOf(anchor, focus)
}

/**
 * The rules behind the PDF text-select slot: which word a press anchors, how far a drag reaches, and
 * what the system clipboard receives. Pure where it can be (the indices), thin where it cannot (PDFBox
 * has already answered the words).
 *
 * A page that is not a PDF page, or whose PDF carries no text layer, answers null — and the shell says
 * so in one English sentence rather than leaving the gesture silent ([DesktopPdfTextNotice]).
 */
internal object DesktopPdfTextSelect {

    /** The **PDF** page number this document page shows, or null when it sits on no PDF. */
    fun pdfPageOf(tab: DesktopTab): Int? {
        val page = runCatching { tab.document.page(tab.page) }.getOrNull() ?: return null
        return (page.background as? Background.Pdf)?.pageNo
    }

    /** The words the current page's text layer holds — empty for a scan, a photograph or a `.mobix`. */
    fun wordsOf(tab: DesktopTab): List<PdfWord> {
        val index = tab.pdfIndex ?: return emptyList()
        val pdfPage = pdfPageOf(tab) ?: return emptyList()
        return index.words(pdfPage)
    }

    /**
     * Anchor a selection at page point ([x], [y]), or null when there is nothing to select there.
     *
     * The point goes through the shared `PdfTextIndex.anchorWord`, so the word under the pointer — and
     * the nearest one when the press lands between words — is the tablet's own answer.
     */
    fun begin(tab: DesktopTab, x: Double, y: Double): DesktopPdfTextSelection? {
        val index = tab.pdfIndex ?: return null
        val pdfPage = pdfPageOf(tab) ?: return null
        val word = index.text.anchorWord(pdfPage, x, y) ?: return null
        return DesktopPdfTextSelection(tab.page, anchor = word, focus = word)
    }

    /** Extend [selection] to the word under ([x], [y]), keeping its anchor. */
    fun extend(tab: DesktopTab, selection: DesktopPdfTextSelection, x: Double, y: Double): DesktopPdfTextSelection {
        val index = tab.pdfIndex ?: return selection
        val pdfPage = pdfPageOf(tab) ?: return selection
        val word = index.text.anchorWord(pdfPage, x, y) ?: return selection
        return selection.copy(focus = word)
    }

    /** The word boxes a selection covers, in reading order — what the overlay washes. */
    fun boxes(tab: DesktopTab, selection: DesktopPdfTextSelection): List<PdfWord> {
        val index = tab.pdfIndex ?: return emptyList()
        val pdfPage = pdfPageOf(tab) ?: return emptyList()
        return index.text.rangeBoxes(pdfPage, selection.first, selection.last)
    }

    /** The selected text, words joined with single spaces — what Copy puts on the clipboard. */
    fun text(tab: DesktopTab, selection: DesktopPdfTextSelection): String {
        val index = tab.pdfIndex ?: return ""
        val pdfPage = pdfPageOf(tab) ?: return ""
        return index.text.rangeText(pdfPage, selection.first, selection.last)
    }
}

/**
 * [tab]'s text selection as the bar's own value; a null bounds is "no bar".
 *
 * [highlightColour] is the highlighter's ink, so *Highlight* writes the marker's own colour even while the
 * pen is the picked tool — the tablet reads `AppSettings.highlighterColor` for the same word.
 */
internal fun pdfTextCopyUi(tab: DesktopTab, highlightColour: Int): PdfTextCopyUi = PdfTextCopyUi(
    bounds = pdfTextSelectionBounds(tab),
    onCopy = { copyPdfTextOf(tab) },
    onHighlight = if (tab.document.readOnly) null else ({ DesktopPdfTextHighlight.highlight(tab, highlightColour) }),
)

/**
 * **Highlight** the words a PDF text selection covers: ordinary highlighter bands on the tab's active layer,
 * as **one** undo step.
 *
 * The bands are `MagneticHighlighter.selectionStrokes` over the selected words — the geometry a magnetic
 * highlighter drag snaps to — so selecting a passage and highlighting it writes the ink a drag over it would
 * have written: the same stroke shape, the same colour, the same layer, and the same `/Highlight` annotation
 * when the page reaches a PDF export. Nothing new enters the document format.
 *
 * One snapshot names the whole highlight, so `Ctrl+Z` takes back every line of a multi-line selection at once,
 * and a redo puts them all back.
 */
internal object DesktopPdfTextHighlight {

    /** Write the selection's bands; false when there is nothing to write or the page took no edit. */
    fun highlight(tab: DesktopTab, colour: Int): Boolean {
        val selection = tab.pdfTextSelection ?: return false
        val words = DesktopPdfTextSelect.boxes(tab, selection)
        if (words.isEmpty()) return false
        val ink = if ((colour ushr 24) == 0xFF) colour.withAlpha(com.mobixournal.format.XoppColor.HIGHLIGHTER_ALPHA) else colour
        val strokes = com.mobixournal.render.MagneticHighlighter.selectionStrokes(words, ink)
        if (strokes.isEmpty()) return false
        val index = tab.page
        val before = tab.document.snapshot()
        // A take that is running stamps the same `fn`/`ts` every committed stroke carries.
        val stamp = DesktopAudioSession.stamp(tab)
        val edited = tab.document.editPage(index) { page ->
            strokes.fold(page) { grown, stroke ->
                val band = if (stamp != null) stroke.withAudio(stamp) else stroke
                grown.withElement(band, tab.inkLayer(grown))
            }
        }
        if (edited == null) return false
        tab.record(before)
        tab.warmInk()
        DesktopPdfTextNotice.highlighted(words.size)
        return true
    }
}

/**
 * The union box of [tab]'s text selection, in page points — where its Copy bar is placed — or null when
 * nothing is selected.
 */
internal fun pdfTextSelectionBounds(tab: DesktopTab): com.mobixournal.render.Bounds? {
    val selection = tab.pdfTextSelection ?: return null
    val words = DesktopPdfTextSelect.boxes(tab, selection)
    if (words.isEmpty()) return null
    return com.mobixournal.render.Bounds(
        left = words.minOf { it.left },
        top = words.minOf { it.top },
        right = words.maxOf { it.right },
        bottom = words.maxOf { it.bottom },
    )
}

/**
 * Copy [tab]'s PDF text selection to the system clipboard, and say so in the status bar.
 *
 * One rule for the two doors onto the same act — `Ctrl+C` and the selection's Copy bar — so a keyboard
 * copy and a clicked one cannot disagree about what they put on the clipboard.
 */
internal fun copyPdfTextOf(tab: DesktopTab): Boolean {
    val selection = tab.pdfTextSelection ?: return false
    val text = DesktopPdfTextSelect.text(tab, selection)
    if (text.isBlank() || !DesktopPdfTextClipboard.copy(text)) return false
    DesktopPdfTextNotice.copied(DesktopPdfTextSelect.boxes(tab, selection).size)
    return true
}

/**
 * The system clipboard, the route `DesktopSelectionCommands` already uses for an element copy.
 *
 * Guarded because a headless toolkit has no clipboard: a copy that cannot happen answers false, and the
 * caller leaves the selection alone rather than claiming it copied.
 */
internal object DesktopPdfTextClipboard {

    fun copy(
        text: String,
        clipboard: Clipboard? = runCatching { Toolkit.getDefaultToolkit().systemClipboard }.getOrNull(),
    ): Boolean {
        if (text.isBlank() || clipboard == null) return false
        return runCatching {
            clipboard.setContents(StringSelection(text), null)
            true
        }.getOrDefault(false)
    }
}

/**
 * The one sentence the status bar owes a reader whose page had no text to select — the page is a scan,
 * a photograph, or a `.mobix` page, and an empty gesture with no explanation reads as a broken tool.
 *
 * An object with snapshot state, exactly like `DesktopExportNotice`: the bar re-reads it, and a new
 * gesture replaces the sentence rather than stacking messages.
 */
internal object DesktopPdfTextNotice {

    var text: String? by mutableStateOf(null)
        private set

    /**
     * The action the sentence offers, if any: a **Try again** after a failed read, nothing when the sentence
     * is just news. The band draws it beside the words (`DesktopStatus`).
     */
    var retry: (() -> Unit)? by mutableStateOf(null)
        private set

    /** Say [sentence], with [retry] when the reader can do something about it. */
    fun show(sentence: String, retry: (() -> Unit)? = null) {
        text = sentence
        this.retry = retry
    }

    /** `No text on this page` — a scan, which is not a failure and offers nothing. */
    fun noText() = show(PdfTextAvailability.NO_TEXT_SENTENCE)

    /** `Reading this PDF's text…` — the walk is running, so there is nothing to retry. */
    fun reading() = show(PdfTextAvailability.INDEXING_SENTENCE)

    /** What the slot says when the press landed between the words of a page that has them. */
    fun missedWord() = show("Press on a word to select it.")

    /**
     * The page's own state, from the shared `PdfTextAvailability`: a failed read comes with the one action
     * that helps.
     */
    fun unavailable(availability: PdfTextAvailability, onRetry: () -> Unit = {}) {
        show(
            sentence = availability.notice(pageShowsPdf = true) ?: PdfTextAvailability.NO_TEXT_SENTENCE,
            retry = if (availability.canRetry) onRetry else null,
        )
    }

    /** `Copied 4 words` — the confirmation a silent clipboard never gives. */
    fun copied(words: Int) = show(if (words == 1) "Copied 1 word" else "Copied $words words")

    /** `Highlighted 4 words` — the page changed, and the bar says which words it marked. */
    fun highlighted(words: Int) = show(if (words == 1) "Highlighted 1 word" else "Highlighted $words words")

    fun clear() {
        text = null
        retry = null
    }
}

/**
 * **Where the window's PDF text layer stands**, per cache key — the process half the tab cannot hold.
 *
 * `DesktopTab.pdfIndex` is the result, and null means both "still being read" and "no text to be had"; this
 * keeps the difference, plus the reason a broken read broke. It is the window's reflection of the tablet's
 * `PdfTextStatus`, and both feed the same `PdfTextAvailability` copy so the two shells cannot tell a reader
 * two different things about one file.
 *
 * [retryToken] is how a **Try again** reaches the loader: the effect that reads the index keys on it, so
 * bumping it re-runs the read after the cache has been told to forget the failed entry.
 */
internal object DesktopPdfTextStatus {

    private val entries = mutableStateMapOf<String, PdfTextAvailability>()

    /** Bumped by a retry; the loader's effect keys on it. */
    var retryToken by mutableStateOf(0)
        private set

    fun of(key: String?): PdfTextAvailability = key?.let { entries[it] } ?: PdfTextAvailability()

    fun indexing(key: String) {
        entries[key] = PdfTextAvailability(PdfTextPhase.INDEXING)
    }

    fun ready(key: String) {
        entries[key] = PdfTextAvailability(PdfTextPhase.READY)
    }

    fun failed(key: String, reason: String?) {
        entries[key] = PdfTextAvailability(PdfTextPhase.FAILED, failure = reason)
    }

    /** Ask for the read again: the loader is woken, and the failed entry stops answering. */
    fun retry() {
        retryToken++
    }
}

/**
 * The slot's answer for [tab]: the phase of the index it is waiting on, and — once the index is there — how
 * many words **this sheet** holds, which is what separates a digital page from a scan in one document.
 *
 * A tab whose page is not a PDF page answers its phase with no words, and the caller says nothing at all: the
 * slot is about a PDF sheet, not about a notebook.
 */
internal fun pdfTextAvailabilityOf(tab: DesktopTab): PdfTextAvailability {
    val stored = DesktopPdfTextStatus.of(tab.pdfIndexKey)
    if (stored.phase != PdfTextPhase.READY) return stored
    val index = tab.pdfIndex ?: return stored
    val sheet = DesktopPdfTextSelect.pdfPageOf(tab) ?: return stored.copy(wordsOnPage = 0)
    return stored.copy(wordsOnPage = index.text.words(sheet).size)
}

/** Forget the cached index and read this tab's PDF again — what the slot's **Try again** does. */
internal fun DesktopTab.retryPdfIndex() {
    pdfIndexKey?.let { key -> DesktopPdfIndexCache.forget(key) }
    pdfIndex = null
    DesktopPdfTextStatus.retry()
}
