package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs
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
 *    ([InkTokens.FIGURE_LABEL_CHAR_W]); the on-screen version measures with the font engine, so a
 *    label can sit a fraction of a millimetre away from where the screen put it. Its *text* is the
 *    same ([DimensionLabel.measurementText]).
 *
 * Labels are placed **around** the figure rather than on it: a side's length sits outside its own
 * edge, on the side away from the figure's centre, and an angle sits inside its corner, along the
 * corner's bisector. Both used to be dropped a flat 6 pt down-right of the midpoint or the vertex,
 * which put every label across the outline it described — on a narrow triangle that meant three
 * numbers stacked on the ink.
 */
object ShapeInk {

    /** Air between the outline and a label's box, in pt. */
    private const val LABEL_GAP_PT = 3.0

    /** How far into a corner its angle label may reach, as a share of the shorter adjacent side. */
    private const val MAX_ANGLE_INSET = 0.4

    /** A figure needs two points to be a figure; one is a dot and none is nothing. */
    private const val MIN_VERTICES = 2

    /** Two corners nearer than this (pt) are the same corner — the seam of a degenerate edge. */
    private const val SAME_POINT_PT = 1e-6

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
        if (count < MIN_VERTICES) return out
        val centerX = shape.vertices.sumOf { it.x } / count
        val centerY = shape.vertices.sumOf { it.y } / count
        if (shape.showSideLengths) {
            for (index in 0 until count) {
                val from = shape.vertices[index]
                val to = shape.vertices[(index + 1) % count]
                // The same sum the editor's vertex handles report, so the badge and the drag agree.
                val text = measurementText(ParametricShapes.sideLengthPt(shape, index)) ?: continue
                val (width, height) = labelSize(text)
                val midX = (from.x + to.x) / 2.0
                val midY = (from.y + to.y) / 2.0
                val (nx, ny) = outwardNormal(from.x, from.y, to.x, to.y, midX, midY, centerX, centerY)
                // Clear of the outline for the **whole box**, not just for its centre: the box is
                // axis-aligned while the edge is not, so a wide label on a steep side reaches back
                // across the line unless the offset accounts for its extent along the normal.
                val offset = LABEL_GAP_PT + abs(nx) * width / 2.0 + abs(ny) * height / 2.0
                out += InkPrimitives.text(
                    content = text,
                    x = midX + nx * offset - width / 2.0,
                    y = midY + ny * offset - height / 2.0,
                    color = shape.strokeColor,
                )
            }
        }
        if (shape.showAngles) {
            for (index in 0 until count) {
                val vertex = shape.vertices[index]
                val previous = shape.vertices[(index - 1 + count) % count]
                val next = shape.vertices[(index + 1) % count]
                val text = "${ParametricShapes.interiorAngleDeg(shape, index).roundToInt()}°"
                val (width, height) = labelSize(text)
                val (ux, uy) = inwardBisector(vertex, previous, next, centerX, centerY)
                val inset = angleInset(vertex, previous, next, width, height, ux, uy)
                out += InkPrimitives.text(
                    content = text,
                    x = vertex.x + ux * inset - width / 2.0,
                    y = vertex.y + uy * inset - height / 2.0,
                    color = shape.strokeColor,
                )
            }
        }
        return out
    }

    /** The label box [content] occupies, in pt: its width and its height at the figure-label size. */
    private fun labelSize(content: String): Pair<Double, Double> {
        val size = InkTokens.FIGURE_LABEL_SIZE_PT
        return (content.length * size * InkTokens.FIGURE_LABEL_CHAR_W) to
            (size * InkTokens.FIGURE_LABEL_LINE_H)
    }

    /**
     * The unit normal of the edge ([fromX],[fromY])–([toX],[toY]) that points **away** from the
     * figure's centre ([centerX],[centerY]) — the side a length label belongs on.
     */
    private fun outwardNormal(
        fromX: Double,
        fromY: Double,
        toX: Double,
        toY: Double,
        midX: Double,
        midY: Double,
        centerX: Double,
        centerY: Double,
    ): Pair<Double, Double> {
        val dx = toX - fromX
        val dy = toY - fromY
        val length = hypot(dx, dy)
        if (length < SAME_POINT_PT) return 0.0 to 1.0
        var nx = -dy / length
        var ny = dx / length
        if (nx * (midX - centerX) + ny * (midY - centerY) < 0.0) {
            nx = -nx
            ny = -ny
        }
        return nx to ny
    }

    /**
     * The unit direction from [vertex] into the figure at that corner — the bisector of the two
     * sides, which for a convex polygon points at the inside. Falls back to the direction of the
     * centre when the bisector is degenerate (two sides on one line).
     */
    private fun inwardBisector(
        vertex: ShapeVertex,
        previous: ShapeVertex,
        next: ShapeVertex,
        centerX: Double,
        centerY: Double,
    ): Pair<Double, Double> {
        val aLength = hypot(previous.x - vertex.x, previous.y - vertex.y)
        val bLength = hypot(next.x - vertex.x, next.y - vertex.y)
        var ux = 0.0
        var uy = 0.0
        if (aLength > SAME_POINT_PT) {
            ux += (previous.x - vertex.x) / aLength
            uy += (previous.y - vertex.y) / aLength
        }
        if (bLength > SAME_POINT_PT) {
            ux += (next.x - vertex.x) / bLength
            uy += (next.y - vertex.y) / bLength
        }
        val length = hypot(ux, uy)
        if (length < SAME_POINT_PT) {
            val cx = centerX - vertex.x
            val cy = centerY - vertex.y
            val cl = hypot(cx, cy)
            return if (cl < SAME_POINT_PT) 0.0 to 1.0 else cx / cl to cy / cl
        }
        return ux / length to uy / length
    }

    /**
     * How far along the bisector an angle label of [labelHeight] pt can sit and stay inside the
     * corner: far enough that its box clears both sides, never further than [MAX_ANGLE_INSET] of the
     * shorter adjacent side (a very sharp corner has no room, and the label would otherwise be
     * pushed into the middle of the figure, over everything else).
     */
    private fun angleInset(
        vertex: ShapeVertex,
        previous: ShapeVertex,
        next: ShapeVertex,
        labelWidth: Double,
        labelHeight: Double,
        ux: Double,
        uy: Double,
    ): Double {
        val half = Math.toRadians(ParametricShapes.interiorAngleDeg(previous, vertex, next)) / 2.0
        val sine = kotlin.math.sin(half).coerceAtLeast(0.15)
        // How far the box's own far corner sits along the bisector — the box has to clear both
        // sides with all four corners, not only with its centre.
        val extent = abs(ux) * labelWidth / 2.0 + abs(uy) * labelHeight / 2.0
        val needed = (extent + LABEL_GAP_PT) / sine
        val room = MAX_ANGLE_INSET * minOf(
            hypot(previous.x - vertex.x, previous.y - vertex.y),
            hypot(next.x - vertex.x, next.y - vertex.y),
        )
        return minOf(needed, room)
    }

    /** Whether two ARGB colours are the same *ink*, ignoring alpha — the fill is the alpha. */
    private fun sameInk(left: Int, right: Int): Boolean = (left and 0x00FFFFFF) == (right and 0x00FFFFFF)

    private fun Int.alphaChannel(): Int = (this ushr 24) and 0xFF
}
