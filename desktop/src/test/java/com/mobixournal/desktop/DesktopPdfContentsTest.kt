package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfOutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val rows = outlineRows(outline, listOf(pdfPage(0, carriesReference = true), pdfPage(1)))
        assertEquals(listOf("Chapter 1", "Section 1.1", "Header only"), rows.map { it.title })
        assertEquals(listOf(0, 1, 1), rows.map { it.depth })
    }

    @Test
    fun `a row leads to the document page showing that sheet, not to its index`() {
        // Two plain sheets first: the PDF's page 0 is the document's page 2.
        val pages = listOf(plainPage(), plainPage(), pdfPage(0, carriesReference = true), pdfPage(1))
        val rows = outlineRows(outline, pages)
        assertEquals(2, rows[0].page)
        assertEquals(3, rows[1].page)
    }

    @Test
    fun `an entry that names no page is listed but not jumpable`() {
        val rows = outlineRows(outline, listOf(pdfPage(0, carriesReference = true)))
        assertNull(rows.last().page)
    }

    @Test
    fun `a sheet no page shows is not jumpable either`() {
        val rows = outlineRows(outline, listOf(plainPage()))
        assertEquals(listOf(null, null, null), rows.map { it.page })
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
