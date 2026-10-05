package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun theWidthIsTheToolWidthTimesTheFactor() {
        // 2pt at 0.4 raw pressure with no scaling: 0.8pt.
        assertEquals(
            0.8,
            PressureCurve.widthPt(baseWidthPt = 2f, pressure = 0.4f, enabled = true, multiplier = 1f, minimum = 0.05f),
            1e-6,
        )
    }

    @Test
    fun raisingTheMultiplierThickensTheDrawnStroke() {
        // The point of the control: a light writer must be able to make a stroke noticeably thicker
        // without touching the size setting. If this ever stops holding, the slider is inert again.
        val plain = PressureCurve.widthPt(2f, 0.3f, enabled = true, multiplier = 1f, minimum = 0.05f)
        val boosted = PressureCurve.widthPt(2f, 0.3f, enabled = true, multiplier = 4f, minimum = 0.05f)
        assertEquals(0.6, plain, 1e-6)
        assertEquals(2.4, boosted, 1e-6)
        assertTrue("the multiplier must change the width", boosted > plain)
    }

    @Test
    fun raisingTheFloorThickensTheLightestPartOfAStroke() {
        val light = PressureCurve.widthPt(2f, 0.02f, enabled = true, multiplier = 1f, minimum = 0.05f)
        val floored = PressureCurve.widthPt(2f, 0.02f, enabled = true, multiplier = 1f, minimum = 0.5f)
        assertEquals(0.1, light, 1e-6)
        assertEquals(1.0, floored, 1e-6)
        assertTrue("the floor must change the width of a light sample", floored > light)
    }

    @Test
    fun theControlRangesAreTheDesktopsOwn() {
        // Taken from desktop Xournal++'s settings dialog (`adjustmentMinimumPressure` 0.01–1,
        // `adjustmentPressureMultiplier` 0.5–4): the range has to be wide enough that the controls
        // can reach a visibly different stroke at both ends.
        assertEquals(0.01f, PressureCurve.MINIMUM_PRESSURE_MIN, 1e-6f)
        assertEquals(1f, PressureCurve.MINIMUM_PRESSURE_MAX, 1e-6f)
        assertEquals(0.5f, PressureCurve.MULTIPLIER_MIN, 1e-6f)
        assertEquals(4f, PressureCurve.MULTIPLIER_MAX, 1e-6f)
    }

    @Test
    fun switchingPressureOffDrawsEverySampleAtTheSizeSetting() {
        // The promise the switch makes: a stroke and a figure come out the same thickness, so neither
        // the multiplier nor the floor may leak through.
        for (p in listOf(0f, 0.2f, 0.5f, 1f)) {
            assertEquals(
                "p=$p",
                2.0,
                PressureCurve.widthPt(2f, p, enabled = false, multiplier = 4f, minimum = 0.9f),
                1e-6,
            )
        }
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
