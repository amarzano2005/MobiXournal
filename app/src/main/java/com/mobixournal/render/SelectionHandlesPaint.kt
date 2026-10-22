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
import com.mobixournal.render.CanvasChrome.Companion.HANDLE_DRAW_PX

/**
 * Draw every handle of [sel] inside its ink box, given the box's **padded** view-px edges
 * ([l], [t], [r], [bottom]) that [DrawingSurfaceView.drawSelectionBox] already computed.
 *
 * The four corner dots resize proportionally; the four edge-midpoint dots stretch a single axis
 * (out-of-proportion resize), which is why they are drawn at all. The right-edge one sits under the
 * rotate arm's foot: the arm starts there and the knob it leads to is what the rotate hit-test
 * answers to, so the two never fight over the same touch.
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
    // Corner resize handles: proportional (both axes together).
    for (hx in floatArrayOf(l, r)) for (hy in floatArrayOf(t, bottom)) {
        canvas.drawCircle(hx, hy, HANDLE_DRAW_PX, chrome.handle)
    }
    // Edge-midpoint handles: one axis each, the out-of-proportion resize.
    val midX = (l + r) / 2f
    val midY = (t + bottom) / 2f
    canvas.drawCircle(midX, t, HANDLE_DRAW_PX, chrome.handle)
    canvas.drawCircle(midX, bottom, HANDLE_DRAW_PX, chrome.handle)
    canvas.drawCircle(l, midY, HANDLE_DRAW_PX, chrome.handle)
    canvas.drawCircle(r, midY, HANDLE_DRAW_PX, chrome.handle)
    // Rotate knob poking out midway from the right edge (strokes only).
    if (gestures.isAllStrokes(sel)) {
        val knobX = r + DrawingSurfaceDefaults.ROTATE_ARM_PX
        canvas.drawLine(r, midY, knobX, midY, chrome.handleArm)
        canvas.drawCircle(knobX, midY, HANDLE_DRAW_PX, chrome.handle)
    }
    // A single selected shape: a dot on each corner, the handles the vertex drag grabs.
    drawShapeVertexHandles(canvas, box)
}
