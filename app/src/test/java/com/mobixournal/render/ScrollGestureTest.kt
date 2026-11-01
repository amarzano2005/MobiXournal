package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scrolling as an object: the pan delta, the pinch, the hold that keeps a two-finger tap a tap, and the
 * auto-scroll loop that keeps a dragged selection moving at an edge.
 *
 * Everything the canvas would supply — the viewport, the zoom, the frame clock — is a recorder here, so
 * the loop is run by hand and each step is asserted as an effect rather than inferred from a device.
 */
class ScrollGestureTest {

    private class FakeMomentum : ScrollMomentum {
        var cleared = 0
        var rebaselines = 0
        var tracks = 0
        var launches = 0
        var lastGain = 0f

        override fun clearRelease() {
            cleared++
        }

        override fun rebaseline(timeMs: Long, x: Float, y: Float) {
            rebaselines++
        }

        override fun track(timeMs: Long, x: Float, y: Float) {
            tracks++
        }

        override fun launch(panSensitivity: Float) {
            launches++
            lastGain = panSensitivity
        }
    }

    private val momentum = FakeMomentum()
    private val pans = mutableListOf<Pair<Float, Float>>()
    private val zooms = mutableListOf<Triple<Float, Float, Float>>()
    private val drags = mutableListOf<Pair<Float, Float>>()
    private var repaints = 0
    private var framePosted = 0
    private var frameRemoved = 0
    private var canScroll = true

    private val scroll = ScrollGesture(
        scrollBy = { dx, dy ->
            pans += dx to dy
            canScroll
        },
        zoomAbout = { fx, fy, factor -> zooms += Triple(fx, fy, factor) },
        momentum = momentum,
        panSensitivity = { 2f },
        touchSlopPx = { 8f },
        edgePx = { 50f },
        stepPx = { 12f },
        viewWidth = { 1000 },
        viewHeight = { 800 },
        dragAt = { x, y -> drags += x to y },
        render = { repaints++ },
        postFrame = { framePosted++ },
        removeFrame = { frameRemoved++ },
    )

    @Test
    fun `a pan starts from a fresh baseline and no old release`() {
        scroll.begin(focusX = 100f, focusY = 200f, span = 0f, timeMs = 1_000)

        assertTrue(scroll.active)
        assertEquals(100f, scroll.lastFocusX, 0f)
        assertEquals(200f, scroll.lastFocusY, 0f)
        assertEquals(1, momentum.cleared)
        assertEquals(1, momentum.rebaselines)
    }

    @Test
    fun `a frame applies the delta at the pan gain and tracks the velocity`() {
        scroll.begin(100f, 200f, 0f, 1_000)

        scroll.move(focusX = 130f, focusY = 190f, span = 0f, timeMs = 1_016, holdForGesture = false)

        assertEquals(-30f * 2f, pans.single().first, 0f)
        assertEquals(10f * 2f, pans.single().second, 0f)
        assertEquals(1, momentum.tracks)
        assertEquals(1, repaints)
        assertEquals("the baseline moved with the finger", 130f, scroll.lastFocusX, 0f)
    }

    @Test
    fun `a gesture that may still be a two-finger tap does not move the page`() {
        scroll.begin(100f, 200f, 100f, 1_000)

        scroll.move(130f, 190f, span = 100f, timeMs = 1_016, holdForGesture = true)

        assertTrue("nothing may drift under a tap that is about to fire", pans.isEmpty())
        assertEquals(0, repaints)
        assertEquals("but the baseline follows the fingers", 130f, scroll.lastFocusX, 0f)
    }

    @Test
    fun `a span change proves it was a pinch, so the hold is lifted`() {
        scroll.begin(100f, 200f, 100f, 1_000)

        scroll.move(100f, 200f, span = 160f, timeMs = 1_016, holdForGesture = true)

        assertEquals("the pan is applied", 1, pans.size)
        assertEquals(1.6f, zooms.single().third, 1e-4f)
        assertEquals(100f, zooms.single().first, 0f)
    }

    @Test
    fun `a tiny span change pinches, because the slop only decides the hold`() {
        scroll.begin(100f, 200f, 100f, 1_000)

        scroll.move(100f, 200f, span = 105f, timeMs = 1_016, holdForGesture = false)

        assertEquals("the pinch is proportional to the span", 1.05f, zooms.single().third, 1e-4f)
    }

    @Test
    fun `the glide inherits the pan gain when the gesture ends`() {
        scroll.begin(100f, 200f, 0f, 1_000)

        scroll.glide()

        assertEquals(1, momentum.launches)
        assertEquals(2f, momentum.lastGain, 0f)
    }

    @Test
    fun `a finger leaving re-baselines instead of reading the jump as a flick`() {
        scroll.begin(100f, 200f, 120f, 1_000)

        scroll.rebaseSpan()
        scroll.rebaseFocus(focusX = 90f, focusY = 210f, timeMs = 1_040)

        assertEquals("0 means \"no span yet\", so the next frame cannot be a pinch-out", 0f, scroll.lastSpan, 0f)
        assertEquals(90f, scroll.lastFocusX, 0f)
        assertEquals(2, momentum.rebaselines)
    }

    @Test
    fun `a drag inside the page scrolls nothing`() {
        scroll.updateDrag(500f, 400f)

        assertEquals(0, framePosted)
    }

    @Test
    fun `pushing into an edge band starts the loop once`() {
        scroll.updateDrag(990f, 400f)
        assertEquals(1, framePosted)

        scroll.updateDrag(980f, 400f)
        assertEquals("the same band does not repost", 1, framePosted)

        scroll.updateDrag(500f, 400f)
        assertEquals("and leaving it cancels the frame", 1, frameRemoved)
    }

    @Test
    fun `one auto-scroll frame moves a step, re-applies the drag and reposts`() {
        scroll.updateDrag(500f, 790f)

        scroll.autoScrollFrame()

        assertEquals(0f, pans.single().first, 0f)
        assertEquals(12f, pans.single().second, 0f)
        assertEquals("the selection rides the scrolling page", 500f to 790f, drags.single())
        assertEquals("and the loop continues", 2, framePosted)
    }

    @Test
    fun `a frame that cannot scroll any further stops the loop`() {
        scroll.updateDrag(500f, 790f)
        canScroll = false

        scroll.autoScrollFrame()
        assertTrue("nothing was dragged into a pinned page", drags.isEmpty())

        scroll.autoScrollFrame()
        assertEquals("and the repeat is over: no step, no drag, no repost", 1, framePosted)
        assertEquals(1, pans.size)
    }
}
