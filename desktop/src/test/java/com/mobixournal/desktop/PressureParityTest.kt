package com.mobixournal.desktop

import com.mobixournal.render.PressureCurve
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **One pressure, three surfaces**: the window's reading filter, the phone's and desktop Xournal++'s are
 * the same arithmetic, so one set of preset numbers means one width everywhere.
 *
 * The window used to shape the reading with its own `tablet.pressureExponent` gamma before the shared
 * filter, and no surface showed that knob any more: two identical presets could therefore draw differently
 * on the two shells. What is checked here is the property that makes a preset transferable — the desktop's
 * factor is `PressureCurve.penFactor` and *nothing else*, a stored exponent no longer reaches it, and the
 * same reading from any device's full scale draws the same width.
 *
 * The device paths themselves are [PressureCurve.reading] at one point each (`TabletInk` for Windows Ink,
 * `TabletWintab` for the driver's packets), and `PressureCurveTest` holds that division.
 */
class PressureParityTest {

    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun handBackTheReading() {
        // The filter is a module-level seam: a test that changed it puts the defaults back.
        PressureFeel.enabled = true
        PressureFeel.multiplier = 1f
        PressureFeel.minimum = PressureCurve.MINIMUM_PRESSURE_DEFAULT
    }

    @Test
    fun `the window's factor is the shared one and nothing else`() {
        PressureFeel.enabled = true
        PressureFeel.multiplier = 1.35f
        PressureFeel.minimum = 0.12f
        for (step in 0..20) {
            val pressure = step / 20f
            assertEquals(
                "p=$pressure",
                PressureCurve.penFactor(pressure, enabled = true, multiplier = 1.35f, minimum = 0.12f),
                PressureFeel.factor(pressure),
                1e-6f,
            )
        }
    }

    @Test
    fun `an exponent left by an older build no longer shapes the reading`() {
        val file = File(temp.root, "legacy.properties")
        file.writeText(
            """
            tablet.pressureExponent=1.6
            tablet.pressureMultiplier=1.35
            tablet.minimumPressure=0.12
            """.trimIndent(),
        )
        val policy = DesktopSettingsState(DesktopSettings(file))
        policy.reload()
        // The stored gamma is ignored: the reading reaches the filter untouched, so a light press draws
        // 0.5 × 1.35 and not the 0.5^1.6 × 1.35 the retired curve would have produced.
        assertEquals("a light press is not bent", 0.675f, PressureFeel.factor(0.5f), 1e-5f)
        assertEquals(
            "the shared filter is what decides",
            PressureCurve.penFactor(0.5f, enabled = true, multiplier = 1.35f, minimum = 0.12f),
            PressureFeel.factor(0.5f),
            1e-6f,
        )
    }

    @Test
    fun `the same preset numbers draw the same width from any device's reading`() {
        // A preset as both shells store it, and one physical press reported on three full scales.
        val minimum = 0.08f
        val multiplier = 1.5f
        val base = 2f
        val wintab = PressureCurve.widthPt(
            base,
            PressureCurve.reading(9_830f, 16_383f),
            enabled = true,
            multiplier = multiplier,
            minimum = minimum,
        )
        val ink = PressureCurve.widthPt(
            base,
            PressureCurve.reading(614f, 1_024f),
            enabled = true,
            multiplier = multiplier,
            minimum = minimum,
        )
        val android = PressureCurve.widthPt(
            base,
            PressureCurve.reading(0.6f, 1f),
            enabled = true,
            multiplier = multiplier,
            minimum = minimum,
        )
        assertEquals(wintab, ink, 0.02)
        assertEquals(wintab, android, 0.02)
        // And the floor still means what the desktop says it means: a press below it draws the floor.
        assertTrue(PressureCurve.widthPt(base, 0.01f, true, multiplier, minimum) >= base * minimum - 1e-6)
    }
}
