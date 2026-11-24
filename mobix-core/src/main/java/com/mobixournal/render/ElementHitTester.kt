package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import kotlin.math.hypot

/**
 * Single geometric hit-test contract for every [Element] family on a page.
 *
 * Closed figures (parametric polygons, closed figure strokes such as ellipses/circles, tables,
 * plots, images, formulas, text, and circuit symbols) hit anywhere from their boundary (`padPt`)
 * through their true interior area. Open strokes (lines, arrows, axes, freehand handwriting) hit
 * only along their stroke segments.
 */
object ElementHitTester {

    /** Closing endpoint tolerance (pt) used to distinguish closed figure strokes from freehand ink. */
    private const val CLOSED_ENDPOINT_PT = 1e-3

    /**
     * True when ([x], [y]) pt hits [element] within [padPt] pt of its boundary or inside its
     * closed interior area.
     */
    fun hits(
        element: Element,
        x: Double,
        y: Double,
        padPt: Double = ElementBounds.TAP_PAD,
    ): Boolean {
        if (!ElementBounds.isHitTestable(element)) return false
        if (!ElementBounds.of(element).expand(padPt).contains(x, y)) return false
        return when (element) {
            is ParametricShapeElement -> {
                val poly = element.vertices.map { Vec2(it.x, it.y) }
                containsInPolygon(poly, x, y, padPt + element.strokeWidth / 2.0)
            }
            is Stroke -> {
                if (isClosedFigureStroke(element)) {
                    val poly = element.points.map { Vec2(it.x, it.y) }
                    containsInPolygon(poly, x, y, padPt) || StrokeHitTester.hits(element, x, y, padPt)
                } else {
                    StrokeHitTester.hits(element, x, y, padPt)
                }
            }
            is TableElement,
            is PlotElement,
            is ImageElement,
            is TexImageElement,
            is MathBlockElement,
            is TextElement,
            is CircuitElement -> true
            is RawElement -> false
        }
    }

    /**
     * True when [element] belongs to the placement/figure tool [kind], across both legacy/stroke
     * figures and living [ParametricShapeElement]s.
     */
    fun matchesFigureTool(element: Element, kind: ShapeKind): Boolean {
        if (ShapeBuilder.isMultiStroke(kind)) return false
        return when (element) {
            is Stroke -> FigurePins.isFigureOf(element, kind)
            is ParametricShapeElement -> {
                val expected = ParametricShapes.shapeNameForTool(kind)
                expected != null && element.shape == expected
            }
            else -> false
        }
    }

    /**
     * True when [stroke] is a closed geometric contour (such as an ellipse/circle or closed polygon)
     * rather than an open path or freehand writing.
     */
    fun isClosedFigureStroke(stroke: Stroke): Boolean {
        val pts = stroke.points
        if (pts.size < 4) return false
        val first = pts.first()
        val last = pts.last()
        if (hypot(last.x - first.x, last.y - first.y) > CLOSED_ENDPOINT_PT) return false
        return distinctCornerCount(pts) >= 3
    }

    private fun distinctCornerCount(points: List<StrokePoint>): Int {
        var count = 0
        val seen = ArrayList<StrokePoint>(4)
        for (p in points) {
            if (seen.none { hypot(it.x - p.x, it.y - p.y) <= CLOSED_ENDPOINT_PT }) {
                seen += p
                count++
                if (count >= 3) return count
            }
        }
        return count
    }

    /** True when ([x], [y]) is strictly inside [poly] or within [pad] pt of any edge. */
    fun containsInPolygon(poly: List<Vec2>, x: Double, y: Double, pad: Double): Boolean {
        if (poly.size < 3) return nearEdge(poly, x, y, pad)
        return strictlyInside(poly, x, y) || nearEdge(poly, x, y, pad)
    }

    /** Even-odd ray-cast point-in-polygon test (pt space). */
    fun strictlyInside(poly: List<Vec2>, x: Double, y: Double): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[j]
            if ((a.y > y) != (b.y > y)) {
                val xCross = a.x + (y - a.y) / (b.y - a.y) * (b.x - a.x)
                if (x < xCross) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** True when ([x], [y]) lies within [pad] pt of any edge of [poly]. */
    fun nearEdge(poly: List<Vec2>, x: Double, y: Double, pad: Double): Boolean {
        if (poly.isEmpty()) return false
        if (poly.size == 1) return hypot(x - poly[0].x, y - poly[0].y) <= pad
        var j = poly.size - 1
        for (i in poly.indices) {
            if (distToSegment(x, y, poly[j], poly[i]) <= pad) return true
            j = i
        }
        return false
    }

    private fun distToSegment(x: Double, y: Double, a: Vec2, b: Vec2): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((x - a.x) * dx + (y - a.y) * dy) / len2).coerceIn(0.0, 1.0)
        val px = a.x + t * dx
        val py = a.y + t * dy
        return hypot(x - px, y - py)
    }
}
