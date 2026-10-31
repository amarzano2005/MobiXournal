package com.mobixournal.desktop

import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.Bounds
import com.mobixournal.render.HandwritingRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * **The window's model**, asked for real: the bridge either reads ink with Windows' own recognizer or the
 * test says so and stops.
 *
 * This is a live probe, in the shape `DesktopFilePickerTest` uses for the shell's dialogs: the binding is
 * the thing that can be wrong (a vtable slot, a struct's passing rule, an `HSTRING`), and no fake would
 * catch it. A machine without the optional handwriting feature skips rather than fails — there, the pure
 * engine is the answer and that is not a bug.
 */
class WinInkTest {

    private fun stroke(vararg coords: Double): Stroke {
        require(coords.size % 2 == 0)
        val points = (coords.indices step 2).map { StrokePoint(coords[it], coords[it + 1], 1.5) }
        return Stroke(Tool.PEN, 0xFF000000.toInt(), null, points, true)
    }

    @Test
    fun theSystemRecognizerReadsAPrintedWord() {
        assumeTrue("no Windows recognizer on this machine", WinInk.recognizerCount() > 0)

        // A dense, hand-like "l" and "o": what a real stroke carries, not three corner points.
        val line = (0..20).map { 10.0 + it * 0.4 to 10.0 + it * 4.0 }
        val loop = (0..24).map { index ->
            val angle = index * (2 * Math.PI / 24)
            60.0 + 18.0 * Math.sin(angle) to 50.0 - 20.0 * Math.cos(angle)
        }
        val reads = WinInk.recognize(listOf(line, loop))
        println("WININK printed: $reads")

        assertTrue("the model answered something", reads.isNotEmpty())
        assertTrue("and it is not an empty word", reads.first().isNotBlank())
    }

    @Test
    fun theSystemRecognizerReadsConnectedCursive() {
        assumeTrue("no Windows recognizer on this machine", WinInk.recognizerCount() > 0)

        // One continuous line with a ligature valley: an "e" followed by a "c".
        val reads = WinInk.recognize(
            listOf(
                listOf(
                    10.0 to 30.0, 12.0 to 42.0, 18.0 to 46.0, 24.0 to 40.0, 24.0 to 30.0, 20.0 to 34.0,
                    24.0 to 44.0, 30.0 to 40.0, 34.0 to 30.0, 38.0 to 44.0,
                ),
            ),
        )
        println("WININK cursive: $reads")

        assertTrue("the model answered something", reads.isNotEmpty())
    }

    @Test
    fun oneLineCallAnswersOneRegionPerWordItRead() {
        assumeTrue("no Windows recognizer on this machine", WinInk.recognizerCount() > 0)

        // A printed "l" and a printed "o", a gap apart: a line of two words, read in ONE call.
        val line = listOf(
            (0..20).map { 10.0 + it * 0.4 to 10.0 + it * 4.0 },
            (0..24).map { index ->
                val angle = index * (2 * Math.PI / 24)
                60.0 + 18.0 * Math.sin(angle) to 50.0 - 20.0 * Math.cos(angle)
            },
        )
        val regions = WinInk.recognizeRegions(line)
        println("WININK line regions=${regions.map { it.candidates.firstOrNull() to it.bounds }}")

        assertTrue("the line was read as at least one region", regions.isNotEmpty())
        assertTrue("every region says something", regions.all { it.candidates.isNotEmpty() })
        assertTrue(
            "every region carries the box it covers",
            regions.all { it.bounds.right > it.bounds.left && it.bounds.bottom > it.bounds.top },
        )

        // The engine's own job: whatever the model segmented, each **word** gets an entry.
        val words = listOf(
            listOf(stroke(10.0, 10.0, 20.0, 90.0, 50.0, 90.0)),
            listOf(
                stroke(
                    75.0, 10.0, 110.0, 10.0, 125.0, 30.0, 125.0, 70.0, 110.0, 90.0,
                    75.0, 90.0, 60.0, 70.0, 60.0, 30.0, 75.0, 10.0,
                ),
            ),
        )
        val mapped = WinInkEngine.recognizeLine(
            words,
            words.map { HandwritingRecognizer.computeWordBounds(it) },
        )
        assertEquals("one entry per word", 2, mapped.size)
        assertTrue("at least one word came back with text", mapped.any { !it?.text.isNullOrEmpty() })
    }

    @Test
    fun theEngineAnswersTheSeamWithTheModelsReading() {
        assumeTrue("no Windows recognizer on this machine", WinInk.recognizerCount() > 0)

        val word = HandwritingRecognizer.recognizeWord(
            listOf(stroke(10.0, 10.0, 10.0, 90.0, 50.0, 90.0)),
            WinInkEngine,
        )
        println("WININK seam: ${word?.text} ${word?.candidates}")

        assertTrue("the seam hands back a word", word != null && word.text.isNotBlank())
        assertEquals("and its bounds are the word's", 10.0, word!!.bounds.left, 0.0)
        assertEquals(Bounds(10.0, 10.0, 50.0, 90.0), word.bounds)
    }
}
