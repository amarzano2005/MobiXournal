package com.mobixournal.desktop

import com.mobixournal.format.model.StrokePoint

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
 * Nothing here is written to the document: the samples stay exactly what they were, which is what keeps a
 * page identical on Android, in Xournal++ and in the window.
 */

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
    if (last == 1) return listOf(InkCurve(points[1].x, points[1].y, points[1].x, points[1].y))
    val out = ArrayList<InkCurve>(last)
    for (i in 1 until last) {
        val sample = points[i]
        val next = points[i + 1]
        out += InkCurve(
            controlX = sample.x,
            controlY = sample.y,
            toX = (sample.x + next.x) / 2.0,
            toY = (sample.y + next.y) / 2.0,
        )
    }
    // A final straight run to the tip: the tip is a sample, not a midpoint, and a curve that stopped at
    // the last midpoint would leave the end of every stroke short of where the pen was lifted.
    out += InkCurve(points[last].x, points[last].y, points[last].x, points[last].y)
    return out
}
