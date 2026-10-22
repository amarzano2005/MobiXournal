package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint

/**
 * A figure the shape recogniser found in a freehand stroke — the *kind* it was matched against,
 * together with the clean geometry the match emitted.
 *
 * The kind is what turns a recognition into something worth keeping alive: a triangle or a rectangle
 * is a closed polygon whose corners the editor can drag ([ParametricShapes] commits those as a
 * `ParametricShapeElement`), while a line, an arrow or an arc stays an ordinary stroke. Keeping the
 * kind next to the points is also what stops a caller from having to re-guess what the recogniser
 * decided by inspecting the point list.
 *
 * @param kind Which primitive the stroke fitted.
 * @param points The emitted geometry, in draw order — for a polygon, the closing point repeats the
 *   first one, exactly as [ShapeRecognizer] has always returned it.
 */
data class RecognisedFigure(
    val kind: FigureKind,
    val points: List<StrokePoint>,
) {

    /**
     * True for the closed polygons whose corners are authored geometry: a triangle or a rectangle.
     *
     * Circles and ellipses are deliberately **not** here. Their vertices would be the sampling
     * points of an approximation, not corners a handle could drag, and the model has no centre/radius
     * to edit instead — so they keep the recogniser's stroke, which is what the sheet shows anyway.
     */
    val isClosedPolygon: Boolean
        get() = kind == FigureKind.TRIANGLE || kind == FigureKind.RECTANGLE
}

/** The primitives [ShapeRecognizer] can name — one per branch it tries, in its own order. */
enum class FigureKind {
    LINE,
    ARROW,
    TRIANGLE,
    RECTANGLE,
    CIRCLE,
    ELLIPSE,
    ARC,
}
