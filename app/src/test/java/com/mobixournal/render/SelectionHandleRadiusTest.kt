package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grab reach a selection's resize handles get on **each axis** ([DrawingSurfaceDefaults.handleReach]),
 * and the rule that decides when a box is a **line** rather than a box
 * ([DrawingSurfaceDefaults.isFlat]).
 *
 * A selection smaller than the touch radius used to be *all* handle: the edge handles sit half a side out
 * from the middle, so a radius of half a side covers the middle itself and the element could not be dragged
 * — only stretched. These pin the reach to a third of **that axis's** extent, so the middle always stays a
 * move. The one axis that is not capped is the one with no extent: a line is only as thick as its pen, its
 * thickness has no middle to protect, and capping the other axis by it left the end handles with a pixel of
 * slack — the press meant to lengthen the line carried it, resized it or dropped the selection instead. The
 * window's `DesktopSelectionTest` pins the same rule for the desktop shell.
 */
class SelectionHandleRadiusTest {

    /** Sides from far below the touch radius up to it, plus a couple of larger ones. */
    private val sides = listOf(4f, 12f, 20f, 30f, 45f, 54f, 59f, 60f, 72f, 90f, 120f, 400f)

    @Test
    fun `the middle of a selection is always clear of every handle`() {
        for (side in sides) {
            val r = DrawingSurfaceDefaults.handleReach(side, flat = false)
            // The four edge handles sit half a side from the centre (the corners are further out), so
            // `side / 2` is the distance to the nearest one. It must exceed the grab radius, or every
            // touch on the middle would start a resize.
            assertTrue(
                "side ${side}px: the middle is ${side / 2f}px from an edge handle, radius $r",
                side / 2f > r,
            )
        }
    }

    @Test
    fun `a selection big enough keeps the full touch radius`() {
        assertEquals(30f, DrawingSurfaceDefaults.handleReach(90f, flat = false), 1e-4f)
        assertEquals(30f, DrawingSurfaceDefaults.handleReach(200f, flat = false), 1e-4f)
    }

    @Test
    fun `each axis is capped on its own extent`() {
        // A wide, short selection: the width keeps the full radius, the height is capped by itself.
        assertEquals(30f, DrawingSurfaceDefaults.handleReach(900f, flat = false), 1e-4f)
        assertEquals(60f / 3f, DrawingSurfaceDefaults.handleReach(60f, flat = false), 1e-4f)
        assertEquals(30f / 3f, DrawingSurfaceDefaults.handleReach(30f, flat = false), 1e-4f)
    }

    @Test
    fun `a line keeps the full radius at its ends`() {
        // A 4 pt stroke at 100 % is 4 px of box — a line, not a box.
        val flat = DrawingSurfaceDefaults.isFlat(4f, 1f)
        assertTrue("a 4 pt box is a line", flat)
        assertEquals(
            "the end keeps the whole reach",
            30f,
            DrawingSurfaceDefaults.handleReach(240f, flat),
            1e-4f,
        )
        assertEquals(
            "and so does the thickness, which has no middle to protect",
            30f,
            DrawingSurfaceDefaults.handleReach(4f, flat),
            1e-4f,
        )
        // The same line at 300 % is the same line: flatness is measured in page points, not pixels.
        assertTrue("zoomed in, a 4 pt line is still a line", DrawingSurfaceDefaults.isFlat(12f, 3f))
        assertFalse("a thicker pen is a box", DrawingSurfaceDefaults.isFlat(24f, 3f))
    }

    /**
     * The **inward** half of a handle's grab band ([DrawingSurfaceDefaults.handleInside]) is the drawn
     * dot plus finger slop, capped on a small axis — beyond it the box is the body a drag carries.
     *
     * Reading the whole 30 px reach inward made every press on a phone-sized selection a resize: the
     * hand landed on the element and grabbed one of its edges, which is the inaccuracy the band fixes.
     */
    @Test
    fun `a handle answers only from a narrow band inside the box`() {
        assertEquals(12f, DrawingSurfaceDefaults.handleInside(400f), 1e-4f)
        assertTrue(
            "the inward band stays well inside the outward reach",
            DrawingSurfaceDefaults.handleInside(400f) < DrawingSurfaceDefaults.HANDLE_HIT_PX / 2f,
        )
        // A small selection keeps a middle to be carried by: under 36 px of side the band shrinks
        // with its own axis, so the third that `handleReach` leaves clear stays clear.
        assertEquals(12f, DrawingSurfaceDefaults.handleInside(60f), 1e-4f)
        assertEquals(36f / 3f, DrawingSurfaceDefaults.handleInside(36f), 1e-4f)
        assertEquals(30f / 3f, DrawingSurfaceDefaults.handleInside(30f), 1e-4f)
        assertEquals(0f, DrawingSurfaceDefaults.handleInside(-4f), 1e-4f)
    }

    /**
     * Inside a box the **body** wins outside the narrow band ([insideSelectionBody]): the middle of an
     * element is always a drag, and only the border the drawn dots straddle is read as a handle.
     */
    @Test
    fun `the middle of a selection is its body and the border is not`() {
        fun body(x: Float, y: Float) = insideSelectionBody(
            x, y, leftPx = 100f, topPx = 200f, rightPx = 500f, bottomPx = 500f, scale = 1f,
        )
        assertTrue("the centre carries the element", body(300f, 350f))
        assertTrue("15 px inside the left edge is still body", body(115f, 350f))
        assertFalse("5 px inside the left edge is the handle band", body(105f, 350f))
        assertFalse("a point above the box is outside it", body(300f, 190f))
    }

    @Test
    fun `a line has no body so its end handles keep the whole reach`() {
        // A 240 px stroke drawn 4 pt thick is 4 px of box at 100 %: flat, so it never answers "body".
        assertFalse(
            insideSelectionBody(160f, 202f, 100f, 200f, 340f, 204f, scale = 1f),
        )
    }

    @Test
    fun `the radius never collapses to an unhittable point`() {
        // A degenerate box (a one-point stroke, an empty text box) still gets a grabbable handle.
        assertTrue(DrawingSurfaceDefaults.handleReach(0f, flat = false) >= 1f)
        assertTrue(DrawingSurfaceDefaults.handleReach(1f, flat = false) >= 1f)
    }
}
