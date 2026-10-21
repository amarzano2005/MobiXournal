package com.mobixournal.render

import android.graphics.Canvas
import kotlin.math.hypot

/** Radius of a connection point's ring, in view px. */
private const val PIN_DOT_PX = 3.5f

/** Radius of the pin a live gesture has caught, in view px. */
private const val PIN_CAUGHT_PX = 5.5f

/** How close (page pt) a gesture's end must be to a pin to count as having caught it. */
private const val CAUGHT_EPSILON_PT = 0.5

/**
 * Draw every component pin on the visible pages while a **connector** is armed — the line tool or a
 * circuit symbol.
 *
 * Pins were invisible and the magnet's reach is only a few points wide, so a wire joined a component
 * only when the user happened to aim at a spot nothing marked: the connection "worked" now and then,
 * with no way to tell why. Showing the targets before the gesture starts (and filling the one a live
 * gesture has caught) turns the magnet into something the user can aim at.
 *
 * Chrome only: nothing here is written to the document, and nothing is drawn for a tool that cannot
 * connect, so ordinary ink and shapes are unaffected.
 */
internal fun DrawingSurfaceView.drawCircuitPins(canvas: Canvas) {
    val kind = shapeKind ?: return
    if (!CircuitElementFactory.isConnectorKind(kind)) return
    val live = current != null
    for (box in layout.visible(scrollY, height.toFloat())) {
        val page = pageAt(box.index) ?: continue
        for (target in CircuitSnapping.collectAllTargets(page)) {
            val x = (target.absX * box.scale + box.leftPx - scrollX).toFloat()
            val y = (target.absY * box.scale + box.topPx - scrollY).toFloat()
            if (live && caughtByGesture(target)) {
                canvas.drawCircle(x, y, PIN_CAUGHT_PX, chrome.handle)
            } else {
                canvas.drawCircle(x, y, PIN_DOT_PX, chrome.handleArm)
            }
        }
    }
}

/** Whether the gesture in progress has one of its ends on [target]. */
private fun DrawingSurfaceView.caughtByGesture(target: CircuitSnapping.PinTarget): Boolean {
    fun on(x: Double, y: Double) =
        hypot(target.absX - x, target.absY - y) <= CAUGHT_EPSILON_PT
    return on(shapeStartX, shapeStartY) || on(shapeEndX, shapeEndY)
}
