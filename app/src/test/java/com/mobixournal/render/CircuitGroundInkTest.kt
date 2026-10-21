package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ground is the library's single-terminal symbol: its ink has to start where its one pin is and
 * hang from there, or a wire ending on that pin would meet the symbol somewhere along its body.
 */
class CircuitGroundInkTest {

    @Test
    fun `ground ink starts on its pin and hangs along its rotation`() {
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

        assertEquals("the lead starts on the pin", 50.0, points.first().x, 1e-6)
        assertEquals(100.0, points.first().y, 1e-6)
        // Dragged downwards, so the symbol hangs downwards for one default length (48pt).
        assertEquals("the plates end the drag", 148.0, points.maxOf { it.y }, 1e-6)
        assertTrue("the symbol keeps its width", points.all { it.x in 30.0..70.0 })
    }

    /** A ground written before the symbol became single-terminal still draws between its two pins. */
    @Test
    fun `legacy two-pin ground still inks across its pins`() {
        val legacy = CircuitElement(
            symbol = "GROUND",
            positionX = 100.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p1", -50.0, 0.0), CircuitPin("p2", 50.0, 0.0)),
            id = "c1",
        )

        val ink = CircuitInk.ink(legacy)
        assertNotNull(ink)
        val points = (ink!!.single() as Stroke).points
        assertEquals("the lead starts on p1", 50.0, points.first().x, 1e-6)
        assertEquals("the plates end on p2", 150.0, points.maxOf { it.x }, 1e-6)
    }
}
