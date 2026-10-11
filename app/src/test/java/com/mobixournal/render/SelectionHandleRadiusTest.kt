package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grab radius a selection's resize handles get, as a function of the selection's on-screen size
 * ([DrawingSurfaceDefaults.handleHitRadius]). A selection smaller than the touch radius used to be
 * *all* handle: the edge handles sit half a side out from the middle, so a radius of half a side
 * covers the middle itself and the element could not be dragged — only stretched. These pin the
 * radius to a third of the smaller side, so the middle always stays a move.
 */
class SelectionHandleRadiusTest {

    /** Sides from far below the touch radius up to it, plus a couple of larger ones. */
    private val sides = listOf(4f, 12f, 20f, 30f, 45f, 54f, 59f, 60f, 72f, 90f, 120f, 400f)

    @Test
    fun `the middle of a selection is always clear of every handle`() {
        for (side in sides) {
            val r = DrawingSurfaceDefaults.handleHitRadius(side, side)
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
        assertEquals(30f, DrawingSurfaceDefaults.handleHitRadius(90f, 90f), 1e-4f)
        assertEquals(30f, DrawingSurfaceDefaults.handleHitRadius(200f, 1080f), 1e-4f)
    }

    @Test
    fun `the smaller side is what caps the radius`() {
        // A wide, short selection is capped by its height, not its width.
        assertEquals(60f / 3f, DrawingSurfaceDefaults.handleHitRadius(900f, 60f), 1e-4f)
        assertEquals(30f / 3f, DrawingSurfaceDefaults.handleHitRadius(400f, 30f), 1e-4f)
    }

    @Test
    fun `the radius never collapses to an unhittable point`() {
        // A degenerate box (a one-point stroke, an empty text box) still gets a grabbable handle.
        assertTrue(DrawingSurfaceDefaults.handleHitRadius(0f, 0f) >= 1f)
        assertTrue(DrawingSurfaceDefaults.handleHitRadius(1f, 1f) >= 1f)
    }
}
