package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopContainer
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.sheet.PageBackdrop
import com.mobixournal.desktop.sheet.PageSelection
import com.mobixournal.desktop.shell.DesktopPages
import com.mobixournal.desktop.shell.DesktopTabs

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **The page clipboard**: copying whole pages out of the list and pasting them back, in this document or
 * in another tab of the window.
 *
 * Two rules hold it together. A paste is a page of **this** document, so every pasted page — and every
 * element on it — gets an id of its own (pasting the same copy twice must not produce one page twice);
 * and a page sitting on a PDF cannot travel into a document backed by another file, because its
 * `Background.Pdf` names bytes that document does not carry (`docs/pdf.md`). That paste is refused with a
 * sentence rather than written as a page that would draw nothing.
 */
class DesktopPageClipboardTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun plain(): Background = Background.Solid(0xFFFFFFFF.toInt(), "plain")

    private fun page(label: String, id: String, background: Background = plain()): Page = Page(
        width = 595.0,
        height = 842.0,
        background = background,
        layers = listOf(
            Layer(
                listOf(
                    TextElement(
                        font = "Sans",
                        size = 12.0,
                        x = 10.0,
                        y = 10.0,
                        color = 0xFF000000.toInt(),
                        content = label,
                        id = "txt_$id",
                    ),
                ),
            ),
        ),
        id = id,
    )

    private fun document(name: String, pages: List<Page>): DesktopDocument {
        val model = Document(pages = pages)
        return DesktopDocument(
            file = File(temp.root, name),
            source = PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
        )
    }

    private fun notebook(name: String = "clipboard.xopp", count: Int = 4): DesktopDocument =
        document(name, (1..count).map { page("p$it", "pg_$it") })

    private fun pdf(name: String): File {
        val file = File(temp.root, name)
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle(595f, 842f)))
            doc.save(file)
        }
        return file
    }

    private fun pagesOf(tabs: DesktopTabs) = DesktopPages(tabs, { _, _ -> }, { 595.0 to 842.0 })

    private fun labels(document: DesktopDocument): List<String> =
        document.model.pages.map { page ->
            (page.layers.firstOrNull()?.elements?.firstOrNull() as? TextElement)?.content ?: "-"
        }

    @Test
    fun `a copied run pastes after the last picked page`() {
        val document = notebook()
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)
        tab.pickedPages = PageSelection(setOf(1, 2))

        assertTrue("two pages enter the clipboard", pages.copyPickedPages())
        assertEquals("a copy is not an edit", 4, document.pageCount)
        assertTrue("the pick is cleared with the paste", pages.pastePages())

        assertEquals(listOf("p1", "p2", "p3", "p2", "p3", "p4"), labels(document))
        assertEquals("and the view lands on the first pasted page", 3, tab.page)
    }

    @Test
    fun `pasting the same copy twice yields pages of their own`() {
        val document = notebook()
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)
        tab.pickedPages = PageSelection(setOf(0))

        assertTrue(pages.copyPickedPages())
        assertTrue(pages.pastePages(1))
        assertTrue(pages.pastePages(3))

        assertEquals(6, document.pageCount)
        val ids = document.model.pages.map { it.id }
        assertEquals("every pasted page is a page of its own: $ids", 6, ids.toSet().size)
        val elementIds = document.model.pages.map { (it.layers.first().elements.first() as TextElement).id }
        assertEquals("and so is every element: $elementIds", 6, elementIds.toSet().size)
    }

    @Test
    fun `a cut takes the pages out in one undo step`() {
        val document = notebook()
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)
        tab.pickedPages = PageSelection(setOf(1, 2))

        assertTrue("the pick is cut", pages.cutPickedPages())
        assertEquals(listOf("p1", "p4"), labels(document))
        assertEquals("the clipboard holds what left", 2, pages.pageClipboard.pages.size)

        assertTrue("one step to undo", tab.undo())
        assertEquals(listOf("p1", "p2", "p3", "p4"), labels(document))
    }

    @Test
    fun `a cut with nothing picked does nothing`() {
        val document = notebook()
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)

        assertTrue("a cut never removes the page in view by itself", !pages.cutPickedPages())
        assertEquals(4, document.pageCount)
        assertTrue("and records nothing", !tab.canUndo)
    }

    @Test
    fun `a copy pastes into another tab of the same window`() {
        val first = notebook("first.xopp")
        val second = notebook("second.xopp", count = 2)
        val tabs = DesktopTabs().also { it.open(first) }
        val pages = pagesOf(tabs)
        tabs.active!!.pickedPages = PageSelection(setOf(0))
        assertTrue(pages.copyPickedPages())

        val other = tabs.open(second)
        assertTrue("the page lands in the other document", pages.pastePages(0))

        assertEquals("the source is untouched", 4, first.pageCount)
        assertEquals(listOf("p1", "p1", "p2"), labels(second))
        assertEquals("and the view is on the pasted page", 1, other.page)
    }

    @Test
    fun `a pdf page pastes back into its own document`() {
        val reference = Background.Pdf(pdf("own.pdf").absolutePath, pageNo = 0, domain = "absolute")
        val document = document("own.xopp", listOf(page("p1", "pg_1", reference), page("p2", "pg_2")))
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)
        tab.pickedPages = PageSelection(setOf(0))

        assertTrue(pages.copyPickedPages())
        assertTrue("the same PDF backs the target", pages.pastePages(1))

        assertEquals(reference, document.model.pages[2].background)
        assertNotNull(document.pdfSource())
        assertNull(pages.lastPageRefusal)
    }

    @Test
    fun `a page of another pdf is refused rather than pasted`() {
        val reference = Background.Pdf(pdf("one.pdf").absolutePath, pageNo = 0, domain = "absolute")
        val withPdf = document("one.xopp", listOf(page("p1", "pg_1", reference), page("p2", "pg_2")))
        val plainDoc = notebook("plain.xopp", count = 2)
        val tabs = DesktopTabs().also { it.open(withPdf) }
        val pages = pagesOf(tabs)
        tabs.active!!.pickedPages = PageSelection(setOf(0))
        assertTrue(pages.copyPickedPages())

        tabs.open(plainDoc)
        assertTrue("the paste is refused", !pages.pastePages())

        assertEquals("nothing was written", 2, plainDoc.pageCount)
        assertNotNull("and the reader is told why", pages.lastPageRefusal)
        assertTrue(pages.lastPageRefusal!!.contains("another PDF"))
        assertNotEquals(withPdf.pdfSource(), plainDoc.pdfSource())
    }

    @Test
    fun `an empty clipboard pastes nothing`() {
        val document = notebook()
        val tabs = DesktopTabs().also { it.open(document) }
        val pages = pagesOf(tabs)

        assertTrue("nothing to paste", !pages.pastePages())
        assertEquals(4, document.pageCount)
    }
}
