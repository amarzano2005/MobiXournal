package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.hypot

/**
 * **Hold-to-snap**, as a rule instead of a timer: the two numbers it is defined by, and the one test
 * that says a hand has come to rest.
 *
 * Both shells draw a freehand stroke whose preview can be replaced by recognised geometry while the
 * pen is still down — the tablet with a `postDelayed` on the view, the window with a silent timer off
 * its own stream. What they must agree on is *when* and *what*: resting means the tip has not left a
 * [SLOP_PT] neighbourhood for [MS], and what a resting stroke becomes is the same
 * [ShapeRecognizer] answer the lift path would have found. That rule lives here so it is one rule, and
 * so a JVM test owns it without a device, a window or a clock.
 *
 * The **anchor** is the sample the tip last moved on. Walking the stroke is what makes the rule
 * stateless: a caller that keeps a live anchor (as both shells do, to restart their timer) reaches the
 * same answer as [restAnchor] on the whole sample list, and a caller that only has the samples (a
 * test, a replay) needs nothing else.
 *
 * A dwell is a **preview**, never a rewrite of what the user drew: the samples stay as they are, and a
 * stroke that grows after the snap gets its own ink back. Rewriting the raw samples and re-recognising
 * the clean geometry is how a held square came back a circle — four corners plus the closing repeat is
 * a perfectly round set for the circle fit — which is why the figure found here is carried to the
 * commit instead of being re-derived from the points.
 */
object ShapeDwell {

    /**
     * How long the tip must rest before the preview snaps, in milliseconds.
     *
     * 300 ms is the deliberate-on-the-glass threshold: long enough that a pause in the middle of a
     * letter is not a request to snap (writing stalls for tens of ms, not hundreds), short enough that
     * a user who *means* it sees the geometry appear while still watching.
     */
    const val MS = 300L

    /**
     * How far (pt) the tip may travel and still count as resting. Above the tremor a stylus reports
     * while held, below a deliberate stroke.
     */
    const val SLOP_PT = 3.0

    /** [MS] in nanoseconds, the unit a caller's monotonic clock usually reads. */
    const val MS_NANOS = MS * 1_000_000L

    /**
     * The index the tip last **moved** on, or -1 when it has not come to rest.
     *
     * Every sample that leaves the anchor's [SLOP_PT] circle becomes the new anchor — the same rule a
     * shell applies as the stroke arrives, and the reason a slow, wobbling hold still counts as still:
     * what would re-arm the timer is the tip *leaving* the slop, not its jitter inside it.
     */
    fun restAnchor(samples: List<StrokePoint>): Int {
        if (samples.size < 2) return -1
        var anchor = 0
        for (index in 1 until samples.lastIndex) {
            if (distance(samples[index], samples[anchor]) >= SLOP_PT) anchor = index
        }
        val last = samples.last()
        return if (distance(last, samples[anchor]) < SLOP_PT) anchor else -1
    }

    /**
     * True when the samples themselves say the tip has come to rest — the last one is still inside the
     * anchor's circle.
     *
     * This is the rule for a stream that keeps reporting while the pen rests (a driver whose packets
     * repeat). It is **not** the window's trigger: Windows sends a held pen nothing at all, so there the
     * rest is the *silence* and the caller times it ([MS]); a finished stroke's last sample is a real
     * move, and asking this would call every completed stroke "still moving".
     */
    fun resting(samples: List<StrokePoint>): Boolean = restAnchor(samples) >= 0

    /**
     * The figure the recogniser finds in [samples], or null when the stroke is not one.
     *
     * Deliberately **not** gated on [resting]: who may snap is the caller's question — the tablet's
     * timer, or the window's silence — and a row of samples that ends on a deliberate move is exactly
     * what a hand looks like when it stops writing. [widthPt] is the width every emitted vertex carries,
     * as [ShapeRecognizer] takes it.
     */
    fun figureFor(samples: List<StrokePoint>, widthPt: Double): RecognisedFigure? =
        ShapeRecognizer.classify(samples, widthPt)

    private fun distance(a: StrokePoint, b: StrokePoint): Double = hypot(a.x - b.x, a.y - b.y)
}
