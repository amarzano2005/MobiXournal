package com.mobixournal.render

import com.mobixournal.format.model.Element

/**
 * The scale factor one axis of a resize drag applies, delegated to [SelectionResizeMath].
 */
fun axisScaleFactor(startPt: Double, currentPt: Double, anchorPt: Double): Double =
    SelectionResizeMath.axisScaleFactor(
        startPt = startPt,
        currentPt = currentPt,
        anchorPt = anchorPt,
        minScale = DrawingSurfaceDefaults.MIN_RESIZE,
        maxScale = DrawingSurfaceDefaults.MAX_RESIZE,
    )

/**
 * Whether a corner drag changes the selection's proportions.
 *
 * Pictures and mixed selections ([element] null) lock proportions by default; passing
 * [lockAspect] = true locks proportions for any single figure as well.
 */
fun cornerChangesProportions(element: Element?, lockAspect: Boolean = false): Boolean =
    !SelectionResizeMath.cornerLocksAspect(element, lockAspect)
