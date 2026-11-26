package com.mobixournal.render

import com.mobixournal.format.model.Stroke
import kotlin.math.sqrt

/**
 * Pure geometry for the delete-stroke eraser: does the eraser disc touch a stroke? Kept free of
 * Android types so it's unit-testable; [DrawingSurfaceView] converts touches to page-local pt and
 * removes every hit stroke. A stroke is hit when the disc of the given radius reaches within the
 * stroke's own half-width of any of its segments (or its single point).
 */
object StrokeHitTester {

    /**
     * Shortest distance from point (px, py) to the segment (ax, ay)-(bx, by).
     * @param px Point X coordinate.
     * @param py Point Y coordinate.
     * @param ax Segment endpoint A X coordinate.
     * @param ay Segment endpoint A Y coordinate.
     * @param bx Segment endpoint B X coordinate.
     * @param by Segment endpoint B Y coordinate.
     * @return Minimum distance from point to segment.
     */
    fun pointSegmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double =
        sqrt(pointSegmentDistanceSq(px, py, ax, ay, bx, by))

    /**
     * True if an eraser disc of [radius] centred at ([px], [py]) touches [stroke].
     * @param stroke Stroke to test against.
     * @param px Eraser center X in page points.
     * @param py Eraser center Y in page points.
     * @param radius Eraser disc radius in points.
     * @return true if eraser touches any segment of the stroke.
     */
    fun hits(stroke: Stroke, px: Double, py: Double, radius: Double): Boolean =
        distance(stroke, px, py) <= radius

    /**
     * Shortest distance in page points from ([px], [py]) to [stroke]'s rendered ink (segment distance
     * minus half-width, clamped to 0), or [Double.POSITIVE_INFINITY] if [stroke] has no points.
     */
    fun distance(stroke: Stroke, px: Double, py: Double): Double {
        val pts = stroke.points
        if (pts.isEmpty()) return Double.POSITIVE_INFINITY
        if (pts.size == 1) {
            val d = sqrt(distSq(px, py, pts[0].x, pts[0].y)) - pts[0].width / 2.0
            return d.coerceAtLeast(0.0)
        }
        var best = Double.POSITIVE_INFINITY
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val halfW = maxOf(a.width, b.width) / 2.0
            val d = pointSegmentDistance(px, py, a.x, a.y, b.x, b.y) - halfW
            if (d <= 0.0) return 0.0
            if (d < best) best = d
        }
        return best
    }

    private fun pointSegmentDistanceSq(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return distSq(px, py, ax, ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0.0, 1.0)
        return distSq(px, py, ax + t * dx, ay + t * dy)
    }

    private fun distSq(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }
}
