package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recogniser's jitter filter. Two things matter and nothing else: the tremor gets smaller, and
 * the stroke does not move — its ends stay put and no sample travels further than the clamp, which
 * is what keeps a corner a corner.
 */
class StrokeSmoothingTest {

    private fun line(n: Int, jitter: Double): List<StrokePoint> =
        (0..n).map { i ->
            val t = i.toDouble() / n
            val dy = if (i % 2 == 0) jitter else -jitter
            StrokePoint(20 + 240 * t, 100 + dy, 1.5)
        }

    @Test
    fun `the ends of the stroke are not moved`() {
        val raw = line(60, 2.0)
        val out = StrokeSmoothing.smooth(raw)
        assertEquals(raw.first(), out.first())
        assertEquals(raw.last(), out.last())
        assertEquals(raw.size, out.size)
    }

    @Test
    fun `alternating tremor is averaged away`() {
        val raw = line(120, 1.5)
        val out = StrokeSmoothing.smooth(raw)
        val before = raw.map { abs(it.y - 100) }.average()
        val after = out.map { abs(it.y - 100) }.average()
        assertTrue("expected the tremor to shrink, got $before -> $after", after < before / 2)
    }

    @Test
    fun `no sample is pulled further than the clamp`() {
        // A hard corner: the average lags it, the clamp is what forbids the recogniser from
        // straightening it into a fillet.
        val corner = listOf(
            StrokePoint(0.0, 0.0, 1.5),
            StrokePoint(50.0, 0.0, 1.5),
            StrokePoint(100.0, 0.0, 1.5),
            StrokePoint(100.0, 50.0, 1.5),
            StrokePoint(100.0, 100.0, 1.5),
        )
        val out = StrokeSmoothing.smooth(corner)
        for (i in corner.indices) {
            val shift = hypot(out[i].x - corner[i].x, out[i].y - corner[i].y)
            assertTrue("sample $i moved $shift pt", shift <= StrokeSmoothing.MAX_SHIFT_PT + 1e-9)
        }
        // And the right angle is still a right angle at (100, 0), within the clamp.
        assertEquals(100.0, out[2].x, StrokeSmoothing.MAX_SHIFT_PT)
    }

    @Test
    fun `a stroke too short to filter is passed through untouched`() {
        val two = listOf(StrokePoint(0.0, 0.0, 1.5), StrokePoint(30.0, 40.0, 1.5))
        assertEquals(two, StrokeSmoothing.smooth(two))
    }
}
