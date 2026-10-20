package com.mobixournal.render

import kotlin.math.hypot

/**
 * How many fingers a completed, still tap held.
 *
 * The detector stops here: which action a two- or three-finger tap runs is the user's choice
 * (Settings → Input → **Finger gestures**), resolved by [fingerTapQuickAction]. Keeping the two
 * apart is what lets the same tap mean undo, redo, a tool toggle or nothing without the gesture
 * recogniser knowing anything about the editor.
 */
enum class FingerTap {
    NONE,
    TWO_FINGER,
    THREE_FINGER,
}

/**
 * The [QuickAction] a completed [tap] runs, under the user's two- and three-finger choices.
 *
 * Pure and total: every combination of the three resolves to exactly one action, so the surface has
 * no branch of its own to get wrong.
 */
fun fingerTapQuickAction(tap: FingerTap, twoFinger: QuickAction, threeFinger: QuickAction): QuickAction =
    when (tap) {
        FingerTap.NONE -> QuickAction.NONE
        FingerTap.TWO_FINGER -> twoFinger
        FingerTap.THREE_FINGER -> threeFinger
    }

/**
 * Pure detector for still two- and three-finger taps.
 *
 * Tracks individual touch contacts across pointers so that centroid shifts upon pointer addition
 * or removal never falsely count as movement, and freezes movement tracking once fingers begin
 * lifting to prevent lift-off jitter from disqualifying a tap.
 */
class MultiFingerTapDetector(
    var shortcutsEnabled: Boolean = true,
    var timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 350L
        private const val MAX_TRACKED_POINTERS = 16
    }

    var candidate: Boolean = false
        private set
    var moved: Boolean = false
        private set
    var lifting: Boolean = false
        private set
    var maxPointers: Int = 0
        private set
    var startTime: Long = 0L
        private set
    var slop: Float = 0f

    private val downX = FloatArray(MAX_TRACKED_POINTERS)
    private val downY = FloatArray(MAX_TRACKED_POINTERS)
    private val downValid = BooleanArray(MAX_TRACKED_POINTERS)

    /** Reset state on the very first touch contact (ACTION_DOWN). */
    fun onDown(time: Long, x: Float, y: Float, pointerId: Int, touchSlop: Float) {
        candidate = false
        moved = false
        lifting = false
        maxPointers = 1
        startTime = time
        slop = touchSlop
        downValid.fill(false)
        if (pointerId in 0 until MAX_TRACKED_POINTERS) {
            downX[pointerId] = x
            downY[pointerId] = y
            downValid[pointerId] = true
        }
    }

    /** Record an additional pointer contact (ACTION_POINTER_DOWN). */
    fun onPointerDown(time: Long, x: Float, y: Float, pointerId: Int, pointerCount: Int) {
        if (pointerId in 0 until MAX_TRACKED_POINTERS) {
            downX[pointerId] = x
            downY[pointerId] = y
            downValid[pointerId] = true
        }
        if (!shortcutsEnabled || moved) return
        maxPointers = maxOf(maxPointers, pointerCount)
        when {
            maxPointers > 3 -> candidate = false
            pointerCount in 2..3 && !moved && (time - startTime <= timeoutMs) -> {
                candidate = true
            }
        }
    }

    /**
     * Check pointer movement against individual touch-down positions.
     * Returns true if movement exceeded slop (disqualifying the tap).
     */
    fun onMove(time: Long, pointerCount: Int, getId: (Int) -> Int, getX: (Int) -> Float, getY: (Int) -> Float): Boolean {
        if (moved || lifting) return moved
        for (i in 0 until pointerCount) {
            val id = getId(i)
            if (id in 0 until MAX_TRACKED_POINTERS && downValid[id]) {
                if (hypot(getX(i) - downX[id], getY(i) - downY[id]) > slop) {
                    moved = true
                    candidate = false
                    return true
                }
            }
        }
        if (candidate && time - startTime > timeoutMs) {
            candidate = false
        }
        return moved
    }

    /** A pointer has lifted (ACTION_POINTER_UP). Freeze movement checks to avoid lift-off jitter. */
    fun onPointerUp(pointerId: Int) {
        if (pointerId in 0 until MAX_TRACKED_POINTERS) {
            downValid[pointerId] = false
        }
        if (candidate) {
            lifting = true
        }
    }

    /** All pointers have lifted (ACTION_UP). Evaluate whether the gesture was a finger tap. */
    fun onUp(time: Long): FingerTap {
        if (!candidate || moved) return FingerTap.NONE
        if (time - startTime > timeoutMs) return FingerTap.NONE
        candidate = false
        return when (maxPointers) {
            2 -> FingerTap.TWO_FINGER
            3 -> FingerTap.THREE_FINGER
            else -> FingerTap.NONE
        }
    }

    /** Explicitly cancel and disarm the detector. */
    fun reset() {
        candidate = false
        moved = false
        lifting = false
        maxPointers = 0
        downValid.fill(false)
    }
}
