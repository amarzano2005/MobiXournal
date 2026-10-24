package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * How an **arrow-like figure** is resized: the figure is *read back* into its parameters and then
 * **re-emitted** — not stretched point by point.
 *
 * This replaces the earlier "stretch the vertices, then pin the barbs" approach, which patched up an
 * affine transform after the fact: it searched the stroke for a repeated tip, moved the two barbs to
 * their old length, and had to guess the direction. Every one of those steps is a guess, and each was
 * a way for a resize to go wrong — a head that lost its symmetry stopped being recognised and grew
 * with the shaft, a two-axis stretch could fold a barb, and the axis diagram's two heads had to be
 * found by the same scan that might find neither.
 *
 * Here the vertices *are* the parameters. [read] classifies a committed stroke as one of the three
 * things the tools draw with heads — a single arrow, a double arrow, a pair of coordinate axes — and
 * captures its anchors (tail, tip, origin, axis tips), its head size in pt and its opening angle.
 * [Figure]s then re-emit the whole figure through the **same** [FigureGeometry] primitives the tools
 * used, at the transformed anchors and at the head size the figure was drawn with. So:
 *
 *  - the head is exactly as big after a resize as before it, whatever the factor and whichever axis;
 *  - the vertices are generated, not moved, so repeated resizes cannot accumulate drift;
 *  - a resize by 1.0 reproduces the original stroke bit for bit;
 *  - anything that is not one of these figures is left to the plain affine path, unchanged.
 *
 * A head is held to its shaft ([FigureGeometry.HEAD_MAX_SHARE_OF_SHAFT]), so shrinking an arrow until
 * the head would swallow the shaft stops at a figure that still reads as an arrow, and the opening
 * angle is clamped to a sane V so a squashed stretch cannot fold the barbs onto the shaft.
 *
 * Pure — no Android types, no view — so the arithmetic is unit-tested on the JVM ([FigureRedrawTest]).
 */
object FigureRedraw {

    /** Vertices closer than this are the same vertex; `ShapeBuilder` repeats a tip exactly. */
    private const val SAME_POINT_PT = 1e-3

    /** A barb shorter than this is noise, not a head. */
    private const val MIN_BARB_PT = 2.0

    /** How far apart two barbs of one head may be (relative) and still be one V. */
    private const val BARB_LENGTH_TOLERANCE = 0.10

    /** The V's opening half-angle survives a stretch inside this range; outside it is clamped. */
    private const val MIN_HALF_ANGLE_DEG = 12.0
    private const val MAX_HALF_ANGLE_DEG = 45.0

    /** A point of the figure, independent of the stroke vertex it came from. */
    private data class P(val x: Double, val y: Double) {
        fun moved(sx: Double, sy: Double, dx: Double, dy: Double) = P(x * sx + dx, y * sy + dy)
    }

    /**
     * A figure read back from its vertices, able to re-emit itself at a new size.
     *
     * [widthPt] is the stroke's own width, carried through untouched: a figure resized is longer, not
     * thicker (`docs/stylus.md` → *A figure is resized, not thickened*).
     */
    sealed interface Figure {
        /** The vertices at the affine `sx`/`sy` scale and `dx`/`dy` shift. */
        fun redraw(sx: Double, sy: Double, dx: Double, dy: Double): List<StrokePoint>
    }

    /** One head, at [tip], pointing away from [tail]. */
    private class Arrow(
        private val tail: P,
        private val tip: P,
        private val headPt: Double,
        private val halfAngleDeg: Double,
        private val widthPt: Double,
    ) : Figure {
        override fun redraw(sx: Double, sy: Double, dx: Double, dy: Double): List<StrokePoint> {
            val newTail = tail.moved(sx, sy, dx, dy)
            val newTip = tip.moved(sx, sy, dx, dy)
            if (same(newTail, newTip)) return line(newTail, newTip, widthPt)
            return FigureGeometry.arrowWith(
                tailX = newTail.x, tailY = newTail.y,
                tipX = newTip.x, tipY = newTip.y,
                widthPt = widthPt,
                headPt = headFor(headPt, newTail, newTip),
                halfAngleDeg = halfAngleDeg,
            )
        }
    }

    /** A head at each end of one shaft. */
    private class DoubleEnded(
        private val tail: P,
        private val tip: P,
        private val headPt: Double,
        private val halfAngleDeg: Double,
        private val widthPt: Double,
    ) : Figure {
        override fun redraw(sx: Double, sy: Double, dx: Double, dy: Double): List<StrokePoint> {
            val newTail = tail.moved(sx, sy, dx, dy)
            val newTip = tip.moved(sx, sy, dx, dy)
            if (same(newTail, newTip)) return line(newTail, newTip, widthPt)
            return FigureGeometry.doubleArrowWith(
                tailX = newTail.x, tailY = newTail.y,
                tipX = newTip.x, tipY = newTip.y,
                widthPt = widthPt,
                headPt = headFor(headPt, newTail, newTip),
                halfAngleDeg = halfAngleDeg,
            )
        }
    }

    /** The coordinate diagram: two shafts from one origin, a head at each tip. */
    private class Axes(
        private val origin: P,
        private val xTip: P,
        private val yTip: P,
        private val xHeadPt: Double,
        private val yHeadPt: Double,
        private val halfAngleDeg: Double,
        private val widthPt: Double,
    ) : Figure {
        override fun redraw(sx: Double, sy: Double, dx: Double, dy: Double): List<StrokePoint> {
            val o = origin.moved(sx, sy, dx, dy)
            val x = xTip.moved(sx, sy, dx, dy)
            val y = yTip.moved(sx, sy, dx, dy)
            return FigureGeometry.axesWith(
                originX = o.x, originY = o.y,
                xTipX = x.x, xTipY = x.y,
                yTipX = y.x, yTipY = y.y,
                widthPt = widthPt,
                xHeadPt = if (same(o, x)) 0.0 else headFor(xHeadPt, o, x),
                yHeadPt = if (same(o, y)) 0.0 else headFor(yHeadPt, o, y),
                halfAngleDeg = halfAngleDeg,
            )
        }
    }

    /**
     * [points] resized by the affine `sx`/`sy`, `dx`/`dy` — as a re-emitted figure when the vertices
     * are one of the tools' headed shapes, and as the plain affine transform otherwise.
     *
     * The affine fallback is the path for every ordinary stroke, and for a headed figure whose stroke
     * carries per-vertex widths (a shape's stroke never does, but a hand-drawn arrow that happens to
     * look like one can): widening those points as a side effect of a resize would be a lie.
     */
    fun stretch(points: List<StrokePoint>, sx: Double, sy: Double, dx: Double, dy: Double): List<StrokePoint> {
        val figure = read(points)
        return figure?.redraw(sx, sy, dx, dy) ?: affine(points, sx, sy, dx, dy)
    }

    /** The figure [points] describe, or null when they are not one of the tools' headed shapes. */
    fun read(points: List<StrokePoint>): Figure? {
        if (points.size < 5) return null
        val width = points.first().width
        // One width for the whole stroke, or it is not what `ShapeBuilder` wrote (a figure is drawn
        // with a single constant width and stored uniformly).
        if (points.any { abs(it.width - width) > 1e-6 }) return null
        val v = points.map { P(it.x, it.y) }
        return when (v.size) {
            9 -> axes(v, width)
            8 -> doubleEnded(v, width)
            5 -> singleArrow(v, width)
            else -> null
        }
    }

    /**
     * Five vertices: `[tail, tip, barb, tip, barb]` as [FigureGeometry.arrowWith] writes them, or the
     * same head **at the stroke's start** (`[barb, tip, barb, tip, tail]`) — which is what the axis tool
     * writes for a drag with no width, and what a hand-authored reversed arrow looks like.
     */
    private fun singleArrow(v: List<P>, width: Double): Figure? {
        val headAtTip = same(v[1], v[3]) && isHead(v[1], v[2], v[4])
        val headAtTail = same(v[1], v[3]) && isHead(v[1], v[0], v[2])
        return when {
            headAtTip && !same(v[0], v[1]) ->
                Arrow(v[0], v[1], barbPt(v[1], v[2]), halfAngle(v[1], v[0], v[2]), width)
            headAtTail && !same(v[4], v[1]) ->
                Arrow(v[4], v[1], barbPt(v[1], v[0]), halfAngle(v[1], v[4], v[0]), width)
            else -> null
        }
    }

    /** Eight vertices: `[b, tail, b, tail, tip, b, tip, b]` — a head at each end of one shaft. */
    private fun doubleEnded(v: List<P>, width: Double): Figure? {
        val tailHead = same(v[1], v[3]) && isHead(v[1], v[0], v[2])
        val tipHead = same(v[4], v[6]) && isHead(v[4], v[5], v[7])
        if (!tailHead || !tipHead || same(v[1], v[4])) return null
        return DoubleEnded(
            tail = v[1],
            tip = v[4],
            headPt = barbPt(v[4], v[5]),
            halfAngleDeg = halfAngle(v[4], v[1], v[5]),
            widthPt = width,
        )
    }

    /** Nine vertices: `[b, yTip, b, yTip, origin, xTip, b, xTip, b]` — the coordinate diagram. */
    private fun axes(v: List<P>, width: Double): Figure? {
        val yHead = same(v[1], v[3]) && isHead(v[1], v[0], v[2])
        val xHead = same(v[5], v[7]) && isHead(v[5], v[6], v[8])
        if (!yHead || !xHead) return null
        val origin = v[4]
        // The origin must sit behind both tips: the heads are what the figure ends with.
        if (same(origin, v[1]) || same(origin, v[5])) return null
        return Axes(
            origin = origin,
            xTip = v[5],
            yTip = v[1],
            xHeadPt = barbPt(v[5], v[6]),
            yHeadPt = barbPt(v[1], v[0]),
            halfAngleDeg = halfAngle(v[5], origin, v[6]),
            widthPt = width,
        )
    }

    /** The plain affine transform: every vertex moved, every width kept. */
    private fun affine(points: List<StrokePoint>, sx: Double, sy: Double, dx: Double, dy: Double) =
        points.map { StrokePoint(it.x * sx + dx, it.y * sy + dy, it.width) }

    /**
     * [drawn] pt of head, held to the shaft the resize produced — the one clamp that keeps a heavily
     * shrunk arrow reading as an arrow.
     */
    private fun headFor(drawn: Double, tail: P, tip: P): Double {
        val shaft = hypot(tip.x - tail.x, tip.y - tail.y)
        return FigureGeometry.clampedHead(drawn, shaft)
    }

    /** The angle between a barb and its shaft, in degrees, clamped to a V that still reads as a head. */
    private fun halfAngle(tip: P, tail: P, barb: P): Double {
        val sx = tail.x - tip.x
        val sy = tail.y - tip.y
        val bx = barb.x - tip.x
        val by = barb.y - tip.y
        val lengths = hypot(sx, sy) * hypot(bx, by)
        if (lengths <= 0.0) return FigureGeometry.HALF_ANGLE_DEG
        val cos = ((sx * bx + sy * by) / lengths).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos)).coerceIn(MIN_HALF_ANGLE_DEG, MAX_HALF_ANGLE_DEG)
    }

    /** A vertex's length from [tip] — the head size a drawn figure carries. */
    private fun barbPt(tip: P, barb: P): Double = hypot(barb.x - tip.x, barb.y - tip.y)

    /** A real V: two barbs of nearly equal length meeting at [tip], neither folded onto the other. */
    private fun isHead(tip: P, barbA: P, barbB: P): Boolean {
        val la = barbPt(tip, barbA)
        val lb = barbPt(tip, barbB)
        if (la < MIN_BARB_PT || lb < MIN_BARB_PT) return false
        if (abs(la - lb) > BARB_LENGTH_TOLERANCE * maxOf(la, lb)) return false
        val angA = atan2(barbA.y - tip.y, barbA.x - tip.x)
        val angB = atan2(barbB.y - tip.y, barbB.x - tip.x)
        var spread = abs(angA - angB)
        if (spread > PI) spread = 2 * PI - spread
        return spread > MIN_HALF_ANGLE_DEG * 2 * PI / 180.0 // the barbs are apart, not one line
    }

    private fun same(a: P, b: P): Boolean = abs(a.x - b.x) <= SAME_POINT_PT && abs(a.y - b.y) <= SAME_POINT_PT

    /** A figure collapsed to a dot by its own drag: two vertices are all that is left of it. */
    private fun line(a: P, b: P, widthPt: Double): List<StrokePoint> =
        listOf(StrokePoint(a.x, a.y, widthPt), StrokePoint(b.x, b.y, widthPt))
}
