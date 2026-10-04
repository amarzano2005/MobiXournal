package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One straight piece of an approximated stroke: the slice of the point array it was cut from
 * ([startpt]..[endpt]) plus the line that best fits it — centre, direction, the two furthest
 * extremes projected onto it, and the inertia radius used as a size scale in the tolerance tests.
 *
 * Ported from desktop Xournal++'s `RecoSegment` (`src/core/control/shaperecognizer/RecoSegment.cpp`,
 * GPL-2.0-or-later, © the Xournal++ authors). The fields are mutable because the polygon tests
 * rewrite them: `angle` is snapped to the shared rectangle angle, and `reversed` is set while
 * orienting a candidate polygon so every side runs the same way round.
 */
internal class RecoSegment {

    /** Index of the first point of this piece in the stroke's point array. */
    var startpt = 0

    /** Index of the last point of this piece. */
    var endpt = 0

    /** Centre of mass of the piece. */
    var xcenter = 0.0
    var ycenter = 0.0

    /** Direction of the fitted line, in radians. */
    var angle = 0.0

    /** `sqrt(3·(xx+yy))` of the piece — the fitted line's half-length, for a straight piece. */
    var radius = 0.0

    /** The furthest sample projected onto the fitted line, in the [angle] direction. */
    var x1 = 0.0
    var y1 = 0.0

    /** The furthest sample projected onto the fitted line, opposite [angle]. */
    var x2 = 0.0
    var y2 = 0.0

    /** Whether this piece runs from ([x2],[y2]) to ([x1],[y1]) rather than the other way. */
    var reversed = false

    /**
     * Fit a line to `points[start]..points[end]` from the already-accumulated [s]: its centre, its
     * direction (the principal axis of the inertia quadratic form, `tan 2θ = 2·xy/(xx−yy)`), and the
     * two endpoints the piece's extremes project onto.
     */
    fun calcSegmentGeometry(points: List<StrokePoint>, start: Int, end: Int, s: Inertia) {
        xcenter = s.centerX
        ycenter = s.centerY
        val a = s.xx
        val b = s.xy
        val c = s.yy

        angle = atan2(2 * b, a - c) / 2
        radius = sqrt(3 * (a + c))

        val cosA = cos(angle)
        val sinA = sin(angle)
        var lmin = 0.0
        var lmax = 0.0
        for (i in start..end) {
            val l = (points[i].x - xcenter) * cosA + (points[i].y - ycenter) * sinA
            if (l < lmin) lmin = l
            if (l > lmax) lmax = l
        }

        x1 = xcenter + lmin * cosA
        y1 = ycenter + lmin * sinA
        x2 = xcenter + lmax * cosA
        y2 = ycenter + lmax * sinA
    }

    /**
     * Where this piece's line meets [r2]'s — the clean corner the recognizer emits instead of the
     * wobbly vertex the user actually drew. The width is left at 0: the caller stamps the stroke's
     * width onto the finished point list.
     *
     * **Deliberate divergence from upstream:** parallel (or anti-parallel) pieces have no
     * intersection, and upstream divides by `sin(θ₂−θ₁)` regardless, producing an infinite or NaN
     * point that would be written straight into the `.xopp` file. We return `null` instead and the
     * caller treats the whole candidate as unrecognised.
     */
    fun calcEdgeIsect(r2: RecoSegment): StrokePoint? {
        val denominator = sin(r2.angle - angle)
        if (denominator == 0.0 || !denominator.isFinite()) return null
        val t = ((r2.xcenter - xcenter) * sin(r2.angle) - (r2.ycenter - ycenter) * cos(r2.angle)) /
            denominator
        val x = xcenter + t * cos(angle)
        val y = ycenter + t * sin(angle)
        if (!x.isFinite() || !y.isFinite()) return null
        return StrokePoint(x, y, 0.0)
    }
}
