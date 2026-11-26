/**
 * The canvas's **scrolling**: the two-finger pan and its pinch, the glide a released pan leaves behind,
 * and the auto-scroll that keeps a dragged selection moving when the finger reaches an edge band.
 *
 * Scrolling is not one gesture but three that share a small amount of state, and that state used to be
 * nine members of `DrawingSurfaceView`:
 *
 *  - the **pan's baseline** — the mean pointer position and the mean span of the previous frame, from
 *    which each frame's delta (and its pinch ratio) is measured. They are the same numbers a two-finger
 *    tap is judged by, which is why moving inside the slop is held back rather than applied: a hold must
 *    not drift the page out from under a tap that is about to fire;
 *  - the **glide**, handed to [MomentumDriver] at lift-off with the velocity the pan was tracking;
 *  - the **auto-scroll loop** — the direction the dragged finger is pushing, the last drag position, and
 *    the frame callback that re-applies the drag as the page moves under it. It runs on the
 *    choreographer, so it scrolls even while the finger is held still in the band.
 *
 * Everything it needs from the canvas is a lambda: how to scroll, how to pinch-zoom, how big the view is,
 * the pan gain, the platform slop, and how to re-apply the drag. That is what makes the whole thing —
 * including the loop — drivable on the JVM by hand (`ScrollGestureTest`), with no `View`, no frame clock
 * and no `MotionEvent`.
 */
package com.mobixournal.render

import kotlin.math.abs

/** View-px bounding box of a dragged selection, re-read live on each auto-scroll frame. */
internal data class DragBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Xournal++-style continuous edge panning speed (px/s) along a single axis:
 * zero while inside `[visMin, visMax]`, continuous linear ramp from zero proportional to the
 * overshoot fraction (with a ramp floor so tiny items don't jump), or pointer-offset control
 * when the dragged selection is larger than the visible viewport.
 */
internal fun computeAxisPanSpeed(
    visMin: Float,
    visMax: Float,
    ptrPos: Float,
    bMin: Float,
    bMax: Float,
    margin: Float,
    basePctPerSec: Float,
    maxMult: Float,
): Float {
    val visLen = (visMax - visMin).coerceAtLeast(1f)
    val bboxLen = (bMax - bMin).coerceAtLeast(0f)
    val dir: Int
    val frac: Float
    when {
        bboxLen < 1f -> {
            val denom = margin.coerceAtLeast(24f)
            when {
                ptrPos > visMax -> { dir = 1; frac = ((ptrPos - visMax) / denom).coerceIn(0f, 1f) }
                ptrPos < visMin -> { dir = -1; frac = ((visMin - ptrPos) / denom).coerceIn(0f, 1f) }
                else -> return 0f
            }
        }
        bboxLen > visLen -> {
            val half = visLen * 0.5f
            val diff = ptrPos - (visMin + half)
            if (abs(diff) <= 0.1f * half) return 0f
            dir = if (diff > 0f) 1 else -1
            frac = (abs(diff) / half).coerceIn(0f, 1f)
        }
        else -> {
            val denom = maxOf(bboxLen, margin * 1.5f)
            when {
                bMax > visMax && bMin >= visMin -> { dir = 1; frac = ((bMax - visMax) / denom).coerceIn(0f, 1f) }
                bMin < visMin && bMax <= visMax -> { dir = -1; frac = ((visMin - bMin) / denom).coerceIn(0f, 1f) }
                else -> return 0f
            }
        }
    }
    return dir * visLen * (basePctPerSec / 100f) * (maxMult * frac)
}

/**
 * The four things scrolling asks of the momentum driver — an interface so that this object, and the
 * loop inside it, can be driven on the JVM with no `Context`, no choreographer and no glide.
 */
internal interface ScrollMomentum {
    /** Forget the release the previous gesture left behind. */
    fun clearRelease()

    /** Adopt a new position as the velocity sample's origin (a finger arrived or left). */
    fun rebaseline(timeMs: Long, x: Float, y: Float)

    /** Feed one frame's focus to the velocity estimate. */
    fun track(timeMs: Long, x: Float, y: Float)

    /** Start the glide with the tracked velocity and the pan gain. */
    fun launch(panSensitivity: Float)
}

internal class ScrollGesture(
    /** Move the viewport by a pan delta (view px); false when it is already pinned at a bound. */
    private val scrollBy: (Float, Float) -> Boolean,
    /** Zoom by [factor] about ([focusX], [focusY]) — the pinch. */
    private val zoomAbout: (Float, Float, Float) -> Unit,
    /** The glide a released pan leaves behind. */
    private val momentum: ScrollMomentum,
    /** The pan gain from Settings: 1 tracks the finger, <1 slower, >1 faster, 0 freezes the page. */
    private val panSensitivity: () -> Float,
    /** The platform touch slop (px) — how far a span must change to count as a pinch. */
    private val touchSlopPx: () -> Float,
    /** The band at each edge (px) that starts the auto-scroll. */
    private val edgePx: () -> Float,
    /** How far one auto-scroll frame moves the page (px) when speed lambdas are omitted. */
    private val stepPx: () -> Float,
    private val viewWidth: () -> Int,
    private val viewHeight: () -> Int,
    /** Re-apply the live drag at a view position, so it rides the scrolling sheet. */
    private val dragAt: (Float, Float) -> Unit,
    private val render: () -> Unit,
    /** Schedule [autoScrollFrame] for the next frame — the view's choreographer callback. */
    private val postFrame: () -> Unit,
    /** Drop a scheduled frame (the drag left the band, or the gesture ended). */
    private val removeFrame: () -> Unit,
    private val delayMs: Long = DrawingSurfaceDefaults.DRAG_AUTOSCROLL_DELAY_MS,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val safeLeftPx: () -> Float = { 0f },
    private val safeTopPx: () -> Float = { 0f },
    private val safeRightPx: () -> Float = { 0f },
    private val safeBottomPx: () -> Float = { 0f },
    private val liveSelectionBounds: () -> DragBounds? = { null },
    private val basePctPerSec: Float = DrawingSurfaceDefaults.DRAG_AUTOSCROLL_BASE_PCT_PER_SEC,
    private val maxMult: Float = DrawingSurfaceDefaults.DRAG_AUTOSCROLL_MAX_MULT,
) {

    /**
     * True from the moment a pan begins until the gesture ends — what the overlays read to know a
     * two-finger gesture is in flight (a stroking pen and a panning hand never coexist).
     */
    var active = false

    /** Mean pointer position of the previous frame, in view px — the delta's origin. */
    var lastFocusX = 0f
        private set
    var lastFocusY = 0f
        private set

    /** Mean span of the previous frame (0 for one finger); its change is the pinch. */
    var lastSpan = 0f
        private set

    /** Where the dragged selection or marquee was last seen, in view px — the auto-scroll re-applies it. */
    var dragX = 0f
        private set
    var dragY = 0f
        private set
    private var dragBoundsL = 0f
    private var dragBoundsT = 0f
    private var dragBoundsR = 0f
    private var dragBoundsB = 0f

    /** The band the drag is pushing into: -1, 0 or +1 per axis. */
    private var dirX = 0
    private var dirY = 0
    private var posted = false
    private var edgeEnterTimeMs = 0L
    private var lastFrameTimeMs = 0L

    /**
     * A pan has begun: take the baselines, and drop the release the momentum would otherwise carry from
     * the previous gesture (a new pan must not inherit the old one's velocity).
     */
    fun begin(focusX: Float, focusY: Float, span: Float, timeMs: Long) {
        active = true
        lastFocusX = focusX
        lastFocusY = focusY
        lastSpan = span
        momentum.clearRelease()
        momentum.rebaseline(timeMs, focusX, focusY)
    }

    /**
     * One frame of the pan. [holdForGesture] is true while the gesture is still a candidate for a
     * two-/three-finger tap: nothing moves (only the baseline advances) unless a span change has already
     * made the gesture a pinch, in which case it never could have been a tap.
     */
    fun move(focusX: Float, focusY: Float, span: Float, timeMs: Long, holdForGesture: Boolean) {
        val spanChanged = lastSpan > 0f && abs(span - lastSpan) > touchSlopPx()
        if (holdForGesture && !spanChanged) {
            lastFocusX = focusX
            lastFocusY = focusY
            lastSpan = span
            return
        }
        val gain = panSensitivity()
        scrollBy((lastFocusX - focusX) * gain, (lastFocusY - focusY) * gain)
        lastFocusX = focusX
        lastFocusY = focusY
        // Two fingers also pinch: a change in span since the last frame scales zoom about the focus.
        if (lastSpan > DrawingSurfaceDefaults.PINCH_MIN_SPAN_PX &&
            span > DrawingSurfaceDefaults.PINCH_MIN_SPAN_PX
        ) {
            zoomAbout(focusX, focusY, span / lastSpan)
        }
        lastSpan = span
        momentum.track(timeMs, focusX, focusY)
        render()
    }

    /** Hand the pan's velocity to the glide once the gesture is over. */
    fun glide() {
        momentum.launch(panSensitivity())
    }

    /** Re-baseline the span after a finger leaves, so the drop is not read as a pinch-out. */
    fun rebaseSpan() {
        lastSpan = 0f
    }

    /** Adopt the remaining pointers' focus after one lifts, without treating the jump as a pan. */
    fun rebaseFocus(focusX: Float, focusY: Float, timeMs: Long) {
        lastFocusX = focusX
        lastFocusY = focusY
        momentum.rebaseline(timeMs, focusX, focusY)
    }

    // --- drag auto-scroll: keep scrolling while a selection is dragged into an edge band -----------

    /**
     * Update the auto-scroll direction from a drag's pointer ([viewX], [viewY]) and optional
     * dragged element bounds relative to the visible canvas area (clear of overlaid toolbars).
     */
    fun updateDrag(
        viewX: Float,
        viewY: Float,
        boundsLeft: Float = viewX,
        boundsTop: Float = viewY,
        boundsRight: Float = viewX,
        boundsBottom: Float = viewY,
    ) {
        dragX = viewX
        dragY = viewY
        dragBoundsL = boundsLeft
        dragBoundsT = boundsTop
        dragBoundsR = boundsRight
        dragBoundsB = boundsBottom
        val margin = edgePx()
        val visL = safeLeftPx() + margin
        val visT = safeTopPx() + margin
        val visR = (viewWidth() - safeRightPx() - margin).coerceAtLeast(visL + 1f)
        val visB = (viewHeight() - safeBottomPx() - margin).coerceAtLeast(visT + 1f)
        val sx = computeAxisPanSpeed(visL, visR, viewX, boundsLeft, boundsRight, margin, basePctPerSec, maxMult)
        val sy = computeAxisPanSpeed(visT, visB, viewY, boundsTop, boundsBottom, margin, basePctPerSec, maxMult)
        val nextX = if (sx > 0f) 1 else if (sx < 0f) -1 else 0
        val nextY = if (sy > 0f) 1 else if (sy < 0f) -1 else 0
        if (nextX == dirX && nextY == dirY) return
        val wasIdle = dirX == 0 && dirY == 0
        dirX = nextX
        dirY = nextY
        if (dirX != 0 || dirY != 0) {
            if (wasIdle) {
                edgeEnterTimeMs = nowMs()
                lastFrameTimeMs = 0L
            }
            postAutoScroll()
        } else {
            stopAutoScroll()
        }
    }

    /** Stop the auto-scroll repeat (a released/cancelled gesture, or the drag left the edge band). */
    fun stopAutoScroll() {
        dirX = 0
        dirY = 0
        edgeEnterTimeMs = 0L
        lastFrameTimeMs = 0L
        if (posted) {
            removeFrame()
            posted = false
        }
    }

    /**
     * One auto-scroll frame: re-reads live selection bounds, computes continuous Xournal++-style
     * edge pan speed for elapsed `dt`, scrolls viewport, re-applies drag, and reposts.
     */
    fun autoScrollFrame() {
        posted = false
        if (dirX == 0 && dirY == 0) return
        val now = nowMs()
        if (now - edgeEnterTimeMs < delayMs) {
            postAutoScroll()
            return
        }
        val dtSec = if (lastFrameTimeMs == 0L) 0.016f else ((now - lastFrameTimeMs).coerceIn(1L, 50L) / 1000f)
        lastFrameTimeMs = now
        liveSelectionBounds()?.let { live ->
            dragBoundsL = live.left; dragBoundsT = live.top; dragBoundsR = live.right; dragBoundsB = live.bottom
        }
        val margin = edgePx()
        val visL = safeLeftPx() + margin
        val visT = safeTopPx() + margin
        val visR = (viewWidth() - safeRightPx() - margin).coerceAtLeast(visL + 1f)
        val visB = (viewHeight() - safeBottomPx() - margin).coerceAtLeast(visT + 1f)
        val sx = computeAxisPanSpeed(visL, visR, dragX, dragBoundsL, dragBoundsR, margin, basePctPerSec, maxMult)
        val sy = computeAxisPanSpeed(visT, visB, dragY, dragBoundsT, dragBoundsB, margin, basePctPerSec, maxMult)
        if (sx == 0f && sy == 0f) {
            stopAutoScroll()
            return
        }
        if (!scrollBy(sx * dtSec, sy * dtSec)) {
            stopAutoScroll() // pinned at a bound in both axes: nothing left to scroll into
            return
        }
        dragAt(dragX, dragY)
        postAutoScroll()
    }

    private fun postAutoScroll() {
        if (posted) return
        posted = true
        postFrame()
    }
}
