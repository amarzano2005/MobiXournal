package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot

/**
 * How a resize stretches a stroke: the shaft **lengthens**, the arrowhead does not grow.
 *
 * A stroke is a bare point list, so the affine resize that lengthens an arrow's shaft also scales
 * the barbs of its head — drag an arrow bigger and it turns into a bigger arrow instead of a longer
 * one. An arrowhead is a decoration, not part of the figure: technical drawing packages lengthen
 * the shaft and leave the head at the size it was drawn. This finds the V-shaped heads
 * `ShapeBuilder` writes (a repeated tip with one barb either side), scales the whole point list, and
 * then puts each barb back at its original distance from the moved tip, aimed along the stretched
 * shaft.
 *
 * Only the resize gesture uses this. Re-homing ink onto another page (`SelectionOps.affine`, the
 * `scaleWidth = true` route) keeps scaling everything, heads included, because there the whole
 * drawing is being rescaled to the new paper.
 */
internal object StrokeStretch {

    /** Points closer than this are the same point; `ShapeBuilder` repeats a tip exactly. */
    private const val SAME_POINT_PT = 1e-3

    /** A head shorter than this is noise, not a decoration worth protecting. */
    private const val MIN_BARB_PT = 2.0

    /**
     * How far apart the two barbs of one head may be (relative) and still be one V.
     *
     * `ShapeBuilder` draws them equal, but the committed stroke is not always the builder's own: the
     * endpoint snap can move the stroke's **last** point — which on an arrow is a barb — and a head
     * that stops being recognised is a head that starts growing with the shaft again, which is the
     * exact bug this class exists to prevent. Wide enough to survive that, narrow enough that the
     * shaft and a barb are never mistaken for a pair (they are an order of magnitude apart).
     */
    private const val BARB_LENGTH_TOLERANCE = 0.10

    /** Below this cross product (relative to the barb lengths) the "V" is a straight line. */
    private const val MIN_V_OPENING = 0.05

    /** One arrowhead: the tip vertex and the two barbs it was drawn with. */
    private data class Head(val tip: Int, val barbA: Int, val barbB: Int)

    /**
     * Scale [points] by (`sx`, `sy`) and shift by (`dx`, `dy`) exactly as the affine resize does,
     * except that every recognised arrowhead moves with its tip instead of growing with it.
     *
     * @param points Original stroke vertices.
     * @param sx Horizontal scale factor (positive).
     * @param sy Vertical scale factor (positive).
     * @param dx Translation X in points.
     * @param dy Translation Y in points.
     * @return The stretched vertices, same order and count as [points].
     */
    fun stretch(
        points: List<StrokePoint>,
        sx: Double,
        sy: Double,
        dx: Double,
        dy: Double,
    ): List<StrokePoint> {
        if (points.isEmpty()) return points
        val out = points.mapTo(ArrayList(points.size)) {
            StrokePoint(it.x * sx + dx, it.y * sy + dy, it.width)
        }
        for (head in heads(points)) {
            val tipOld = points[head.tip]
            val tipNew = out[head.tip]
            out[head.barbA] = rigidBarb(tipOld, tipNew, points[head.barbA], sx, sy)
            out[head.barbB] = rigidBarb(tipOld, tipNew, points[head.barbB], sx, sy)
        }
        return out
    }

    /**
     * Move one barb to [tipNew], keeping the barb's length from the old tip and aiming it along the
     * direction the stretch gave it. A uniform scale therefore leaves the head bit-for-bit the
     * size it was; a one-axis stretch still turns the head with its shaft rather than flattening it.
     */
    private fun rigidBarb(
        tipOld: StrokePoint,
        tipNew: StrokePoint,
        barbOld: StrokePoint,
        sx: Double,
        sy: Double,
    ): StrokePoint {
        val vx = (barbOld.x - tipOld.x) * sx
        val vy = (barbOld.y - tipOld.y) * sy
        val stretched = hypot(vx, vy)
        val drawn = hypot(barbOld.x - tipOld.x, barbOld.y - tipOld.y)
        if (stretched < SAME_POINT_PT) {
            return StrokePoint(barbOld.x - tipOld.x + tipNew.x, barbOld.y - tipOld.y + tipNew.y, barbOld.width)
        }
        val k = drawn / stretched
        return StrokePoint(tipNew.x + vx * k, tipNew.y + vy * k, barbOld.width)
    }

    /** Every V-shaped head in [points], in order. */
    private fun heads(points: List<StrokePoint>): List<Head> {
        val found = ArrayList<Head>()
        var i = 0
        while (i + 3 < points.size) {
            val a = points[i]
            val b = points[i + 1]
            val c = points[i + 2]
            val d = points[i + 3]
            when {
                same(a, c) && isBarb(a, b, d) -> {
                    found += Head(tip = i, barbA = i + 1, barbB = i + 3)
                    i += 4
                }
                same(b, d) && isBarb(b, a, c) -> {
                    found += Head(tip = i + 1, barbA = i, barbB = i + 2)
                    i += 4
                }
                else -> i++
            }
        }
        return found
    }

    /** A real V: two barbs of equal length meeting at [tip], neither one folded onto the other. */
    private fun isBarb(tip: StrokePoint, barbA: StrokePoint, barbB: StrokePoint): Boolean {
        val ax = barbA.x - tip.x
        val ay = barbA.y - tip.y
        val bx = barbB.x - tip.x
        val by = barbB.y - tip.y
        val la = hypot(ax, ay)
        val lb = hypot(bx, by)
        if (la < MIN_BARB_PT || lb < MIN_BARB_PT) return false
        if (abs(la - lb) > BARB_LENGTH_TOLERANCE * maxOf(la, lb)) return false
        val cross = abs(ax * by - ay * bx)
        return cross > MIN_V_OPENING * la * lb
    }

    private fun same(a: StrokePoint, b: StrokePoint): Boolean =
        abs(a.x - b.x) <= SAME_POINT_PT && abs(a.y - b.y) <= SAME_POINT_PT
}
