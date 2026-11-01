package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pen's pressure as an object: the three settings it owns, and that its width is exactly the shared
 * curve's — the object holds *which* numbers are used, never what a pressure means.
 */
class PenPressureTest {

    @Test
    fun `the defaults draw a full press at the tool's width`() {
        val pen = PenPressure()

        assertEquals(2.0, pen.widthPt(baseWidthPt = 2f, pressure = 1f), 1e-9)
    }

    @Test
    fun `pressure switched off draws every sample at the tool's width`() {
        val pen = PenPressure(enabled = false, minimum = 0.1f)

        assertEquals(2.0, pen.widthPt(2f, pressure = 0.2f), 1e-9)
        assertEquals(2.0, pen.widthPt(2f, pressure = 0f), 1e-9)
    }

    @Test
    fun `the multiplier scales the raw reading`() {
        val pen = PenPressure(multiplier = 2f)

        assertEquals(
            PressureCurve.widthPt(1f, 0.5f, true, 2f, PressureCurve.MINIMUM_PRESSURE_DEFAULT),
            pen.widthPt(1f, 0.5f),
            1e-9,
        )
    }

    @Test
    fun `the floor holds a zero reading at a light line`() {
        val pen = PenPressure(minimum = 0.2f)

        assertEquals(0.4, pen.widthPt(2f, pressure = 0f), 1e-6)
    }

    @Test
    fun `a setting changed mid-session applies to the next sample`() {
        val pen = PenPressure()
        val above = pen.widthPt(2f, 0.5f)

        pen.minimum = 0.5f
        assertEquals("half a press is still above a 0.5 floor", above, pen.widthPt(2f, 0.5f), 1e-9)

        pen.multiplier = 0.25f
        assertEquals(
            "and the next sample uses both new numbers",
            PressureCurve.widthPt(2f, 0.5f, true, 0.25f, 0.5f),
            pen.widthPt(2f, 0.5f),
            1e-9,
        )
    }
}
