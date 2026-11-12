package com.mobixournal.render

import android.view.MotionEvent
import com.mobixournal.format.model.Page
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Where a handle-resize gesture begins: the axis it scales, the corner or edge midpoint that stays fixed,
 * and the pointer at grab time — all in page pt, so the controller recomputes the transform from the
 * gesture-start snapshot every frame.
 */
internal data class ResizeStart(
    val axis: ResizeAxis,
    val anchorX: Double,
    val anchorY: Double,
    val startPtX: Double,
    val startPtY: Double,
    val startDist: Double,
)

/**
 * The resize a touch at ([event]) on one of [b]'s eight handles means, or null when it is on none.
 *
 * Each axis is reached with **its own** radius, and an axis the box has no extent on keeps the whole
 * radius: a line is only as thick as its pen (`DrawingSurfaceDefaults.isFlat`), so capping both axes by
 * that thickness left its end handles with a pixel of slack — the same press on an end caught the
 * handle, carried the whole line or dropped the selection, which is a tool that behaves unpredictably.
 * A **flat** box wears its two end handles and nothing else: no corners, which would sit exactly on
 * them, and no edge handles along the axis that could only multiply by one. The band between the ends
 * is then the body the press falls through to, so a drag above or below the line carries it.
 *
 * A corner's axis is [cornerAxis]'s: a single figure's proportions are its own to change, a picture's
 * and a mixed selection's are kept.
 */
internal fun handleResizeStart(
    event: MotionEvent,
    box: PageBox,
    b: Bounds,
    viewport: ViewportState,
    selection: ActiveSelection?,
    page: Page?,
): ResizeStart? {
    val pad = DrawingSurfaceDefaults.SELECT_PAD_PX
    val boxWpx = (b.width * box.scale).toFloat()
    val boxHpx = (b.height * box.scale).toFloat()
    val flatX = DrawingSurfaceDefaults.isFlat(boxWpx, box.scale)
    val flatY = DrawingSurfaceDefaults.isFlat(boxHpx, box.scale)
    val reachX = DrawingSurfaceDefaults.handleReach(boxWpx, flatX)
    val reachY = DrawingSurfaceDefaults.handleReach(boxHpx, flatY)
    fun grabs(hx: Float, hy: Float): Boolean =
        abs(event.x - hx) <= reachX && abs(event.y - hy) <= reachY
    fun start(axis: ResizeAxis, anchorX: Double, anchorY: Double): ResizeStart {
        val startPtX = box.toPtX(event.x, viewport.scrollX)
        val startPtY = box.toPtY(event.y, viewport.scrollY)
        val dist = hypot(startPtX - anchorX, startPtY - anchorY).coerceAtLeast(1e-3)
        return ResizeStart(axis, anchorX, anchorY, startPtX, startPtY, dist)
    }
    // The corner handles: the anchor is the diagonally-opposite corner.
    if (!flatX && !flatY) {
        val cornersPt = arrayOf(b.left to b.top, b.right to b.top, b.right to b.bottom, b.left to b.bottom)
        for (i in 0..3) {
            val padX = if (cornersPt[i].first == b.left) -pad else pad
            val padY = if (cornersPt[i].second == b.top) -pad else pad
            val hx = (cornersPt[i].first * box.scale + box.leftPx - viewport.scrollX).toFloat() + padX
            val hy = (cornersPt[i].second * box.scale + box.topPx - viewport.scrollY).toFloat() + padY
            if (grabs(hx, hy)) {
                val anchor = cornersPt[(i + 2) % 4]
                return start(cornerAxis(selection, page), anchor.first, anchor.second)
            }
        }
    }
    // Edge-midpoint handles stretch one axis only — the out-of-proportion resize. The anchor is the
    // opposite edge's midpoint, so the far edge stays pinned while this one follows the pointer: pull
    // the right edge out and the element gets wider without getting taller.
    val midXpt = (b.left + b.right) / 2
    val midYpt = (b.top + b.bottom) / 2
    val edges = arrayOf(
        Triple(b.left to midYpt, b.right to midYpt, ResizeAxis.X),
        Triple(b.right to midYpt, b.left to midYpt, ResizeAxis.X),
        Triple(midXpt to b.top, midXpt to b.bottom, ResizeAxis.Y),
        Triple(midXpt to b.bottom, midXpt to b.top, ResizeAxis.Y),
    )
    for ((handle, anchor, axis) in edges) {
        if ((axis == ResizeAxis.X && flatX) || (axis == ResizeAxis.Y && flatY)) continue
        val hx = (handle.first * box.scale + box.leftPx - viewport.scrollX).toFloat() +
            if (handle.first == b.left) -pad else if (handle.first == b.right) pad else 0f
        val hy = (handle.second * box.scale + box.topPx - viewport.scrollY).toFloat() +
            if (handle.second == b.top) -pad else if (handle.second == b.bottom) pad else 0f
        if (grabs(hx, hy)) return start(axis, anchor.first, anchor.second)
    }
    return null
}

/**
 * The axis a corner drag scales: a single **figure**'s corner is free (its proportions are the figure's),
 * while a picture's and a mixed selection's stay uniform ([ResizeAxis.BOTH]).
 */
private fun cornerAxis(selection: ActiveSelection?, page: Page?): ResizeAxis {
    val ref = selection?.refs?.singleOrNull() ?: return ResizeAxis.BOTH
    val element = page?.layers?.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
    return if (cornerChangesProportions(element)) ResizeAxis.FREE else ResizeAxis.BOTH
}
