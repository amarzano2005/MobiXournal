package com.mobixournal.desktop

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import com.mobixournal.render.PdfOutline
import com.mobixournal.render.PdfTextIndex
import com.mobixournal.render.PdfWord
import com.mobixournal.render.SearchHit
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **The window's search**: what a query finds, where the highlight lands, and which page the answer names.
 *
 * The engine itself is mobix-core's and tested there; what this file pins is the window's half — the PDF
 * mapping (a reordered page is searched through **its own** `Background.Pdf.pageNo`), the formula, and the
 * navigation arithmetic `Enter` and a click on a result run.
 */
class DesktopSearchTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun plain(): Background = Background.Solid(0xFFFFFFFF.toInt(), "plain")

    private fun page(elements: List<Element> = emptyList(), background: Background = plain()): Page = Page(
        width = 595.0,
        height = 842.0,
        background = background,
        layers = listOf(Layer(elements)),
    )

    private fun text(content: String, y: Double = 10.0): TextElement = TextElement(
        font = "Sans",
        size = 12.0,
        x = 10.0,
        y = y,
        color = 0xFF000000.toInt(),
        content = content,
    )

    private fun formula(source: String): MathBlockElement = MathBlockElement(
        left = 40.0,
        top = 50.0,
        right = 160.0,
        bottom = 82.0,
        source = source,
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

    private fun tab(name: String, pages: List<Page>): DesktopTab {
        val tabs = DesktopTabs()
        tabs.open(document(name, pages))
        return tabs.active!!
    }

    private fun search(tab: DesktopTab, query: String): DesktopSearch = DesktopSearchEngine.run(tab, query)

    @Test
    fun `a text box matches and carries its line as the snippet`() {
        val tab = tab("text.xopp", listOf(page(listOf(text("The quick brown fox")))))

        val answer = search(tab, "brown")

        assertEquals("one match", 1, answer.total)
        assertEquals("on the first page", 0, answer.hits.first().pageIndex)
        assertTrue("the snippet reads the line", answer.hits.first().snippet.contains("brown"))
        assertTrue("and the box is the match's geometry", answer.hits.first().boxes.isNotEmpty())
    }

    @Test
    fun `a formula is found through its source`() {
        val tab = tab("formula.xopp", listOf(page(listOf(formula("\\frac{1}{2}")))))

        val answer = search(tab, "frac")

        assertEquals(1, answer.total)
        val hit = answer.hits.first()
        assertEquals("the whole block is the match", 40.0, hit.boxes.first().left, 0.0)
        assertEquals(160.0, hit.boxes.first().right, 0.0)
        assertTrue("the snippet is the source", hit.snippet.contains("frac"))
    }

    /**
     * The window can reorder pages, so document page 0 is not PDF page 0. The search must ask each page for
     * **its own** number: the engine is handed words by document index, and this is the mapping that makes
     * that true (`DesktopSearchEngine.pdfWordsOf`).
     */
    @Test
    fun `a reordered PDF page is searched through its own page number`() {
        val pdfPage = { no: Int -> Background.Pdf(filename = "paper.pdf", pageNo = no, domain = null) }
        // Document order: the PDF's second page first, then its first — a reorder the window allows.
        val tab = tab("paper.xopp", listOf(page(background = pdfPage(1)), page(background = pdfPage(0))))
        tab.pdfIndex = DesktopPdfIndex(
            PdfTextIndex(
                listOf(
                    listOf(PdfWord("alpha", 10.0, 10.0, 30.0, 20.0)),
                    listOf(PdfWord("beta", 10.0, 40.0, 30.0, 50.0)),
                ),
            ),
            PdfOutline.EMPTY,
        )

        val beta = search(tab, "beta")
        val alpha = search(tab, "alpha")

        assertEquals("the PDF's page 1 words are on document page 0", 0, beta.hits.single().pageIndex)
        assertEquals("and the PDF's page 0 words on document page 1", 1, alpha.hits.single().pageIndex)
    }

    @Test
    fun `a blank or one-character query searches nothing`() {
        val tab = tab("short.xopp", listOf(page(listOf(text("alpha beta")))))

        assertEquals(0, search(tab, " ").total)
        assertEquals(0, search(tab, "a").total)
        assertTrue("nothing is active", !search(tab, " ").isActive)
    }

    @Test
    fun `enter wraps past the last match and shift-enter past the first`() {
        val hits = listOf(SearchHit(0, emptyList()), SearchHit(1, emptyList()), SearchHit(2, emptyList()))
        var answer = DesktopSearch(query = "q", hits = hits, current = 2)

        assertEquals("one-based counter", 3, answer.position)

        answer = answer.next()
        assertEquals("wrap to the first", 0, answer.current)
        assertEquals(1, answer.position)

        answer = answer.previous()
        assertEquals("and back past it", 2, answer.current)

        assertEquals("a row click clamps", 2, answer.at(99).current)
        assertEquals(0, answer.at(-4).current)
    }

    @Test
    fun `a hit names its page so the shell can go there`() {
        val tab = tab(
            "later.xopp",
            listOf(page(listOf(text("nothing here"))), page(listOf(text("find me"))), page()),
        )

        val answer = search(tab, "find me")

        val hit = answer.hits.single()
        assertEquals(1, hit.pageIndex)
        assertNotNull("the shell has a page to select", hit)
    }
}
