package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The **arc** half of the shape recogniser: an *open* stroke that lies on a circle, but not all the
 * way round, is rebuilt as the clean stretch of that circle between its two ends.
 *
 * A circle fit is only three unknowns, so it is fitted by least squares (Kåsa's algebraic fit, which
 * solves `x² + y² + Dx + Ey + F = 0` from the normal equations) rather than by an iterative
 * approach: exact for a perfect arc, and with no starting guess to converge from.
 *
 * This is a class upstream does not have — desktop Xournal++ leaves a curved stroke as drawn — and
 * it is the riskiest of the added ones, because a handwritten **C**, **e** or **s** is an arc. Three
 * gates keep handwriting out, and they are deliberately conservative:
 * - the stroke must be **open** (a closed loop is a circle or an ellipse, never an arc);
 * - it must sweep **at least [MIN_SWEEP_DEG]°** — a short curved flick is not an arc;
 * - it must be **large** ([MIN_CHORD_PT] across its chord), where handwriting is normally well
 *   under that, and lie on the circle to within [MAX_MEAN_RESIDUAL] of the radius.
 *
 * Pure and Android-free, so it unit-tests on the JVM.
 */
internal object ArcRecognizer {

    /** Narrowest sweep accepted. Below it the stroke is a curved line the line fitter should own. */
    private const val MIN_SWEEP_DEG = 100.0

    /** Widest sweep accepted — beyond this the stroke nearly closes, so it belongs to the circle. */
    private const val MAX_SWEEP_DEG = 300.0

    /** Shortest end-to-end chord (pt) accepted; keeps letter-sized curves out. */
    private const val MIN_CHORD_PT = 60.0

    /** Mean distance from the fitted circle, as a fraction of its radius. */
    private const val MAX_MEAN_RESIDUAL = 0.06

    /** Furthest a fitted radius may stray across the stroke, relative to itself — rejects a spiral. */
    private const val MAX_RADIUS_SPREAD = 0.35

    /** Samples the rebuilt arc is drawn with, at least. */
    private const val MIN_SAMPLES = 16

    /**
     * The clean arc standing in for [points], or **null** when the stroke is not a large, open,
     * circular stretch. Every emitted vertex carries [widthPt]; the arc is *not* closed.
     */
    fun recognize(points: List<StrokePoint>, widthPt: Double): List<StrokePoint>? {
        if (points.size < 8) return null
        val chord = hypot(points.last().x - points.first().x, points.last().y - points.first().y)
        if (chord < MIN_CHORD_PT) return null

        val fit = fitCircle(points) ?: return null
        val radius = fit.radius
        if (radius <= 0.0) return null

        // Residual: how far the samples stray from the fitted circle, relative to its size.
        var sum = 0.0
        var smallest = Double.MAX_VALUE
        var largest = 0.0
        for (p in points) {
            val d = hypot(p.x - fit.cx, p.y - fit.cy)
            sum += abs(d - radius)
            if (d < smallest) smallest = d
            if (d > largest) largest = d
        }
        if (sum / points.size / radius > MAX_MEAN_RESIDUAL) return null
        if ((largest - smallest) / radius > MAX_RADIUS_SPREAD) return null

        val sweep = sweep(points, fit.cx, fit.cy)
        val degrees = abs(sweep) * 180 / PI
        if (degrees < MIN_SWEEP_DEG || degrees > MAX_SWEEP_DEG) return null

        return arc(points, fit.cx, fit.cy, radius, sweep, widthPt)
    }

    /** Least-squares circle through [points]: its centre and radius, or null when the fit is degenerate. */
    private fun fitCircle(points: List<StrokePoint>): Fit? {
        // Normal equations of x² + y² + Dx + Ey + F = 0, taken about the centroid so the numbers
        // stay small on a page-sized stroke.
        val n = points.size.toDouble()
        val mx = points.sumOf { it.x } / n
        val my = points.sumOf { it.y } / n

        var suu = 0.0
        var svv = 0.0
        var suv = 0.0
        var suuu = 0.0
        var svvv = 0.0
        var suvv = 0.0
        var svuu = 0.0
        for (p in points) {
            val u = p.x - mx
            val v = p.y - my
            suu += u * u
            svv += v * v
            suv += u * v
            suuu += u * u * u
            svvv += v * v * v
            suvv += u * v * v
            svuu += v * u * u
        }

        // 2x2 system in (uc, vc): the centre offset from the centroid.
        val det = suu * svv - suv * suv
        if (abs(det) < 1e-9) return null
        val b1 = (suuu + suvv) / 2
        val b2 = (svvv + svuu) / 2
        val uc = (b1 * svv - b2 * suv) / det
        val vc = (b2 * suu - b1 * suv) / det

        val cx = mx + uc
        val cy = my + vc
        val radius = sqrtOf(uc * uc + vc * vc + (suu + svv) / n)
        return Fit(cx, cy, radius)
    }

    /** Squared-root helper, kept apart so the fit above reads as maths. */
    private fun sqrtOf(v: Double): Double = kotlin.math.sqrt(if (v < 0) 0.0 else v)

    /**
     * Signed angle swept about ([cx], [cy]) from the first sample to the last, following the
     * stroke — so a top-to-bottom sweep and its reverse come out with opposite signs.
     */
    private fun sweep(points: List<StrokePoint>, cx: Double, cy: Double): Double {
        var total = 0.0
        var prev = atan2(points[0].y - cy, points[0].x - cx)
        for (i in 1 until points.size) {
            val angle = atan2(points[i].y - cy, points[i].x - cx)
            var delta = angle - prev
            // Shortest way round: the branch cut is not a jump in the stroke.
            while (delta > PI) delta -= 2 * PI
            while (delta < -PI) delta += 2 * PI
            total += delta
            prev = angle
        }
        return total
    }

    /** The fitted arc between the stroke's own end angles, sampled in the direction it was drawn. */
    private fun arc(
        points: List<StrokePoint>,
        cx: Double,
        cy: Double,
        radius: Double,
        sweep: Double,
        widthPt: Double,
    ): List<StrokePoint> {
        val start = atan2(points.first().y - cy, points.first().x - cx)
        val samples = maxOf(MIN_SAMPLES, (abs(sweep) * radius / 3).toInt())
        return (0..samples).map { i ->
            val a = start + sweep * i / samples
            StrokePoint(cx + radius * cos(a), cy + radius * sin(a), widthPt)
        }
    }

    /** A fitted circle: centre and radius. */
    private data class Fit(val cx: Double, val cy: Double, val radius: Double)
}
