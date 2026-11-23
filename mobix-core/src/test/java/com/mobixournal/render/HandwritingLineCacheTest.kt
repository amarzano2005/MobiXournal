package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
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
        cache.saveTo(file)
        val restored = HandwritingLineCache()
        restored.loadFrom(file)
        val third = restored.indexDocument(doc2, engine = countingEngine)
        assertEquals(3, third.words(0).size)
        assertEquals(3, calls)
    }
}
