package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The arc-length moment accumulator the shape recognizer is built on: the 0th and 1st moments of a
 * polyline's length measure, from which a best-fit line's centre, direction and residual all fall
 * out in closed form.
 *
 * Ported from desktop Xournal++'s `Inertia` (`src/core/control/shaperecognizer/Inertia.cpp`,
 * GPL-2.0-or-later, © the Xournal++ authors) so our recognizer classifies the same stroke the same
 * way theirs does. The fields are deliberately left as the raw running sums the C++ keeps, since
 * every derived quantity is a cheap closed form over them.
 *
 * Weights are lengths, not samples: a densely sampled slow bit of stroke counts for less than a
 * sparsely sampled fast one, which is what makes the fits independent of sampling rate.
 *
 * One convention worth knowing, because it is not the obvious one: every moment is armed on a
 * segment's **start** point, so the "centre" is the length-weighted mean of the segment starts and
 * sits half a sample behind the geometric midpoint of a uniformly sampled run. The whole recognizer
 * is built on that starting point, so it is kept exactly as upstream has it.
 *
 * Android-free and pure, so it unit-tests on the JVM with the recognizer.
 */
internal class Inertia {

    private var mass = 0.0
    private var sx = 0.0
    private var sy = 0.0
    private var sxx = 0.0
    private var sxy = 0.0
    private var syy = 0.0

    /** Total length accumulated — the recognizer's "how much stroke is in this fit". */
    val amount: Double get() = mass

    /** An independent copy: the fitter grows a candidate piece from a shared starting fit. */
    fun copy(): Inertia = Inertia().also {
        it.mass = mass
        it.sx = sx
        it.sy = sy
        it.sxx = sxx
        it.sxy = sxy
        it.syy = syy
    }

    /** Centre of mass of the polyline. */
    val centerX: Double get() = sx / mass

    /** Centre of mass of the polyline. */
    val centerY: Double get() = sy / mass

    /** Second moment about the centre, x direction (0 for a fit with no length). */
    val xx: Double get() = if (mass <= 0.0) 0.0 else (sxx - sx * sx / mass) / mass

    /** Second moment about the centre, cross term — the one that tilts the principal axis. */
    val xy: Double get() = if (mass <= 0.0) 0.0 else (sxy - sx * sy / mass) / mass

    /** Second moment about the centre, y direction (0 for a fit with no length). */
    val yy: Double get() = if (mass <= 0.0) 0.0 else (syy - sy * sy / mass) / mass

    /** Root-mean-square spread of the polyline about its centre — a fitted circle's radius. */
    val rad: Double get() {
        val sum = xx + yy
        return if (sum <= 0.0) 0.0 else sqrt(sum)
    }

    /**
     * How two-dimensional the length distribution is: **0** for a perfectly straight polyline and
     * **1** for a perfectly circular one. This one number is what the recognizer thresholds to
     * decide "this piece is straight" and "this whole stroke is a circle", so it is the single most
     * load-bearing value in the port.
     */
    val det: Double get() {
        if (mass <= 0.0) return 0.0
        val ixx = xx
        val iyy = yy
        val sum = ixx + iyy
        if (sum <= 0.0) return 0.0
        return 4.0 * (ixx * iyy - xy * xy) / (sum * sum)
    }

    /** Fold the segment [p1]–[p2] in [coef] times (1 to add it, −1 to take it back out again). */
    fun increase(p1: StrokePoint, p2: StrokePoint, coef: Int) {
        val dm = coef * hypot(p2.x - p1.x, p2.y - p1.y)
        mass += dm
        sx += dm * p1.x
        sy += dm * p1.y
        sxx += dm * p1.x * p1.x
        syy += dm * p1.y * p1.y
        sxy += dm * p1.x * p1.y
    }

    /** Reset, then accumulate [points] over the inclusive index range [from]..[to]. */
    fun calc(points: List<StrokePoint>, from: Int = 0, to: Int = points.size - 1) {
        mass = 0.0
        sx = 0.0
        sy = 0.0
        sxx = 0.0
        sxy = 0.0
        syy = 0.0
        for (i in from until to) increase(points[i], points[i + 1], 1)
    }
}
