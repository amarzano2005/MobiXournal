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

    @Test
    fun `the radius never collapses to an unhittable point`() {
        // A degenerate box (a one-point stroke, an empty text box) still gets a grabbable handle.
        assertTrue(DrawingSurfaceDefaults.handleReach(0f, flat = false) >= 1f)
        assertTrue(DrawingSurfaceDefaults.handleReach(1f, flat = false) >= 1f)
    }
}
