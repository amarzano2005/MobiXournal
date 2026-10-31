package com.mobixournal.render

import com.mobixournal.audio.withAudio
import com.mobixournal.format.model.AudioRef
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The **Play-object tap's** pick: which stroke a click on the page replays.
 *
 * A selection asks "what is under the pointer?" and may answer with a box; the audio tap asks "where in
 * the recording is the ink I touched?", and a box is not an answer to that. The strokes here are laid out
 * the way handwriting lays them out — a long diagonal whose rectangle covers everything, words inside it,
 * one stroke crossing another — and each case is a tap that used to play the wrong word (or nothing at
 * all, because the stroke the box rule named carried no recording).
 */
class AudioTapTest {

    private var clock = 0

    private fun stroke(vararg pts: Pair<Double, Double>, recorded: Boolean = false): Stroke {
        val built = Stroke(
            Tool.PEN,
            0xFF000000.toInt(),
            "round",
            pts.map { StrokePoint(it.first, it.second, 0.0, dtMs = 0) },
            false,
        )
        if (!recorded) return built
        // Each recorded stroke gets its own offset, so "which recording did it pick" is readable.
        clock += 1_000
        return built.withAudio(AudioRef("take$clock.wav", clock))
    }

    private fun page(vararg elements: Stroke) =
        Page(200.0, 200.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), listOf(Layer(elements.toList())))

    @Test
    fun `a tap inside a diagonal's box replays the ink under it`() {
        // The word first, the diagonal over it: the last-drawn stroke's rectangle covers the whole square,
        // which is what the box rule used to answer with.
        val word = stroke(5.0 to 90.0, 20.0 to 90.0, recorded = true)
        val diagonal = stroke(0.0 to 0.0, 100.0 to 100.0)
        val picked = AudioTap.pickStroke(page(word, diagonal), x = 10.0, y = 90.0)
        assertEquals("the stroke the pointer is on, not the rectangle over it", word, picked)
    }

    @Test
    fun `a stroke drawn over a recorded one does not hide its recording`() {
        val recorded = stroke(0.0 to 50.0, 100.0 to 50.0, recorded = true)
        val crossing = stroke(50.0 to 40.0, 50.0 to 60.0)
        assertEquals(recorded, AudioTap.pickStroke(page(recorded, crossing), x = 50.0, y = 50.0))
    }

    @Test
    fun `the topmost recorded ink wins when two recordings are under the tap`() {
        val lower = stroke(0.0 to 50.0, 100.0 to 50.0, recorded = true)
        val upper = stroke(0.0 to 51.0, 100.0 to 51.0, recorded = true)
        assertEquals(upper, AudioTap.pickStroke(page(lower, upper), x = 50.0, y = 50.5))
    }

    @Test
    fun `a stroke with no recording is still named, so the shell can say why nothing plays`() {
        val plain = stroke(5.0 to 90.0, 20.0 to 90.0)
        val diagonal = stroke(0.0 to 0.0, 100.0 to 100.0)
        assertEquals(plain, AudioTap.pickStroke(page(plain, diagonal), x = 10.0, y = 90.0))
    }

    @Test
    fun `a tap beside the ink is on it, and a tap across the page is not`() {
        val word = stroke(0.0 to 50.0, 100.0 to 50.0, recorded = true)
        val pg = page(word)
        assertEquals("three points away is a hand, not a miss", word, AudioTap.pickStroke(pg, x = 50.0, y = 53.0))
        assertNull("the middle of the desk is nobody's stroke", AudioTap.pickStroke(pg, x = 50.0, y = 70.0))
    }

    @Test
    fun `a tap on the page past every stroke picks nothing`() {
        assertNull(AudioTap.pickStroke(page(stroke(0.0 to 0.0, 10.0 to 10.0)), x = 190.0, y = 190.0))
    }
}
