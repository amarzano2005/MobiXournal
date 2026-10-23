/**
 * The **double-tap on a living element**: the gesture that opens a table's cells, a plot's formula,
 * a component's value or a shape's readouts while the canvas is in selection mode.
 *
 * It lives beside [LivingEditTargets] rather than in `DrawingSurfaceInput` because it is one topic —
 * "how a living element is opened" — and because the dispatch itself is pure and tested on the JVM,
 * while this file is only the event half: the same double-tap window and slop the barrel and the Hand
 * tool use ([DrawingSurfaceView.doubleTapTimeoutMs]), measured in view pixels, keyed to the element
 * under the first tap so a tap on a *different* element never pairs with it.
 */
package com.mobixournal.render

import android.view.MotionEvent
import kotlin.math.hypot

private var lastLivingTapTime = 0L
private var lastLivingTapX = 0f
private var lastLivingTapY = 0f
private var lastLivingTapKey: Any? = null

/**
 * If this `ACTION_DOWN` completes a double-tap on a living element, select that element, open its
 * editor and return true so the touch goes no further (no marquee, no stroke).
 *
 * **Selection mode is the gate.** Opening an editor is an act about a selected object, and every
 * other tool's double-tap already means something of its own (a pen's second tap is a second dot),
 * so a component under the pen is drawn over rather than opened.
 */
internal fun DrawingSurfaceView.tryLivingDoubleTap(event: MotionEvent): Boolean {
    if (!selectMode && !lassoMode) return false
    val box = layout.pageAt(event.x + scrollX, event.y + scrollY) ?: return false
    val page = pageAt(box.index) ?: return false
    val hit = livingEditHit(page, box.toPtX(event.x, scrollX), box.toPtY(event.y, scrollY)) ?: return false
    // Where the first tap landed, so the pair is about one element on one page.
    val key = box.index to hit.ref
    val now = event.eventTime
    val isDouble = now - lastLivingTapTime <= doubleTapTimeoutMs &&
        hypot(event.x - lastLivingTapX, event.y - lastLivingTapY) <= doubleTapSlopPx &&
        lastLivingTapKey == key
    lastLivingTapTime = if (isDouble) 0L else now
    lastLivingTapX = event.x
    lastLivingTapY = event.y
    lastLivingTapKey = key
    if (!isDouble) return false
    selection = ActiveSelection(box.index, setOf(hit.ref))
    onSelectionChanged?.invoke(true)
    reportSelectionRect()
    render()
    onLivingDoubleTap?.invoke(hit.target)
    return true
}
