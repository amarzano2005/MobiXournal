package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.shell.DesktopTab

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfOutlineView

/**
 * One row of the window's **Contents** list: a title, its indentation, and the **document** page it jumps
 * to (null when the entry names no page at all).
 *
 * The indentation is the outline's own depth, so the tree reads as the PDF's chapters and sections rather
 * than as a flat list of bookmarks. [page] is already resolved — see [outlineRows] — because a PDF's page
 * index and the document's page index agree only by construction, never by rule.
 */
internal data class OutlineRow(
    val title: String,
    val depth: Int,
    /** The document page showing the PDF sheet this entry leads to, or null when it points nowhere. */
    val page: Int?,
    /**
     * The row's **position** in the flat outline — its identity. Two chapters may honestly be called
     * "Introduction", so a collapsed set of titles would fold both at once; the position does not move while
     * the file is open.
     */
    val row: Int = 0,
    /** True when the row opens a branch, so it draws a chevron. */
    val branch: Boolean = false,
    /** True while the branch's rows are on screen. */
    val expanded: Boolean = true,
    /** Shown only to keep a filter's match reachable: an ancestor, not an answer. */
    val context: Boolean = false,
    /** This row's own title carries the filter's words. */
    val matched: Boolean = false,
    /** The row naming the sheet the reader stands on. */
    val current: Boolean = false,
)

/**
 * The outline flattened into the rows the sidebar prints, with every PDF page index mapped onto the
 * **document** page that shows it.
 *
 * The mapping is [PdfPageReferences]' business, not an offset: an import appends the PDF's sheets, but a
 * merge renumbers every page it appends, and a delete can move the reference between pages. So a row is
 * resolved by asking which page's `Background.Pdf` names that PDF sheet — the same inheritance the painter
 * and the exporter use — and an entry nothing shows is drawn dimmed and cannot be clicked.
 *
 * Pure, so the order, the indentation and the offset are JVM-testable without a window.
 */
internal fun outlineRows(outline: PdfOutline, pages: List<Page>): List<OutlineRow> {
    // Where each PDF sheet is shown, read once: the document's pages are a short list next to the outline.
    val shown = HashMap<Int, Int>()
    pages.forEachIndexed { index, page ->
        val background = page.background as? Background.Pdf ?: return@forEachIndexed
        shown.putIfAbsent(background.pageNo, index)
    }
    return outline.entries.map { entry ->
        OutlineRow(
            title = entry.title,
            depth = entry.depth,
            page = entry.pageIndex.takeIf { entry.hasDestination }?.let { shown[it] },
        )
    }
}

/**
 * The rows the sidebar draws: the outline mapped onto **document** pages, then the view of it a reader has
 * made — branches closed and a filter typed — through `PdfOutlineView`, the rules the tablet's card uses.
 *
 * Filtering keeps the ancestors of its matches ([OutlineRow.context]) and never touches [collapsed], so
 * clearing the box gives back the tree as it was left. [currentPdfPage] marks the row the reader stands on.
 */
internal fun visibleContentsRows(
    outline: PdfOutline,
    pages: List<Page>,
    collapsed: Set<Int> = emptySet(),
    query: String = "",
    currentPdfPage: Int? = null,
): List<OutlineRow> {
    val mapped = outlineRows(outline, pages)
    val branches = PdfOutlineView.branchRows(outline.entries)
    val current = PdfOutlineView.currentRow(outline.entries, currentPdfPage)
    return PdfOutlineView.visible(outline.entries, collapsed, query).map { shown ->
        mapped[shown.row].copy(
            row = shown.row,
            branch = shown.row in branches,
            expanded = shown.row !in collapsed,
            context = shown.context,
            matched = shown.matched,
            current = shown.row == current,
        )
    }
}

/**
 * The rows for [tab]'s open document, or an empty list when it has no PDF or its PDF has no outline — the
 * two cases in which the sidebar shows **no section at all** rather than an empty one.
 */
internal fun contentsRowsOf(
    tab: DesktopTab,
    collapsed: Set<Int> = emptySet(),
    query: String = "",
): List<OutlineRow> {
    val outline = tab.pdfIndex?.outline ?: return emptyList()
    if (outline.isEmpty) return emptyList()
    return visibleContentsRows(
        outline = outline,
        pages = tab.document.model.pages,
        collapsed = collapsed,
        query = query,
        currentPdfPage = DesktopPdfTextSelect.pdfPageOf(tab),
    )
}
