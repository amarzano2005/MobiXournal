package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ground's ink must start on its `p1` — the terminal a wire meets — and end on `p2`, so a wire that
 * lands on either anchor meets the symbol exactly there and the plates hang off the far end.
 */
class CircuitGroundInkTest {

    @Test
    fun `ground ink runs from p1 to p2 along the drag`() {
        val ground = CircuitElementFactory.circuitElementFor(
            symbol = "GROUND",
            startX = 50.0,
            startY = 100.0,
            endX = 50.0,
            endY = 148.0,
        )!!

        val ink = CircuitInk.ink(ground)!!
        assertEquals(1, ink.size)
        val points = (ink.single() as Stroke).points

        assertEquals("the lead starts on the terminal", 50.0, points.first().x, 1e-6)
        assertEquals(100.0, points.first().y, 1e-6)
        assertEquals("the plates end on the drag's far end", 148.0, points.maxOf { it.y }, 1e-6)
        assertTrue("the symbol keeps its width", points.all { it.x in 30.0..70.0 })
    }

    /** A ground saved while the symbol briefly had one pin still inks from that pin. */
    @Test
    fun `a single-anchor ground inks out from its pin`() {
        val single = CircuitElement(
            symbol = "GROUND",
            positionX = 100.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p1", 0.0, 0.0)),
            id = "c1",
        )

        val ink = CircuitInk.ink(single)
        assertNotNull(ink)
        val points = (ink!!.single() as Stroke).points
        assertEquals("the lead starts on the pin", 100.0, points.first().x, 1e-6)
        assertEquals(100.0, points.first().y, 1e-6)
        assertEquals("it is drawn at the library's default size", 148.0, points.maxOf { it.x }, 1e-6)
    }
}
