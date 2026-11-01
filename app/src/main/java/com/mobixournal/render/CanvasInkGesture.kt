/**
 * The **ink gesture in progress**: the samples the pen has laid down since touch-down, the multi-stroke
 * shape a shape tool is building, the page it belongs to, and the two machines that produce and filter
 * those samples — the router and the smoother.
 *
 * This is the canvas's hottest path, which is exactly why its state should not be five members of a
 * 1 300-line `View` that every extension can reach. A stroke is live for the length of one gesture and
 * every one of them follows the same sequence:
 *
 *  1. the **router** ([InkPointerRouter]) turns a `MotionEvent`'s pointers into the samples this gesture
 *     consumes, so a palm or a second finger never becomes ink (`DrawingSurfaceRouter.kt`);
 *  2. the **smoother** ([StrokeSmoother]) low-passes each one against the page's real px/pt, so a stroke
 *     drawn zoomed out keeps the document-space detail of one drawn at 100%;
 *  3. the samples accumulate in [current] — one list, in page-local pt, per gesture;
 *  4. a shape tool instead rebuilds [currentStrokes] from the anchor and the live end on every move;
 *  5. lift-off reads [current], commits it, and clears it.
 *
 * [currentPage] is the page the gesture started on: it is fixed at touch-down (a stroke may not jump
 * pages mid-drag) and every commit appends to it.
 *
 * The object owns the state and nothing about the document: it never calls `render`, never edits a page
 * and never decides what a stroke means — `DrawingSurfaceStrokes.kt` does that, reading the same fields
 * it always did, one `ink.` away. Android-free apart from the router's own `MotionEvent` plumbing, so the
 * storage rules are unit-tested on the JVM ([InkGestureTest]).
 */
package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint

internal class InkGesture(
    /** The filter every freehand sample passes through before it is stored. */
    val smoother: StrokeSmoother = StrokeSmoother(),
    /** Builds a router carrying the settings in force **now** — the canvas's `newPointerRouter`. */
    private val newRouter: () -> InkPointerRouter,
) {

    private var router: InkPointerRouter? = null

    /**
     * The `MotionEvent` → samples machine. Replaceable because a router latches the contacts it has seen:
     * when a gesture is abandoned or a different device takes over, a fresh one is the clean way to say
     * "nothing is down" (`DrawingSurfaceInput.kt`). Built on first read, not at construction, so it sees
     * the settings the canvas has by the time a gesture starts rather than the zeroes of field order.
     */
    var pointerRouter: InkPointerRouter
        get() = router ?: newRouter().also { router = it }
        set(value) {
            router = value
        }

    /** The freehand stroke in progress, in page-local pt, or null when no stroke is being drawn. */
    var current: ArrayList<StrokePoint>? = null

    /** The multi-stroke shape in progress (a capacitor with its gap, a table's grid), or null. */
    var currentStrokes: List<List<StrokePoint>>? = null

    /** The page the gesture started on — fixed at touch-down, and where the commit appends. */
    var currentPage = 0

    /** True while either kind of in-progress ink exists. */
    val inProgress: Boolean get() = current != null || currentStrokes != null

    /**
     * Forget the ink of the gesture (not the page it was on): what a cancelled gesture, an abandoned
     * drag and a committed stroke all do. A fresh router goes with it, so its contact map is empty for
     * the next gesture — a router left holding lifted contacts would read the next sample as a
     * two-finger navigation.
     */
    fun drop() {
        current = null
        currentStrokes = null
        pointerRouter.reset()
    }
}
