package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The **arrow** half of the shape recogniser: one stroke drawn as a straight shaft with two short
 * barbs at its point becomes `tail → tip → barb → tip → barb`.
 *
 * The emitted list is a single stroke, and that is not a compromise: an arrow drawn without lifting
 * the pen *is* that path — the shaft, then out to one barb, back through the point, out to the other.
 * Re-tracing the last two segments is invisible at stroke width, so the result looks exactly like an
 * arrow tool's arrow while staying one ordinary constant-width stroke that round-trips through
 * `.xopp` untouched.
 *
 * The geometry alone has to be enough, because nothing distinguishes "an arrow" from "a line with a
 * tick" except shape, so every condition below is a rejection the user would agree with:
 * - a long **straight shaft** ([MIN_SHAFT_PT], [MAX_SHAFT_BOW]) from the first sample to the point
 *   furthest from it;
 * - **two barbs**, one on each side of the shaft ([MIN_BARB_FRACTION]..[MAX_BARB_FRACTION] of its
 *   length, within [MAX_BARB_RATIO] of each other), both reaching *back* from the tip;
 * - and the stroke must **return to the tip** between them, which is what separates an arrow from a
 *   V, a check mark or a stray flick.
 *
 * Unlike the curve fits, this one is measured on the **raw** samples rather than the tremor-filtered
 * copy: the arrow's point is a corner where three legs meet, the filter's clamp rounds it slightly,
 * and a rounded point is no longer the sample furthest from the tail — the whole reading would come
 * off the wrong vertex. Jitter cannot hurt the tests here anyway; the shaft's bow allowance is a
 * tenth of its length, far above a tremor of a point or two.
 *
 * Pure and Android-free ([ShapeRecognizerTest]).
 */
internal object ArrowRecognizer {

    /** Shortest shaft (pt) accepted, so a small tick with a hook is never read as an arrow. */
    private const val MIN_SHAFT_PT = 60.0

    /** How far the shaft's samples may bow away from the straight line, as a fraction of its length. */
    private const val MAX_SHAFT_BOW = 0.10

    /** Barb length, as a fraction of the shaft's. Shorter reads as a thickening, longer as a V. */
    private const val MIN_BARB_FRACTION = 0.10
    private const val MAX_BARB_FRACTION = 0.55

    /** How unequal the two barbs may be; a hand-drawn arrow is never symmetrical. */
    private const val MAX_BARB_RATIO = 2.5

    /**
     * How far *beyond* the tip (a fraction of the shaft) a barb's far end may sit. Barbs sweep back
     * from the point, so a barb sticking out past it is a spike, not a barb; the length bounds above
     * already keep the sweep sane, so this only has to catch "past the point".
     */
    private const val BARB_BEYOND_MAX = 0.12

    /** The stroke has to come back within this fraction of the shaft to count as "through the tip". */
    private const val RETURN_TO_TIP = 0.12

    /** Fewest samples after the tip for a barb pair to exist at all. */
    private const val MIN_TAIL_SAMPLES = 4

    /**
     * The arrow standing in for [points], or **null** when the stroke is not a shaft with two barbs.
     * Every emitted vertex carries [widthPt].
     */
    fun recognize(points: List<StrokePoint>, widthPt: Double): List<StrokePoint>? {
        if (points.size < 8) return null

        val start = points.first()
        // The tip is the sample furthest from the tail — an arrow's point, whatever the hand did
        // after it. A closed stroke has no single such point relative to its start only by luck;
        // closed shapes are taken by the polygon/circle paths before this runs.
        var tipIndex = 0
        var bestSq = -1.0
        for (i in points.indices) {
            val d = dist2(start, points[i])
            if (d > bestSq) {
                bestSq = d
                tipIndex = i
            }
        }
        val shaft = sqrt(bestSq)
        if (shaft < MIN_SHAFT_PT) return null
        if (points.size - tipIndex < MIN_TAIL_SAMPLES) return null

        val tip = points[tipIndex]
        val ux = (tip.x - start.x) / shaft
        val uy = (tip.y - start.y) / shaft
        if (!straightShaft(points, tipIndex, start, ux, uy, shaft)) return null

        // Perpendicular offset of every sample after the tip: the barbs are the extremes on each side.
        var plus: StrokePoint? = null
        var minus: StrokePoint? = null
        var plusDist = -1.0
        var minusDist = -1.0
        var returned = false
        for (i in tipIndex + 1 until points.size) {
            val p = points[i]
            val dx = p.x - tip.x
            val dy = p.y - tip.y
            val across = -dx * uy + dy * ux
            val along = dx * ux + dy * uy
            if (hypot(dx, dy) <= RETURN_TO_TIP * shaft) returned = true
            if (along > BARB_BEYOND_MAX * shaft) continue
            if (across > plusDist) {
                plusDist = across
                plus = p
            }
            if (-across > minusDist) {
                minusDist = -across
                minus = p
            }
        }

        val barbA = plus ?: return null
        val barbB = minus ?: return null
        if (!returned) return null

        val lenA = hypot(barbA.x - tip.x, barbA.y - tip.y)
        val lenB = hypot(barbB.x - tip.x, barbB.y - tip.y)
        if (lenA < MIN_BARB_FRACTION * shaft || lenA > MAX_BARB_FRACTION * shaft) return null
        if (lenB < MIN_BARB_FRACTION * shaft || lenB > MAX_BARB_FRACTION * shaft) return null
        val ratio = lenA / lenB
        if (ratio > MAX_BARB_RATIO || ratio < 1 / MAX_BARB_RATIO) return null

        return listOf(
            StrokePoint(start.x, start.y, widthPt),
            StrokePoint(tip.x, tip.y, widthPt),
            StrokePoint(barbA.x, barbA.y, widthPt),
            StrokePoint(tip.x, tip.y, widthPt),
            StrokePoint(barbB.x, barbB.y, widthPt),
        )
    }

    /** True when every sample up to the tip stays close to the shaft's straight line. */
    private fun straightShaft(
        points: List<StrokePoint>,
        tipIndex: Int,
        start: StrokePoint,
        ux: Double,
        uy: Double,
        shaft: Double,
    ): Boolean {
        for (i in 1 until tipIndex) {
            val p = points[i]
            val dx = p.x - start.x
            val dy = p.y - start.y
            val across = abs(-dx * uy + dy * ux)
            if (across > MAX_SHAFT_BOW * shaft) return false
        }
        return true
    }

    /** Squared distance between two samples. */
    private fun dist2(a: StrokePoint, b: StrokePoint): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }
}
