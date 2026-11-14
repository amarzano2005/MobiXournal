package com.mobixournal.desktop.io

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.format.model.Page
import com.mobixournal.render.OutlineResolution
import com.mobixournal.render.OutlineRow
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfOutlineView

/**
 * The rows the sidebar draws for a document whose index is the **PDF's own** outline: resolved onto document
 * pages by `OutlineResolution` — the same resolver the tablet uses — and then viewed through
 * `PdfOutlineView`, so a chapter closed on one shell is closed the same way on the other.
 *
 * The overload exists because a document that has never been seeded with a notebook tree still has to show
 * something, and because a test wants to ask the question about an outline and a page list directly.
 */
internal fun visibleContentsRows(
    outline: PdfOutline,
    pages: List<Page>,
    collapsed: Set<Int> = emptySet(),
    query: String = "",
    currentPage: Int? = null,
): List<OutlineRow> = visibleContentsRows(
    rows = OutlineResolution.rows(outline, pages),
    collapsed = collapsed,
    query = query,
    currentPage = currentPage,
)

/**
 * The rows to draw, given the resolved rows: branches closed and a filter typed, with the page the reader
 * stands on marked. [currentPage] is a **document** page index — the resolved rows already speak that
 * language, which is what lets a notebook bookmark and a PDF one sit in the same list.
 */
internal fun visibleContentsRows(
    rows: List<OutlineRow>,
    collapsed: Set<Int> = emptySet(),
    query: String = "",
    currentPage: Int? = null,
): List<OutlineRow> = PdfOutlineView.visible(
    rows = rows,
    collapsed = collapsed,
    query = query,
    currentPage = currentPage,
)

/**
 * The rows for [tab]'s open document, or an empty list when there is nothing to index — in which case the
 * sidebar shows **no section at all** rather than an empty one.
 *
 * The notebook's own tree wins when it has one; the PDF's outline is the fallback for a document that has
 * never been imported into, and is what the reader was seeing before the format could hold a tree.
 */
internal fun contentsRowsOf(
    tab: DesktopTab,
    collapsed: Set<Int> = emptySet(),
    query: String = "",
): List<OutlineRow> {
    val pages = tab.document.model.pages
    val rows = OutlineResolution.rowsOf(tab.document.model.outline, tab.pdfIndex?.outline, pages)
    if (rows.isEmpty()) return emptyList()
    return visibleContentsRows(rows = rows, collapsed = collapsed, query = query, currentPage = tab.page)
}
