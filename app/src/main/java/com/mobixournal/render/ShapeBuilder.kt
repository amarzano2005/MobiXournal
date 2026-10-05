package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A geometric shape the shape tools draw. Each is emitted as an ordinary constant-width stroke. */
enum class ShapeKind {
    LINE, ARROW, DOUBLE_ARROW, COORDINATE_AXIS, RECTANGLE, ELLIPSE, SPLINE,
    // The STEM set: the regular figures reached for when annotating maths and science.
    TRIANGLE, SQUARE, RHOMBUS, PENTAGON, HEXAGON,
    TABLE,
    // Electronic circuits & logic gates
    RESISTOR, CAPACITOR, INDUCTOR, GROUND, AND_GATE, OR_GATE, NOT_GATE,
    NAND_GATE, NOR_GATE, XOR_GATE, XNOR_GATE,
}

/**
 * Pure geometry that turns a drag (start → end, in page-local pt) into the vertex list of a shape.
 * Shapes round-trip as normal `<stroke>` point-lists — desktop Xournal++ records line/arrow/rect/
 * ellipse the same way — so nothing new touches the `.xopp` format. Kept Android-free so it's
 * unit-testable on the JVM ([ShapeBuilderTest]). All points carry the same constant [widthPt].
 */
object ShapeBuilder {

    /** Length of an arrowhead barb relative to the shaft, and its opening half-angle. */
    private const val ARROW_HEAD_FRACTION = 0.18
    private const val ARROW_HEAD_MIN_PT = 8.0
    private const val ARROW_HEAD_MAX_PT = 28.0
    private val ARROW_HALF_ANGLE = Math.toRadians(28.0)

    /** How many segments approximate an ellipse (more when it's larger, clamped for sanity). */
    private const val ELLIPSE_MIN_SEGMENTS = 24
    private const val ELLIPSE_MAX_SEGMENTS = 96

    fun build(
        kind: ShapeKind,
        startX: Double, startY: Double,
        endX: Double, endY: Double,
        widthPt: Double,
        rows: Int = 3,
        cols: Int = 3,
        hasHeader: Boolean = false,
        triangleKind: TriangleKind = TriangleKind.EQUILATERAL,
        angleA: Double = 40.0,
        angleB: Double = 60.0,
        angleC: Double = 80.0,
    ): List<StrokePoint> = when (kind) {
        ShapeKind.LINE -> line(startX, startY, endX, endY, widthPt)
        ShapeKind.ARROW -> arrow(startX, startY, endX, endY, widthPt)
        ShapeKind.DOUBLE_ARROW -> doubleArrow(startX, startY, endX, endY, widthPt)
        ShapeKind.COORDINATE_AXIS -> coordinateAxis(startX, startY, endX, endY, widthPt)
        ShapeKind.RECTANGLE -> rectangle(startX, startY, endX, endY, widthPt)
        ShapeKind.ELLIPSE -> ellipse(startX, startY, endX, endY, widthPt)
        ShapeKind.TRIANGLE -> triangle(startX, startY, endX, endY, widthPt, triangleKind, angleA, angleB, angleC)
        ShapeKind.SQUARE -> square(startX, startY, endX, endY, widthPt)
        ShapeKind.RHOMBUS -> rhombus(startX, startY, endX, endY, widthPt)
        ShapeKind.PENTAGON -> regularPolygon(startX, startY, endX, endY, widthPt, sides = 5)
        ShapeKind.HEXAGON -> regularPolygon(startX, startY, endX, endY, widthPt, sides = 6)
        ShapeKind.TABLE -> table(startX, startY, endX, endY, widthPt, rows, cols, hasHeader)
        ShapeKind.RESISTOR -> CircuitShapes.resistor(startX, startY, endX, endY, widthPt)
        ShapeKind.CAPACITOR -> CircuitShapes.capacitor(startX, startY, endX, endY, widthPt).flatten()
        ShapeKind.INDUCTOR -> CircuitShapes.inductor(startX, startY, endX, endY, widthPt)
        ShapeKind.GROUND -> CircuitShapes.ground(startX, startY, endX, endY, widthPt)
        ShapeKind.AND_GATE -> CircuitShapes.andGate(startX, startY, endX, endY, widthPt)
        ShapeKind.OR_GATE -> CircuitShapes.orGate(startX, startY, endX, endY, widthPt)
        ShapeKind.NOT_GATE -> CircuitShapes.notGate(startX, startY, endX, endY, widthPt)
        ShapeKind.NAND_GATE -> CircuitShapes.nandGate(startX, startY, endX, endY, widthPt)
        ShapeKind.NOR_GATE -> CircuitShapes.norGate(startX, startY, endX, endY, widthPt)
        ShapeKind.XOR_GATE -> CircuitShapes.xorGate(startX, startY, endX, endY, widthPt)
        ShapeKind.XNOR_GATE -> CircuitShapes.xnorGate(startX, startY, endX, endY, widthPt)
        // A spline is laid down over many taps, so its real geometry comes from [SplineBuilder]; the
        // two-point case this signature can express is just a straight line between the ends.
        ShapeKind.SPLINE -> line(startX, startY, endX, endY, widthPt)
    }

    /**
     * When a shape decomposes into multiple disconnected strokes (such as a capacitor with an open gap),
     * returns the list of stroke point-lists; returns null for single-stroke figures.
     */
    fun buildMulti(
        kind: ShapeKind,
        startX: Double, startY: Double,
        endX: Double, endY: Double,
        widthPt: Double,
        rows: Int = 3,
        cols: Int = 3,
        hasHeader: Boolean = false,
    ): List<List<StrokePoint>>? = when (kind) {
        ShapeKind.CAPACITOR -> CircuitShapes.capacitor(startX, startY, endX, endY, widthPt)
        else -> null
    }

    private fun p(x: Double, y: Double, w: Double) = StrokePoint(x, y, w)

    private fun line(sx: Double, sy: Double, ex: Double, ey: Double, w: Double) =
        listOf(p(sx, sy, w), p(ex, ey, w))

    /** A shaft plus a V-shaped head, traced as one polyline (barb, tip, barb) so it's one stroke. */
    private fun arrow(sx: Double, sy: Double, ex: Double, ey: Double, w: Double): List<StrokePoint> {
        val len = hypot(ex - sx, ey - sy)
        if (len == 0.0) return line(sx, sy, ex, ey, w)
        return listOf(p(sx, sy, w)) + head(sx, sy, ex, ey, len, w)
    }

    /**
     * A shaft with a head at *both* ends. Traced as one polyline: the tail head is drawn first
     * (walking back out to the tail tip), then the shaft, then the tip head — so it stays one stroke.
     */
    private fun doubleArrow(sx: Double, sy: Double, ex: Double, ey: Double, w: Double): List<StrokePoint> {
        val len = hypot(ex - sx, ey - sy)
        if (len == 0.0) return line(sx, sy, ex, ey, w)
        return head(ex, ey, sx, sy, len, w).reversed() + head(sx, sy, ex, ey, len, w)
    }

    /**
     * The V-shaped head for a shaft pointing from (sx,sy) to the tip (ex,ey): tip, barb, tip, barb.
     * The caller prefixes/joins these so the whole arrow stays a single polyline.
     */
    private fun head(
        sx: Double, sy: Double, ex: Double, ey: Double, len: Double, w: Double,
    ): List<StrokePoint> {
        val size = (len * ARROW_HEAD_FRACTION).coerceIn(ARROW_HEAD_MIN_PT, ARROW_HEAD_MAX_PT)
        val dir = atan2(ey - sy, ex - sx)
        val left = dir + PI - ARROW_HALF_ANGLE
        val right = dir + PI + ARROW_HALF_ANGLE
        return listOf(
            p(ex, ey, w),
            p(ex + size * cos(left), ey + size * sin(left), w),
            p(ex, ey, w),
            p(ex + size * cos(right), ey + size * sin(right), w),
        )
    }

    /**
     * A pair of arrowed axes meeting at the drag's start: y runs vertically to the drag's end
     * height, x runs horizontally to its end width. Traced as one polyline — y tip (with head)
     * down to the origin, then out to the x tip (with head) — so it round-trips as a single stroke.
     */
    private fun coordinateAxis(
        sx: Double, sy: Double, ex: Double, ey: Double, w: Double,
    ): List<StrokePoint> {
        val yLen = kotlin.math.abs(ey - sy)
        val xLen = kotlin.math.abs(ex - sx)
        if (yLen == 0.0 && xLen == 0.0) return line(sx, sy, ex, ey, w)
        // Axes point away from the origin in whichever direction the drag went.
        val yTip = sy + if (ey <= sy) -yLen else yLen
        val xTip = sx + if (ex >= sx) xLen else -xLen
        val yHead = if (yLen == 0.0) emptyList() else head(sx, sy, sx, yTip, yLen, w)
        val xHead = if (xLen == 0.0) emptyList() else head(sx, sy, xTip, sy, xLen, w)
        return yHead.reversed() + p(sx, sy, w) + xHead
    }

    /** A closed rectangle from the drag's bounding box (corners in order, back to the start). */
    private fun rectangle(sx: Double, sy: Double, ex: Double, ey: Double, w: Double): List<StrokePoint> {
        val l = minOf(sx, ex); val r = maxOf(sx, ex)
        val top = minOf(sy, ey); val bot = maxOf(sy, ey)
        return listOf(
            p(l, top, w), p(r, top, w), p(r, bot, w), p(l, bot, w), p(l, top, w),
        )
    }

    /**
     * A triangle according to [kind]:
     * - [TriangleKind.EQUILATERAL]: All 3 sides equal, 60° angles (factory default).
     * - [TriangleKind.RIGHT]: 90° angle at corner (sx, ey) connecting to (sx, sy) and (ex, ey).
     * - [TriangleKind.ISOSCELES]: Two equal sides, apex centered over base.
     * - [TriangleKind.SCALENE]: 3 customizable angles [angleA], [angleB], [angleC], preserved under scaling.
     */
    private fun triangle(
        sx: Double, sy: Double, ex: Double, ey: Double, w: Double,
        kind: TriangleKind = TriangleKind.EQUILATERAL,
        angleA: Double = 40.0,
        angleB: Double = 60.0,
        angleC: Double = 80.0,
    ): List<StrokePoint> {
        val l = minOf(sx, ex); val r = maxOf(sx, ex)
        val top = minOf(sy, ey); val bot = maxOf(sy, ey)
        val width = r - l; val height = bot - top
        if (width == 0.0 || height == 0.0) return line(sx, sy, ex, ey, w)

        return when (kind) {
            TriangleKind.EQUILATERAL -> {
                // Height of an equilateral triangle with side s is s * sqrt(3) / 2
                val s = minOf(width, height * 2.0 / kotlin.math.sqrt(3.0))
                val h = s * kotlin.math.sqrt(3.0) / 2.0
                val cx = (l + r) / 2.0
                if (sy <= ey) {
                    val yTop = top + (height - h) / 2.0
                    val yBot = yTop + h
                    listOf(p(cx, yTop, w), p(cx + s / 2.0, yBot, w), p(cx - s / 2.0, yBot, w), p(cx, yTop, w))
                } else {
                    val yTop = top + (height - h) / 2.0
                    val yBot = yTop + h
                    listOf(p(cx - s / 2.0, yTop, w), p(cx + s / 2.0, yTop, w), p(cx, yBot, w), p(cx - s / 2.0, yTop, w))
                }
            }
            TriangleKind.RIGHT -> {
                listOf(p(sx, sy, w), p(sx, ey, w), p(ex, ey, w), p(sx, sy, w))
            }
            TriangleKind.ISOSCELES -> {
                val cx = (l + r) / 2.0
                if (sy <= ey) {
                    listOf(p(cx, top, w), p(r, bot, w), p(l, bot, w), p(cx, top, w))
                } else {
                    listOf(p(l, top, w), p(r, top, w), p(cx, bot, w), p(l, top, w))
                }
            }
            TriangleKind.SCALENE -> {
                scaleneTriangle(l, top, r, bot, sy <= ey, w, angleA, angleB, angleC)
            }
        }
    }

    private fun scaleneTriangle(
        l: Double, top: Double, r: Double, bot: Double,
        apexUp: Boolean, w: Double,
        aA: Double, aB: Double, aC: Double,
    ): List<StrokePoint> {
        val total = aA + aB + aC
        val (angA, angB, angC) = if (total > 0 && kotlin.math.abs(total - 180.0) < 1.0) {
            Triple(aA, aB, aC)
        } else if (aA > 0 && aB > 0 && aA + aB < 180) {
            Triple(aA, aB, 180.0 - aA - aB)
        } else {
            Triple(40.0, 60.0, 80.0)
        }

        val rA = Math.toRadians(angA)
        val rB = Math.toRadians(angB)
        val rC = Math.toRadians(angC)

        // Side lengths by Law of Sines: c = sin(C), b = sin(B)
        val c = kotlin.math.sin(rC)
        val b = kotlin.math.sin(rB)

        // Vertex A at (0, 0), Vertex B at (c, 0)
        // Vertex C at (b * cos(A), b * sin(A))
        val cx = b * kotlin.math.cos(rA)
        val cy = b * kotlin.math.sin(rA)

        val xMin = minOf(0.0, cx)
        val xMax = maxOf(c, cx)
        val normW = xMax - xMin
        val normH = cy

        val boxW = r - l
        val boxH = bot - top
        if (normW <= 1e-9 || normH <= 1e-9) return listOf(p(l, top, w), p(r, bot, w))

        val scale = minOf(boxW / normW, boxH / normH)
        val scaledW = normW * scale
        val scaledH = normH * scale

        val xOff = l + (boxW - scaledW) / 2.0 - xMin * scale
        return if (apexUp) {
            val yOff = top + (boxH - scaledH) / 2.0 + scaledH
            val ptA = p(xOff, yOff, w)
            val ptB = p(xOff + c * scale, yOff, w)
            val ptC = p(xOff + cx * scale, yOff - cy * scale, w)
            listOf(ptC, ptB, ptA, ptC)
        } else {
            val yOff = top + (boxH - scaledH) / 2.0
            val ptA = p(xOff, yOff, w)
            val ptB = p(xOff + c * scale, yOff, w)
            val ptC = p(xOff + cx * scale, yOff + cy * scale, w)
            listOf(ptC, ptB, ptA, ptC)
        }
    }

    /**
     * A square: the drag's longer side, squared off in the direction it was drawn, so a drag that is
     * not exactly diagonal still gives four equal sides.
     */
    private fun square(sx: Double, sy: Double, ex: Double, ey: Double, w: Double): List<StrokePoint> {
        val dx = ex - sx
        val dy = ey - sy
        val side = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
        val ex2 = sx + if (dx >= 0) side else -side
        val ey2 = sy + if (dy >= 0) side else -side
        return rectangle(sx, sy, ex2, ey2, w)
    }

    /** A rhombus (diamond): the four edge midpoints of the drag's box, joined. */
    private fun rhombus(sx: Double, sy: Double, ex: Double, ey: Double, w: Double): List<StrokePoint> {
        val l = minOf(sx, ex); val r = maxOf(sx, ex)
        val top = minOf(sy, ey); val bot = maxOf(sy, ey)
        val cx = (l + r) / 2.0; val cy = (top + bot) / 2.0
        return listOf(p(cx, top, w), p(r, cy, w), p(cx, bot, w), p(l, cy, w), p(cx, top, w))
    }

    /**
     * A regular [sides]-gon inscribed in the drag's box, first vertex at the top. The box may be a
     * non-square rectangle (a stretched figure), matching how [ellipse] fills its box.
     */
    private fun regularPolygon(
        sx: Double, sy: Double, ex: Double, ey: Double, w: Double, sides: Int,
    ): List<StrokePoint> {
        val cx = (sx + ex) / 2.0; val cy = (sy + ey) / 2.0
        val rx = kotlin.math.abs(ex - sx) / 2.0; val ry = kotlin.math.abs(ey - sy) / 2.0
        return (0..sides).map { i ->
            val a = -PI / 2.0 + 2.0 * PI * i / sides
            p(cx + rx * cos(a), cy + ry * sin(a), w)
        }
    }

    /** An axis-aligned ellipse inscribed in the drag's bounding box, sampled to a closed polyline. */
    private fun ellipse(sx: Double, sy: Double, ex: Double, ey: Double, w: Double): List<StrokePoint> {
        val cx = (sx + ex) / 2.0
        val cy = (sy + ey) / 2.0
        val rx = kotlin.math.abs(ex - sx) / 2.0
        val ry = kotlin.math.abs(ey - sy) / 2.0
        val perimeter = 2.0 * PI * maxOf(rx, ry)
        val segments = (perimeter / 8.0).toInt().coerceIn(ELLIPSE_MIN_SEGMENTS, ELLIPSE_MAX_SEGMENTS)
        val out = ArrayList<StrokePoint>(segments + 1)
        for (i in 0..segments) {
            val a = 2.0 * PI * i / segments
            out += p(cx + rx * cos(a), cy + ry * sin(a), w)
        }
        return out
    }

    /**
     * A grid table with [rows] and [cols] inscribed in the drag bounding box, traced as a single
     * continuous polyline along its outer borders and dividing lines so it round-trips as an
     * ordinary stroke.
     */
    fun table(
        sx: Double, sy: Double, ex: Double, ey: Double, w: Double,
        rows: Int = 3, cols: Int = 3,
        hasHeader: Boolean = false,
    ): List<StrokePoint> {
        val l = minOf(sx, ex); val r = maxOf(sx, ex)
        val top = minOf(sy, ey); val bot = maxOf(sy, ey)
        if (kotlin.math.abs(r - l) < 1e-4 && kotlin.math.abs(bot - top) < 1e-4) return line(sx, sy, ex, ey, w)

        val safeRows = rows.coerceIn(1, 50)
        val safeCols = cols.coerceIn(1, 50)

        val pts = ArrayList<StrokePoint>()
        // Outer border
        pts += p(l, top, w)
        pts += p(r, top, w)
        pts += p(r, bot, w)
        pts += p(l, bot, w)
        pts += p(l, top, w)

        var curX = l
        var curY = top

        val effectiveRows = if (hasHeader && safeRows == 1) 2 else safeRows
        val rowHeight = (bot - top) / effectiveRows
        val headerGap = if (hasHeader) {
            (w * 2.5).coerceIn(2.0, (rowHeight * 0.3).coerceAtLeast(2.0))
        } else 0.0

        // Horizontal dividers
        if (effectiveRows > 1) {
            for (i in 1 until effectiveRows) {
                val y = top + i * rowHeight
                pts += p(curX, y, w)
                curX = if (curX == l) r else l
                pts += p(curX, y, w)
                curY = y
                if (hasHeader && i == 1) {
                    val doubleY = (y + headerGap).coerceAtMost(bot)
                    if (doubleY > y && doubleY < bot) {
                        pts += p(curX, doubleY, w)
                        curX = if (curX == l) r else l
                        pts += p(curX, doubleY, w)
                        curY = doubleY
                    }
                }
            }
        }

        // Vertical dividers: divide every column from top to bottom (including the header row)
        if (safeCols > 1) {
            val colWidth = (r - l) / safeCols
            val targetY = if (kotlin.math.abs(curY - top) <= kotlin.math.abs(curY - bot)) top else bot
            if (curY != targetY) {
                pts += p(curX, targetY, w)
                curY = targetY
            }
            val colIndices = if (curX == l) (1 until safeCols).toList() else (safeCols - 1 downTo 1).toList()
            for (j in colIndices) {
                val x = l + j * colWidth
                pts += p(x, curY, w)
                curY = if (curY == top) bot else top
                pts += p(x, curY, w)
                curX = x
            }
        }

        return pts
    }
}
