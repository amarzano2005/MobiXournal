package com.mobixournal.desktop

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Windows Ink's vocabulary, without a tablet.
 *
 * Two things are checked here, and they are the two that could be wrong silently: the **structure
 * layout** (a `PenPointerInfo` JNA measured differently from the SDK would read a pressure out of the
 * wrong four bytes and call it a pen) and the **units** (0–1024 to 0–1, degrees to radians, "declared"
 * versus "reported zero" — the rule `docs/stylus.md` states for every tablet axis).
 */
class TabletInkTest {

    /** The offsets `windows.ui.input.h` gives `POINTER_PEN_INFO` on x64. */
    private companion object {
        const val POINTER_INFO_BYTES = 96
        const val PEN_INFO_BYTES = 120
        const val PEN_FLAGS = 96
        const val PEN_MASK = 100
        const val PRESSURE = 104
        const val ROTATION = 108
        const val TILT_X = 112
        const val TILT_Y = 116
    }

    @Test
    fun `the pen structure is the SDK's 120 bytes`() {
        val info = PenPointerInfo()
        assertEquals("POINTER_INFO", POINTER_INFO_BYTES, info.pointerInfo.size())
        assertEquals("POINTER_PEN_INFO", PEN_INFO_BYTES, info.size())

        // Written through JNA's own layout and read back at the header's offsets: the fields land where
        // Windows will put them, not merely in the order they were declared.
        info.penFlags = 4
        info.penMask = 0x0F
        info.pressure = 512
        info.rotation = 90
        info.tiltX = -30
        info.tiltY = 12
        info.write()
        val bytes = info.pointer
        assertEquals(4, bytes.getInt(PEN_FLAGS.toLong()))
        assertEquals(0x0F, bytes.getInt(PEN_MASK.toLong()))
        assertEquals(512, bytes.getInt(PRESSURE.toLong()))
        assertEquals(90, bytes.getInt(ROTATION.toLong()))
        assertEquals(-30, bytes.getInt(TILT_X.toLong()))
        assertEquals(12, bytes.getInt(TILT_Y.toLong()))
    }

    @Test
    fun `a device that declares pressure reports it in halves and quarters`() {
        val half = PenContact.of(info(pressure = 512, mask = InkUser32.PEN_MASK_PRESSURE), atNanos = 0)
        assertEquals("0–1024 is normalised to 0–1", 0.5f, half.pressure!!, 1e-6f)
        assertEquals(512, half.rawPressure)

        val none = PenContact.of(info(pressure = 0, mask = 0), atNanos = 0)
        assertNull("no pressure axis is null, never zero — zero would draw nothing", none.pressure)
    }

    @Test
    fun `both tilt axes become the angle from vertical and the direction it leans`() {
        val tilted = PenContact.of(
            info(
                mask = InkUser32.PEN_MASK_TILT_X or InkUser32.PEN_MASK_TILT_Y,
                tiltX = 30,
                tiltY = 40,
            ),
            atNanos = 0,
        )
        assertEquals("a 30-40-50 triangle leans 50° from vertical", 50.0 * PI / 180.0, tilted.tiltRad!!.toDouble(), 1e-6)
        assertEquals(
            "the azimuth is atan2(tiltX, tiltY), clockwise from away",
            kotlin.math.atan2(30.0, 40.0),
            tilted.azimuthRad!!.toDouble(),
            1e-6,
        )

        val halfDeclared = PenContact.of(info(mask = InkUser32.PEN_MASK_TILT_X, tiltX = 30), atNanos = 0)
        assertNull("one axis is not a direction", halfDeclared.tiltRad)
        assertNull(halfDeclared.azimuthRad)
    }

    @Test
    fun `the twist and the eraser end are read from their own fields`() {
        val twisted = PenContact.of(
            info(mask = InkUser32.PEN_MASK_ROTATION, rotation = 90, flags = InkUser32.PEN_FLAG_ERASER),
            atNanos = 0,
        )
        assertEquals(PI / 2, twisted.rotationRad!!.toDouble(), 1e-6)
        assertTrue("the pen is turned round", twisted.eraser)
    }

    @Test
    fun `a contact is fresh only while it could belong to the pointer event being handled`() {
        val at = 1_000_000_000L
        val contact = PenContact.of(info(mask = 0), atNanos = at)
        assertTrue(contact.fresh(at + 60_000_000L, TabletInk.CONTACT_MS))
        assertFalse("a lifted pen must not colour a later state", contact.fresh(at + 500_000_000L, TabletInk.CONTACT_MS))
        assertFalse("nor can a sample from the future", contact.fresh(at - 1_000_000L, TabletInk.CONTACT_MS))
    }

    @Test
    fun `the status bar names what the pen really is`() {
        assertEquals("pen: mouse", penLabel(PenSource.MOUSE, dial = false))
        assertEquals("pen: Windows Ink", penLabel(PenSource.INK, dial = false))
        assertEquals("pen: Windows Ink · dial", penLabel(PenSource.INK, dial = true))
    }

    private fun info(
        pressure: Int = 0,
        mask: Int = 0,
        tiltX: Int = 0,
        tiltY: Int = 0,
        rotation: Int = 0,
        flags: Int = 0,
    ): PenPointerInfo = PenPointerInfo().apply {
        this.pressure = pressure
        this.penMask = mask
        this.tiltX = tiltX
        this.tiltY = tiltY
        this.rotation = rotation
        this.penFlags = flags
    }
}
