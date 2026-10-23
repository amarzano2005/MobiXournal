package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wintab, as arithmetic and as the bytes the driver reads.
 *
 * A tablet cannot be faked on a build machine, so what is pinned here is everything around the packets:
 * the **layout** of the two structures the driver's DLL reads (`LOGCONTEXT` is 172 bytes on x64 and the
 * packet is its mask's fields in their bit order — a struct that drifted reads a pressure out of the
 * wrong four bytes), and the **units** the readings come in: fourteen-bit pressure, angles in tenths of
 * a degree with altitude measured up from the glass, and `TPS_INVERT` for the eraser end.
 *
 * The readings themselves were measured on the machine the window runs on, with `wintab32.dll` and the
 * correct `wintab.h` indices (`x` 0…50800, `y` 0…31750, `pressure` 0…16383, `orientation` 0…3600); the
 * table is in `docs/desktop-workstation.md`.
 */
class TabletWintabTest {

    @Test
    fun `the context structure is the size the driver reads`() {
        assertEquals(
            "sizeof(LOGCONTEXT) on x64 — the size the earlier probe measured against the driver",
            WintabLogContext.SIZE_BYTES,
            WintabLogContext().size(),
        )
    }

    @Test
    fun `a packet is its mask's fields, in bit order`() {
        // status, buttons, x, y, pressure, orientation (three ints) — eight 32-bit fields.
        val packet = WintabPacket().apply {
            status = 1
            buttons = 2
            x = 3
            y = 4
            pressure = 5
            azimuth = 6
            altitude = 7
            twist = 8
        }
        packet.write()
        assertEquals("eight 32-bit fields, and no padding between them", 32, packet.size())
        // Field-by-field, because JNA reads them in the order the struct declares: a shift would put
        // the pressure where the altitude belongs.
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), (0 until 8).map { packet.pointer.getInt(it * 4L) })
    }

    @Test
    fun `fourteen bit pressure becomes the shared zero to one`() {
        val max = 16383
        assertEquals(0f, contact(pressure = 0, pressureMax = max).pressure!!, 1e-6f)
        assertEquals(1f, contact(pressure = max, pressureMax = max).pressure!!, 1e-6f)
        assertEquals(0.5f, contact(pressure = max / 2, pressureMax = max).pressure!!, 1e-3f)
        assertEquals(
            "a reading past the declared range cannot draw wider than the pen",
            1f,
            contact(pressure = max * 2, pressureMax = max).pressure!!,
            1e-6f,
        )
        assertNull(
            "a device that declares no pressure draws at the tool's width, never at zero",
            contact(pressure = 100, pressureMax = 0).pressure,
        )
    }

    @Test
    fun `altitude measures up from the glass and the angles are tenths of a degree`() {
        val upright = contact(pressure = 800, pressureMax = 16383, altitude = 900, azimuth = 0, twist = 0)
        assertEquals("a pen held upright has no tilt", 0f, upright.tiltRad!!, 1e-4f)
        assertEquals("with no azimuth, no direction", 0f, upright.azimuthRad!!, 1e-4f)

        val leaning = contact(pressure = 800, pressureMax = 16383, altitude = 450, azimuth = 900, twist = 1800)
        assertEquals("altitude 45° is a pen at 45° from vertical", Math.PI / 4, leaning.tiltRad!!.toDouble(), 1e-4)
        assertEquals("azimuth 90° leans a quarter turn round", Math.PI / 2, leaning.azimuthRad!!.toDouble(), 1e-4)
        assertEquals("twist 180° is the pen turned over", Math.PI, leaning.rotationRad!!.toDouble(), 1e-4)
    }

    @Test
    fun `the inverted cursor is the eraser end`() {
        assertTrue(contact(pressure = 500, pressureMax = 16383, status = WintabApi.TPS_INVERT).eraser)
        assertFalse(contact(pressure = 500, pressureMax = 16383, status = 0).eraser)
    }

    private fun contact(
        pressure: Int,
        pressureMax: Int,
        status: Int = 0,
        altitude: Int = 900,
        azimuth: Int = 0,
        twist: Int = 0,
    ): PenContact {
        val packet = WintabPacket().apply {
            this.status = status
            this.buttons = WintabApi.BTN_TIP
            this.pressure = pressure
            this.altitude = altitude
            this.azimuth = azimuth
            this.twist = twist
        }
        return PenContact.of(packet, pressureMax, atNanos = 1_000L)
    }
}
