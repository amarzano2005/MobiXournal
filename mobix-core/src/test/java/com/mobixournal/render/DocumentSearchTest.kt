package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared search engine's own tests — the half both shells depend on.
 *
 * Handwriting's fuzzy matching is still tested in the app, beside the recogniser it serves; what is
 * tested here is everything that is true of a document no matter who is reading it, plus the two
 * properties the window relies on: a formula is found, and a page-by-page document is never
 * materialised whole to search it.
 */
class DocumentSearchTest {

    private fun page(vararg elements: Element): Page = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    private fun document(vararg pages: Page): Document = Document(pages = pages.toList())

    @Test
    fun findsATextBoxAndReadsItsLineAsTheSnippet() {
        val doc = document(page(TextElement("Sans", 10.0, 20.0, 30.0, 0, "Alpha beta")))

        val hits = DocumentSearch.find(doc, null, "beta")

        assertEquals(1, hits.size)
        assertEquals("Alpha beta", hits.single().snippet)
    }

    @Test
    fun findsPdfWordsOnEveryPageTheIndexHasThem() {
        val doc = document(page(), page())
        val pdf = PdfTextIndex(
            listOf(
                listOf(PdfWord("hello", 10.0, 20.0, 40.0, 30.0)),
                listOf(PdfWord("hello", 10.0, 20.0, 40.0, 30.0), PdfWord("again", 45.0, 20.0, 90.0, 30.0)),
            ),
        )

        val hits = DocumentSearch.find(doc, pdf, "hello")

        assertEquals(listOf(0, 1), hits.map { it.pageIndex })
        assertEquals("hello", hits.first().snippet)
    }

    @Test
    fun findsAMathBlocksSourceAndHighlightsItsOwnBox() {
        val block = MathBlockElement(
            left = 40.0,
            top = 100.0,
            right = 200.0,
            bottom = 130.0,
            source = "\\frac{1}{2}",
        )
        val doc = document(page(block))

        val hits = DocumentSearch.find(doc, null, "FRAC")

        assertEquals(1, hits.size)
        assertEquals(listOf(Bounds(40.0, 100.0, 200.0, 130.0)), hits.single().boxes)
        assertEquals("\\frac{1}{2}", hits.single().snippet)
    }

    @Test
    fun findsAMathBlockThroughThePageSourceOverloadToo() {
        val block = MathBlockElement(0.0, 0.0, 120.0, 32.0, source = "x^2 + y^2")
        val source = FakeSource(listOf(page(block)))

        val hits = DocumentSearch.find(source, null, "y^2")

        assertEquals(1, hits.size)
    }

    @Test
    fun aBlankQueryAnswersNothing() {
        val doc = document(page(TextElement("Sans", 10.0, 0.0, 0.0, 0, "alpha")))

        assertTrue(DocumentSearch.find(doc, null, "   ").isEmpty())
    }

    @Test
    fun walksAPageSourceOnePageAtATimeAndNeverPastItsEnd() {
        val source = FakeSource(listOf(page(), page(), page()))

        DocumentSearch.find(source, null, "nothing")

        assertEquals(listOf(0, 1, 2), source.opened)
    }

    @Test
    fun aShellWithHandwritingFindsItsWords() {
        val handwritten = object : SearchableHandwriting {
            override fun words(page: Int): List<HandwrittenWord> = listOf(
                HandwrittenWord("appunti", Bounds(10.0, 20.0, 80.0, 40.0)),
            )

            override val hasAnyText: Boolean = true
        }

        val hits = DocumentSearch.find(document(page()), null, "appunti", handwritten)

        assertEquals(1, hits.size)
        assertEquals("appunti", hits.single().snippet)
    }

    /** A source that remembers which pages were opened, so "one at a time" is checked, not assumed. */
    private class FakeSource(private val pages: List<Page>) : PageSource {

        val opened = mutableListOf<Int>()

        override val pageCount: Int get() = pages.size

        override fun pageId(index: Int): String? = "pg_$index"

        override fun page(index: Int): Page {
            opened += index
            return pages[index]
        }
    }
}
