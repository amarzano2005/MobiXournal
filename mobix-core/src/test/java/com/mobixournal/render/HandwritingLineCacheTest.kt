package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HandwritingLineCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun strokeAt(x0: Double, y0: Double, x1: Double, y1: Double): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(x0, y0, 0.5), StrokePoint(x1, y1, 0.5)),
        uniformWidth = true,
    )

    @Test
    fun `unchanged lines hit cache across edits and restarts`() {
        val line1 = strokeAt(20.0, 40.0, 80.0, 40.0)
        val line2 = strokeAt(20.0, 120.0, 80.0, 120.0)
        val doc1 = Document(pages = listOf(Page(612.0, 792.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), listOf(Layer(listOf(line1, line2))))))

        var calls = 0
        val countingEngine = object : InkLineEngine {
            override fun recognize(strokes: List<Stroke>, bounds: Bounds): HandwrittenWord? {
                calls++
                return HandwrittenWord("word", bounds, 1.0, listOf("word"))
            }

            override fun recognizeLine(words: List<List<Stroke>>, bounds: List<Bounds>): List<HandwrittenWord?> {
                calls++
                return bounds.mapIndexed { i, b -> HandwrittenWord("line$i", b, 1.0, listOf("line$i")) }
            }
        }

        val cache = HandwritingLineCache()
        val first = cache.indexDocument(doc1, engine = countingEngine)
        assertEquals(2, first.words(0).size)
        assertEquals(2, calls)

        // Adding a third line only recognizes the new line (1 extra call instead of 3).
        val line3 = strokeAt(20.0, 200.0, 80.0, 200.0)
        val doc2 = Document(pages = listOf(Page(612.0, 792.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), listOf(Layer(listOf(line1, line2, line3))))))
        val second = cache.indexDocument(doc2, engine = countingEngine)
        assertEquals(3, second.words(0).size)
        assertEquals(3, calls)

        // Persisting and loading into a fresh cache serves all 3 lines with zero engine calls.
        val file = temp.newFile("hw_cache.bin")
        assertTrue(cache.isDirty)
        cache.saveTo(file)
        assertFalse(cache.isDirty)
        val restored = HandwritingLineCache()
        restored.loadFrom(file)
        val third = restored.indexDocument(doc2, engine = countingEngine)
        assertEquals(3, third.words(0).size)
        assertEquals(3, calls)
    }

    @Test
    fun `unchanged pages reuse prior index list and clean cache skips disk rewrite`() {
        val page0 = Page(612.0, 792.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), listOf(Layer(listOf(strokeAt(20.0, 40.0, 80.0, 40.0)))))
        val page1 = Page(612.0, 792.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), listOf(Layer(listOf(strokeAt(20.0, 120.0, 80.0, 120.0)))))
        val doc1 = Document(pages = listOf(page0, page1))

        val engine = object : InkLineEngine {
            override fun recognize(strokes: List<Stroke>, bounds: Bounds): HandwrittenWord? =
                HandwrittenWord("w", bounds, 1.0, listOf("w"))

            override fun recognizeLine(words: List<List<Stroke>>, bounds: List<Bounds>): List<HandwrittenWord?> =
                bounds.map { HandwrittenWord("line", it, 1.0, listOf("line")) }
        }

        val cache = HandwritingLineCache()
        val first = cache.indexDocument(doc1, engine = engine)
        val file = temp.newFile("skip_write.bin")
        cache.saveTo(file)
        assertFalse(cache.isDirty)
        val lenBefore = file.length()
        assertTrue(lenBefore > 0L)

        // Overwrite sentinel on disk and call saveTo while clean: clean cache skips rewriting.
        file.writeBytes(byteArrayOf(1, 2, 3))
        cache.saveTo(file)
        assertEquals(3L, file.length())

        // Re-indexing doc2 with identical page0 instance carries page0 list forward by reference.
        val page1Edited = page1.copy(layers = listOf(Layer(listOf(strokeAt(20.0, 200.0, 80.0, 200.0)))))
        val doc2 = Document(pages = listOf(page0, page1Edited))
        val second = cache.indexDocument(doc2, engine = engine, previousDoc = doc1, previousIndex = first)
        assertSame(first.words(0), second.words(0))
        assertTrue(cache.isDirty)
        assertTrue(DocumentSearch.hasAnyStroke(doc2))
    }
}
