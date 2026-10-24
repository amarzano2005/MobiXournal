package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The canvas's **edge scroll** as arithmetic: where the band is, which way the sheet travels, and how
 * fast it gets there.
 *
 * A drag that ran off the border used to stop at it, so a marquee could only catch what happened to be on
 * screen. The rule is a pure function on purpose — the band and the ramp are the kind of thing that is
 * either exactly right or subtly wrong, and "it felt fine with the mouse" is not a check.
 */
class EdgePanTest {

    private val viewW = 1000f
    private val viewH = 700f

    private fun delta(x: Float, y: Float) = edgePanDelta(x, y, viewW, viewH)

    @Test
    fun `the middle of the canvas does not scroll`() {
        assertEquals("no horizontal travel away from the band", 0f, delta(viewW / 2f, viewH / 2f).first, 0f)
        assertEquals("no vertical travel either", 0f, delta(viewW / 2f, viewH / 2f).second, 0f)
        assertEquals("nor just inside the band", 0f, delta(EDGE_PAN_BAND_PX + 1f, viewH / 2f).first, 0f)
    }

    @Test
    fun `the band reveals what lies past the edge it is at`() {
        // Near the right edge the sheet has to travel left, which is a negative offset delta.
        assertTrue("the right edge scrolls right", delta(viewW - 4f, viewH / 2f).first < 0f)
        assertTrue("the left edge scrolls left", delta(4f, viewH / 2f).first > 0f)
        assertTrue("the bottom edge scrolls down", delta(viewW / 2f, viewH - 4f).second < 0f)
        assertTrue("the top edge scrolls up", delta(viewW / 2f, 4f).second > 0f)
    }

    @Test
    fun `the speed ramps from the band's inner edge to its border`() {
        val inner = delta(EDGE_PAN_BAND_PX - 2f, viewH / 2f).first
        val middle = delta(viewW - EDGE_PAN_BAND_PX / 2f, viewH / 2f).first
        val border = delta(viewW - 1f, viewH / 2f).first

        assertTrue("a shallow dip into the band is slow ($inner)", inner > 0f && inner < 2f)
        assertTrue("halfway across is quicker ($middle)", -middle > -inner)
        assertEquals("the border is the full speed", -EDGE_PAN_SPEED_PX, border, 0.5f)
        assertEquals("and never more, even outside the window", -EDGE_PAN_SPEED_PX, delta(viewW + 400f, viewH / 2f).first, 0f)
    }

    @Test
    fun `a canvas narrower than two bands keeps one direction per position`() {
        // The band shrinks to half the extent, so the two edges meet in the middle rather than both
        // claiming the same pixel: a small window still scrolls the way the pointer points.
        val near = edgePanDelta(2f, 2f, EDGE_PAN_BAND_PX, EDGE_PAN_BAND_PX)
        assertTrue("the top-left corner scrolls left and up", near.first > 0f && near.second > 0f)
        val far = edgePanDelta(EDGE_PAN_BAND_PX - 2f, EDGE_PAN_BAND_PX - 2f, EDGE_PAN_BAND_PX, EDGE_PAN_BAND_PX)
        assertTrue("the opposite one the other way", far.first < 0f && far.second < 0f)
        assertEquals("and a canvas with no width has nowhere to go", 0f, edgePanDelta(0f, 0f, 0f, 0f).first, 0f)
    }
}
