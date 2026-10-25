package com.mobixournal.desktop

import com.mobixournal.format.model.StrokePoint
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The window's **ink smoothing**: a stroke's samples as a chain of quadratic curves instead of a chain of
 * straight segments, so a hand-drawn line is a line and not a run of corners.
 *
 * The page stores **samples**, and a sample is wherever the pointer was a few milliseconds ago — 125 Hz of
 * a mouse, 240 of a stylus, more on a slow frame. Joining them with `lineTo` draws the sampling rate: every
 * sample is a visible vertex, and the faster the hand the longer and more obvious the facets. The tablet
 * never looked that way because it paints its strokes through a spline, so the two shells showing the same
 * stroke differently was a difference in the *painters*, not in the file.
 *
 * The rule here is the standard midpoint one: each sample becomes the **control point** of a quadratic
 * that starts at the previous midpoint and ends at the next one. The curve therefore passes through every
 * midpoint, is tangent-continuous there (no corner at any sample), and stays within half a sample spacing
 * of the drawn points — which at ink spacing is a fraction of a point, so the stroke is the stroke the user
 * drew, only without the facets.
 *
 * **A corner stays a corner.** A figure is a stroke too: a rectangle, a triangle, an arrow's head and the
 * axes are four, three, two and two sharp turns, and averaging through them turned every one of them into a
 * rounded blob. So a sample whose turn is at least [CORNER_DEGREES] is an **anchor**: the runs either side
 * of it are smoothed independently and the point itself is kept exactly where the geometry put it. Freehand
 * ink turns a few degrees per sample — well under the threshold — so handwriting is smoothed whole, while a
 * 60° or 90° corner is drawn as the shape it is.
 *
 * Nothing here is written to the document: the samples stay exactly what they were, which is what keeps a
 * page identical on Android, in Xournal++ and in the window.
 */

/** A sample that turns by at least this much is a **corner**, kept as a vertex of the drawn line. */
internal const val CORNER_DEGREES: Double = 35.0

/** One quadratic segment of a smoothed stroke: its control point and where it ends. */
internal data class InkCurve(val controlX: Double, val controlY: Double, val toX: Double, val toY: Double)

/**
 * [points] as the curves that follow it, in the order they are drawn — one fewer curve than there are
 * points, or none at all below two points.
 *
 * The chain starts at the **first sample**, not at its midpoint, because a stroke has to begin where the
 * pen touched down; the last curve ends on the **last sample** for the same reason at the other end.
 */
internal fun smoothedInk(points: List<StrokePoint>): List<InkCurve> {
    if (points.size < 2) return emptyList()
    val last = points.size - 1
    if (last == 1) return listOf(straight(points[1]))

    val corner = BooleanArray(points.size)
    corner[0] = true
    corner[last] = true
    for (i in 1 until last) corner[i] = turnDegrees(points, i) >= CORNER_DEGREES

    val out = ArrayList<InkCurve>(last)
    var anchor = 0
    while (anchor < last) {
        // The run is the samples between this anchor and the next one (a corner, or the tip).
        var end = anchor + 1
        while (end < last && !corner[end]) end++
        if (end == anchor + 1) {
            // Two samples with nothing to average between them: a straight run, or one side of a corner.
            out += straight(points[end])
        } else {
            for (j in anchor + 1 until end) {
                val control = points[j]
                val to = if (j + 1 < end) midpoint(points[j], points[j + 1]) else points[end]
                out += InkCurve(controlX = control.x, controlY = control.y, toX = to.x, toY = to.y)
            }
        }
        anchor = end
    }
    return out
}

/**
 * A quadratic whose control point is its end: the straight run the chain is already standing on, towards
 * [to]. A quadratic with `P1 == P2` lies on the segment, so one curve type covers both halves.
 */
private fun straight(to: StrokePoint): InkCurve = InkCurve(to.x, to.y, to.x, to.y)

private fun midpoint(a: StrokePoint, b: StrokePoint): StrokePoint = StrokePoint((a.x + b.x) / 2.0, (a.y + b.y) / 2.0, a.width)

/** How sharply the stroke turns at sample [i], in degrees: 0 straight on, 90 a right angle. */
private fun turnDegrees(points: List<StrokePoint>, i: Int): Double {
    val a = points[i - 1]
    val b = points[i]
    val c = points[i + 1]
    val inX = b.x - a.x
    val inY = b.y - a.y
    val outX = c.x - b.x
    val outY = c.y - b.y
    if (hypot(inX, inY) <= 0.0 || hypot(outX, outY) <= 0.0) return 0.0
    val cross = inX * outY - inY * outX
    val dot = inX * outX + inY * outY
    return Math.toDegrees(atan2(cross, dot)).let { if (it < 0) -it else it }
}
