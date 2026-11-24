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
import com.mobixournal.format.model.ParametricShapeElement
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

internal fun DrawingSurfaceView.drawShapePolygonOutline(canvas: Canvas, box: PageBox, shape: ParametricShapeElement): Boolean {
    if (shape.vertices.size < 3) return false
    chrome.lassoPath.reset()
    chrome.lassoPath.moveTo(box.toViewX(shape.vertices[0].x, scrollX), box.toViewY(shape.vertices[0].y, scrollY))
    for (i in 1 until shape.vertices.size) {
        chrome.lassoPath.lineTo(box.toViewX(shape.vertices[i].x, scrollX), box.toViewY(shape.vertices[i].y, scrollY))
    }
    chrome.lassoPath.close()
    canvas.drawPath(chrome.lassoPath, chrome.selectionFill)
    canvas.drawPath(chrome.lassoPath, chrome.selectionStroke)
    return true
}

/**
 * Draw a haloed dot on each corner of the selected figure — distinct from box edge handles and
 * drawn exactly where [SelectionGestureController] hit-tests them.
 */
internal fun DrawingSurfaceView.drawShapeVertexHandles(canvas: Canvas, box: PageBox) {
    val shape = singleSelectedShape() ?: return
    for (v in shape.vertices) {
        val vx = box.toViewX(v.x, scrollX)
        val vy = box.toViewY(v.y, scrollY)
        canvas.drawCircle(vx, vy, HANDLE_DRAW_PX + 2f, chrome.pinHalo)
        canvas.drawCircle(vx, vy, HANDLE_DRAW_PX, chrome.handle)
    }
}

/**
 * Commit [figure] as a living shape and return **true** when it was kept as geometry; false means the
 * caller should carry on and commit the stroke as ink.
 *
 * A highlighter's ink carries its own blending and stays a stroke, while a dashed or dotted outline
 * now travels cleanly on `ParametricShapeElement.lineStyle`.
 */
internal fun DrawingSurfaceView.commitRecognisedFigure(
    figure: RecognisedFigure,
    raw: List<StrokePoint>,
): Boolean {
    if (shapeDraft.active || tool != Tool.PEN) return false
    val shape = ParametricShapes.elementFor(
        figure = figure,
        strokeColor = strokeColor(),
        strokeWidth = raw.map { it.width }.average(),
        lineStyle = currentLineStyle,
    ) ?: return false
    commitLivingShape(shape, select = false)
    return true
}

/**
 * Commit an explicit closed-polygon tool drag (`RECTANGLE`, `SQUARE`, `TRIANGLE`, `TRAPEZOID`, `POLYGON`)
 * as a living [ParametricShapeElement] and leave it selected; returns false for strokes such as lines,
 * arrows, axes, and ellipses.
 */
internal fun DrawingSurfaceView.commitShapeToolFigure(
    kind: ShapeKind?,
    points: List<StrokePoint>,
): Boolean {
    if (kind == null || tool != Tool.PEN) return false
    val shape = ParametricShapes.elementForShapeTool(
        kind = kind,
        points = points,
        strokeColor = strokeColor(),
        strokeWidth = shapeDraft.widthPt,
        lineStyle = currentLineStyle,
    ) ?: return false
    commitLivingShape(shape, select = true)
    return true
}

private fun DrawingSurfaceView.commitLivingShape(shape: ParametricShapeElement, select: Boolean) {
    ElementEdits.addElement(doc, ink.currentPage, shape) { resolvedActiveLayer(it) }?.let { edited ->
        doc = edited
        if (select) selectInsertedElement(ink.currentPage)
        relayout()
    }
}
