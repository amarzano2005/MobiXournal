package com.mobixournal.render

import android.graphics.Canvas
import kotlin.math.hypot

/** Radius of a connection point's ring, in view px. */
private const val PIN_DOT_PX = 3.5f

/** Radius of the pin a live gesture has caught, in view px. */
private const val PIN_CAUGHT_PX = 5.5f

/** Radius of a pin the placement in flight is about to mint, in view px. */
private const val PIN_PENDING_PX = 4.5f

/** Side of the square that marks an existing wire's end, in view px. */
private const val WIRE_END_PX = 4.0f

/** How close (page pt) a gesture's end must be to a pin to count as having caught it. */
private const val CAUGHT_EPSILON_PT = 0.5

/** The `pinId` [CircuitSnapping.collectPlacementTargets] gives a wire's ends. */
private const val WIRE_END_PIN = "wire-end"

/**
 * Draw every component pin on the visible pages while a **connector** is armed — the line tool or a
 * circuit symbol — plus, while a component is actually being dragged, the pins that drag is about to
 * mint.
 *
 * Pins were invisible and the magnet's reach is only a few points wide, so a wire joined a component
 * only when the user happened to aim at a spot nothing marked: the connection "worked" now and then,
 * with no way to tell why. Showing the targets before the gesture starts (and filling the one a live
 * gesture has caught) turns the magnet into something the user can aim at. The pins of the part being
 * placed are drawn the same way and at the same time: they only existed once the part had been
 * released, so there was no way to line them up with a wire that was already on the page.
 *
 * With a **component** armed, a wire's ends are marked too (a small square): placing a part aims at
 * them ([CircuitSnapping.collectPlacementTargets]), and an invisible target is exactly the bug the
 * rings were added to fix. They are not drawn for the line tool, which never joins onto a wire's end.
 *
 * Chrome only: nothing here is written to the document, and nothing is drawn for a tool that cannot
 * connect, so ordinary ink and shapes are unaffected.
 */
internal fun DrawingSurfaceView.drawCircuitPins(canvas: Canvas) {
    val kind = shapeKind ?: return
    if (!CircuitElementFactory.isConnectorKind(kind)) return
    val live = current != null
    // A component tool also offers the ends of the wires already drawn; the line tool only pins.
    val marksWireEnds = CircuitElementFactory.isCircuitKind(kind)
    for (box in layout.visible(scrollY, height.toFloat())) {
        val page = pageAt(box.index) ?: continue
        val targets = CircuitSnapping.gestureTargets(kind, page).orEmpty()
        for (target in targets) {
            val x = (target.absX * box.scale + box.leftPx - scrollX).toFloat()
            val y = (target.absY * box.scale + box.topPx - scrollY).toFloat()
            if (marksWireEnds && target.pinId == WIRE_END_PIN) {
                canvas.drawRect(x - WIRE_END_PX, y - WIRE_END_PX, x + WIRE_END_PX, y + WIRE_END_PX, chrome.handleArm)
            } else if (live && caughtByGesture(target)) {
                canvas.drawCircle(x, y, PIN_CAUGHT_PX, chrome.handle)
            } else {
                canvas.drawCircle(x, y, PIN_DOT_PX, chrome.handleArm)
            }
        }
    }
    drawPendingPins(canvas, kind)
}

/**
 * The pins a **component drag in flight** would land, drawn where they would be.
 *
 * The rings come from [CircuitSnapping.previewTargets], the same placement rule the commit uses, so
 * the marks the user lines up against a wire end are the pins that get minted. They are filled rather
 * than hollow because they are not targets yet — they are the thing being placed.
 */
private fun DrawingSurfaceView.drawPendingPins(canvas: Canvas, kind: ShapeKind) {
    if (current == null || !CircuitElementFactory.isCircuitKind(kind)) return
    val box = layout.boxes.getOrNull(currentPage) ?: return
    for (pin in CircuitSnapping.previewTargets(kind, shapeStartX, shapeStartY, shapeEndX, shapeEndY)) {
        val x = (pin.absX * box.scale + box.leftPx - scrollX).toFloat()
        val y = (pin.absY * box.scale + box.topPx - scrollY).toFloat()
        canvas.drawCircle(x, y, PIN_PENDING_PX, chrome.handle)
    }
}

/** Whether the gesture in progress has one of its ends on [target]. */
private fun DrawingSurfaceView.caughtByGesture(target: CircuitSnapping.PinTarget): Boolean {
    fun on(x: Double, y: Double) =
        hypot(target.absX - x, target.absY - y) <= CAUGHT_EPSILON_PT
    return on(shapeStartX, shapeStartY) || on(shapeEndX, shapeEndY)
}
