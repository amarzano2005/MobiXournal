package com.mobixournal.desktop

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The page commands behind the sidebar's **multi-page selection**: several pages deleted, or several pages
 * moved as one block, each as a single undo step.
 *
 * The rules are `PageOps`' (`removeAll`, `moveAll`, tested on the JVM); this file holds what only the
 * window can get wrong — that the whole pick is one undo step, that the view lands somewhere sensible, and
 * that a delete which takes the page carrying the PDF reference does not take the PDF with it.
 */
class DesktopPageSelectionCommandTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A document of [count] pages, each a distinct shade, so a page can be told from another by its colour. */
    private fun shades(count: Int): DesktopDocument {
        val pages = (0 until count).map { index ->
            Page(
                595.0,
                842.0,
                Background.Solid(0xFF000000.toInt() or (index * 0x101010), "plain"),
                listOf(Layer(emptyList())),
            )
        }
        return document(Document(pages = pages), "shades.xopp")
    }

    private fun document(model: Document, name: String) = DesktopDocument(
        file = File(temp.root, name),
        source = PageSource.of(model),
        model = model,
        container = DesktopContainer.XOPP_GZIP,
        backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
    )

    private fun pagesOf(tabs: DesktopTabs) = DesktopPages(tabs, { _, _ -> }, { 595.0 to 842.0 })

    /** Which shadeless index each page carries, in the document's order. */
    private fun shadesOf(document: DesktopDocument): List<Int> =
        document.model.pages.map { page -> ((page.background as Background.Solid).color and 0xFF) / 0x10 }

    /** A PDF of [count] pages, so the document can hold the one reference a `.xopp` is allowed. */
    private fun pdf(count: Int): File {
        val file = File(temp.root, "source-$count.pdf")
        PDDocument().use { doc ->
            repeat(count) { doc.addPage(PDPage(PDRectangle(595f, 842f))) }
            doc.save(file)
        }
        return file
    }

    @Test
    fun `a scattered pick is deleted in one step`() {
        val document = shades(6)
        val tabs = DesktopTabs().also { it.open(document) }
        val pages = pagesOf(tabs)

        assertTrue("pages 2 and 5 go", pages.removePages(setOf(1, 4)))

        assertEquals("four pages left", 4, document.pageCount)
        assertEquals("and the rest keep their order", listOf(0, 2, 3, 5), shadesOf(document))
        assertTrue("one step to undo", tabs.active!!.undo())
        assertEquals("every page back", 6, document.pageCount)
    }

    @Test
    fun `deleting a pick that holds the pdf reference keeps the pdf`() {
        val file = pdf(4)
        val model = DesktopPdfImport.open(file).use { pdf ->
            DesktopPdfImport.document(pdf, file.absolutePath)
        }
        val document = document(model, "imported.xopp")
        val tabs = DesktopTabs().also { it.open(document) }
        val pages = pagesOf(tabs)

        assertTrue("pages 1 and 3 go, one of them the carrier", pages.removePages(setOf(0, 2)))

        assertEquals(2, document.pageCount)
        assertNotNull("the document still carries its PDF", document.pdfSource())
        for (index in 0 until document.pageCount) {
            val background = document.page(index).background as Background.Pdf
            assertEquals("the sheet names the source page it draws", listOf(1, 3)[index], background.pageNo)
            assertNotNull(
                "page ${index + 1} still draws its PDF page",
                document.backdrop.image(document.page(index).background),
            )
        }
    }

    @Test
    fun `a pick covering every page is refused`() {
        val document = shades(3)
        val tabs = DesktopTabs().also { it.open(document) }
        val pages = pagesOf(tabs)

        assertFalse("a document is never emptied", pages.removePages(setOf(0, 1, 2)))
        assertEquals("nothing was touched", 3, document.pageCount)
    }

    @Test
    fun `a scattered pick moves as one block`() {
        val document = shades(6)
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)

        assertTrue("pages 2 and 4 travel together", pages.movePages(setOf(1, 3), 4))

        assertEquals("the block lands where it was dropped, in order", listOf(0, 2, 4, 5, 1, 3), shadesOf(document))
        assertEquals("the view follows the block", 4, tab.page)
        assertTrue("one step to undo", tab.undo())
        assertEquals(listOf(0, 1, 2, 3, 4, 5), shadesOf(document))
    }

    @Test
    fun `moving a pick onto where it already starts changes nothing`() {
        val document = shades(5)
        val tabs = DesktopTabs().also { it.open(document) }
        val pages = pagesOf(tabs)

        assertFalse("pages 2 and 3 are already there", pages.movePages(setOf(1, 2), 1))
        assertEquals(listOf(0, 1, 2, 3, 4), shadesOf(document))
    }
}
