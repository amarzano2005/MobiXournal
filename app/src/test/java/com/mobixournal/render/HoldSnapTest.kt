package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hold-to-snap as an object: when the timer is armed, what it finds, and what it keeps.
 *
 * The canvas hands [HoldSnap] its clock, so a "rest of 300 ms" is a captured `Runnable` run here rather
 * than a `postDelayed` on a device, and the whole state machine is exercised on the JVM.
 */
class HoldSnapTest {

    /** A stand-in for `View.postDelayed`/`removeCallbacks`: it keeps the armed timer and can run it. */
    private class Clock {
        var armed: Runnable? = null
            private set
        var delayMs = -1L
            private set
        var armedCount = 0
            private set

        fun schedule(runnable: Runnable, delayMs: Long) {
            armed = runnable
            this.delayMs = delayMs
            armedCount++
        }

        fun unschedule(runnable: Runnable) {
            if (armed === runnable) armed = null
        }

        /** The tip rests: the armed timer fires. */
        fun rest() {
            val runnable = armed ?: return
            armed = null
            runnable.run()
        }
    }

    private val clock = Clock()
    private var points = listOf<StrokePoint>()
    private var freehand = true
    private var inking = true
    private var repaints = 0

    private val holdSnap = HoldSnap(
        schedule = clock::schedule,
        unschedule = clock::unschedule,
        freehand = { freehand },
        inking = { inking },
        stroke = { points },
        onSnapped = { repaints++ },
    ).also { it.enabled = true }

    /** The hold's own switch: while it is off the tip may rest as long as it likes and the stroke stays ink. */
    @Test
    fun `a resting tip is left alone while the hold switch is off`() {
        holdSnap.enabled = false
        holdSnap.start(straight(1))

        assertNull("no timer is armed", holdSnap.pending)
        assertNull("and nothing is previewed", holdSnap.preview(12))
    }

    /** A straight, slow stroke: the recogniser reads it as a segment. */
    private fun straight(count: Int = 12): List<StrokePoint> =
        (0 until count).map { StrokePoint(it * 12.0, 0.0, 1.5) }

    @Test
    fun `a stroke arms the dwell from where the tip landed`() {
        holdSnap.start(listOf(StrokePoint(30.0, 40.0, 1.5)))

        assertEquals(ShapeDwell.MS, clock.delayMs)
        assertEquals(30.0, holdSnap.anchorX, 1e-9)
        assertEquals(40.0, holdSnap.anchorY, 1e-9)
    }

    @Test
    fun `a move inside the slop keeps the timer, a move past it re-arms`() {
        holdSnap.start(listOf(StrokePoint(0.0, 0.0, 1.5)))
        val afterStart = clock.armedCount

        holdSnap.advance(listOf(StrokePoint(0.0, 0.0, 1.5), StrokePoint(1.0, 0.0, 1.5)))
        assertEquals("tremor while held is not a move", afterStart, clock.armedCount)

        holdSnap.advance(listOf(StrokePoint(0.0, 0.0, 1.5), StrokePoint(9.0, 0.0, 1.5)))
        assertEquals(afterStart + 1, clock.armedCount)
        assertEquals(9.0, holdSnap.anchorX, 1e-9)
    }

    @Test
    fun `a rest over a straight stroke snaps it before lift-off`() {
        points = straight()
        holdSnap.start(points)
        clock.rest()

        assertNotNull("a straight stroke is a segment", holdSnap.figure)
        assertTrue(holdSnap.snapped)
        assertEquals(1, repaints)
        assertNotNull("and the figure is what gets painted", holdSnap.preview(points.size))
    }

    @Test
    fun `ink the pen kept drawing is not replaced by the figure`() {
        points = straight()
        holdSnap.start(points)
        clock.rest()

        val grown = points + StrokePoint(400.0, 300.0, 1.5)
        holdSnap.advance(grown)

        assertNull("the user's own ink comes back", holdSnap.figure)
        assertTrue(!holdSnap.snapped)
        assertNull(holdSnap.preview(grown.size))
    }

    @Test
    fun `lift-off keeps the figure only while the stroke has not grown`() {
        points = straight()
        holdSnap.start(points)
        clock.rest()

        assertNull("a grown stroke is re-read by the recogniser", holdSnap.take(points.size + 1))

        holdSnap.start(points)
        clock.rest()
        assertNotNull("and a stroke that did not grow keeps the figure", holdSnap.take(points.size))
    }

    @Test
    fun `taking the figure ends the hold`() {
        points = straight()
        holdSnap.start(points)
        clock.rest()
        holdSnap.take(points.size)

        assertNull(holdSnap.figure)
        assertTrue(!holdSnap.snapped)
        assertNull("nothing left to paint", holdSnap.preview(points.size))
    }

    @Test
    fun `no timer is armed when the stroke may not snap`() {
        freehand = false
        holdSnap.start(listOf(StrokePoint(0.0, 0.0, 1.5)))
        holdSnap.advance(listOf(StrokePoint(0.0, 0.0, 1.5), StrokePoint(50.0, 0.0, 1.5)))

        assertEquals("a shape gesture or a recogniser switched off never arms", 0, clock.armedCount)
    }

    @Test
    fun `a tool that lays no ink is not snapped`() {
        points = straight()
        inking = false
        holdSnap.start(points)
        clock.rest()

        assertNull(holdSnap.figure)
        assertEquals(0, repaints)
    }

    @Test
    fun `a reset drops the timer and the figure`() {
        points = straight()
        holdSnap.start(points)
        clock.rest()

        holdSnap.reset()

        assertNull(clock.armed)
        assertNull(holdSnap.figure)
        assertTrue(!holdSnap.snapped)
    }
}
