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
    /** How far one auto-scroll frame moves the page (px). */
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

    /** The band the drag is pushing into: -1, 0 or +1 per axis. */
    private var dirX = 0
    private var dirY = 0
    private var posted = false

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
     * Update the auto-scroll direction from a drag's position (view px): pushing into an edge band
     * starts the page scrolling that way, and moving back out stops it. Idempotent, so it is safe on
     * every MOVE.
     */
    fun updateDrag(viewX: Float, viewY: Float) {
        dragX = viewX
        dragY = viewY
        val edge = edgePx()
        val nextX = when {
            viewX > viewWidth() - edge -> 1
            viewX < edge -> -1
            else -> 0
        }
        val nextY = when {
            viewY > viewHeight() - edge -> 1
            viewY < edge -> -1
            else -> 0
        }
        if (nextX == dirX && nextY == dirY) return
        dirX = nextX
        dirY = nextY
        if (dirX != 0 || dirY != 0) postAutoScroll() else stopAutoScroll()
    }

    /** Stop the auto-scroll repeat (a released/cancelled gesture, or the drag left the edge band). */
    fun stopAutoScroll() {
        dirX = 0
        dirY = 0
        if (posted) {
            removeFrame()
            posted = false
        }
    }

    /**
     * One auto-scroll frame: scroll one step toward the edge the finger is in, re-apply the drag at the
     * finger's last position so it rides the scrolling sheet, then repost.
     */
    fun autoScrollFrame() {
        posted = false
        if (dirX == 0 && dirY == 0) return
        val step = stepPx()
        if (!scrollBy(dirX * step, dirY * step)) {
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
