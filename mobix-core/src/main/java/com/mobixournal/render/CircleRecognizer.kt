package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The circle half of the shape recognizer: an inertia roundness test followed by a
 * length-weighted radial-residual score, and a clean circle rebuilt from the fit.
 *
 * Ported from desktop Xournal++'s `CircleRecognizer`
 * (`src/core/control/shaperecognizer/CircleRecognizer.cpp`, GPL-2.0-or-later, © the Xournal++
 * authors). Note that this recognises **circles, not ellipses** — an oval stroke is left alone,
 * exactly as on the desktop.
 *
 * Android-free and pure, so it unit-tests on the JVM.
 */
internal object CircleRecognizer {

    /**
     * How round the length distribution has to be before the stroke is even a circle candidate
     * (upstream `CIRCLE_MIN_DET`); 1.0 is a perfect circle, 0 a perfectly straight stroke.
     */
    private const val MIN_DET = 0.95

    /**
     * Largest accepted radial residual (upstream `CIRCLE_MAX_SCORE`), as a fraction of the fitted
     * radius. A hand-drawn circle of a given wobble passes at any size, which is why the score is
     * normalised rather than an absolute distance.
     */
    private const val MAX_SCORE = 0.10

    /** Fewest samples the rebuilt circle is drawn with, so a tiny one still looks smooth. */
    private const val MIN_SAMPLES = 24

    /**
     * A clean circle standing in for [points], or **null** when the stroke isn't round enough. Every
     * emitted vertex carries [widthPt], and the list is closed by repeating its first point.
     */
    fun recognize(points: List<StrokePoint>, widthPt: Double): List<StrokePoint>? {
        val inertia = Inertia().also { it.calc(points) }
        if (inertia.det <= MIN_DET) return null
        if (score(points, inertia) >= MAX_SCORE) return null
        return circle(inertia, widthPt)
    }

    /** A circle of the fitted centre and radius, sampled all the way round and closed. */
    private fun circle(inertia: Inertia, widthPt: Double): List<StrokePoint> {
        val radius = inertia.rad
        val samples = maxOf(MIN_SAMPLES, (2 * radius).toInt())
        val cx = inertia.centerX
        val cy = inertia.centerY
        return (0..samples).map { i ->
            val a = 2 * PI * i / samples
            StrokePoint(cx + radius * cos(a), cy + radius * sin(a), widthPt)
        }
    }

    /**
     * Mean absolute departure from the fitted circle, weighted by how much of the stroke lies in
     * each stretch and divided by the radius — so it is size-independent. 0 is a perfect circle.
     */
    private fun score(points: List<StrokePoint>, inertia: Inertia): Double {
        val radius = inertia.rad
        val divisor = inertia.amount * radius
        if (divisor == 0.0) return 0.0

        val cx = inertia.centerX
        val cy = inertia.centerY
        var sum = 0.0
        for (i in 0 until points.size - 1) {
            val dm = hypot(points[i + 1].x - points[i].x, points[i + 1].y - points[i].y)
            val deltaR = hypot(points[i].x - cx, points[i].y - cy) - radius
            sum += dm * abs(deltaR)
        }
        return sum / divisor
    }
}
