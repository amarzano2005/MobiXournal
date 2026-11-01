package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.number
import com.mobixournal.desktop.shell.DesktopTab

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.format.model.Background
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

/** [tab]'s text selection as the Copy bar's own value; a null bounds is "no bar". */
internal fun pdfTextCopyUi(tab: DesktopTab): PdfTextCopyUi =
    PdfTextCopyUi(bounds = pdfTextSelectionBounds(tab), onCopy = { copyPdfTextOf(tab) })

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

    /** `No text on this page` — what the slot says when it can select nothing. */
    fun noText() {
        text = "No text on this page: it has no PDF text layer."
    }

    /** `Copied 4 words` — the confirmation a silent clipboard never gives. */
    fun copied(words: Int) {
        text = if (words == 1) "Copied 1 word" else "Copied $words words"
    }

    fun clear() {
        text = null
    }
}
