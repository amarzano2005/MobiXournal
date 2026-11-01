package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.shell.DesktopTab

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.render.PdfOutline

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
 * The rows for [tab]'s open document, or an empty list when it has no PDF or its PDF has no outline — the
 * two cases in which the sidebar shows **no section at all** rather than an empty one.
 */
internal fun contentsRowsOf(tab: DesktopTab): List<OutlineRow> {
    val outline = tab.pdfIndex?.outline ?: return emptyList()
    if (outline.isEmpty) return emptyList()
    return outlineRows(outline, tab.document.model.pages)
}
