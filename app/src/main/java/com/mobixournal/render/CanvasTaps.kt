/**
 * The canvas's **taps**: the platform's tap timings, and the two double-tap sequences that depend on them.
 *
 * Three gestures on the glass are decided by timing rather than by geometry, and all three used to keep
 * their own copy of the same arithmetic on `DrawingSurfaceView`:
 *
 *  - the **Hand tool's** double-tap (left third → previous page, right third → next, centre → full page).
 *    A press arms a candidate, a travel past the double-tap slop disqualifies it, and a lift that does not
 *    pair with the previous one becomes the first tap of a new pair;
 *  - the **living element's** double-tap (a table's cells, a plot's formula, a component's value), which
 *    additionally has to be about the *same* element on the same page — so its sequence is keyed;
 *  - the **long press** that lifts a selection, whose window and slop are read by `SelectionDragOut`.
 *
 * The timings are the platform's own — `ViewConfiguration`'s double-tap window, double-tap slop, long-press
 * window and touch slop — and they are constructor values here, so the whole thing is testable on the JVM
 * with no `View`, no clock and no `MotionEvent`: the tests feed event times and pixels
 * ([TapClassifierTest]).
 *
 * It also fixes a real leak: the living-element sequence used to live in **file-level** variables, so every
 * canvas in the app shared one tap history and a tap in one pane could pair with a tap in another. The
 * history belongs to the canvas that saw the taps, which is what an instance is.
 *
 * "Pen versus hand" is not decided here: which pointer owns the gesture is input routing
 * ([DrawingSurfaceRouter]), and this object is told only whether the Hand tool armed a candidate.
 */
package com.mobixournal.render

import kotlin.math.hypot

internal class TapClassifier(
    /** The platform's double-tap window, in ms: two presses further apart than this never pair. */
    val doubleTapTimeoutMs: Long,
    /** How far (view px) two taps may land apart and still be one double-tap. */
    val doubleTapSlopPx: Float,
    /** The platform's long-press window, in ms — what lifts a selection held still. */
    val longPressMs: Long,
    /** A touch that travels further than this (view px) is a move, not a tap. */
    val touchSlopPx: Float,
) {

    /** Where the current Hand-tool press landed — the x a double-tap routes by. */
    var tapX = 0f
        private set

    /** True while a Hand-tool press is still a candidate for a tap. */
    var candidate = false
        private set

    private var moved = false
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f

    /** The previous tap of the Hand tool's pair; a time of 0 means "no pair in progress". */
    private var firstTime = 0L
    private var firstX = 0f
    private var firstY = 0f

    /** The previous tap on a living element, keyed to what was under it. */
    private var livingTime = 0L
    private var livingX = 0f
    private var livingY = 0f
    private var livingKey: Any? = null

    /**
     * A single finger has gone down. [armed] is whether this press may become a tap at all — the Hand
     * tool, since every other tool's second tap means something of its own.
     */
    fun beginHand(armed: Boolean, timeMs: Long, x: Float, y: Float) {
        candidate = armed
        moved = false
        downTime = timeMs
        downX = x
        downY = y
        tapX = x
    }

    /** A move of the gesture in progress: travelling past the slop makes it a pan, not a tap. */
    fun handMove(x: Float, y: Float) {
        if (!candidate) return
        if (hypot(x - downX, y - downY) > doubleTapSlopPx) moved = true
    }

    /**
     * A lift. Returns true when it **completes** a double-tap — the previous tap was close enough in time
     * and place — and [tapX] then names where it landed. [flicked] disqualifies a short-but-fast swipe (the
     * gesture that started a glide, or the canvas below it, is a pan that stayed inside the slop).
     */
    fun endHand(flicked: Boolean): Boolean {
        if (!candidate) return false
        candidate = false
        if (moved || flicked) {
            firstTime = 0L
            return false
        }
        val pairs = firstTime != 0L &&
            downTime - firstTime <= doubleTapTimeoutMs &&
            hypot(downX - firstX, downY - firstY) <= doubleTapSlopPx
        if (pairs) {
            firstTime = 0L // consume, so a third tap doesn't immediately re-fire
            return true
        }
        firstTime = downTime
        firstX = downX
        firstY = downY
        return false
    }

    /** The press is no longer a tap at all (a second finger landed, or the gesture was cancelled). */
    fun cancelHand() {
        candidate = false
    }

    /**
     * A press on a living element. Returns true when it **completes** a double-tap on the same element of
     * the same page ([key] is the page and the element's ref), close enough in time and place. A press that
     * does not complete one starts the next pair; a completed one consumes the pair, so a third press opens
     * nothing.
     */
    fun livingTap(timeMs: Long, x: Float, y: Float, key: Any?): Boolean {
        val isDouble = livingTime != 0L &&
            timeMs - livingTime <= doubleTapTimeoutMs &&
            hypot(x - livingX, y - livingY) <= doubleTapSlopPx &&
            livingKey == key
        livingTime = if (isDouble) 0L else timeMs
        livingX = x
        livingY = y
        livingKey = key
        return isDouble
    }
}
