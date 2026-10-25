package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The **hand's packet pan**: what the driver's readings do to the sheet.
 *
 * The live complaint this pins is a hand that crawled with the stylus: Windows' copy of the contact is
 * coalesced, so the pan is taken from the driver's own packets instead (`PenPacketPan`). Two facts are
 * asserted here, both of them things a person feels rather than reads:
 *
 *  - the sheet travels **exactly** as far as the pen did (a packet delta divided by the density is the
 *    dp the viewport moves by, so a HiDPI screen pans at the same speed as a 1× one);
 *  - a frame is **one** viewport write, not one per packet — 400 readings a second recomposed the
 *    window far more often than it can paint, and the total is still the same distance.
 *
 * Pure: the viewport is a value and the pan is a plain class, so no window and no scene are involved.
 */
class PenPacketPanTest {

    /**
     * A pan over a settled viewport, with every write and every ownership change recorded.
     *
     * The page is wider and taller than the window and the viewport starts **inside** it: a page that
     * fits is centred by the clamp (`PageIndexScroll.range`), which would pin the offset and hide a pan
     * that was wrong by a sign or a factor of the density.
     */
    private class Bench(density: Float) {
        var viewport = Viewport(zoom = 1f, offsetX = -500f, offsetY = -500f)
        val writes = mutableListOf<Viewport>()
        val ownership = mutableListOf<Boolean>()
        val pan = PenPacketPan(
            viewport = { viewport },
            pageW = 2_000f,
            pageH = 2_000f,
            viewW = 800f,
            viewH = 600f,
            density = density,
            onViewport = { next ->
                viewport = next
                writes += next
            },
            onOwnership = { ownership += it },
        )

        /** One frame: everything the driver sent since the last one. */
        fun flush() = pan.flush()

        fun packet(x: Float, y: Float, tip: Boolean = true) {
            pan.packet(
                PenPacket(
                    x = x,
                    y = y,
                    pressure = 0.5f,
                    tiltRad = null,
                    azimuthRad = null,
                    rotationRad = null,
                    tip = tip,
                    eraser = false,
                    atNanos = System.nanoTime(),
                ),
            )
        }
    }

    @Test
    fun `the sheet travels as far as the pen did, in dp`() {
        val pan = Bench(density = 2f)
        pan.packet(100f, 100f)
        pan.flush()
        // The press only records where it landed: a click is not a scroll.
        assertEquals(0, pan.writes.size)
        assertTrue(pan.ownership.last())
        pan.packet(140f, 120f)
        pan.flush()
        assertEquals(1, pan.writes.size)
        // 40 px and 20 px at 2× are 20 dp and 10 dp of page travel, in the hand's own direction.
        assertEquals(-480f, pan.viewport.offsetX, 0.001f)
        assertEquals(-490f, pan.viewport.offsetY, 0.001f)
    }

    @Test
    fun `a batch of packets is one write, and the last frame is not dropped`() {
        val pan = Bench(density = 1f)
        pan.packet(100f, 100f)
        pan.flush()
        // Ten readings between two paints: one frame, the whole distance.
        for (step in 1..10) pan.packet(100f + step * 5f, 100f)
        pan.flush()
        assertEquals(1, pan.writes.size)
        assertEquals(-450f, pan.viewport.offsetX, 0.001f)
        // The lift arrives in the same batch as the travel before it: the sheet still moves.
        pan.packet(160f, 100f, tip = false)
        pan.flush()
        assertEquals(2, pan.writes.size)
        assertEquals(-440f, pan.viewport.offsetX, 0.001f)
        assertFalse(pan.ownership.last())
    }

    @Test
    fun `a hover after the lift pans nothing`() {
        val pan = Bench(density = 1f)
        pan.packet(100f, 100f, tip = false)
        pan.flush()
        pan.packet(300f, 300f, tip = false)
        pan.flush()
        assertEquals(0, pan.writes.size)
        assertTrue(pan.ownership.isEmpty())
    }
}
