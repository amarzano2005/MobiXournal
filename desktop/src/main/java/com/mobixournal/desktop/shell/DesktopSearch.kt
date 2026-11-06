package com.mobixournal.desktop.shell
import com.mobixournal.desktop.ink.HandwritingCache
import com.mobixournal.desktop.ink.PageHandwriting
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.ink.warmInk
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.save
import com.mobixournal.desktop.sheet.number

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.render.DocumentSearch
import com.mobixournal.render.PdfWord
import com.mobixournal.render.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The window's answer to **one** query: what it matched, and which match the reader is standing on.
 *
 * View state, like the ink selection and the PDF text selection: a search reads the document and writes
 * nothing, so no undo step is recorded and a save after a search writes the file it read
 * (`docs/desktop-workstation.md`). It is one value rather than three fields because a query, its hits and
 * the current hit can only be changed together — a current index left over from another query's hits is
 * exactly the bug a reader would see as "next jumped to the wrong page".
 */
internal data class DesktopSearch(
    /** The query the hits answer, trimmed — never the field's raw text. */
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    /** Which hit the reader is on, 0-based; `0` with no hits. */
    val current: Int = 0,
) {

    /** How many matches the document holds. */
    val total: Int get() = hits.size

    /** True while a query is up — the state the results section and the counter are drawn on. */
    val isActive: Boolean get() = query.isNotEmpty()

    /** The counter's own number: one-based, or zero when there is nothing to be at. */
    val position: Int get() = if (hits.isEmpty()) 0 else current.coerceIn(0, hits.lastIndex) + 1

    /** The match the page highlight and the results list mark as current, or null with no hits. */
    val hit: SearchHit? get() = hits.getOrNull(current)

    /** The next match, wrapping past the last one — `Enter` in the field. */
    fun next(): DesktopSearch = if (hits.isEmpty()) this else copy(current = (current + 1) % hits.size)

    /** The previous match, wrapping before the first one — `Shift+Enter`. */
    fun previous(): DesktopSearch =
        if (hits.isEmpty()) this else copy(current = (current - 1 + hits.size) % hits.size)

    /** The same answer standing on [index] — a click on a row of the results list. */
    fun at(index: Int): DesktopSearch = if (hits.isEmpty()) this else copy(current = index.coerceIn(0, hits.lastIndex))

    companion object {
        val EMPTY = DesktopSearch()
    }
}

/**
 * True while the field holds a query the tab's [DesktopSearch] does not answer yet — the walk is still
 * running ([SearchRunner]).
 *
 * The answer carries the query it belongs to, so "is it done" is a comparison rather than a flag kept up to
 * date by hand: a flag set at the start of the walk and cleared at the end can be left `true` by a cancelled
 * walk that a keystroke replaced. It is the one rule behind the three readouts — the field's counter, the
 * status bar's count and the results heading — because a query still being read must not be reported as
 * `0 / 0`, the value that means *this query found nothing*.
 *
 * A query below [DesktopSearchEngine.MIN_QUERY] is answered on the spot with no hits, so it is never pending.
 */
internal val DesktopTab.isSearchPending: Boolean
    get() {
        val trimmed = searchQuery.trim()
        return trimmed.length >= DesktopSearchEngine.MIN_QUERY && trimmed != search.query
    }

/**
 * The walk from a query to its matches, off the UI thread.
 *
 * The engine is mobix-core's ([DocumentSearch]) — the same one the tablet's field uses — so a phrase found
 * on the phone is found on the PC at the same place. What is the window's own is **which PDF words belong
 * to which page**: the window reorders pages, so a document page's words come from its own
 * `Background.Pdf.pageNo`, never from the page's position in the document (`pdfWordsOf`).
 *
 * Pages are read through [TabPages], which asks the tab's document — its page window — so a 200-page
 * `.mobix` is searched one page at a time and never inflated whole, and the pages being edited are the ones
 * searched (a page's unsaved strokes live in the window, not in the archive).
 *
 * Handwriting is searched too ([PageHandwriting]): the models differ between the shells, but a word written
 * on the page is the document's text on both, so a phrase written on the tablet is found on the PC.
 */
internal object DesktopSearchEngine {

    /** A query shorter than this matches half the document, so it answers nothing instead. */
    const val MIN_QUERY = 2

    fun run(
        tab: DesktopTab,
        rawQuery: String,
        handwriting: PageHandwriting? = null,
    ): DesktopSearch {
        val query = rawQuery.trim()
        if (query.length < MIN_QUERY) return DesktopSearch(query)
        val hits = DocumentSearch.find(
            TabPages(tab.document),
            { index -> pdfWordsOf(tab, index) },
            query,
            handwriting,
        )
        return DesktopSearch(query = query, hits = hits)
    }

    /**
     * The words of **document** page [pageIndex]: the page's own PDF page number, looked up in the tab's
     * index. A page with no PDF background has no words, and a tab whose index has not landed answers an
     * empty list — the text layer is a reading, never a promise.
     */
    private fun pdfWordsOf(tab: DesktopTab, pageIndex: Int): List<PdfWord> {
        val index = tab.pdfIndex ?: return emptyList()
        val page = runCatching { tab.document.page(pageIndex) }.getOrNull() ?: return emptyList()
        val pdfPage = (page.background as? Background.Pdf)?.pageNo ?: return emptyList()
        return index.words(pdfPage)
    }
}

/** The tab's document as a [PageSource], so the shared engine's lazy walk reads the **edited** pages. */
private class TabPages(private val document: DesktopDocument) : PageSource {

    override val pageCount: Int get() = document.pageCount

    /** Search reads pages, never identities: a page's id is the save's question, not the query's. */
    override fun pageId(index: Int): String? = null

    override fun page(index: Int): Page = document.page(index)
}

/**
 * Answers the field's query for [tab], one walk per keystroke.
 *
 * A `LaunchedEffect` keyed on the tab, the text and the document's edit count: the last is what re-runs the
 * search after an edit (a paragraph typed since the last query is findable), and *not* being keyed on the
 * viewport or the zoom is what keeps a repaint from walking the document again. The walk runs on
 * [Dispatchers.Default]; the next keystroke cancels the effect and its answer is dropped, so a burst of
 * typing costs one walk per settled query rather than one per letter on screen.
 *
 * Handwriting is read through the tab's [HandwritingCache], so the recognised pages survive a keystroke —
 * which is what keeps a query on a written page as quick as a query on a typed one after the first walk.
 */
@Composable
internal fun SearchRunner(tab: DesktopTab?) {
    if (tab == null) return
    val query = tab.searchQuery
    val edits = tab.document.edits
    // The page in view is read a moment after it is opened or turned, so the first query on a written
    // sheet answers from a reading that already exists rather than from thirty lines of recognition.
    LaunchedEffect(tab, tab.page) { tab.warmInk() }
    LaunchedEffect(tab, query, edits) {
        if (query.trim().length < DesktopSearchEngine.MIN_QUERY) {
            tab.search = DesktopSearch(query.trim())
            return@LaunchedEffect
        }
        val handwriting = tab.handwriting.of(tab)
        tab.search = withContext(Dispatchers.Default) { DesktopSearchEngine.run(tab, query, handwriting) }
    }
}
