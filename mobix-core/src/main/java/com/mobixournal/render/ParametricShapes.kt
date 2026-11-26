package com.mobixournal.render

import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The **vertex maths** of a living parametric shape `ParametricShapeElement`: what a recognised
 * polygon becomes, which corner a handle grabs, where that corner lands when the pen drags it, and
 * what the two readout badges (side lengths, interior angles) say.
 *
 * It is pure and Android-free, and it is the single home of these sums: the canvas draws through
 * `ShapeInk`, whose labels call the very functions here, so a badge can never disagree with the
 * handles the user is dragging. The kinds a recognised figure becomes are the two closed polygons —
 * a triangle and a rectangle — because those are the figures whose *corners* are the geometry a
 * handle can move (`docs/drawing-tools.md`). A circle's "vertices" would be sampling points of an
 * approximation and the model has no centre and radius to drag instead, so circles and ellipses keep
 * the recogniser's stroke.
 */
object ParametricShapes {

    /** The shape-library key a recognised triangle is stored under (`docs/mobix-elements.md`). */
    const val TRIANGLE = "TRIANGLE"

    /** The shape-library key a recognised rectangle is stored under. */
    const val RECTANGLE = "RECTANGLE"

    /** The shape-library key a square-tool polygon is stored under. */
    const val SQUARE = "SQUARE"

    /** The shape-library key a trapezoid-tool polygon is stored under. */
    const val TRAPEZOID = "TRAPEZOID"

    /** The shape-library key a generic or regular polygon is stored under. */
    const val POLYGON = "POLYGON"

    /** The shape-library key a circular conic is stored under (`vertices = [N, E, S, W]`). */
    const val CIRCLE = "CIRCLE"

    /** The shape-library key an elliptical conic is stored under (`vertices = [N, E, S, W]`). */
    const val ELLIPSE = "ELLIPSE"

    /** Local axes of a conic [ParametricShapeElement] centred at ([cx], [cy]). */
    data class ConicFrame(
        val cx: Double,
        val cy: Double,
        val ux: Double,
        val uy: Double,
        val vx: Double,
        val vy: Double,
        val rx: Double,
        val ry: Double,
    )

    /** Minimum diameter (pt) enforced when dragging a conic cardinal handle or setting radii. */
    private const val MIN_CONIC_DIAMETER_PT = 8.0
    private const val CONIC_OUTLINE_STEPS = 64

    /** True when [shape] stores a circle or ellipse encoded by its four cardinal points `[N, E, S, W]`. */
    fun isConic(shape: ParametricShapeElement): Boolean =
        (shape.shape == CIRCLE || shape.shape == ELLIPSE) && shape.vertices.size == 4

    /** Maximum deviation (degrees) from 90° for a quadrilateral to still count as rectangular. */
    private const val RIGHT_ANGLE_TOL_DEG = 0.5

    /** Maximum side-length spread (pt) for a rectangular 4-gon to still count as square. */
    private const val EQUAL_SIDE_TOL_PT = 0.5

    /**
     * How far (degrees) a dragged side may sit from a guided direction and still be pulled onto it.
     *
     * Tight enough that a deliberate free angle still exists — a shape whose every side snapped
     * would be unusable for sketching an odd triangle — and loose enough that a finger aiming at
     * square lands square.
     */
    const val VERTEX_SNAP_TOLERANCE_DEG = 7.0

    /**
     * The directions a dragged side is pulled onto, in degrees from the page's horizontal axis: the
     * ruling's own two axes (0° and 90°) and the two diagonals a squared page invites (30° and 45°),
     * with their mirror images — a direction and its 180° opposite are the same line.
     *
     * The page is the reference frame, so a snapped side runs with the ruling on screen — which is
     * what "ruled straight" means when the sheet's grid is under the pen.
     */
    val VERTEX_SNAP_ANGLES_DEG: DoubleArray = doubleArrayOf(0.0, 30.0, 45.0, 90.0)

    /** The two-vertex minimum a figure needs before there is an angle or a side to talk about. */
    private const val MIN_VERTICES = 3

    /** Two points nearer than this (pt) are the same corner. */
    private const val SAME_POINT_PT = 1e-6

    /** The shape-library key [figure] is stored under, or null when it stays a plain stroke. */
    fun shapeName(figure: RecognisedFigure): String? = when (figure.kind) {
        FigureKind.TRIANGLE -> TRIANGLE
        FigureKind.RECTANGLE -> RECTANGLE
        FigureKind.CIRCLE -> CIRCLE
        FigureKind.ELLIPSE -> ELLIPSE
        else -> null
    }

    /** The shape-library key a figure tool [kind] writes, or null when it stays a stroke. */
    fun shapeNameForTool(kind: ShapeKind): String? = when (kind) {
        ShapeKind.RECTANGLE -> RECTANGLE
        ShapeKind.SQUARE -> SQUARE
        ShapeKind.TRIANGLE -> TRIANGLE
        ShapeKind.TRAPEZOID -> TRAPEZOID
        ShapeKind.POLYGON -> POLYGON
        ShapeKind.ELLIPSE -> ELLIPSE
        else -> null
    }

    /**
     * [figure] as the living shape it should be kept as, or **null** when it is not a closed figure
     * (line/arrow/arc stay strokes).
     */
    fun elementFor(
        figure: RecognisedFigure,
        strokeColor: Int,
        strokeWidth: Double,
        lineStyle: LineStyle = LineStyle.PLAIN,
        aspectLocked: Boolean = true,
    ): ParametricShapeElement? {
        val isConicFigure = figure.kind == FigureKind.CIRCLE || figure.kind == FigureKind.ELLIPSE
        if (!figure.isClosedPolygon && !isConicFigure) return null
        if (isConicFigure) {
            return conicElementFromPoints(
                points = figure.points,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                extraAttrs = emptyMap(),
                lineStyle = lineStyle,
                aspectLocked = aspectLocked,
            )
        }
        val vertices = verticesOf(figure)
        if (vertices.size < MIN_VERTICES) return null
        return ParametricShapeElement(
            shape = shapeName(figure) ?: return null,
            vertices = vertices,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            id = ElementId.mint(ElementId.SHAPE),
            lineStyle = lineStyle,
            aspectLocked = aspectLocked,
        )
    }

    /**
     * A figure tool's [points] turned into a living [ParametricShapeElement], or **null**
     * when [kind] is not a living shape tool or has degenerate geometry.
     */
    fun elementForShapeTool(
        kind: ShapeKind,
        points: List<StrokePoint>,
        strokeColor: Int,
        strokeWidth: Double,
        lineStyle: LineStyle = LineStyle.PLAIN,
        aspectLocked: Boolean = true,
    ): ParametricShapeElement? {
        if (kind == ShapeKind.ELLIPSE) {
            return conicElementFromPoints(
                points = points,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth,
                extraAttrs = mapOf(FigurePins.SHAPE_ATTR to kind.name),
                lineStyle = lineStyle,
                aspectLocked = aspectLocked,
            )
        }
        val key = shapeNameForTool(kind) ?: return null
        val vertices = verticesOfPoints(points)
        if (vertices.size < MIN_VERTICES) return null
        return ParametricShapeElement(
            shape = key,
            vertices = vertices,
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            extraAttrs = mapOf(FigurePins.SHAPE_ATTR to kind.name),
            id = ElementId.mint(ElementId.SHAPE),
            lineStyle = lineStyle,
            aspectLocked = aspectLocked,
        )
    }

    private fun conicElementFromPoints(
        points: List<StrokePoint>,
        strokeColor: Int,
        strokeWidth: Double,
        extraAttrs: Map<String, String>,
        lineStyle: LineStyle,
        aspectLocked: Boolean,
    ): ParametricShapeElement? {
        if (points.size < 2) return null
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val cx = (minX + maxX) / 2.0
        val cy = (minY + maxY) / 2.0
        val rx = ((maxX - minX) / 2.0).coerceAtLeast(MIN_CONIC_DIAMETER_PT / 2.0)
        val ry = ((maxY - minY) / 2.0).coerceAtLeast(MIN_CONIC_DIAMETER_PT / 2.0)
        val kind = if (abs(rx - ry) <= EQUAL_SIDE_TOL_PT) CIRCLE else ELLIPSE
        return ParametricShapeElement(
            shape = kind,
            vertices = listOf(
                ShapeVertex(cx, cy - ry),
                ShapeVertex(cx + rx, cy),
                ShapeVertex(cx, cy + ry),
                ShapeVertex(cx - rx, cy),
            ),
            strokeColor = strokeColor,
            strokeWidth = strokeWidth,
            extraAttrs = extraAttrs,
            id = ElementId.mint(ElementId.SHAPE),
            lineStyle = lineStyle,
            aspectLocked = aspectLocked,
        )
    }

    /** Extracts center and semi-axes vectors from a conic [shape], or null if invalid. */
    fun conicFrame(shape: ParametricShapeElement): ConicFrame? {
        if (!isConic(shape)) return null
        val v = shape.vertices
        val cx = (v[0].x + v[2].x) / 2.0
        val cy = (v[0].y + v[2].y) / 2.0
        val ux = v[1].x - cx
        val uy = v[1].y - cy
        val vx = v[2].x - cx
        val vy = v[2].y - cy
        return ConicFrame(cx, cy, ux, uy, vx, vy, hypot(ux, uy), hypot(vx, vy))
    }

    /** Sampled closed ellipse polyline around [shape]'s `[N, E, S, W]` cardinal frame. */
    fun conicOutlinePoints(shape: ParametricShapeElement, steps: Int = CONIC_OUTLINE_STEPS): List<ShapeVertex> {
        val f = conicFrame(shape) ?: return emptyList()
        val count = steps.coerceAtLeast(16)
        val out = ArrayList<ShapeVertex>(count + 1)
        for (i in 0..count) {
            val theta = (2.0 * Math.PI * i) / count
            val c = cos(theta)
            val s = sin(theta)
            out += ShapeVertex(f.cx + f.ux * c + f.vx * s, f.cy + f.uy * c + f.vy * s)
        }
        return out
    }

    /** True when ([x], [y]) lies inside or within [padPt] of the ellipse [shape]. */
    fun conicContains(shape: ParametricShapeElement, x: Double, y: Double, padPt: Double = 0.0): Boolean {
        val f = conicFrame(shape) ?: return false
        val dx = x - f.cx
        val dy = y - f.cy
        val du = if (f.rx > SAME_POINT_PT) (dx * f.ux + dy * f.uy) / f.rx else 0.0
        val dv = if (f.ry > SAME_POINT_PT) (dx * f.vx + dy * f.vy) / f.ry else 0.0
        val a = (f.rx + padPt).coerceAtLeast(SAME_POINT_PT)
        val b = (f.ry + padPt).coerceAtLeast(SAME_POINT_PT)
        return (du / a) * (du / a) + (dv / b) * (dv / b) <= 1.0
    }

    /** Updates [shape]'s horizontal ([rxPt]) and/or vertical ([ryPt]) semi-axes about its centre. */
    fun setConicRadii(
        shape: ParametricShapeElement,
        rxPt: Double? = null,
        ryPt: Double? = null,
    ): ParametricShapeElement {
        val f = conicFrame(shape) ?: return shape
        val minR = MIN_CONIC_DIAMETER_PT / 2.0
        val nextRx = (rxPt ?: f.rx).takeIf { it.isFinite() }?.coerceAtLeast(minR) ?: f.rx
        val nextRy = (ryPt ?: f.ry).takeIf { it.isFinite() }?.coerceAtLeast(minR) ?: f.ry
        val su = if (f.rx > SAME_POINT_PT) nextRx / f.rx else 1.0
        val sv = if (f.ry > SAME_POINT_PT) nextRy / f.ry else 1.0
        val ux = if (f.rx > SAME_POINT_PT) f.ux * su else nextRx
        val uy = if (f.rx > SAME_POINT_PT) f.uy * su else 0.0
        val vx = if (f.ry > SAME_POINT_PT) f.vx * sv else 0.0
        val vy = if (f.ry > SAME_POINT_PT) f.vy * sv else nextRy
        val updated = shape.copy(
            vertices = listOf(
                ShapeVertex(f.cx - vx, f.cy - vy),
                ShapeVertex(f.cx + ux, f.cy + uy),
                ShapeVertex(f.cx + vx, f.cy + vy),
                ShapeVertex(f.cx - ux, f.cy - uy),
            ),
        )
        return updated.copy(shape = reclassifiedKind(updated))
    }

    /** [figure]'s corners: its emitted points without the closing repetition of the first one. */
    fun verticesOf(figure: RecognisedFigure): List<ShapeVertex> = verticesOfPoints(figure.points)

    /** Polygon corners extracted from [points], dropping any closing repeat of the first point. */
    private fun verticesOfPoints(points: List<StrokePoint>): List<ShapeVertex> {
        if (points.isEmpty()) return emptyList()
        val first = points.first()
        val last = points.last()
        val closed = points.size > 1 &&
            hypot(last.x - first.x, last.y - first.y) <= SAME_POINT_PT
        return (if (closed) points.dropLast(1) else points).map { ShapeVertex(it.x, it.y) }
    }

    /**
     * [shape] with the vertex at [index] moved to ([x], [y]) pt. Out-of-range indices come back
     * unchanged so a stale handle can never corrupt a shape; a rectangular kind whose right angles
     * or equal sides were broken by the move is honestly relabelled.
     */
    fun moveVertex(shape: ParametricShapeElement, index: Int, x: Double, y: Double): ParametricShapeElement {
        if (isConic(shape)) return moveConicCardinal(shape, index, x, y)
        if (index !in shape.vertices.indices) return shape
        val moved = shape.vertices.toMutableList()
        moved[index] = ShapeVertex(x, y)
        val raw = shape.copy(vertices = moved)
        return raw.copy(shape = reclassifiedKind(raw))
    }

    private fun moveConicCardinal(shape: ParametricShapeElement, index: Int, x: Double, y: Double): ParametricShapeElement {
        val f = conicFrame(shape) ?: return shape
        if (index !in 0..3) return shape
        val v = shape.vertices
        val oppIdx = (index + 2) % 4
        val opp = v[oppIdx]
        val dx0 = v[index].x - opp.x
        val dy0 = v[index].y - opp.y
        val len0 = hypot(dx0, dy0)
        if (len0 <= SAME_POINT_PT) return shape
        val ax = dx0 / len0
        val ay = dy0 / len0
        val span = ((x - opp.x) * ax + (y - opp.y) * ay).coerceAtLeast(MIN_CONIC_DIAMETER_PT)
        val moved = ShapeVertex(opp.x + ax * span, opp.y + ay * span)
        val cx = (opp.x + moved.x) / 2.0
        val cy = (opp.y + moved.y) / 2.0
        val next = v.toMutableList()
        next[index] = moved
        if (index == 0 || index == 2) {
            next[1] = ShapeVertex(cx + f.ux, cy + f.uy)
            next[3] = ShapeVertex(cx - f.ux, cy - f.uy)
        } else {
            next[0] = ShapeVertex(cx - f.vx, cy - f.vy)
            next[2] = ShapeVertex(cx + f.vx, cy + f.vy)
        }
        val raw = shape.copy(vertices = next)
        return raw.copy(shape = reclassifiedKind(raw))
    }

    /**
     * Updates [edgeIndex]'s side length to [newLengthPt] pt by moving its end vertex along the side's
     * current direction. Leaves the start corner fixed so the figure grows or shrinks cleanly.
     */
    fun setSideLength(shape: ParametricShapeElement, edgeIndex: Int, newLengthPt: Double): ParametricShapeElement {
        val count = shape.vertices.size
        if (count < MIN_VERTICES || edgeIndex !in shape.vertices.indices) return shape
        if (!newLengthPt.isFinite() || newLengthPt <= SAME_POINT_PT) return shape
        val from = shape.vertices[edgeIndex]
        val nextIndex = (edgeIndex + 1) % count
        val to = shape.vertices[nextIndex]
        val dx = to.x - from.x
        val dy = to.y - from.y
        val current = hypot(dx, dy)
        val (ux, uy) = if (current > SAME_POINT_PT) (dx / current) to (dy / current) else 1.0 to 0.0
        return moveVertex(shape, nextIndex, from.x + ux * newLengthPt, from.y + uy * newLengthPt)
    }

    /**
     * Sets the interior angle at [vertexIndex] to [newAngleDeg] degrees by rotating its outgoing side
     * around [vertexIndex] while preserving that side's length and the polygon's local winding.
     */
    fun setInteriorAngle(shape: ParametricShapeElement, vertexIndex: Int, newAngleDeg: Double): ParametricShapeElement {
        val count = shape.vertices.size
        if (count < MIN_VERTICES || vertexIndex !in shape.vertices.indices) return shape
        if (!newAngleDeg.isFinite() || newAngleDeg !in 1.0..179.0) return shape
        val prev = shape.vertices[(vertexIndex - 1 + count) % count]
        val vertex = shape.vertices[vertexIndex]
        val nextIndex = (vertexIndex + 1) % count
        val next = shape.vertices[nextIndex]
        val ax = prev.x - vertex.x
        val ay = prev.y - vertex.y
        val bx = next.x - vertex.x
        val by = next.y - vertex.y
        val lenA = hypot(ax, ay)
        val lenB = hypot(bx, by)
        if (lenA <= SAME_POINT_PT || lenB <= SAME_POINT_PT) return shape
        val sign = if (ax * by - ay * bx >= 0.0) 1.0 else -1.0
        val targetAngle = atan2(ay, ax) + sign * Math.toRadians(newAngleDeg)
        return moveVertex(shape, nextIndex, vertex.x + lenB * cos(targetAngle), vertex.y + lenB * sin(targetAngle))
    }

    /** Keeps `CIRCLE`/`ELLIPSE` and `SQUARE`/`RECTANGLE` labels truthful after edits. */
    fun reclassifiedKind(shape: ParametricShapeElement): String {
        if (isConic(shape)) {
            val f = conicFrame(shape) ?: return shape.shape
            return if (abs(f.rx - f.ry) <= EQUAL_SIDE_TOL_PT) CIRCLE else ELLIPSE
        }
        if (shape.shape != RECTANGLE && shape.shape != SQUARE) return shape.shape
        if (shape.vertices.size != 4) return POLYGON
        val rightAngles = (0 until 4).all { abs(interiorAngleDeg(shape, it) - 90.0) <= RIGHT_ANGLE_TOL_DEG }
        if (!rightAngles) return POLYGON
        if (shape.shape == SQUARE) {
            val lengths = (0 until 4).map { sideLengthPt(shape, it) }
            if (lengths.max() - lengths.min() > EQUAL_SIDE_TOL_PT) return RECTANGLE
        }
        return shape.shape
    }

    /**
     * Where the vertex at [index] should sit for a pointer at ([x], [y]) pt: the free position,
     * unless either adjacent side (from the previous or next vertex) is within [toleranceDeg] of
     * [VERTEX_SNAP_ANGLES_DEG], in which case the closer-aligned side snaps onto that direction while
     * keeping the pointer's distance from that anchor.
     */
    fun snappedVertex(
        shape: ParametricShapeElement,
        index: Int,
        x: Double,
        y: Double,
        toleranceDeg: Double = VERTEX_SNAP_TOLERANCE_DEG,
    ): Pair<Double, Double> {
        val count = shape.vertices.size
        if (count < 2 || index !in shape.vertices.indices) return x to y
        val anchors = listOf(
            shape.vertices[(index - 1 + count) % count],
            shape.vertices[(index + 1) % count],
        )
        var bestAnchor: ShapeVertex? = null
        var bestDirection = 0.0
        var bestLength = 0.0
        var bestDelta = Double.MAX_VALUE
        for (anchor in anchors) {
            val dx = x - anchor.x
            val dy = y - anchor.y
            val length = hypot(dx, dy)
            if (length < SAME_POINT_PT) continue
            val rawDeg = Math.toDegrees(atan2(dy, dx))
            val direction = snapDirectionDeg(rawDeg, toleranceDeg) ?: continue
            val delta = abs(normaliseDeg(rawDeg - direction))
            if (delta < bestDelta) {
                bestDelta = delta
                bestAnchor = anchor
                bestDirection = direction
                bestLength = length
            }
        }
        val anchor = bestAnchor ?: return x to y
        val radians = Math.toRadians(bestDirection)
        return (anchor.x + bestLength * cos(radians)) to (anchor.y + bestLength * sin(radians))
    }

    /** The length of the side that starts at [index] and ends at the next vertex, in pt. */
    fun sideLengthPt(shape: ParametricShapeElement, index: Int): Double {
        val count = shape.vertices.size
        if (count < 2 || index !in shape.vertices.indices) return 0.0
        val from = shape.vertices[index]
        val to = shape.vertices[(index + 1) % count]
        return hypot(to.x - from.x, to.y - from.y)
    }

    /** The interior angle at [index], in degrees, between the sides to its two neighbours. */
    fun interiorAngleDeg(shape: ParametricShapeElement, index: Int): Double {
        val count = shape.vertices.size
        if (count < MIN_VERTICES || index !in shape.vertices.indices) return 0.0
        return interiorAngleDeg(
            shape.vertices[(index - 1 + count) % count],
            shape.vertices[index],
            shape.vertices[(index + 1) % count],
        )
    }

    /** The interior angle at [vertex], in degrees, between the rays to [from] and [to]. */
    fun interiorAngleDeg(from: ShapeVertex, vertex: ShapeVertex, to: ShapeVertex): Double {
        val a = atan2(from.y - vertex.y, from.x - vertex.x)
        val b = atan2(to.y - vertex.y, to.x - vertex.x)
        var degrees = Math.toDegrees(abs(a - b))
        if (degrees > 180.0) degrees = 360.0 - degrees
        return degrees
    }

    /**
     * The nearest of [VERTEX_SNAP_ANGLES_DEG] that [degrees] (the direction of a side) may be pulled
     * onto, or null when it is further than [toleranceDeg] from all of them.
     *
     * The candidate set is each guided angle in **all four quadrants** — 30° is also 150°, 210° and
     * 330° — because a side has no arrow: a line running at 210° is the same straight edge as one
     * running at 30°, and both should snap.
     */
    fun snapDirectionDeg(degrees: Double, toleranceDeg: Double = VERTEX_SNAP_TOLERANCE_DEG): Double? {
        var best: Double? = null
        var bestDelta = Double.MAX_VALUE
        for (base in VERTEX_SNAP_ANGLES_DEG) {
            for (candidate in doubleArrayOf(base, -base, 180.0 - base, base - 180.0)) {
                val delta = abs(normaliseDeg(degrees - candidate))
                if (delta < bestDelta) {
                    bestDelta = delta
                    best = candidate
                }
            }
        }
        return if (bestDelta <= toleranceDeg) best else null
    }

    /** [degrees] folded into (-180°, 180°], the frame [snapDirectionDeg] compares in. */
    private fun normaliseDeg(degrees: Double): Double {
        var d = degrees % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }
}
