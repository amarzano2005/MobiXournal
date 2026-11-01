package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.find

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.mobixournal.format.model.Background
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfTextIndex
import java.lang.ref.SoftReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument

/**
 * A PDF's **two reading layers** — its positioned text and its Contents outline — as one value.
 *
 * They travel together because they cost the same file: one [PDDocument.load] answers both, and a
 * selection tool that had the words but not the outline (or the sidebar the outline but not the words)
 * would open the same PDF twice. Both halves are the shared, platform-free types, so what the window
 * selects and lists is what the tablet selects and lists.
 */
internal class DesktopPdfIndex(
    val text: PdfTextIndex,
    val outline: PdfOutline,
) {
    /** The words of **PDF page** [pdfPage] (a page's `Background.Pdf.pageNo`), never by document index. */
    fun words(pdfPage: Int): List<com.mobixournal.render.PdfWord> = text.words(pdfPage)

    companion object {
        val EMPTY = DesktopPdfIndex(PdfTextIndex(emptyList()), PdfOutline.EMPTY)
    }
}

/**
 * The window's **one** parser of that pair: a single `PDDocument.load`, and an empty answer instead of
 * a throw for a scanned, corrupt or missing file.
 *
 * Compression is the caller's: this runs off the UI thread ([DesktopPdfIndexCache.load] is the seam a
 * `withContext(Dispatchers.Default)` call site uses).
 */
internal object DesktopPdfIndexer {

    fun read(bytes: ByteArray): DesktopPdfIndex = runCatching {
        PDDocument.load(bytes).use { doc -> DesktopPdfIndex(DesktopPdfText.of(doc), DesktopPdfOutline.of(doc)) }
    }.getOrDefault(DesktopPdfIndex.EMPTY)
}

/**
 * The extracted text layers and outlines of the PDFs currently open, keyed by **the resolved reference
 * plus the file's length**.
 *
 * The length is part of the key because it is the cheap evidence that the bytes changed: a merge or a
 * re-import replaces the file a reference names, and a key of the name alone would keep answering with
 * the outline of the PDF that used to be there. The save, Save-As, merge and re-import paths still
 * [forgetAll] outright — "the file is the same length" is not a promise anyone made.
 *
 * Entries are held **softly**, exactly as the app's `PdfTextIndexCache` does: an index nobody is using
 * is a pure cache, and the collector may drop it under memory pressure rather than have a 200-page
 * text layer compete with the page bitmaps.
 */
internal object DesktopPdfIndexCache {

    private val entries = HashMap<String, SoftReference<DesktopPdfIndex>>()

    /** The key of [reference]'s bytes when they are [length] long. */
    fun key(reference: Background.Pdf, length: Int): String =
        "${reference.bundledAsset}|${reference.domain}|${reference.filename}|$length"

    /** The index already extracted for [key], or null if it has not been (or has been reclaimed). */
    fun get(key: String): DesktopPdfIndex? = synchronized(entries) {
        val hit = entries[key]?.get()
        if (hit == null) entries.remove(key)
        hit
    }

    /** Extract [bytes] as [key]'s index, unless another view of the same PDF already did. */
    fun load(key: String, bytes: () -> ByteArray?): DesktopPdfIndex {
        get(key)?.let { return it }
        val read = bytes()?.let(DesktopPdfIndexer::read) ?: DesktopPdfIndex.EMPTY
        synchronized(entries) { entries[key] = SoftReference(read) }
        return read
    }

    /** Drop one entry — the reference's bytes were replaced. */
    fun forget(key: String) {
        synchronized(entries) { entries.remove(key) }
    }

    /** Drop everything: the PDF behind any reference may be a different file now. */
    fun forgetAll() {
        synchronized(entries) { entries.clear() }
    }
}

/**
 * The current document's index, computed **once per PDF** and **off the UI thread**, and published on
 * the tab where the canvas and the sidebar read it.
 *
 * A `.mobix` or a `.xopp` with no PDF answers nothing at all and the load never starts: the canvas and
 * the sidebar then behave exactly as they did before, which is the rule the PDF features follow
 * (`docs/pdf.md`). A PDF still rasterising its first page does not stall a frame either — the index
 * lands a moment later, and the tools read whatever is there.
 */
@Composable
internal fun PdfIndexLoader(tab: DesktopTab) {
    // The reference changes only when a PDF is imported, merged or replaced; `edits` is the cheap signal
    // that the page list may have moved it. It is looked up only for a page that **is** a PDF page, so a
    // plain notebook never walks its lazy page list to find a reference it does not have.
    val page = remember(tab, tab.page, tab.document.edits) {
        runCatching { tab.document.page(tab.page) }.getOrNull()
    }
    val background = remember(tab, page, tab.document.edits) {
        val own = page?.background as? Background.Pdf
        when {
            own == null -> null
            own.bundledAsset != null || !own.filename.isNullOrBlank() -> own
            else -> tab.document.pdfBackground()
        }
    }
    // The bytes are read once per reference: a reinflated page (a scroll, a hover) must not re-read a
    // whole PDF, and the indexer below runs on another thread anyway.
    val bytes = remember(background) {
        background?.let { runCatching { tab.document.backdrop.pdfBytes(it) }.getOrNull() }
    }
    val key = remember(background, bytes) {
        if (background == null || bytes == null) null else DesktopPdfIndexCache.key(background, bytes.size)
    }
    LaunchedEffect(tab, key) {
        if (key == null) {
            tab.pdfIndex = null
            tab.pdfIndexKey = null
            return@LaunchedEffect
        }
        if (tab.pdfIndexKey == key && tab.pdfIndex != null) return@LaunchedEffect
        val index = withContext(Dispatchers.Default) { DesktopPdfIndexCache.load(key) { bytes } }
        tab.pdfIndex = index
        tab.pdfIndexKey = key
    }
}
