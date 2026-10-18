package com.mobixournal.render

import kotlin.math.hypot

/**
 * Result of a completed multi-finger touch gesture.
 */
enum class MultiFingerTapAction {
    NONE,
    UNDO,
    REDO,
}

/**
 * Pure detector for two-finger (Undo) and three-finger (Redo) tap shortcuts.
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

    /** All pointers have lifted (ACTION_UP). Evaluate whether the gesture was a shortcut tap. */
    fun onUp(time: Long): MultiFingerTapAction {
        if (!candidate || moved) return MultiFingerTapAction.NONE
        if (time - startTime > timeoutMs) return MultiFingerTapAction.NONE
        candidate = false
        return when (maxPointers) {
            2 -> MultiFingerTapAction.UNDO
            3 -> MultiFingerTapAction.REDO
            else -> MultiFingerTapAction.NONE
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
