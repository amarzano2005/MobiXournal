/**
 * The **double-tap on a living element**: the gesture that opens a table's cells, a plot's formula,
 * a component's value or a shape's readouts while the canvas is in selection mode.
 *
 * It lives beside [LivingEditTargets] rather than in `DrawingSurfaceInput` because it is one topic —
 * "how a living element is opened" — and because the dispatch itself is pure and tested on the JVM,
 * while this file is only the event half: the same double-tap window and slop the Hand tool uses
 * ([TapClassifier]), measured in view pixels, keyed to the element under the first tap so a tap on a
 * *different* element never pairs with it — and held per canvas rather than in file-level variables, so
 * two panes cannot pair with each other's taps.
 */
package com.mobixournal.render

import android.view.MotionEvent

/**
 * If this `ACTION_DOWN` completes a double-tap on a living element, select that element, open its
 * editor and return true so the touch goes no further (no marquee, no stroke).
 *
 * **Selection mode is the gate.** Opening an editor is an act about a selected object, and every
 * other tool's double-tap already means something of its own (a pen's second tap is a second dot),
 * so a component under the pen is drawn over rather than opened.
 */
internal fun DrawingSurfaceView.recordLivingDoubleTapDown(event: MotionEvent) {
    val inSelection = selectMode || lassoMode
    val toolKind = shapeKind
    val isPlotTool = placeKind == PlaceKind.PLOT
    if (!inSelection && toolKind == null && !isPlotTool) {
        taps.clearPendingLiving()
        return
    }
    val box = layout.pageAt(event.x + scrollX, event.y + scrollY) ?: run {
        taps.clearPendingLiving()
        return
    }
    val page = pageAt(box.index) ?: run {
        taps.clearPendingLiving()
        return
    }
    val hit = livingEditHit(page, box.toPtX(event.x, scrollX), box.toPtY(event.y, scrollY)) ?: run {
        taps.clearPendingLiving()
        return
    }
    if (!inSelection) {
        val expected = when {
            isPlotTool -> LivingEditFamily.PLOT
            toolKind != null -> livingFamilyOf(toolKind) ?: LivingEditFamily.SHAPE
            else -> null
        }
        if (expected != null && hit.target.family != expected) {
            taps.clearPendingLiving()
            return
        }
    }
    val key = box.index to hit.ref
    taps.livingDown(event.eventTime, event.x, event.y, key, box.index to hit)
}

/**
 * If the lift completes an un-dragged double-tap on a living element, select it and open its editor.
 */
internal fun DrawingSurfaceView.tryFinishLivingDoubleTap(upX: Float, upY: Float): Boolean {
    @Suppress("UNCHECKED_CAST")
    val pair = taps.consumePendingLiving(upX, upY, DrawingSurfaceDefaults.TAP_SLOP_PX) as? Pair<Int, LivingEditHit>
        ?: return false
    val (pageIndex, hit) = pair
    selection = ActiveSelection(pageIndex, setOf(hit.ref))
    onSelectionChanged?.invoke(true)
    reportSelectionRect()
    render()
    onLivingDoubleTap?.invoke(hit.target)
    return true
}
