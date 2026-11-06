package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of recognition that is shared: which strokes form a word, and the [InkWordEngine] seam.
 *
 * The engines' own accuracy is not tested here — the fallback's templates are the app's tests, and ML
 * Kit's model is Google's. What is tested is the contract the desktop window leans on: an index can be
 * built page by page over a [PageSource], it never reads past a cancellation, and the engine a call
 * site names is the one that answers.
 */
class HandwritingEngineTest {

    private fun stroke(vararg coords: Double): Stroke {
        require(coords.size % 2 == 0)
        val pts = (coords.indices step 2).map { StrokePoint(coords[it], coords[it + 1], 1.5) }
        return Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = null,
            points = pts,
            uniformWidth = true,
        )
    }

    private fun page(vararg elements: Element): Page = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    private fun fixedEngine(text: String) = InkWordEngine { _, bounds -> HandwrittenWord(text, bounds) }

    @Test
    fun readsAPageSourceOnePageAtATime() {
        val source = FakeSource(
            listOf(
                page(stroke(10.0, 10.0, 60.0, 40.0)),
                page(),
                page(stroke(10.0, 100.0, 60.0, 130.0)),
            ),
        )

        val index = HandwritingIndex.build(source, engine = fixedEngine("appunti"))

        assertEquals(listOf(0, 1, 2), source.opened)
        assertEquals("appunti", index.words(0).single().text)
        assertTrue("a blank page reads nothing", index.words(1).isEmpty())
        assertEquals("appunti", index.words(2).single().text)
        assertTrue(index.hasAnyText)
    }

    @Test
    fun aCancelledWalkReadsNoFurtherThanThePageItGaveUpOn() {
        val source = FakeSource(listOf(page(stroke(10.0, 10.0, 60.0, 40.0)), page(), page()))

        val index = HandwritingIndex.build(
            source,
            isCancelled = { source.opened.isNotEmpty() },
            engine = fixedEngine("appunti"),
        )

        assertEquals("the walk stops after the page it was on", listOf(0), source.opened)
        assertTrue("a partial index is never handed back as if it were whole", !index.hasAnyText)
    }

    @Test
    fun theEngineACallSiteNamesIsTheOneThatAnswers() {
        val strokes = listOf(stroke(10.0, 10.0, 60.0, 40.0))
        val previous = InkEngines.platform
        try {
            InkEngines.platform = fixedEngine("platform")
            assertEquals("the shell's engine is the default", "platform", HandwritingRecognizer.recognizeWord(strokes)?.text)

            val named = HandwritingRecognizer.recognizeWord(strokes, fixedEngine("named"))
            assertEquals("an explicit engine overrides the shell's", "named", named?.text)
        } finally {
            InkEngines.platform = previous
        }
    }

    @Test
    fun aLineReadingEngineIsAskedOnceForTheWholeLineAndItsGapsFallBack() {
        val calls = mutableListOf<Int>()
        val lineEngine = object : InkLineEngine {
            override fun recognize(strokes: List<Stroke>, bounds: Bounds): HandwrittenWord? =
                HandwrittenWord("single", bounds)

            override fun recognizeLine(words: List<List<Stroke>>, bounds: List<Bounds>): List<HandwrittenWord?> {
                calls += words.size
                // Reads the first word of the line, leaves the rest to the fallback.
                return words.indices.map { index ->
                    if (index == 0) HandwrittenWord("riga", bounds[index]) else null
                }
            }
        }
        // Two words on one line: a gap wider than the word break between the two strokes.
        val page = page(
            stroke(10.0, 10.0, 40.0, 40.0),
            stroke(120.0, 10.0, 150.0, 40.0),
        )

        val words = HandwritingRecognizer.recognize(page, engine = lineEngine)

        assertEquals("one call for the line, not one per word", listOf(2), calls)
        assertEquals("the line's own reading survives", "riga", words.first().text)
        assertTrue("the word the line left unread is still found", words.size == 2 && words[1].text.isNotEmpty())
    }

    /**
     * A page can be handed out as **lines** without being read. That is what lets a shell keep a reading
     * against the line's own ink and re-read only the line an edit touched ([InkLine.key]).
     */
    @Test
    fun aPageIsSegmentedIntoLinesThatCarryTheirOwnKey() {
        val page = page(
            stroke(10.0, 10.0, 60.0, 40.0),
            stroke(10.0, 200.0, 60.0, 230.0),
        )

        val lines = HandwritingRecognizer.lines(page)
        val again = HandwritingRecognizer.lines(page)

        assertEquals("two lines", 2, lines.size)
        assertTrue("each carries its words", lines.all { it.words.isNotEmpty() })
        assertEquals("the same ink is the same key", lines[0].key, again[0].key)
        assertTrue("and two lines are two keys", lines[0].key != lines[1].key)
        assertEquals(
            "a line reads on its own",
            listOf("appunti"),
            HandwritingRecognizer.recognize(lines[0], fixedEngine("appunti")).map { it.text },
        )
    }

    @Test
    fun theDocumentOverloadStillBuildsAnIndexForTheTablet() {
        val doc = Document(pages = listOf(page(stroke(10.0, 10.0, 60.0, 40.0))))

        val index = HandwritingIndex.build(doc, engine = fixedEngine("ciao"))

        assertEquals("ciao", index.words(0).single().text)
    }

    /** A source that records which pages were opened, so "one at a time" is checked, not assumed. */
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
