package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-axis factor behind the edge handles: the drag's distance ratio measured from the fixed
 * anchor. What is worth pinning is that the ratio is read against the **anchor** (so the opposite edge
 * stays put), that a click with no travel is "no change" rather than a division blow-up, and that a
 * drag crossing the anchor collapses to the clamp instead of flipping the element inside out.
 */
class SelectionResizeTest {

    @Test
    fun `doubling the distance from the anchor doubles the axis`() {
        // Started 100 pt right of the anchor, dragged out to 200.
        assertEquals(2.0, axisScaleFactor(startPt = 100.0, currentPt = 200.0, anchorPt = 0.0), 1e-9)
    }

    @Test
    fun `the anchor is the fixed point, wherever the drag began`() {
        // An edge handle 30 pt from its anchor, pulled to 15: the axis halves.
        assertEquals(0.5, axisScaleFactor(startPt = 30.0, currentPt = 15.0, anchorPt = 0.0), 1e-9)
        // The same travel with the anchor 100 pt away is measured from the anchor, not from zero:
        // 130 → 115 is (115-100)/(130-100) = 0.5 as well.
        assertEquals(0.5, axisScaleFactor(startPt = 130.0, currentPt = 115.0, anchorPt = 100.0), 1e-9)
    }

    @Test
    fun `no travel means no change`() {
        assertEquals(1.0, axisScaleFactor(50.0, 50.0, 0.0), 1e-9)
    }

    @Test
    fun `a drag starting on the anchor has no readable ratio`() {
        // The pointer began exactly on the anchor: any travel would read as an infinite factor, so the
        // only safe answer is "no change".
        assertEquals(1.0, axisScaleFactor(0.0, 80.0, 0.0), 1e-9)
    }

    @Test
    fun `crossing the anchor collapses to the smallest factor instead of flipping`() {
        // 40 pt right of the anchor dragged to 40 pt left: the raw ratio is -1, which is not a legal
        // scale (it would mirror the element); it clamps to MIN_RESIZE.
        assertEquals(
            DrawingSurfaceDefaults.MIN_RESIZE,
            axisScaleFactor(40.0, -40.0, 0.0),
            1e-9,
        )
    }

    @Test
    fun `an enormous stretch is capped`() {
        assertEquals(
            DrawingSurfaceDefaults.MAX_RESIZE,
            axisScaleFactor(1.0, 10_000.0, 0.0),
            1e-9,
        )
    }
}
