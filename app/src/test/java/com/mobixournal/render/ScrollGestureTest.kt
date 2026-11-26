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
    private var clockMs = 1_000L

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
        stepPx = { 10f },
        viewWidth = { 1000 },
        viewHeight = { 800 },
        dragAt = { x, y -> drags += x to y },
        render = { repaints++ },
        postFrame = { framePosted++ },
        removeFrame = { frameRemoved++ },
        delayMs = 0L,
        nowMs = { clockMs },
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
    fun `continuous Xournalpp edge pan scales from zero with selection overshoot and handles large boxes`() {
        // Visible inner height is [50, 750] (visLen = 700). A 100px box overshooting by 50px -> frac = 0.5 -> speed = 700 * 0.2 * 5 * 0.5 = 350 px/s
        scroll.updateDrag(viewX = 500f, viewY = 760f, boundsLeft = 400f, boundsTop = 700f, boundsRight = 600f, boundsBottom = 800f)
        scroll.autoScrollFrame()
        assertEquals(0f, pans.single().first, 0f)
        assertEquals(350f * 0.016f, pans.single().second, 1e-2f)
        assertEquals("the selection rides the scrolling page", 500f to 760f, drags.single())
        assertEquals(2, framePosted)

        // Large selection (bboxLen 900 > visLen 700): 10% center deadzone (center=400, half=350) -> no scroll near center
        assertEquals(0f, computeAxisPanSpeed(50f, 750f, ptrPos = 410f, bMin = 0f, bMax = 900f, margin = 50f, basePctPerSec = 20f, maxMult = 5f), 1e-4f)
        // Pointer pushed toward bottom of large selection scrolls down proportionally
        assertTrue(computeAxisPanSpeed(50f, 750f, ptrPos = 600f, bMin = 0f, bMax = 900f, margin = 50f, basePctPerSec = 20f, maxMult = 5f) > 0f)
    }

    @Test
    fun `a frame that cannot scroll any further stops the loop`() {
        scroll.updateDrag(500f, 790f)
        canScroll = false

        scroll.autoScrollFrame()
        assertTrue("nothing was dragged into a pinned page", drags.isEmpty())

        clockMs += 20L
        scroll.autoScrollFrame()
        assertEquals("and the repeat is over: no step, no drag, no repost", 1, framePosted)
        assertEquals(1, pans.size)
    }

    @Test
    fun `safe insets and dragged bounds trigger auto-scroll at visible inner edge`() {
        var live: DragBounds? = DragBounds(400f, 70f, 600f, 200f)
        val insetScroll = ScrollGesture(
            scrollBy = { dx, dy -> pans += dx to dy; true },
            zoomAbout = { _, _, _ -> },
            momentum = momentum,
            panSensitivity = { 1f },
            touchSlopPx = { 8f },
            edgePx = { 50f },
            stepPx = { 10f },
            viewWidth = { 1000 },
            viewHeight = { 800 },
            dragAt = { x, y -> drags += x to y },
            render = { repaints++ },
            postFrame = { framePosted++ },
            removeFrame = { frameRemoved++ },
            delayMs = 0L,
            nowMs = { clockMs },
            safeLeftPx = { 80f },
            safeTopPx = { 100f },
            safeRightPx = { 0f },
            safeBottomPx = { 0f },
            liveSelectionBounds = { live },
        )
        // visTop = safeTopPx(100) + edgePx(50) = 150; selection top=70 overshoots above -> scrolls up
        insetScroll.updateDrag(viewX = 500f, viewY = 130f, boundsLeft = 400f, boundsTop = 70f, boundsRight = 600f, boundsBottom = 200f)
        insetScroll.autoScrollFrame()
        assertTrue("top safe inset band scrolls up", pans.last().second < 0f)

        // Live bounds moved to right visible inner edge (visRight = 1000 - 50 = 950)
        pans.clear()
        live = DragBounds(400f, 350f, 995f, 450f)
        insetScroll.updateDrag(viewX = 500f, viewY = 400f, boundsLeft = 400f, boundsTop = 350f, boundsRight = 995f, boundsBottom = 450f)
        insetScroll.autoScrollFrame()
        assertTrue("dragged element edge pushes scroll right", pans.last().first > 0f)
    }

    @Test
    fun `pinching tracks active pinch span and clears on lift, rebase, or single-finger pan`() {
        scroll.begin(100f, 200f, span = 0f, timeMs = 1_000)
        assertFalse(scroll.pinching)

        scroll.move(110f, 210f, span = 0f, timeMs = 1_016, holdForGesture = false)
        assertFalse("single-finger pan never enters pinching", scroll.pinching)

        scroll.begin(100f, 200f, span = 100f, timeMs = 1_100)
        scroll.move(100f, 200f, span = 140f, timeMs = 1_116, holdForGesture = false)
        assertTrue("span change marks active pinch", scroll.pinching)

        scroll.rebaseSpan()
        assertFalse("lifting extra finger clears pinching", scroll.pinching)

        scroll.begin(100f, 200f, span = 100f, timeMs = 1_200)
        scroll.move(100f, 200f, span = 150f, timeMs = 1_216, holdForGesture = false)
        assertTrue(scroll.pinching)
        scroll.active = false
        assertFalse("ending scroll clears pinching", scroll.pinching)
    }
}
