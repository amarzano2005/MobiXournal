package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
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
    ): Boolean = hitDistance(element, x, y, padPt) != null

    /**
     * Distance in page points from ([x], [y]) to [element]'s ink when it hits within [padPt] pt, or `null`
     * when it misses. Closed interiors and non-stroke boxes report `0.0`; open strokes report their true
     * ink-edge distance so [SelectionTester.pickTopmost] can prefer a direct hit over a nearby stroke.
     */
    fun hitDistance(
        element: Element,
        x: Double,
        y: Double,
        padPt: Double = ElementBounds.TAP_PAD,
    ): Double? {
        if (!ElementBounds.isHitTestable(element)) return null
        if (!ElementBounds.of(element).expand(padPt).contains(x, y)) return null
        val hit = when (element) {
            is ParametricShapeElement -> {
                if (ParametricShapes.isConic(element)) {
                    ParametricShapes.conicContains(element, x, y, padPt + element.strokeWidth / 2.0)
                } else {
                    val poly = element.vertices.map { Vec2(it.x, it.y) }
                    containsInPolygon(poly, x, y, padPt + element.strokeWidth / 2.0)
                }
            }
            is Stroke -> {
                if (isClosedFigureStroke(element)) {
                    val poly = element.points.map { Vec2(it.x, it.y) }
                    if (containsInPolygon(poly, x, y, padPt)) return 0.0
                    val d = StrokeHitTester.distance(element, x, y)
                    return if (d <= padPt) d else null
                } else {
                    val d = StrokeHitTester.distance(element, x, y)
                    return if (d <= padPt) d else null
                }
            }
            is SplineElement -> {
                val pts = SplineInk.samplePoints(element.nodes, element.closed) ?: return null
                val d = StrokeHitTester.distance(
                    InkPrimitives.stroke(pts, element.color, element.width),
                    x,
                    y,
                )
                return if (d <= padPt + element.width / 2.0) d else null
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
        return if (hit) 0.0 else null
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
                if (kind == ShapeKind.ELLIPSE && ParametricShapes.isConic(element)) true
                else {
                    val expected = ParametricShapes.shapeNameForTool(kind)
                    expected != null && (element.shape == expected || element.extraAttrs[FigurePins.SHAPE_ATTR] == kind.name)
                }
            }
            else -> false
        }
    }

    /** Stem anchor ([footX], [footY]) on the figure's outline and rotate handle ([knobX], [knobY]) [gapPt] away. */
    data class RotateStem(val footX: Double, val footY: Double, val knobX: Double, val knobY: Double)

    /** Right-side rotate stem (Android): starts on [element]'s rightmost outline near mid-height. */
    fun rotateStemRight(element: Element?, bounds: Bounds, gapPt: Double): RotateStem {
        val midY = (bounds.top + bounds.bottom) / 2.0
        val (fx, fy) = nearestOutlinePoint(element, bounds.right, midY)
        return RotateStem(fx, fy, fx + gapPt, fy)
    }

    /** Top-side rotate stem (Desktop): starts on [element]'s top outline near mid-width. */
    fun rotateStemTop(element: Element?, bounds: Bounds, gapPt: Double): RotateStem {
        val midX = (bounds.left + bounds.right) / 2.0
        val (fx, fy) = nearestOutlinePoint(element, midX, bounds.top)
        return RotateStem(fx, fy, fx, fy - gapPt)
    }

    private fun nearestOutlinePoint(element: Element?, targetX: Double, targetY: Double): Pair<Double, Double> {
        return when (element) {
            is ParametricShapeElement -> {
                if (ParametricShapes.isConic(element)) {
                    val verts = ParametricShapes.conicOutlinePoints(element)
                    if (verts.size < 2) return targetX to targetY
                    var bestX = verts[0].x
                    var bestY = verts[0].y
                    var bestDist = Double.MAX_VALUE
                    for (i in 0 until verts.size - 1) {
                        val (px, py) = closestPointOnSegment(targetX, targetY, verts[i].x, verts[i].y, verts[i + 1].x, verts[i + 1].y)
                        val d = hypot(targetX - px, targetY - py)
                        if (d < bestDist) { bestDist = d; bestX = px; bestY = py }
                    }
                    bestX to bestY
                } else {
                    val verts = element.vertices
                    if (verts.isEmpty()) return targetX to targetY
                    if (verts.size == 1) return verts[0].x to verts[0].y
                    var bestX = verts[0].x
                    var bestY = verts[0].y
                    var bestDist = Double.MAX_VALUE
                    var j = verts.size - 1
                    for (i in verts.indices) {
                        val (px, py) = closestPointOnSegment(targetX, targetY, verts[j].x, verts[j].y, verts[i].x, verts[i].y)
                        val d = hypot(targetX - px, targetY - py)
                        if (d < bestDist) { bestDist = d; bestX = px; bestY = py }
                        j = i
                    }
                    bestX to bestY
                }
            }
            is SplineElement -> {
                val pts = SplineInk.samplePoints(element.nodes, element.closed) ?: return targetX to targetY
                var bestX = pts[0].x
                var bestY = pts[0].y
                var bestDist = Double.MAX_VALUE
                for (i in 0 until pts.size - 1) {
                    val (px, py) = closestPointOnSegment(targetX, targetY, pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y)
                    val d = hypot(targetX - px, targetY - py)
                    if (d < bestDist) { bestDist = d; bestX = px; bestY = py }
                }
                bestX to bestY
            }
            is Stroke -> {
                val pts = element.points
                if (pts.isEmpty()) return targetX to targetY
                if (pts.size == 1) return pts[0].x to pts[0].y
                var bestX = pts[0].x
                var bestY = pts[0].y
                var bestDist = Double.MAX_VALUE
                for (i in 0 until pts.size - 1) {
                    val (px, py) = closestPointOnSegment(targetX, targetY, pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y)
                    val d = hypot(targetX - px, targetY - py)
                    if (d < bestDist) { bestDist = d; bestX = px; bestY = py }
                }
                bestX to bestY
            }
            else -> targetX to targetY
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
        val (px, py) = closestPointOnSegment(x, y, a.x, a.y, b.x, b.y)
        return hypot(x - px, y - py)
    }

    private fun closestPointOnSegment(
        x: Double,
        y: Double,
        ax: Double,
        ay: Double,
        bx: Double,
        by: Double,
    ): Pair<Double, Double> {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((x - ax) * dx + (y - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return (ax + t * dx) to (ay + t * dy)
    }
}
