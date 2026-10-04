package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PressureCurve] is the desktop Xournal++ pressure mapping — `max(minimum, pressure × multiplier)` —
 * with no curve of its own, so a stroke drawn here carries the same per-point widths a stroke drawn on
 * the desktop would for the same physical press. These tests pin that, plus the pressure-off case.
 */
class PressureCurveTest {

    @Test
    fun aFullPressDrawsAtTheNominalWidth() {
        assertEquals(
            PressureCurve.FULL,
            PressureCurve.penFactor(1f, enabled = true, multiplier = 1f, minimum = 0.05f),
            1e-6f,
        )
    }

    @Test
    fun theMinimumFloorsAZeroPressureSample() {
        assertEquals(
            0.05f,
            PressureCurve.penFactor(0f, enabled = true, multiplier = 1f, minimum = 0.05f),
            1e-6f,
        )
    }

    @Test
    fun pressureMultiplierScalesTheRawValue() {
        // 0.4 raw × 2 must land where 0.8 raw would.
        assertEquals(
            0.8f,
            PressureCurve.penFactor(0.4f, enabled = true, multiplier = 2f, minimum = 0.05f),
            1e-6f,
        )
    }

    @Test
    fun multiplierAboveOneMayExceedTheNominalWidth() {
        // The desktop applies no upper clamp, so a multiplier above 1 thickens past the size setting.
        assertEquals(
            1.8f,
            PressureCurve.penFactor(0.9f, enabled = true, multiplier = 2f, minimum = 0.05f),
            1e-6f,
        )
    }

    @Test
    fun pressureSwitchedOffAlwaysDrawsFullWidthWhateverThePressure() {
        for (p in listOf(0f, 0.2f, 0.5f, 1f)) {
            assertEquals(
                "p=$p",
                PressureCurve.FULL,
                PressureCurve.penFactor(p, enabled = false, multiplier = 1f, minimum = 0.05f),
                1e-6f,
            )
            // The multiplier and floor must not leak through when pressure is off.
            assertEquals(
                PressureCurve.FULL,
                PressureCurve.penFactor(p, enabled = false, multiplier = 2f, minimum = 0.3f),
                1e-6f,
            )
        }
    }
}
