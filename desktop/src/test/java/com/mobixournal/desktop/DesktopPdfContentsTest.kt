package com.mobixournal.desktop

import com.mobixournal.desktop.io.contentsRowsOf
import com.mobixournal.desktop.io.visibleContentsRows
import com.mobixournal.render.OutlineResolution
import com.mobixournal.render.OutlineRow
import com.mobixournal.desktop.shell.Header

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfOutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Contents list's mapping: an outline names **PDF** sheets, the sidebar lists **document** pages, and
 * the two agree only by construction — an import appends the sheets, but a merge renumbers every appended
 * page, so the row has to ask which page shows that sheet rather than add an offset.
 */
class DesktopPdfContentsTest {

    @get:Rule val temp = TemporaryFolder()

    private fun pdfPage(pageNo: Int, carriesReference: Boolean = false) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Pdf(
            filename = if (carriesReference) "doc.pdf" else null,
            pageNo = pageNo,
            domain = if (carriesReference) "absolute" else null,
        ),
        layers = listOf(Layer(emptyList())),
    )

    private fun plainPage() = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain", config = ""),
        layers = listOf(Layer(emptyList())),
    )

    private val outline = PdfOutline(
        listOf(
            PdfOutlineEntry("Chapter 1", 0, 0),
            PdfOutlineEntry("Section 1.1", 1, 1),
            PdfOutlineEntry("Header only", PdfOutlineEntry.NO_DESTINATION, 1),
        ),
    )

    @Test
    fun `rows keep the outline's order and indentation`() {
        val rows = OutlineResolution.rows(outline, listOf(pdfPage(0, carriesReference = true), pdfPage(1)))
        assertEquals(listOf("Chapter 1", "Section 1.1", "Header only"), rows.map { it.title })
        assertEquals(listOf(0, 1, 1), rows.map { it.depth })
    }

    @Test
    fun `a row leads to the document page showing that sheet, not to its index`() {
        // Two plain sheets first: the PDF's page 0 is the document's page 2.
        val pages = listOf(plainPage(), plainPage(), pdfPage(0, carriesReference = true), pdfPage(1))
        val rows = OutlineResolution.rows(outline, pages)
        assertEquals(2, rows[0].page)
        assertEquals(3, rows[1].page)
    }

    @Test
    fun `an entry that names no page is listed but not jumpable`() {
        val rows = OutlineResolution.rows(outline, listOf(pdfPage(0, carriesReference = true)))
        assertNull(rows.last().page)
    }

    @Test
    fun `a sheet no page shows is not jumpable either`() {
        val rows = OutlineResolution.rows(outline, listOf(plainPage()))
        assertEquals(listOf(null, null, null), rows.map { it.page })
    }

    /** Two chapters that honestly share a title, each with a section, plus a header that names no page. */
    private val duplicated = PdfOutline(
        listOf(
            PdfOutlineEntry("Introduction", 0, 0),
            PdfOutlineEntry("Getting started", 0, 1),
            PdfOutlineEntry("Introduction", 1, 0),
            PdfOutlineEntry("Header only", PdfOutlineEntry.NO_DESTINATION, 1),
        ),
    )

    private val twoSheets = listOf(pdfPage(0, carriesReference = true), pdfPage(1))

    @Test
    fun `a branch folds by position, so two chapters called Introduction fold one at a time`() {
        val collapsed = visibleContentsRows(duplicated, twoSheets, collapsed = setOf(0))
        assertEquals(listOf("Introduction", "Introduction", "Header only"), collapsed.map { it.title })
        assertEquals("the second chapter keeps its section", listOf(0, 2, 3), collapsed.map { it.row })
        assertTrue("and it is still open", collapsed[1].expanded)
        assertTrue("while the first is closed", !collapsed[0].expanded)
    }

    @Test
    fun `a filter keeps the chapters above its hit as context, and the pages still resolve`() {
        val rows = visibleContentsRows(duplicated, twoSheets, query = "getting")
        assertEquals(listOf(0, 1), rows.map { it.row })
        assertTrue("the chapter is the path", rows[0].context)
        assertTrue("it is not the answer itself", !rows[0].matched)
        assertFalse("while the section is not merely context", rows[1].context)
        assertEquals("and the hit still jumps", 0, rows[1].page)
    }

    @Test
    fun `filtering does not move the collapse state, so clearing the box gives the tree back`() {
        val collapsed = setOf(0)
        val before = visibleContentsRows(duplicated, twoSheets, collapsed)
        val filtered = visibleContentsRows(duplicated, twoSheets, collapsed, query = "getting")
        assertEquals("the folded section is found anyway", listOf(0, 1), filtered.map { it.row })
        assertEquals("and clearing restores it exactly", before, visibleContentsRows(duplicated, twoSheets, collapsed))
    }

    @Test
    fun `the row naming the sheet in view is marked, with the page mapped to the document`() {
        val pages = listOf(plainPage(), pdfPage(0, carriesReference = true), pdfPage(1))
        val rows = visibleContentsRows(duplicated, pages, currentPage = 2)
        assertEquals("the second chapter names sheet 1", listOf(false, false, true, false), rows.map { it.current })
        assertEquals("and it points at the document's page 2", 2, rows[2].page)
    }

    @Test
    fun `a search no bookmark answers is an empty list, not the whole outline`() {
        assertTrue(visibleContentsRows(duplicated, twoSheets, query = "bibliography").isEmpty())
    }

    @Test
    fun `folding and filtering the contents writes nothing to the document`() {
        val tab = DesktopPdfFixture.tab(temp.root, pages = 2, outline = DesktopPdfFixture.outline())
        try {
            val before = tab.document.edits
            val rows = contentsRowsOf(tab, collapsed = setOf(0), query = "section")
            assertTrue("the filter still finds the section", rows.isNotEmpty())
            assertEquals("browsing is reading, not editing", before, tab.document.edits)
            assertFalse("and it records no undo step", tab.canUndo)
        } finally {
            tab.document.close()
        }
    }

    /**
     * The tree a reader edits outranks the file's own outline — and each kind of target resolves on its own
     * terms: a sheet by which page shows it, a page id by identity, and a page that is gone by saying so
     * rather than by pointing at its neighbour.
     */
    @Test
    fun `a document with its own tree lists the tree, with both kinds of target resolved`() {
        val tab = DesktopPdfFixture.tab(
            temp.root,
            pages = 2,
            outline = DesktopPdfFixture.outline(),
            outlineTree = OutlineTree(
                nodes = listOf(
                    OutlineNode("bkm_1", "My chapter", OutlineTarget.Sheet(1)),
                    OutlineNode("bkm_2", "My page", OutlineTarget.Page("pg_2")),
                    OutlineNode("bkm_3", "A page I deleted", OutlineTarget.Page("pg_gone")),
                ),
            ),
        )
        try {
            val rows = contentsRowsOf(tab)

            assertEquals(
                "the notebook's index, not the PDF's",
                listOf("My chapter", "My page", "A page I deleted"),
                rows.map { it.title },
            )
            assertEquals(
                "a sheet resolves to the page showing it, an id to that page, and a dead id to nothing",
                listOf(1, 1, null),
                rows.map { it.page },
            )
            assertEquals("and the id is the row's identity", listOf("bkm_1", "bkm_2", "bkm_3"), rows.map { it.id })
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a document with no PDF has no contents section`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            assertEquals(emptyList<OutlineRow>(), contentsRowsOf(tab))
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a PDF whose outline is empty shows no section`() {
        val tab = DesktopPdfFixture.tab(temp.root, outline = PdfOutline.EMPTY)
        try {
            assertEquals(emptyList<OutlineRow>(), contentsRowsOf(tab))
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a PDF with an outline lists its rows`() {
        val tab = DesktopPdfFixture.tab(temp.root, pages = 2, outline = DesktopPdfFixture.outline())
        try {
            val rows = contentsRowsOf(tab)
            assertEquals(listOf("Chapter 1", "Section 1.1", "Header only"), rows.map { it.title })
            assertEquals(0, rows[0].page)
            assertEquals(1, rows[1].page)
            assertNull("a header with no page cannot be clicked", rows[2].page)
        } finally {
            tab.document.close()
        }
    }
}
