package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.StrokePoint
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * A parametric shape compiled into ink (`docs/mobix-format.md`, the synthesis export) — and, through `LivingInk`, the drawing on screen.
 *
 * The figure's geometry **is** its vertices — that is what the element stores, and what its editor's
 * handles drag — so the outline is a closed polyline through them and nobody has to re-derive the
 * kind's angles: the vertices the user is looking at are the vertices that get written.
 *
 * Two things about the mapping are worth stating, because `.xopp` cannot say everything the element
 * does:
 *
 *  - **A fill whose colour differs from the outline's is its own stroke.** A `.xopp` stroke carries one
 *    colour and one interior alpha, so a blue figure filled with a light yellow is a filled stroke
 *    underneath plus the outline on top. When the two colours agree, one stroke does both.
 *  - **The angle and side-length labels are drawn, positioned by the app's own text estimate**
 *    ([ElementBounds]); the on-screen version measures with the font engine, so a label can sit a
 *    fraction of a millimetre away from where the screen put it. Its *text* is the same
 *    ([DimensionLabel.measurementText]).
 */
internal object ShapeInk {

    /** How far a label sits from the edge it names, at the figure's own scale. */
    private const val LABEL_OFFSET_PT = 6.0

    /** A figure needs two points to be a figure; one is a dot and none is nothing. */
    private const val MIN_VERTICES = 2

    /** [shape] as ink, or null when there is nothing to draw. */
    fun ink(shape: ParametricShapeElement): List<Element>? {
        if (shape.vertices.size < MIN_VERTICES) return null
        val outline = outlineOf(shape)
        val out = mutableListOf<Element>()
        val fill = shape.fillColor
        if (fill != null && !sameInk(fill, shape.strokeColor)) {
            out += InkPrimitives.stroke(outline, fill, shape.strokeWidth, fill = fill.alphaChannel())
        }
        out += InkPrimitives.stroke(
            points = outline,
            color = shape.strokeColor,
            widthPt = shape.strokeWidth,
            fill = fill?.takeIf { sameInk(it, shape.strokeColor) }?.alphaChannel(),
        )
        out += labels(shape)
        return out
    }

    /** The figure closed on itself: the vertices, and the first one again. */
    private fun outlineOf(shape: ParametricShapeElement): List<StrokePoint> {
        val out = ArrayList<StrokePoint>(shape.vertices.size + 1)
        shape.vertices.forEach { out += InkPrimitives.pt(it.x, it.y, shape.strokeWidth) }
        val first = shape.vertices.first()
        out += InkPrimitives.pt(first.x, first.y, shape.strokeWidth)
        return out
    }

    private fun labels(shape: ParametricShapeElement): List<Element> {
        val out = mutableListOf<Element>()
        val count = shape.vertices.size
        if (shape.showSideLengths) {
            for (index in 0 until count) {
                val from = shape.vertices[index]
                val to = shape.vertices[(index + 1) % count]
                val text = measurementText(hypot(to.x - from.x, to.y - from.y)) ?: continue
                out += InkPrimitives.text(
                    content = text,
                    x = (from.x + to.x) / 2.0 + LABEL_OFFSET_PT,
                    y = (from.y + to.y) / 2.0 - LABEL_OFFSET_PT,
                    color = shape.strokeColor,
                )
            }
        }
        if (shape.showAngles) {
            for (index in 0 until count) {
                val previous = shape.vertices[(index - 1 + count) % count]
                val vertex = shape.vertices[index]
                val next = shape.vertices[(index + 1) % count]
                out += InkPrimitives.text(
                    content = "${interiorAngleDeg(previous, vertex, next).roundToInt()}°",
                    x = vertex.x + LABEL_OFFSET_PT,
                    y = vertex.y + LABEL_OFFSET_PT,
                    color = shape.strokeColor,
                )
            }
        }
        return out
    }

    /** The angle at [vertex], in degrees, between the segments to [from] and [to]. */
    private fun interiorAngleDeg(
        from: com.mobixournal.format.model.ShapeVertex,
        vertex: com.mobixournal.format.model.ShapeVertex,
        to: com.mobixournal.format.model.ShapeVertex,
    ): Double {
        val a = atan2(from.y - vertex.y, from.x - vertex.x)
        val b = atan2(to.y - vertex.y, to.x - vertex.x)
        var degrees = Math.toDegrees(kotlin.math.abs(a - b))
        if (degrees > 180.0) degrees = 360.0 - degrees
        return degrees
    }

    /** Whether two ARGB colours are the same *ink*, ignoring alpha — the fill is the alpha. */
    private fun sameInk(left: Int, right: Int): Boolean = (left and 0x00FFFFFF) == (right and 0x00FFFFFF)

    private fun Int.alphaChannel(): Int = (this ushr 24) and 0xFF
}
