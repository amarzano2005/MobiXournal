/**
 * The **dots** of a selection: the eight resize handles, the rotate knob for an all-stroke
 * selection, and a corner handle on each corner of a living figure.
 *
 * Split out of the render loop (`DrawingSurfacePaint`) because the handles are what the gesture
 * layer hit-tests ([SelectionGestureController]) — drawn here, tested there, and the two must agree
 * on the same geometry. Extensions on [DrawingSurfaceView], so they read the live selection.
 */
package com.mobixournal.render

import android.graphics.Canvas
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.render.CanvasChrome.Companion.HANDLE_DRAW_PX

/**
 * Draw every handle of [sel] inside its ink box, given the box's **padded** view-px edges
 * ([l], [t], [r], [bottom]) that [DrawingSurfaceView.drawSelectionBox] already computed.
 *
 * The four corner dots resize proportionally; the four edge-midpoint dots stretch a single axis
 * (out-of-proportion resize), which is why they are drawn at all. The right-edge one sits under the
 * rotate arm's foot: the arm starts there and the knob it leads to is what the rotate hit-test
 * answers to, so the two never fight over the same touch.
 *
 * A **flat** box — a line, only as thick as its pen (`DrawingSurfaceDefaults.isFlat`) — shows its two
 * end dots and nothing else: a corner would sit exactly on an end, and a dot along the thickness would
 * answer a press with a factor of one. Drawn here and tested in `beginHandleResize`, so the two agree.
 */
internal fun DrawingSurfaceView.drawSelectionHandleDots(
    canvas: Canvas,
    sel: ActiveSelection,
    box: PageBox,
    l: Float,
    t: Float,
    r: Float,
    bottom: Float,
) {
    val shaft = gestures.shaftOfSelection(sel)
    val flatX = DrawingSurfaceDefaults.isFlat(r - l, box.scale)
    val flatY = DrawingSurfaceDefaults.isFlat(bottom - t, box.scale)
    // A line or an arrow has one degree of freedom — the length of its shaft — so its two ends are the
    // only resize dots, wherever the box's corners happen to fall.
    if (shaft != null) {
        for (end in listOf(shaft.tailX to shaft.tailY, shaft.tipX to shaft.tipY)) {
            canvas.drawCircle(
                box.toViewX(end.first, viewport.scrollX),
                box.toViewY(end.second, viewport.scrollY),
                HANDLE_DRAW_PX,
                chrome.handle,
            )
        }
    }
    // Corner & edge resize handles: skipped for splines and for unlocked parametric shapes (which show vertex handles).
    val isSingleShape = singleSelectedShape() != null
    val isSingleSpline = singleSelectedSpline() != null
    val aspectLocked = gestures.aspectLocked && !isSingleSpline
    val showBoxHandles = !isSingleSpline && (!isSingleShape || aspectLocked)
    if (shaft == null && !flatX && !flatY && showBoxHandles) {
        for (hx in floatArrayOf(l, r)) for (hy in floatArrayOf(t, bottom)) {
            canvas.drawCircle(hx, hy, HANDLE_DRAW_PX, chrome.handle)
        }
    }
    // Edge-midpoint handles: one axis each (X on left/right, Y on top/bottom).
    val midX = (l + r) / 2f
    val midY = (t + bottom) / 2f
    if (shaft == null && !flatY && showBoxHandles) {
        canvas.drawCircle(midX, t, HANDLE_DRAW_PX, chrome.handle)
        canvas.drawCircle(midX, bottom, HANDLE_DRAW_PX, chrome.handle)
    }
    if (shaft == null && !flatX && showBoxHandles) {
        canvas.drawCircle(l, midY, HANDLE_DRAW_PX, chrome.handle)
        canvas.drawCircle(r, midY, HANDLE_DRAW_PX, chrome.handle)
    }
    // Rotate knob: on a shaft it hangs from its ink; on a single figure/stroke it anchors to the figure's rightmost outline.
    if (gestures.isAllStrokes(sel)) {
        val gapPt = (DrawingSurfaceDefaults.ROTATE_ARM_PX / box.scale).toDouble()
        val turn = shaft?.let { shaftTurn(it, gapPt) }
        if (turn != null) {
            val pivotX = box.toViewX(turn.pivotX, viewport.scrollX)
            val pivotY = box.toViewY(turn.pivotY, viewport.scrollY)
            val knobX = box.toViewX(turn.ringX, viewport.scrollX)
            val knobY = box.toViewY(turn.ringY, viewport.scrollY)
            canvas.drawLine(pivotX, pivotY, knobX, knobY, chrome.handleArm)
            canvas.drawCircle(knobX, knobY, HANDLE_DRAW_PX, chrome.handle)
        } else {
            val page = pageAt(sel.pageIndex)
            val singleEl: Element? = if (sel.refs.size == 1 && page != null) {
                val ref = sel.refs.first()
                page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
            } else null
            val b = page?.let { SelectionTester.boundsOf(it, sel.refs) }
            if (singleEl != null && b != null) {
                val padPt = if (singleEl is ParametricShapeElement) 0.0 else (DrawingSurfaceDefaults.SELECT_PAD_PX / box.scale).toDouble()
                val stem = ElementHitTester.rotateStemRight(singleEl, b, gapPt + padPt)
                val fx = box.toViewX(stem.footX, viewport.scrollX)
                val fy = box.toViewY(stem.footY, viewport.scrollY)
                val kx = box.toViewX(stem.knobX, viewport.scrollX)
                val ky = box.toViewY(stem.knobY, viewport.scrollY)
                canvas.drawLine(fx, fy, kx, ky, chrome.handleArm)
                canvas.drawCircle(kx, ky, HANDLE_DRAW_PX, chrome.handle)
            } else {
                val knobX = r + DrawingSurfaceDefaults.ROTATE_ARM_PX
                canvas.drawLine(r, midY, knobX, midY, chrome.handleArm)
                canvas.drawCircle(knobX, midY, HANDLE_DRAW_PX, chrome.handle)
            }
        }
    }
    // A single selected shape: a dot on each corner unless aspect-lock is active.
    if (!aspectLocked) drawShapeVertexHandles(canvas, box)
    drawSplineEditHandles(canvas, box)
}
