package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a stylus telemetry reading counts.
 *
 * The rule exists because `0` is ambiguous on this platform: a tablet with no tilt axis reports `0`,
 * and so does a pen held upright — the ordinary writing posture. Both facts are needed to tell them
 * apart, and `MotionEvent.getAxisValue` throws outright for an axis its device does not declare, so the
 * decision has to be made before the value is read at all.
 */
class StrokeTelemetryTest {

    @Test
    fun `a declared axis reporting zero keeps the zero`() {
        val tilt = StrokeTelemetry.recorded(declared = true) { 0f }

        assertEquals("a pen held upright is a reading, not an absence", 0f, tilt)
    }

    @Test
    fun `an undeclared axis stores nothing and is never read`() {
        val azimuth = StrokeTelemetry.recorded(declared = false) {
            throw AssertionError("a device without the axis must not be asked for its value")
        }

        assertNull("no such axis: the model says so by saying nothing", azimuth)
    }

    @Test
    fun `a declared reading passes through unconverted`() {
        assertEquals(0.248f, StrokeTelemetry.recorded(declared = true) { 0.248f })
        assertEquals(
            "azimuth is signed: the pen leans either way around the screen normal",
            -2.5f,
            StrokeTelemetry.recorded(declared = true) { -2.5f },
        )
    }
}
