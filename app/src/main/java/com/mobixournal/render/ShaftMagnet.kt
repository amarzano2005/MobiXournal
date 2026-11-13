package com.mobixournal.render

import com.mobixournal.format.model.Page

/**
 * The **dragged end of a selected line or arrow**, magnetised onto the pin it is aimed at.
 *
 * A shaft drag was the one transform with no magnet: the page drew the pins a figure could be paired with,
 * the hand pulled the line's end towards one, and the line stopped two or three points short of it — a join
 * the sheet had just promised and never made. The end is a single moving point, so it follows [PointMagnet]'s
 * rule, the same one a wire's end follows while it is drawn, and the targets are the ones the element may
 * really join ([CircuitSnapping.collectMoveTargets]) so no mark is a promise the lift breaks.
 *
 * Lives here rather than in the gesture controller because that file is at its line budget: the rule is the
 * point's, not the gesture's, and it is the same pair of lines in both shells.
 */
internal fun shaftEndOnPin(
    pages: List<Page>,
    pageIndex: Int,
    refs: Set<ElementRef>,
    x: Double,
    y: Double,
    scale: Float,
    magnet: PointMagnet,
): Pair<Double, Double> {
    val page = pages.getOrNull(pageIndex) ?: return x to y
    val targets = CircuitSnapping.collectMoveTargets(page, refs)
    if (targets.isEmpty()) return x to y
    return magnet.snap(x, y, targets, CircuitSnapping.thresholdFor(scale))
}
