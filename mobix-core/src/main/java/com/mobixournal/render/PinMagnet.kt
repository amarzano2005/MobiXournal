package com.mobixournal.render

import com.mobixournal.format.model.Page

/**
 * The magnet of one drag: the target it has **caught**, held from frame to frame.
 *
 * [CircuitSnapping.snapTranslation] answers each frame on its own, so a hand resting on the edge of the
 * reach makes the joined pin flicker between caught and free on every jitter — the moved part twitches
 * between snapped and not, which reads as a magnet that cannot make up its mind. This keeps the choice:
 * a caught target holds to [CircuitSnapping.SNAP_RELEASE_RATIO] × the reach, and only past that does the
 * ordinary nearest-within-reach search answer again.
 *
 * One instance per gesture (a drag), [reset] between them. Pure apart from that one field, so the
 * hysteresis is a JVM test rather than something to feel for with a pen.
 */
class PinMagnet(
    private val releaseRatio: Double = CircuitSnapping.SNAP_RELEASE_RATIO,
) {

    /** The target the last [translation] landed on, or null while the magnet is holding nothing. */
    var caught: CircuitSnapping.PinTarget? = null
        private set

    /** Let go of whatever was caught — a new gesture starts with nothing held. */
    fun reset() {
        caught = null
    }

    /**
     * The translation ([rawDx], [rawDy]) should become, with the hold applied, and the target it joins
     * (null for a free move). [thresholdPt] is the magnet's reach for **this** frame, from
     * [CircuitSnapping.thresholdFor] and the view's scale.
     */
    fun translation(
        page: Page?,
        refs: Set<ElementRef>,
        rawDx: Double,
        rawDy: Double,
        thresholdPt: Double,
    ): Pair<Double, Double> {
        val snap = CircuitSnapping.snapTranslationTo(
            page = page,
            refs = refs,
            rawDx = rawDx,
            rawDy = rawDy,
            thresholdPt = thresholdPt,
            caught = caught,
            releaseRatio = releaseRatio,
        )
        caught = snap.target
        return snap.dx to snap.dy
    }
}

/**
 * The magnet of one **moving point**: a wire's end as it is drawn, a part's end while it is pulled out.
 *
 * The same hold as [PinMagnet], for a gesture that aims a single point instead of a selection: the pin it
 * caught keeps winning to [CircuitSnapping.SNAP_RELEASE_RATIO] × the reach, so the end does not jump
 * between the pin and the hand on every jitter of a stroke.
 */
class PointMagnet(
    private val releaseRatio: Double = CircuitSnapping.SNAP_RELEASE_RATIO,
) {

    /** The target the last [snap] landed on, or null while nothing is held. */
    var caught: CircuitSnapping.PinTarget? = null
        private set

    fun reset() {
        caught = null
    }

    /**
     * Where a point at ([x], [y]) lands: the held target while it is in hold, else the nearest one within
     * [thresholdPt], else the point itself — and [grid] is what a free point falls back to (the paper
     * ruling), so a magnet that catches nothing changes no other rule.
     */
    fun snap(
        x: Double,
        y: Double,
        targets: List<CircuitSnapping.PinTarget>,
        thresholdPt: Double,
        grid: ((Double, Double) -> Pair<Double, Double>)? = null,
    ): Pair<Double, Double> {
        val snap = CircuitSnapping.snapPointTo(x, y, targets, thresholdPt, caught, releaseRatio)
        caught = snap.target
        if (snap.target == null && grid != null) return grid(x, y)
        return snap.x to snap.y
    }
}

/**
 * The magnets of one **two-ended gesture** — a wire's first and last point, or the start and end of a part
 * being placed — one hold per end.
 *
 * One instance per gesture, [reset] between them. The two ends are independent on purpose: aiming one at a
 * pin must not drag the other onto it, which is exactly what a single magnet for the whole gesture would do.
 */
class EndsMagnet(
    releaseRatio: Double = CircuitSnapping.SNAP_RELEASE_RATIO,
) {

    private val first = PointMagnet(releaseRatio)
    private val second = PointMagnet(releaseRatio)

    /** The pin each end has caught — what a preview can light, in `[start, end]` order. */
    val caught: List<CircuitSnapping.PinTarget?> get() = listOf(first.caught, second.caught)

    fun reset() {
        first.reset()
        second.reset()
    }

    /** The start of the gesture, held across its frames. */
    fun snapStart(
        x: Double,
        y: Double,
        targets: List<CircuitSnapping.PinTarget>,
        thresholdPt: Double,
        grid: ((Double, Double) -> Pair<Double, Double>)? = null,
    ): Pair<Double, Double> = first.snap(x, y, targets, thresholdPt, grid)

    /** The end that follows the hand, held across the gesture's frames. */
    fun snapEnd(
        x: Double,
        y: Double,
        targets: List<CircuitSnapping.PinTarget>,
        thresholdPt: Double,
        grid: ((Double, Double) -> Pair<Double, Double>)? = null,
    ): Pair<Double, Double> = second.snap(x, y, targets, thresholdPt, grid)
}
