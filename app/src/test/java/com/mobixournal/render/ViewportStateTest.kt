package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportStateTest {

    /** A viewport of 100×100 over content of 200×400 at zoom 1. */
    private fun viewport() = ViewportState().apply { setBounds(100f, 100f, 200f, 400f) }

    @Test fun `max scroll is the content beyond the viewport`() {
        val v = viewport()
        assertEquals(100f, v.maxScrollX(), 0f)
        assertEquals(300f, v.maxScrollY(), 0f)
        assertTrue(v.canScroll())
    }

    @Test fun `content smaller than the viewport cannot scroll`() {
        val v = ViewportState().apply { setBounds(100f, 100f, 50f, 50f) }
        assertEquals(0f, v.maxScrollX(), 0f)
        assertEquals(0f, v.maxScrollY(), 0f)
        assertFalse(v.canScroll())
    }

    @Test fun `scrollBy clamps and reports whether it moved`() {
        val v = viewport()
        assertTrue(v.scrollBy(10f, 20f))
        assertEquals(10f, v.scrollX, 0f)
        assertEquals(20f, v.scrollY, 0f)
        assertTrue(v.scrollBy(0f, 1000f))
        assertEquals(300f, v.scrollY, 0f)
        assertFalse(v.scrollBy(0f, 50f)) // already pinned at the bottom
        assertTrue(v.scrollBy(1000f, 0f))
        assertEquals(100f, v.scrollX, 0f)
        assertFalse(v.scrollBy(50f, 0f)) // already pinned at the right edge
    }

    @Test fun `scrollToY clamps to the range`() {
        val v = viewport()
        v.scrollToY(-5f)
        assertEquals(0f, v.scrollY, 0f)
        v.scrollToY(9999f)
        assertEquals(300f, v.scrollY, 0f)
    }

    @Test fun `setBounds pulls a stale offset back into range`() {
        val v = viewport()
        v.scrollToY(300f)
        v.setBounds(100f, 100f, 200f, 150f) // pages shrank
        assertEquals(50f, v.scrollY, 0f)
    }

    /** Relayout the way the stacked layout does: extents scale with the zoom. */
    private fun ViewportState.relayoutTo(w: Float, h: Float): () -> Unit =
        { setBoundsKeepingOffsets(w * zoom, h * zoom) }

    private fun ViewportState.setBoundsKeepingOffsets(cw: Float, ch: Float) =
        setBounds(100f, 100f, cw, ch)

    @Test fun `zoomTo clamps to the zoom range`() {
        val v = viewport()
        assertTrue(v.zoomTo(100f, v.relayoutTo(200f, 400f)))
        assertEquals(ViewportState.MAX_ZOOM, v.zoom, 0f)
        assertFalse(v.zoomTo(100f, v.relayoutTo(200f, 400f)))
        assertTrue(v.zoomTo(0f, v.relayoutTo(200f, 400f)))
        assertEquals(ViewportState.MIN_ZOOM, v.zoom, 0f)
    }

    @Test fun `zoomAbout keeps the focus point fixed`() {
        val v = viewport()
        v.scrollToY(100f)
        // The content point under viewport y=50 is 150 of 400 — 0.375 of the way down.
        v.zoomAbout(0f, 50f, 2f, v.relayoutTo(200f, 400f))
        assertEquals(2f, v.zoom, 0f)
        assertEquals(0.375f * 800f - 50f, v.scrollY, 0.001f)
    }

    @Test fun `zoomTo keeps the viewport centre fixed`() {
        val v = viewport()
        v.scrollToY(100f)
        // Centre (y=50) sits at content 150/400; after 2× it should stay centred on that fraction.
        v.zoomTo(2f, v.relayoutTo(200f, 400f))
        assertEquals(0.375f * 800f - 50f, v.scrollY, 0.001f)
    }

    @Test fun `reset returns to the top-left at 100 percent`() {
        val v = viewport()
        v.scrollBy(50f, 50f)
        v.zoomTo(2f, v.relayoutTo(200f, 400f))
        v.reset()
        assertEquals(0f, v.scrollX, 0f)
        assertEquals(0f, v.scrollY, 0f)
        assertEquals(1f, v.zoom, 0f)
    }

    @Test fun `infinite vertical mode locks width and scrolls downwards unbounded`() {
        val v = viewport().apply { canvasMode = com.mobixournal.format.mobix.CanvasMode.INFINITE_VERTICAL }
        // Horizontal is clamped to maxScrollX (100)
        v.scrollBy(1000f, 5000f)
        assertEquals(100f, v.scrollX, 0f)
        assertEquals(5000f, v.scrollY, 0f)
        // Cannot scroll above top (y >= 0)
        v.scrollBy(0f, -10000f)
        assertEquals(0f, v.scrollY, 0f)
    }

    @Test fun `infinite horizontal mode locks height and scrolls rightwards unbounded`() {
        val v = viewport().apply { canvasMode = com.mobixournal.format.mobix.CanvasMode.INFINITE_HORIZONTAL }
        // Vertical is clamped to maxScrollY (300)
        v.scrollBy(5000f, 1000f)
        assertEquals(5000f, v.scrollX, 0f)
        assertEquals(300f, v.scrollY, 0f)
        // Cannot scroll left of origin (x >= 0)
        v.scrollBy(-10000f, 0f)
        assertEquals(0f, v.scrollX, 0f)
    }

    @Test fun `infinite 2d mode allows completely free unbounded scrolling in all directions`() {
        val v = viewport().apply { canvasMode = com.mobixournal.format.mobix.CanvasMode.INFINITE_2D }
        v.scrollBy(-2000f, -3000f)
        assertEquals(-2000f, v.scrollX, 0f)
        assertEquals(-3000f, v.scrollY, 0f)
        v.scrollBy(5000f, 7000f)
        assertEquals(3000f, v.scrollX, 0f)
        assertEquals(4000f, v.scrollY, 0f)
    }

    @Test fun `visible chunk range computes spatial coordinates and 1-chunk margin`() {
        val v = ViewportState().apply {
            canvasMode = com.mobixournal.format.mobix.CanvasMode.INFINITE_2D
            setBounds(1024f, 1024f, 1024f, 1024f)
        }
        // At origin (0, 0), viewport covers [0..1024, 0..1024] pt
        var range = v.visibleChunkRange(1024.0)
        assertEquals(0, range.cxMin)
        assertEquals(1, range.cxMax)
        assertEquals(0, range.cyMin)
        assertEquals(1, range.cyMax)

        // 1-chunk margin
        val marginRange = range.withMargin(1)
        assertEquals(-1, marginRange.cxMin)
        assertEquals(2, marginRange.cxMax)
        assertEquals(-1, marginRange.cyMin)
        assertEquals(2, marginRange.cyMax)

        // Negative coordinates in 2D mode: scroll to (-2048, 3072)
        v.scrollToX(-2048f)
        v.scrollToY(3072f)
        range = v.visibleChunkRange(1024.0)
        assertEquals(-2, range.cxMin)
        assertEquals(-1, range.cxMax)
        assertEquals(3, range.cyMin)
        assertEquals(4, range.cyMax)
    }

    @Test fun `kept scale holds the page's size across a width change`() {
        // The page is drawn at `width × zoom`: half the width needs twice the zoom for the same px.
        val split = keptScaleZoom(1f, 2432f, 1194f)
        assertEquals(2432f, 1194f * split, 0.5f)
        assertEquals(2432f, 2432f * keptScaleZoom(split, 1194f, 2432f), 0.5f)
    }

    @Test fun `kept scale is exactly reversible`() {
        // The toggle in both directions: landing back on the old width restores the old zoom.
        val zoomed = keptScaleZoom(0.8f, 2432f, 1194f)
        assertEquals(0.8f, keptScaleZoom(zoomed, 1194f, 2432f), 1e-5f)
    }

    @Test fun `kept scale clamps like any other zoom`() {
        assertEquals(ViewportState.MAX_ZOOM, keptScaleZoom(9f, 2400f, 600f), 0f)
        assertEquals(ViewportState.MIN_ZOOM, keptScaleZoom(0.3f, 600f, 2400f), 0f)
    }

    @Test fun `kept scale leaves the zoom alone on a degenerate width`() {
        // A canvas that has not been laid out yet has no width to compensate against.
        assertEquals(1.4f, keptScaleZoom(1.4f, 0f, 1200f), 0f)
        assertEquals(1.4f, keptScaleZoom(1.4f, 1200f, 0f), 0f)
    }

    @Test fun `an unqueued resize keeps the zoom`() {
        val v = viewport()
        assertEquals(1f, v.adoptResizeZoom(40f), 0f)
    }

    @Test fun `a queued keep compensates once and then clears`() {
        val v = viewport()
        v.keepScaleOnResize = true
        // The canvas grew from 100 to 200: the page's fit doubles, so the zoom halves to hold its px.
        assertEquals(0.5f, v.adoptResizeZoom(200f), 1e-5f)
        // Only the toggle's own resize is compensated; the next one re-fits like a drag.
        assertEquals(v.zoom, v.adoptResizeZoom(100f), 0f)
    }

    @Test fun `a relayout at the same width leaves the queue armed`() {
        val v = viewport()
        v.keepScaleOnResize = true
        // The toggle's own repaint can still run against the old extent: that is not the resize.
        assertEquals(1f, v.adoptResizeZoom(100f), 0f)
        assertEquals(0.5f, v.adoptResizeZoom(200f), 1e-5f)
    }
}
