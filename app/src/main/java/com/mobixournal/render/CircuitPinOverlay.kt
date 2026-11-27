package com.mobixournal.render

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.hypot

/** Half-diagonal of a proximity pin diamond, in view px. */
private const val PIN_DIAMOND_PX = 6.5f

/** Half-diagonal of a caught (snapped) pin diamond, in view px. */
private const val PIN_CAUGHT_PX = 9.5f

/** White halo padding around every pin diamond, in view px. */
private const val PIN_HALO_PX = 2.5f

/** How close (page pt) a gesture's end must be to a pin to count as having caught it. */
private const val CAUGHT_EPSILON_PT = 0.5

/** Reusable diamond path (main-thread canvas paint only). */
private val pinDiamondPath = Path()

/**
 * Draw magnetic pins **only by pointer proximity** (during active drawing, active selection drag,
 * or stylus hover). At rest — idle selection or armed tool with no pointer nearby — the sheet stays clean.
 *
 * Pins are rendered as distinct **diamonds** (rombi) so they never visually collide with square corner
 * resize handles, pill edge handles, or circular vertex/spline nodes.
 */
internal fun DrawingSurfaceView.drawCircuitPins(canvas: Canvas) {
    // 1. Active drawing gesture: reveal only pins near the live stylus/pointer tip.
    val drawing = ink.current != null || ink.currentStrokes != null
    val drawingKind = if (drawing) shapeKind else null
    if (drawingKind != null) {
        val page = pageAt(ink.currentPage) ?: return
        val box = layout.boxes.getOrNull(ink.currentPage) ?: return
        val targets = if (CircuitElementFactory.isConnectorKind(drawingKind)) {
            CircuitSnapping.gestureTargets(drawingKind, page).orEmpty()
        } else {
            FigurePins.joinedBy(page, drawingKind)
        }
        val nearEnd = ElementInteraction.filterTargetsByProximity(
            targets, shapeDraft.endX, shapeDraft.endY, box.scale,
        )
        val nearStart = ElementInteraction.filterTargetsByProximity(
            targets, shapeDraft.startX, shapeDraft.startY, box.scale,
        )
        val visible = (nearEnd + nearStart).distinctBy { it.absX to it.absY }
        drawPinTargets(canvas, ink.currentPage, visible, highlightCaught = true)
        return
    }

    // 2. Active selection move/shaft-resize: reveal only compatible pins near the live drag pointer.
    if (gestures.moving || gestures.resizing) {
        drawSelectionDragPins(canvas)
        return
    }

    // 3. Stylus hover with a pin-capable tool armed: reveal only targets near the hovering nib.
    val armed = shapeKind ?: return
    if (!hovering || FigurePins.familyOf(armed) == null) return
    val hoverBox = layout.pageAt(hoverX + scrollX, hoverY + scrollY) ?: return
    val hoverPage = pageAt(hoverBox.index) ?: return
    val curXPt = hoverBox.toPtX(hoverX, scrollX)
    val curYPt = hoverBox.toPtY(hoverY, scrollY)
    val allTargets = if (FigurePins.aimFamily(armed) != null) {
        CircuitSnapping.gestureTargets(armed, hoverPage).orEmpty()
    } else {
        FigurePins.joinedBy(hoverPage, armed)
    }
    val nearHover = ElementInteraction.filterTargetsByProximity(allTargets, curXPt, curYPt, hoverBox.scale)
    drawPinTargets(canvas, hoverBox.index, nearHover, highlightCaught = false)
}

/** Compatible pins near the live pointer while dragging or shaft-resizing a single selection. */
private fun DrawingSurfaceView.drawSelectionDragPins(canvas: Canvas) {
    val sel = selection ?: return
    if (sel.refs.size != 1) return
    val page = pageAt(sel.pageIndex) ?: return
    val box = layout.boxes.getOrNull(sel.pageIndex) ?: return
    val ref = sel.refs.first()
    val element = page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) ?: return
    val marks = FigurePins.selectionMarks(page, element, exclude = sel.refs)
    if (marks.joinable.isEmpty()) return

    val nearPointer = ElementInteraction.filterTargetsByProximity(
        marks.joinable, gestures.livePointerPtX, gestures.livePointerPtY, box.scale,
    ).toMutableList()
    // Also reveal pins near any of the dragged element's own pins (e.g. when dragging a wide rectangle's side toward a wire).
    val radiusPt = (ElementInteraction.PIN_REVEAL_RADIUS_PX / box.scale).toDouble()
    for (target in marks.joinable) {
        if (marks.own.any { (ox, oy) -> hypot(ox - target.absX, oy - target.absY) <= radiusPt }) {
            nearPointer += target
        }
    }
    gestures.magnetTarget?.let { nearPointer += it }
    val visible = nearPointer.distinctBy { it.absX to it.absY }
    drawPinTargets(canvas, sel.pageIndex, visible, highlightCaught = false, caughtTarget = gestures.magnetTarget)
}

/** Paint each visible pin target as a diamond with a white halo. */
private fun DrawingSurfaceView.drawPinTargets(
    canvas: Canvas,
    pageIndex: Int,
    targets: List<CircuitSnapping.PinTarget>,
    highlightCaught: Boolean,
    caughtTarget: CircuitSnapping.PinTarget? = null,
) {
    if (targets.isEmpty()) return
    val box = layout.boxes.getOrNull(pageIndex) ?: return
    for (target in targets) {
        val x = (target.absX * box.scale + box.leftPx - scrollX).toFloat()
        val y = (target.absY * box.scale + box.topPx - scrollY).toFloat()
        val caught = (highlightCaught && caughtByGesture(target)) ||
            (caughtTarget != null && hypot(target.absX - caughtTarget.absX, target.absY - caughtTarget.absY) <= CAUGHT_EPSILON_PT)
        val r = if (caught) PIN_CAUGHT_PX else PIN_DIAMOND_PX
        drawDiamond(canvas, x, y, r + PIN_HALO_PX, chrome.pinHalo)
        drawDiamond(canvas, x, y, r, chrome.pin)
        if (caught) {
            canvas.drawCircle(x, y, r + 4f, chrome.pinRing)
        }
    }
}

private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: android.graphics.Paint) {
    pinDiamondPath.reset()
    pinDiamondPath.moveTo(cx, cy - radius)
    pinDiamondPath.lineTo(cx + radius, cy)
    pinDiamondPath.lineTo(cx, cy + radius)
    pinDiamondPath.lineTo(cx - radius, cy)
    pinDiamondPath.close()
    canvas.drawPath(pinDiamondPath, paint)
}

/** Whether the drawing gesture in progress has one of its ends on [target]. */
private fun DrawingSurfaceView.caughtByGesture(target: CircuitSnapping.PinTarget): Boolean {
    fun on(x: Double, y: Double) =
        hypot(target.absX - x, target.absY - y) <= CAUGHT_EPSILON_PT
    return on(shapeDraft.startX, shapeDraft.startY) || on(shapeDraft.endX, shapeDraft.endY)
}
