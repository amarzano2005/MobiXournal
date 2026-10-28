package com.mobixournal.desktop

import androidx.compose.ui.graphics.toPixelMap
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The **page list of a PDF-backed document**: every page on its background, and what reordering or
 * removing a sheet does to the one reference the document is allowed to carry.
 *
 * The defect this file exists for is the shape an import writes: `filename` on the first page and
 * `pageno` alone on the rest (`docs/pdf.md`), which desktop Xournal++ reads by inheritance. The window
 * read every page on its own, so a three-page import painted page one and left the others blank — and an
 * export of it lost the source's vectors from page two onwards, because the preserved-page arm could not
 * find a source either.
 */
class DesktopPdfPageListTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A PDF of [count] pages, each carrying a word of its own, so a page can be told from another. */
    private fun source(count: Int = 3): File {
        val file = File(temp.root, "source-$count.pdf")
        PDDocument().use { doc ->
            for (index in 0 until count) {
                val page = PDPage(PDRectangle(595f, 842f))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    // A light-grey sheet, so a page of the source can be told from bare paper by a pixel
                    // as well as by its text.
                    cs.setNonStrokingColor(0.5f)
                    cs.addRect(0f, 0f, 595f, 842f)
                    cs.fill()
                    cs.setNonStrokingColor(0f)
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 24f)
                    cs.newLineAtOffset(72f, 760f)
                    cs.showText("Source page $index")
                    cs.endText()
                }
            }
            doc.save(file)
        }
        return file
    }

    /** The document an **Import PDF · Replace** makes: the PDF's pages, the reference on the first. */
    private fun imported(file: File): DesktopDocument {
        val model = DesktopPdfImport.open(file).use { pdf -> DesktopPdfImport.document(pdf, file.absolutePath) }
        return document(model, "imported.xopp")
    }

    private fun document(model: com.mobixournal.format.model.Document, name: String) = DesktopDocument(
        file = File(temp.root, name),
        source = PageSource.of(model),
        model = model,
        container = DesktopContainer.XOPP_GZIP,
        backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
    )

    private fun pagesOf(tabs: DesktopTabs) =
        DesktopPages(tabs, { _, _ -> }, { 595.0 to 842.0 })

    private fun pageNos(document: DesktopDocument): List<Int> =
        document.model.pages.map { (it.background as Background.Pdf).pageNo }

    /** The regression: every page of an import sits on the PDF, not just the one that names it. */
    @Test
    fun `every imported page draws its own pdf page`() {
        val document = imported(source(3))
        try {
            assertEquals("the import makes one page per PDF page", 3, document.pageCount)
            for (index in 0 until document.pageCount) {
                val page = document.page(index)
                assertNotNull(
                    "page ${index + 1} sits on the PDF rather than on bare paper",
                    document.backdrop.image(page.background),
                )
            }
        } finally {
            document.close()
        }
    }

    /**
     * The same shape in the export: the source's pages are imported **verbatim**, so each page's own text
     * is still text in the file — which is exactly what a flattened or blank page loses.
     */
    @Test
    fun `the export keeps the vectors of every source page`() {
        val document = imported(source(3))
        try {
            val target = File(temp.root, "annotated.pdf")
            assertEquals("a page per page", 3, DesktopPdfExport.write(document, target, document.backdrop))
            PDDocument.load(target).use { pdf ->
                assertEquals(3, pdf.numberOfPages)
                for (index in 0 until 3) {
                    val words = PDFTextStripper().apply { startPage = index + 1; endPage = index + 1 }.getText(pdf)
                    assertTrue(
                        "page ${index + 1} is source page $index, still vector text: $words",
                        words.contains("Source page $index"),
                    )
                }
            }
        } finally {
            document.close()
        }
    }

    /**
     * The **page list** shows the PDF too: a row of bare paper cannot be reordered or pruned, because
     * nothing on it says which sheet of the PDF it is.
     */
    @Test
    fun `a page thumbnail carries the pdf page it stands for`() {
        val document = imported(source(3))
        try {
            val thumbnail = renderPageBitmap(document.page(1), scale = 0.2f, backdrop = document.backdrop)
            val corner = thumbnail.toPixelMap()[2, 2]
            val grey = (corner.red * 255f).toInt()
            assertTrue(
                "the row is the source's grey sheet, not white paper (was $grey)",
                grey in 0x60..0xD0,
            )
        } finally {
            document.close()
        }
    }

    /** A reference that does not sit on page one is still the document's: the pages after it resolve. */
    @Test
    fun `a reference on a later page serves the pages around it`() {
        val file = source(2)
        val model = com.mobixournal.format.model.Document(
            pages = listOf(
                Page(595.0, 842.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), listOf(Layer(emptyList()))),
                Page(
                    595.0,
                    842.0,
                    Background.Pdf(file.absolutePath, pageNo = 0, domain = "absolute"),
                    listOf(Layer(emptyList())),
                ),
                Page(595.0, 842.0, Background.Pdf(null, pageNo = 1, domain = null), listOf(Layer(emptyList()))),
            ),
        )
        val document = document(model, "later.xopp")
        try {
            assertNotNull("the carrier is found off the first page", document.pdfBackground())
            assertNotNull("and it paints the page that follows", document.backdrop.image(document.page(2).background))
        } finally {
            document.close()
        }
    }

    /**
     * **Delete**: removing the page that carries the reference must not take the PDF out of the document —
     * the reference moves to the first pdf page that survives, so the remaining sheets still draw and a
     * save still carries the bytes.
     */
    @Test
    fun `removing the page that carries the reference keeps the pdf`() {
        val document = imported(source(3))
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)

        assertTrue("the first page is removed", pages.removePageAt(0))

        assertEquals("two pages left", 2, document.pageCount)
        assertNotNull("the document still carries its PDF", document.pdfSource())
        assertEquals("and the numbering follows the source", listOf(1, 2), pageNos(document))
        for (index in 0 until document.pageCount) {
            assertNotNull(
                "page ${index + 1} still sits on the PDF: ${document.page(index).background}",
                document.backdrop.image(document.page(index).background),
            )
        }
        assertTrue("one step to undo", tab.undo())
        assertEquals("the removed page is back", 3, document.pageCount)
        assertEquals(listOf(0, 1, 2), pageNos(document))
    }

    /** Removing a page that carries **nothing** leaves the reference exactly where it was. */
    @Test
    fun `removing an ordinary page leaves the reference alone`() {
        val document = imported(source(3))
        val tabs = DesktopTabs().also { it.open(document) }
        val pages = pagesOf(tabs)
        val carrier = document.pdfBackground()!!

        assertTrue(pages.removePageAt(1))

        assertEquals("the reference did not move", carrier, document.pdfBackground())
        assertEquals(listOf(0, 2), pageNos(document))
        assertNotNull(document.backdrop.image(document.page(0).background))
        assertNotNull(document.backdrop.image(document.page(1).background))
    }

    /**
     * **Reorder**: the page list is the document's order, so moving a sheet moves which PDF page is drawn
     * where — and puts nothing in the file: the reference is a fact about the PDF, not about page one.
     */
    @Test
    fun `moving a page reorders the list and keeps the reference`() {
        val document = imported(source(3))
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)

        assertTrue("the first page goes to the end", pages.movePage(0, 2))

        assertEquals("the order is the reader's", listOf(1, 2, 0), pageNos(document))
        assertEquals("and the view follows the page", 2, tab.page)
        assertNotNull("the PDF is still the document's", document.pdfSource())
        for (index in 0 until document.pageCount) {
            assertNotNull(document.backdrop.image(document.page(index).background))
        }
        assertTrue("one step, like every page command", tab.undo())
        assertEquals(listOf(0, 1, 2), pageNos(document))
    }
}
