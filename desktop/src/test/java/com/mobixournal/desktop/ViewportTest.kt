package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The canvas transform, tested without a window.
 *
 * The two properties that decide whether zooming and panning feel right are asserted directly: the
 * point under the cursor does not move when the wheel turns, and the page cannot be dragged out of the
 * window. Both are invisible failures in a window — the page just drifts a little each gesture — which
 * is exactly why the arithmetic is a plain class.
 */
class ViewportTest {

    private val pageW = 600f
    private val pageH = 800f
    private val viewW = 1000f
    private val viewH = 700f

    @Test
    fun `zoom keeps the point under the cursor fixed`() {
        // A page that overflows the window on both axes: with a page that fits there is nothing to pan,
        // so the clamp re-centres it — that is the behaviour asserted in the next test, not this one.
        val wide = 1400f
        val tall = 1200f
        val start = Viewport.centred(1f, wide, tall, viewW, viewH)
        val anchorX = 320f
        val anchorY = 210f
        val before = start.toPage(anchorX, anchorY)

        val zoomed = start.zoomBy(1.5f, anchorX, anchorY, wide, tall, viewW, viewH)
        val after = zoomed.toPage(anchorX, anchorY)

        assertEquals("zoom was applied", 1.5f, zoomed.zoom, 1e-4f)
        assertEquals("the page point under the cursor keeps its x", before.first, after.first, 0.01f)
        assertEquals("and its y", before.second, after.second, 0.01f)
    }

    @Test
    fun `zooming at the centre keeps the point under the centre fixed`() {
        val start = Viewport.centred(2f, pageW, pageH, viewW, viewH)
        val before = start.toPage(viewW / 2f, viewH / 2f)
        val zoomed = start.zoomTo(3f, pageW, pageH, viewW, viewH)
        val after = zoomed.toPage(viewW / 2f, viewH / 2f)
        assertEquals("the page point under the window centre keeps its x", before.first, after.first, 0.01f)
        assertEquals("and its y", before.second, after.second, 0.01f)
    }

    @Test
    fun `screen and page coordinates round-trip`() {
        val viewport = Viewport(1.7f, -120f, -60f).clamped(pageW, pageH, viewW, viewH)
        val (x, y) = viewport.toScreen(123.5f, 456.25f)
        val (backX, backY) = viewport.toPage(x, y)
        assertEquals(123.5f, backX, 1e-3f)
        assertEquals(456.25f, backY, 1e-3f)
    }

    @Test
    fun `zoom is clamped to the range the shell promises`() {
        val start = Viewport.centred(1f, pageW, pageH, viewW, viewH)
        val tiny = start.zoomBy(0.01f, viewW / 2f, viewH / 2f, pageW, pageH, viewW, viewH)
        assertEquals("never smaller than 25%", Viewport.MIN_ZOOM, tiny.zoom, 1e-4f)

        val huge = start.zoomBy(100f, viewW / 2f, viewH / 2f, pageW, pageH, viewW, viewH)
        assertEquals("never larger than 400%", Viewport.MAX_ZOOM, huge.zoom, 1e-4f)
    }

    @Test
    fun `a page smaller than the window is centred and stays centred`() {
        val fitted = Viewport.fitZoom(pageW, pageH, viewW, viewH)
        val centred = Viewport.centred(fitted, pageW, pageH, viewW, viewH)
        assertTrue("the fitted page fits", centred.showsWholePage(pageW, pageH, viewW, viewH))
        assertEquals("centred horizontally", (viewW - pageW * fitted) / 2f, centred.offsetX, 0.01f)
        assertEquals("centred vertically", (viewH - pageH * fitted) / 2f, centred.offsetY, 0.01f)

        val dragged = centred.pannedBy(400f, 400f, pageW, pageH, viewW, viewH)
        assertEquals("dragging a fitted page does not move it", centred.offsetX, dragged.offsetX, 0.01f)
        assertEquals(centred.offsetY, dragged.offsetY, 0.01f)
    }

    @Test
    fun `a zoomed page cannot be dragged off the window`() {
        val zoomed = Viewport.centred(3f, pageW, pageH, viewW, viewH)
        val farRight = zoomed.pannedBy(10_000f, 10_000f, pageW, pageH, viewW, viewH)
        assertEquals("the page's left edge cannot pass the window's left edge", 0f, farRight.offsetX, 0.01f)
        assertEquals(0f, farRight.offsetY, 0.01f)

        val farLeft = zoomed.pannedBy(-10_000f, -10_000f, pageW, pageH, viewW, viewH)
        assertEquals(
            "the page's right edge cannot pass the window's right edge",
            viewW - pageW * 3f,
            farLeft.offsetX,
            0.01f,
        )
        assertEquals(viewH - pageH * 3f, farLeft.offsetY, 0.01f)
    }

    @Test
    fun `fit width fills the window horizontally and leaves the page scrollable vertically`() {
        val zoom = Viewport.fitWidthZoom(pageW, viewW)
        val width = pageW * zoom
        assertTrue("the page's width fills the usable window", width <= viewW && width > viewW * 0.85f)
        assertTrue("and its height does not fit", pageH * zoom > viewH)
    }

    @Test
    fun `fit modes report the zoom they imply`() {
        assertEquals(FitMode.WIDTH, FitMode.WIDTH)
        val pageZoom = FitMode.PAGE.zoomFor(pageW, pageH, viewW, viewH)!!
        val widthZoom = FitMode.WIDTH.zoomFor(pageW, pageH, viewW, viewH)!!
        assertTrue("fit page is the smaller of the two", pageZoom < widthZoom)
        assertEquals("no mode means the user's own zoom", null, FitMode.NONE.zoomFor(pageW, pageH, viewW, viewH))
    }
}
