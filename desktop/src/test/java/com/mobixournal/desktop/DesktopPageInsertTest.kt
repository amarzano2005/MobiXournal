package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopContainer
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.sheet.PageBackdrop
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Insert and duplicate from the page list**: the two ways a page list grows, and what they do to a
 * page's identity.
 *
 * The identity is the point of the file. A `.mobix` matches two copies of a page by its id
 * (`docs/mobix-format.md`), so a duplicate that kept the original's page id — or its elements' ids —
 * would be folded into the page it came from on the next merge. A `.xopp` has no page ids at all, so
 * there the copy is simply a second page.
 */
class DesktopPageInsertTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** One ruled page carrying one text box, so a page can be told from another by what it holds. */
    private fun page(label: String, id: String? = null, background: Background = plain()): Page = Page(
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
                        id = id?.let { "txt_$it" },
                    ),
                ),
            ),
        ),
        id = id,
    )

    private fun plain(): Background = Background.Solid(0xFFFFFFFF.toInt(), "plain")

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

    private fun threePages(name: String = "insert.xopp"): DesktopDocument =
        document(name, (1..3).map { page("p$it", "pg_$it") })

    private fun pagesOf(tabs: DesktopTabs) = DesktopPages(tabs, { _, _ -> }, { 595.0 to 842.0 })

    /** The labels of the pages that hold text, in document order, with `-` for a blank sheet. */
    private fun labels(document: DesktopDocument): List<String> = document.model.pages.map { page ->
        (page.layers.firstOrNull()?.elements?.firstOrNull() as? TextElement)?.content ?: "-"
    }

    private fun ids(document: DesktopDocument): List<String?> = document.model.pages.map { it.id }

    @Test
    fun `insert after a page lands below it and steps onto it`() {
        val document = threePages()
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)

        assertTrue("the sheet is inserted", pages.insertPageAt(0, before = false))

        assertEquals(listOf("p1", "-", "p2", "p3"), labels(document))
        assertEquals("the view follows the new page", 1, tab.page)
        assertNotNull("a written page has an id", document.model.pages[1].id)
        assertTrue("an inserted sheet is blank", document.model.pages[1].layers.all { it.elements.isEmpty() })
    }

    @Test
    fun `insert before a page lands above it`() {
        val document = threePages()
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)

        assertTrue(pages.insertPageAt(2, before = true))

        assertEquals(listOf("p1", "p2", "-", "p3"), labels(document))
        assertEquals("the view follows the new page", 2, tab.page)
    }

    @Test
    fun `an insert is one undo step`() {
        val document = threePages()
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)

        assertTrue(pages.insertPageAt(1, before = false))
        assertEquals(4, document.pageCount)
        assertTrue("one step to undo", tab.undo())
        assertEquals(3, document.pageCount)
        assertEquals(listOf("p1", "p2", "p3"), labels(document))
    }

    @Test
    fun `duplicating a page keeps its content under an identity of its own`() {
        val document = threePages()
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)

        assertTrue(pages.duplicatePage(0))

        assertEquals("the copy sits straight after its original", listOf("p1", "p1", "p2", "p3"), labels(document))
        assertEquals("and the view is on the copy", 1, tab.page)
        val original = document.model.pages[0]
        val copy = document.model.pages[1]
        assertNotEquals("the copy is not the same page to a merge", original.id, copy.id)
        val originalElementId = (original.layers.first().elements.first() as TextElement).id
        val copyElementId = (copy.layers.first().elements.first() as TextElement).id
        assertNotEquals("nor are its elements", originalElementId, copyElementId)
        assertTrue("one step to undo", tab.undo())
        assertEquals(listOf("p1", "p2", "p3"), labels(document))
    }

    @Test
    fun `a one-page document can still be duplicated`() {
        val document = document("single.xopp", listOf(page("only", "pg_only")))
        val tabs = DesktopTabs().also { it.open(document) }
        val pages = pagesOf(tabs)

        assertTrue(pages.duplicatePage(0))

        assertEquals(2, document.pageCount)
        assertEquals(listOf("only", "only"), labels(document))
    }

    @Test
    fun `a duplicate keeps the pdf page its original stood for`() {
        val pdf = File(temp.root, "source.pdf")
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle(595f, 842f)))
            doc.save(pdf)
        }
        val reference = Background.Pdf(pdf.absolutePath, pageNo = 0, domain = "absolute")
        val document = document("pdf.xopp", listOf(page("p1", "pg_1", reference), page("p2", "pg_2")))
        val tabs = DesktopTabs().also { it.open(document) }
        val pages = pagesOf(tabs)

        assertTrue(pages.duplicatePage(0))

        assertEquals("the copy sits on the same sheet", reference, document.model.pages[1].background)
        assertNotNull("and the document still resolves its PDF", document.pdfSource())
    }

    @Test
    fun `an out-of-range insert or duplicate changes nothing`() {
        val document = threePages()
        val tabs = DesktopTabs().also { it.open(document) }
        val pages = pagesOf(tabs)

        assertEquals(3, document.pageCount)
        assertTrue("nothing to insert after the last page plus one", !pages.insertPageAt(3, before = false))
        assertTrue("nor duplicate a page that does not exist", !pages.duplicatePage(-1))
        assertEquals(3, document.pageCount)
        assertTrue("and nothing was recorded", !tabs.active!!.canUndo)
    }
}
