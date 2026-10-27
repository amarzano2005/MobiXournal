package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * **Where a press lands is where the sheet was drawn** — one derivation, not two.
 *
 * The painter places a page with `Viewport.inChrome` (a fitted page is centred in the room the floating
 * strips leave), while the pointer path used to re-derive its viewport with `clamp` alone from the
 * window's own scroll. On a fitted page the two disagreed by the chrome's inset, so ink, a marquee and a
 * figure landed a rail's width away from the pointer, and the gap *shrank* as the reader zoomed in — which
 * reads as a zoom problem and is not one.
 *
 * The arithmetic is checked here rather than in a scene because both halves are pure: the viewport the
 * painter used, and the viewport [SheetLayout.liveViewport] hands a press.
 */
class PointerMappingTest {

    /** A4 in points, and a window wide enough to hold it with the rail beside it. */
    private val pageW = 595f
    private val pageH = 842f
    private val viewW = 1400f
    private val viewH = 900f

    /** The rail's lane: the space a fitted page is centred in. */
    private val chrome = ChromeInset(left = 64f, top = 48f)

    /** The window's own scroll, untouched: a fitted page is centred by the canvas, not stored by the tab. */
    private val scroll = Viewport(zoom = (viewW - chrome.width).let { 1f })

    private fun fittedSheet(): SheetLayout {
        val zoom = Viewport.fitZoom(pageW, pageH, viewW - chrome.width, viewH - chrome.height)
        val painted = Viewport(zoom, 0f, 0f).inChrome(chrome, pageW, pageH, viewW, viewH)
        return SheetLayout(pageW, pageH, zoom, viewW, viewH, painted, chrome)
    }

    @Test
    fun `a press maps through the viewport the painter used`() {
        val sheet = fittedSheet()
        assertEquals(
            "the mapping is the painting",
            sheet.viewport,
            sheet.liveViewport(scroll),
        )
    }

    @Test
    fun `the retired derivation centred the page in the whole window, not beside the rail`() {
        val sheet = fittedSheet()
        // What the pointer path used to do: clamp the window's scroll, which centres in the *window*.
        val retired = scroll.copy(zoom = sheet.zoom).clamped(pageW, pageH, viewW, viewH)
        assertNotEquals(
            "the two derivations disagreed by half the rail's lane",
            sheet.viewport.offsetX,
            retired.offsetX,
        )
        // Painted x = chrome.left + (free − page)/2, retired x = (view − page)/2: half the lane apart.
        assertEquals(chrome.left / 2f, sheet.viewport.offsetX - retired.offsetX, 1e-3f)
    }

    @Test
    fun `a press lands on the page point the painter put under it`() {
        val sheet = fittedSheet()
        val density = 1.5f
        val mapping = sheet.liveViewport(scroll)
        for ((px, py) in listOf(300f to 200f, 640f to 470f, 900f to 700f)) {
            // Where the painter put page point p: offset × density + p × zoom × density.
            val paintedPageX = (px - sheet.viewport.offsetX * density) / (sheet.zoom * density)
            val paintedPageY = (py - sheet.viewport.offsetY * density) / (sheet.zoom * density)
            val (mappedX, mappedY) = pagePointOf(px, py, density, mapping)
            assertEquals("x at $px", paintedPageX.toDouble(), mappedX, 1e-3)
            assertEquals("y at $py", paintedPageY.toDouble(), mappedY, 1e-3)
        }
    }

    @Test
    fun `a page zoomed past the window keeps the scroll the reader dragged`() {
        // Free zoom: the page is bigger than the room beside the rail, so no centring applies and the
        // drag's own offsets are what a press maps through.
        val zoom = 3f
        val painted = Viewport(zoom, offsetX = -220f, offsetY = -140f)
            .inChrome(chrome, pageW, pageH, viewW, viewH)
        val sheet = SheetLayout(pageW, pageH, zoom, viewW, viewH, painted, chrome)
        val panned = Viewport(zoom, offsetX = -220f, offsetY = -140f)
        val mapping = sheet.liveViewport(panned)
        assertEquals("the drag is kept, not re-centred", panned.offsetX, mapping.offsetX, 1e-3f)
        assertEquals(panned.offsetY, mapping.offsetY, 1e-3f)
        assertEquals(painted, mapping)
    }
}
