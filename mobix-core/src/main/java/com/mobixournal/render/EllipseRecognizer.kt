package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The **ellipse** half of the shape recogniser: a closed stroke whose second moments are clearly
 * unequal becomes a clean ellipse of the same centre, axes and tilt.
 *
 * This is the class upstream deliberately does *not* have — desktop Xournal++ only rebuilds circles
 * — and the reason is the old hand-rolled recogniser's failure: fitting an ellipse to a bounding box
 * turns any scribbled loop into an oval. The fit here is not a bounding box. The principal axes come
 * from the stroke's own covariance, and the shape is then re-scored against the samples, so a
 * rounded rectangle or a squiggle that does not actually lie on an ellipse is rejected — the
 * polygon path has already taken the rectangles before this runs.
 *
 * Rejection is deliberate and tight, because a wrong snap destroys what the user drew:
 * - the stroke must **close** (last sample back near the first), or it is an *arc*, not an ellipse;
 * - the axes must differ enough to be an ellipse at all ([MIN_ASPECT]) — a round stroke belongs to
 *   [CircleRecognizer], which is stricter and already ran;
 * - and not so much that the shape is really a line ([MAX_ASPECT]);
 * - every sample must sit near the fitted ellipse ([MAX_MEAN_SCORE], [MAX_SCORE]).
 *
 * Pure and Android-free, so it unit-tests on the JVM.
 */
internal object EllipseRecognizer {

    /** Smallest long/short axis ratio accepted; below it the shape is round and the circle path owns it. */
    private const val MIN_ASPECT = 1.25

    /** Largest long/short axis ratio accepted; above it the stroke is too flat to be an oval. */
    private const val MAX_ASPECT = 6.0

    /** Distance between the first and last sample, as a fraction of the bounding diagonal, that counts as closed. */
    private const val CLOSE_FRACTION = 0.30

    /**
     * Mean radial residual accepted, in units of the normalised radius (`(u/a)² + (v/b)²` = 1 on the
     * rim). 0.10 tolerates a hand-drawn oval; a box with rounded corners does not get in.
     */
    private const val MAX_MEAN_SCORE = 0.10

    /** Worst single-sample residual accepted, so one stray corner cannot be averaged away. */
    private const val MAX_SCORE = 0.45

    /** Fewest samples the rebuilt ellipse is drawn with, so a small one still looks smooth. */
    private const val MIN_SAMPLES = 32

    /**
     * A clean ellipse standing in for [points], or **null** when the stroke is not a closed, clearly
     * oval loop. Every emitted vertex carries [widthPt], and the list is closed by repeating its
     * first point.
     */
    fun recognize(points: List<StrokePoint>, widthPt: Double): List<StrokePoint>? {
        if (points.size < 8) return null
        if (!closed(points)) return null

        val n = points.size.toDouble()
        val cx = points.sumOf { it.x } / n
        val cy = points.sumOf { it.y } / n

        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (p in points) {
            val dx = p.x - cx
            val dy = p.y - cy
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        sxx /= n
        syy /= n
        sxy /= n

        // Principal axes: the tilt is the eigenvector angle, the two moments become the squared
        // semi-axes. A stroke sampled all the way round has E[u²] = a²/2 and E[v²] = b²/2.
        val theta = 0.5 * atan2(2 * sxy, sxx - syy)
        val mid = (sxx + syy) / 2
        val gap = sqrt(((sxx - syy) / 2) * ((sxx - syy) / 2) + sxy * sxy)
        val a = sqrt(2 * (mid + gap))
        val b = sqrt(2 * (mid - gap))
        if (b <= 0.0 || a <= 0.0) return null

        val aspect = a / b
        if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) return null

        if (!onRim(points, cx, cy, a, b, theta)) return null
        return ellipse(cx, cy, a, b, theta, widthPt)
    }

    /** True when the last sample comes back near the first, relative to the stroke's own size. */
    private fun closed(points: List<StrokePoint>): Boolean {
        var minX = points[0].x
        var maxX = minX
        var minY = points[0].y
        var maxY = minY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val diagonal = hypot(maxX - minX, maxY - minY)
        val gap = hypot(points.last().x - points.first().x, points.last().y - points.first().y)
        return gap <= CLOSE_FRACTION * diagonal
    }

    /** True when every sample sits on the fitted rim: the mean deviation is small *and* none is wild. */
    private fun onRim(
        points: List<StrokePoint>,
        cx: Double,
        cy: Double,
        a: Double,
        b: Double,
        theta: Double,
    ): Boolean {
        val cosT = cos(theta)
        val sinT = sin(theta)
        var sum = 0.0
        var worst = 0.0
        for (p in points) {
            val dx = p.x - cx
            val dy = p.y - cy
            val u = dx * cosT + dy * sinT
            val v = -dx * sinT + dy * cosT
            val residual = abs((u / a) * (u / a) + (v / b) * (v / b) - 1)
            sum += residual
            if (residual > worst) worst = residual
        }
        return sum / points.size <= MAX_MEAN_SCORE && worst <= MAX_SCORE
    }

    /** The fitted ellipse, sampled all the way round and closed. */
    private fun ellipse(
        cx: Double,
        cy: Double,
        a: Double,
        b: Double,
        theta: Double,
        widthPt: Double,
    ): List<StrokePoint> {
        val samples = maxOf(MIN_SAMPLES, (2 * (a + b)).toInt())
        val cosT = cos(theta)
        val sinT = sin(theta)
        return (0..samples).map { i ->
            val t = 2 * PI * i / samples
            val u = a * cos(t)
            val v = b * sin(t)
            StrokePoint(cx + u * cosT - v * sinT, cy + u * sinT + v * cosT, widthPt)
        }
    }
}
