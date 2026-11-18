package com.mobixournal.render

import com.mobixournal.format.model.Page

/**
 * The **dragged end of a selected line or arrow**, magnetised onto the pin it is aimed at.
 *
 * A shaft drag was the one transform with no magnet: the page drew the pins a figure could be paired with,
 * the hand pulled the line's end towards one, and the line stopped two or three points short of it — a join
 * the sheet had just promised and never made. Moving the end from the **press** made that worse in a second
 * way: the press lands within a fingertip of the ink, so the ratio it read scaled the shaft by
 * `length / pressDistance` and the tip missed the hand itself.
 *
 * The rule is [shaftResizeToPin]'s (mobix-core): the end follows the hand's travel from where it started,
 * `PointMagnet` holds the pin it caught, and the targets are the ones the element may really join
 * (`CircuitSnapping.collectMoveTargets`). This file is only the tablet's seam onto it — the resize limits are
 * the shell's, so the controller never has to know them.
 *
 * Lives here rather than in the gesture controller because that file is at its line budget.
 */
internal fun shaftEndDrag(
    pages: List<Page>,
    pageIndex: Int,
    refs: Set<ElementRef>,
    anchorX: Double,
    anchorY: Double,
    pressX: Double,
    pressY: Double,
    pointerX: Double,
    pointerY: Double,
    scale: Float,
    magnet: PointMagnet,
): List<Page> = shaftResizeToPin(
    pages = pages,
    pageIndex = pageIndex,
    refs = refs,
    anchorX = anchorX,
    anchorY = anchorY,
    pressX = pressX,
    pressY = pressY,
    pointerX = pointerX,
    pointerY = pointerY,
    scale = scale,
    magnet = magnet,
    minFactor = DrawingSurfaceDefaults.MIN_RESIZE,
    maxFactor = DrawingSurfaceDefaults.MAX_RESIZE,
)
