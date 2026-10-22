/**
 * The **living figure** commit: a closed polygon the pen drew is kept as a `ParametricShapeElement`
 * instead of the uniform-width stroke it would flatten into, so its corners stay handles the editor
 * can drag (`docs/drawing-tools.md` → *Living figures*).
 *
 * Kept out of [DrawingSurfaceStrokes]' commit path on purpose: that path is about ink, and this is the
 * one place where a finished stroke decides *not* to become ink. Extensions on [DrawingSurfaceView],
 * so the decision reads the live tool, style and page like the rest of the gesture code.
 */
package com.mobixournal.render

import android.graphics.Canvas
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.CanvasChrome.Companion.HANDLE_DRAW_PX

/**
 * The figure the recogniser found for a freehand stroke, or null when it found nothing (or the
 * switch is off).
 *
 * The recogniser gets the **raw** samples, not the thinned ones: another implementation of the same
 * algorithm feeds it the whole recorded stroke, and thinning first would hide exactly the dense
 * detail its piecewise line fit reads to find corners. The highlighter is included because desktop
 * Xournal++ gives it the same recogniser — whether the result is *kept* as a figure is
 * [commitRecognisedFigure]'s decision, not this one's.
 */
internal fun DrawingSurfaceView.recogniseStroke(raw: List<StrokePoint>): RecognisedFigure? {
    val recognisable = tool == Tool.PEN || tool == Tool.HIGHLIGHTER
    if (!recognizeShapes || !recognisable || raw.isEmpty()) return null
    // Keep the thickness the stroke was actually drawn at, not the un-scaled base width, so
    // snapping to a primitive doesn't fatten the line under the user's hand.
    return ShapeRecognizer.classify(raw, raw.map { it.width }.average())
}

/**
 * Draw a dot on each corner of the selected figure — the handles [SelectionGestureController] drags,
 * drawn exactly where its hit test looks for them.
 */
internal fun DrawingSurfaceView.drawShapeVertexHandles(canvas: Canvas, box: PageBox) {
    val shape = singleSelectedShape() ?: return
    for (v in shape.vertices) {
        canvas.drawCircle(
            box.toViewX(v.x, scrollX), box.toViewY(v.y, scrollY),
            HANDLE_DRAW_PX, chrome.handle,
        )
    }
}

/**
 * Commit [figure] as a living shape and return **true** when it was kept as geometry; false means the
 * caller should carry on and commit the stroke as ink.
 *
 * Only the pen and a plain line qualify:
 *  - a highlighter's ink is its own thing (alpha, blending) and a `parametricShape` has no such field,
 *  - a dashed line style is not carried by the element either, so keeping the figure would silently
 *    straighten a dashed outline into a solid one,
 *  - a board's ink lives in chunks rather than on a page, which the element cannot address.
 *
 * The stroke is adopted through the gesture's own bookkeeping (as a stroke is), so one drawn figure is
 * one undo step.
 */
internal fun DrawingSurfaceView.commitRecognisedFigure(
    figure: RecognisedFigure,
    raw: List<StrokePoint>,
): Boolean {
    if (shaping || tool != Tool.PEN || currentLineStyle != LineStyle.PLAIN || board != null) return false
    val shape = ParametricShapes.elementFor(
        figure = figure,
        strokeColor = strokeColor(),
        strokeWidth = raw.map { it.width }.average(),
    ) ?: return false
    ElementEdits.addElement(doc, currentPage, shape) { resolvedActiveLayer(it) }?.let { edited ->
        doc = edited
        relayout()
    }
    return true
}
