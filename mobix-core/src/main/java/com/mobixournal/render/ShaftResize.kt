package com.mobixournal.render

import com.mobixournal.format.model.Page

/**
 * The **live drag of a line's or an arrow's end**, from the press to this frame — a new length and a new
 * inclination in one grip, with the far end still and the pen's width untouched.
 *
 * Two things here are the whole reason it is a function of its own rather than three lines in the
 * gesture controller.
 *
 * **The end is the thing that moves, not the press.** A hand goes down anywhere within its own reach of
 * the end, so the ratio that used to be read from the press (`|pointer − anchor| / |press − anchor|`)
 * scaled the shaft by `length / pressDistance` instead — the tip came out a few points short of where
 * the hand put it, and a lift aimed at a pin left a join that visibly missed the pin it was shown. The
 * end now travels with the hand's own travel from where it started ([SelectionShaft.draggedEnd]), so it
 * lands exactly under the finger, and on a pin exactly on that pin ([PointMagnet]).
 *
 * **It is read from the snapshot.** The drag rewrites the ink it moves, so the shaft — and which of its
 * ends the anchor holds still — is asked of the page the gesture began on. Reading the live page would
 * report an end that has already moved as the one that started there.
 *
 * Pure arithmetic over the page list, so the landing is a JVM test (`ShaftResizeTest`) rather than
 * something to feel for with a pen.
 */
fun shaftResizeToPin(
    pages: List<Page>,
    pageIndex: Int,
    refs: Set<ElementRef>,
    /** The end the drag holds still, in page pt — the far end of the shaft. */
    anchorX: Double,
    anchorY: Double,
    /** Where the hand went down, in page pt. Only its travel matters, never its distance from the ink. */
    pressX: Double,
    pressY: Double,
    /** Where the hand is now, in page pt. */
    pointerX: Double,
    pointerY: Double,
    /** The view's page-points-to-pixels factor, which is how far the magnet reaches. */
    scale: Float,
    magnet: PointMagnet,
    /** The resize limits the caller owns (`DrawingSurfaceDefaults.MIN_RESIZE`…`MAX_RESIZE`). */
    minFactor: Double,
    maxFactor: Double,
): List<Page> {
    val page = pages.getOrNull(pageIndex) ?: return pages
    val shaft = shaftOf(page, refs) ?: return pages
    val (endX, endY) = shaft.draggedEnd(anchorX, anchorY)
    val targetX = endX + (pointerX - pressX)
    val targetY = endY + (pointerY - pressY)
    // The end is a single moving point, so it follows the same magnet a wire's end does while it is drawn,
    // over the targets this element may really join — no mark on the sheet is a promise the lift breaks.
    val (aimX, aimY) = magnet.snap(
        targetX,
        targetY,
        CircuitSnapping.collectMoveTargets(page, refs),
        CircuitSnapping.thresholdFor(scale),
    )
    val drag = shaftDrag(endX, endY, aimX, aimY, anchorX, anchorY)
    return SelectionOps.similarity(
        pages,
        pageIndex,
        refs,
        drag.factor.coerceIn(minFactor, maxFactor),
        drag.angle,
        anchorX,
        anchorY,
    )
}
