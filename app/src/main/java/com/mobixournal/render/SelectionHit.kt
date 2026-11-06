package com.mobixournal.render

/**
 * Which part of a selected box a press lands on: the **body** (a drag carries it) or the border band
 * (a resize handle answers, see `beginHandleResize`).
 *
 * A handle's reach ([DrawingSurfaceDefaults.HANDLE_HIT_PX]) is what makes a small outline grabbable,
 * but read inward it covers the element whole: on a phone-sized selection every touch meant to carry
 * it was within 30 px of an edge and started a resize instead — the hand landed on the element and
 * grabbed its border. So the box is split here: a border band of
 * [DrawingSurfaceDefaults.HANDLE_INSIDE_PX] (capped at a third of a small axis, so a tiny selection
 * keeps a middle) where a handle may answer, and the body inside it, which always moves.
 *
 * A **line** — a box only as thick as the pen that drew it ([DrawingSurfaceDefaults.isFlat]) — has no
 * interior to protect, so it never answers "body": its two end handles keep the whole reach, exactly
 * as before. Pure view-px geometry, so the rule is unit-testable without a `SurfaceView`.
 */
internal fun insideSelectionBody(
    viewX: Float,
    viewY: Float,
    leftPx: Float,
    topPx: Float,
    rightPx: Float,
    bottomPx: Float,
    scale: Float,
): Boolean {
    if (DrawingSurfaceDefaults.isFlat(rightPx - leftPx, scale) ||
        DrawingSurfaceDefaults.isFlat(bottomPx - topPx, scale)
    ) {
        return false
    }
    val insetX = DrawingSurfaceDefaults.handleInside(rightPx - leftPx)
    val insetY = DrawingSurfaceDefaults.handleInside(bottomPx - topPx)
    return viewX > leftPx + insetX && viewX < rightPx - insetX &&
        viewY > topPx + insetY && viewY < bottomPx - insetY
}
