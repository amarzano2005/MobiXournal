package com.mobixournal.render

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Dragging a selection out of the canvas.
 *
 * A **long-press** on the selected elements lifts them as a system drag; the reply is the same
 * [SelectionClipData] Copy publishes, so a drop in another MobiXournal view arrives live and a drop
 * in another app arrives as SVG, PNG or text depending on what it understands. Only a finger lifts a
 * selection (a stylus is drawing), and a touch that travels past slop before the timeout is the
 * ordinary move/resize gesture, not a lift — the arm is disarmed on that first move.
 *
 * This is deliberately a small piece of state plus a [Runnable]: the heavy lifting is the clipboard
 * payload and the drop target, which both already exist.
 */
internal class SelectionDragOut {
    var armed = false
    var downX = 0f
    var downY = 0f
    var fire: Runnable? = null
}

/** Arm the long-press that lifts the current selection, for a finger down inside its outline. */
internal fun DrawingSurfaceView.armSelectionDrag(event: MotionEvent) {
    cancelSelectionDrag()
    if (!selectMode || event.pointerCount != 1) return
    if (pointerKindOf(event, 0) != PointerKind.FINGER) return
    val sel = selection ?: return
    val box = layout.boxes.getOrNull(sel.pageIndex) ?: return
    val page = pageAt(sel.pageIndex) ?: return
    val bounds = SelectionTester.boundsOf(page, sel.refs) ?: return
    val x = box.toPtX(event.x, scrollX)
    val y = box.toPtY(event.y, scrollY)
    if (x < bounds.left || x > bounds.right || y < bounds.top || y > bounds.bottom) return
    val out = selectionDragOut
    out.armed = true
    out.downX = event.x
    out.downY = event.y
    val runnable = Runnable { startSelectionDrag() }
    out.fire = runnable
    postDelayed(runnable, longPressMs)
}

/** A touch that travels before the timeout is a move or a resize; stop waiting to lift it. */
internal fun DrawingSurfaceView.trackSelectionDragArm(event: MotionEvent) {
    val out = selectionDragOut
    if (!out.armed) return
    if (hypot(event.x - out.downX, event.y - out.downY) > touchSlopPx) cancelSelectionDrag()
}

/** Disarm any pending lift (release, a second finger, or a gesture that became a move). */
internal fun DrawingSurfaceView.cancelSelectionDrag() {
    val out = selectionDragOut
    out.fire?.let(::removeCallbacks)
    out.fire = null
    out.armed = false
}

private fun DrawingSurfaceView.startSelectionDrag() {
    val out = selectionDragOut
    out.armed = false
    out.fire = null
    val sel = selection ?: return
    val page = pageAt(sel.pageIndex) ?: return
    val elements = SelectionOps.elementsAt(page, sel.refs)
    val clip = selectionClipData(elements) ?: return
    // The lift replaces the move this down began: no handle follows the finger, and the release
    // must not commit a transform that never happened.
    gestures.reset()
    cancelPageDrag()
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    startDragAndDrop(
        clip,
        View.DragShadowBuilder(this),
        null,
        View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ,
    )
}
