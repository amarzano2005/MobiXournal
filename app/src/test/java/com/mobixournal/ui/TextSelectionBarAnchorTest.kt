package com.mobixournal.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the **PDF text** Copy bar lands, pinned as geometry rather than left to a device pass: the bar used
 * to be a fixed strip on the pane's bottom edge, which is exactly where a passage at the foot of a page
 * sits, so the highlight ended up under the two buttons that act on it.
 *
 * The rule is the element bar's own ([selectionBarOffset]) — **below** the last selected line, **above** the
 * block when the pane runs out below it, always inside the pane's edges — and what these cases add is the
 * one thing that rule has to guarantee for text: the bar and the highlight never occupy the same rows when
 * there is any room at all.
 */
class TextSelectionBarAnchorTest {

    /** A split-screen pane: narrow, and shorter than the page it shows. */
    private val canvas = IntSize(900, 1200)
    private val bar = IntSize(260, 96)
    private val gap = 10
    private val margin = 8

    private fun offset(top: Float, bottom: Float, anchorX: Float = 450f, canvasSize: IntSize = canvas) =
        selectionBarOffset(
            anchorX = anchorX,
            selectionTop = top,
            selectionBottom = bottom,
            barSize = bar,
            canvasSize = canvasSize,
            gapPx = gap,
            marginPx = margin,
        )

    @Test
    fun `a passage mid-page gets the bar under its last line`() {
        val placed = offset(top = 300f, bottom = 380f)
        assertEquals("the bar starts one gap under the highlight", 390, placed.y)
        assertTrue("so the highlight is not under it", placed.y >= 380)
    }

    @Test
    fun `a passage at the foot of the pane gets the bar above it`() {
        // Below would need rows past 1200; the bar goes above the block's first line instead.
        val placed = offset(top = 1000f, bottom = 1190f)
        assertEquals(1000 - bar.height - gap, placed.y)
        assertTrue("and clears the highlight upwards too", placed.y + bar.height <= 1000)
    }

    @Test
    fun `copy stays reachable at the left edge of a narrow pane`() {
        // Centring on a selection hugging the left edge would put the bar at -110.
        val placed = offset(top = 300f, bottom = 380f, anchorX = 40f)
        assertEquals(margin, placed.x)
    }

    @Test
    fun `copy stays reachable at the right edge of a narrow pane`() {
        val placed = offset(top = 300f, bottom = 380f, anchorX = 880f)
        assertEquals(canvas.width - bar.width - margin, placed.x)
    }

    @Test
    fun `a selection taller than the room left stays nearest its last line but inside`() {
        // A block filling the pane: neither side has room, so the bar is pulled just inside the bottom
        // edge — still the nearest legal place to the words, and still tappable.
        val placed = offset(top = 40f, bottom = 1180f)
        assertEquals(canvas.height - bar.height - margin, placed.y)
    }

    @Test
    fun `a bar wider than the pane is still pinned inside it`() {
        // A large text scale widens the capsule; the clamp may not push its left edge past the margin.
        val wide = IntSize(600, 96)
        val placed = selectionBarOffset(
            anchorX = 450f,
            selectionTop = 300f,
            selectionBottom = 380f,
            barSize = wide,
            canvasSize = IntSize(320, 1200),
            gapPx = gap,
            marginPx = margin,
        )
        assertEquals(margin, placed.x)
        assertTrue("and it does not start off the left of the pane", placed.x >= 0)
    }

    @Test
    fun `with the bar not yet measured it parks against the margin`() {
        val placed = selectionBarOffset(
            anchorX = 450f,
            selectionTop = 300f,
            selectionBottom = 380f,
            barSize = IntSize.Zero,
            canvasSize = canvas,
            gapPx = gap,
            marginPx = margin,
        )
        assertEquals(IntOffset(margin, margin), placed)
    }
}
