package com.mobixournal.render

import android.graphics.Canvas
import com.mobixournal.format.model.CircuitElement
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
 * A **selected** component arms the same marks, because the selection tool is the other way a part is
 * joined: pick up a part that was placed and drag it onto a pin, and what it may join answers
 * [CircuitSnapping.collectMoveTargets] — the page's pins plus both ends of every wire. The window has
 * always drawn these for a selected part; the tablet drew nothing at all with the selection tool, so a
 * moved part could be aimed at a wire end nobody could see.
 *
 * Chrome only: nothing here is written to the document, and nothing is drawn for a tool that cannot
 * connect, so ordinary ink and shapes are unaffected.
 */
internal fun DrawingSurfaceView.drawCircuitPins(canvas: Canvas) {
    // The marks belong to a **gesture**, not to a state: they are there while a figure is being drawn and
    // while the selection is moving it, and are gone otherwise — a part merely selected marks nothing.
    val drawing = ink.current != null || ink.currentStrokes != null
    val drawingKind = if (drawing) shapeKind else null
    if (drawingKind != null) {
        if (CircuitElementFactory.isConnectorKind(drawingKind)) drawTargetPins(canvas, drawingKind)
        // The inserted object's own pins are drawn only when the page holds something it could join
        // (`FigurePins.hasCompany`): on a page with nothing compatible they are dots on empty paper.
        val page = pageAt(ink.currentPage)
        if (page != null && FigurePins.hasCompany(page, drawingKind)) drawPendingPins(canvas, drawingKind)
        return
    }
    // A **selected** element wears the marks it can be paired with, at rest as well as in hand: its own pins
    // *and* the compatible ones — a figure's border against the wires' ends and arrows' tips, a connector's
    // pins against everything it may reach ([FigurePins.selectionMarks]). The slot that placed it is gone, so
    // the selection is the only thing left to answer *what can this join*, and a pairing needs both ends
    // visible. Nothing at all on a page that holds nothing compatible.
    val sel = selection ?: return
    if (sel.refs.size != 1) return
    val page = pageAt(sel.pageIndex) ?: return
    val ref = sel.refs.first()
    val element = page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) ?: return
    val marks = FigurePins.selectionMarks(page, element, exclude = sel.refs)
    if (marks.own.isEmpty() && marks.compatible.isEmpty()) return
    val box = layout.boxes.getOrNull(sel.pageIndex) ?: return
    fun at(x: Double, y: Double) =
        (x * box.scale + box.leftPx - scrollX).toFloat() to (y * box.scale + box.topPx - scrollY).toFloat()
    // The selection's own pins, filled: they are the thing being joined.
    for ((px, py) in marks.own) {
        val (x, y) = at(px, py)
        canvas.drawCircle(x, y, PIN_PENDING_PX, chrome.handle)
    }
    // And what it may join, as the sheet marks a target: a wire's end is a square, every other pin a dot.
    for (target in marks.compatible) {
        val (x, y) = at(target.absX, target.absY)
        if (target.isWireEnd) {
            canvas.drawRect(x - WIRE_END_PX, y - WIRE_END_PX, x + WIRE_END_PX, y + WIRE_END_PX, chrome.handleArm)
        } else {
            canvas.drawCircle(x, y, PIN_DOT_PX, chrome.handleArm)
        }
    }
}

/** The pins a connector [kind] could join, as the sheet draws them while the gesture is live. */
private fun DrawingSurfaceView.drawTargetPins(
    canvas: Canvas,
    kind: ShapeKind,
    exclude: Set<ElementRef> = emptySet(),
) {
    val live = ink.current != null
    for (box in layout.visible(scrollY, height.toFloat())) {
        val page = pageAt(box.index) ?: continue
        val targets = CircuitSnapping.gestureTargets(kind, page, exclude = exclude).orEmpty()
        for (target in targets) {
            val x = (target.absX * box.scale + box.leftPx - scrollX).toFloat()
            val y = (target.absY * box.scale + box.topPx - scrollY).toFloat()
            if (target.isWireEnd) {
                canvas.drawRect(x - WIRE_END_PX, y - WIRE_END_PX, x + WIRE_END_PX, y + WIRE_END_PX, chrome.handleArm)
            } else if (live && caughtByGesture(target)) {
                canvas.drawCircle(x, y, PIN_CAUGHT_PX, chrome.handle)
            } else {
                canvas.drawCircle(x, y, PIN_DOT_PX, chrome.handleArm)
            }
        }
    }
}

/**
 * The pins a **component drag in flight** would land, drawn where they would be.
 *
 * The rings come from [CircuitSnapping.previewTargets], the same placement rule the commit uses, so
 * the marks the user lines up against a wire end are the pins that get minted. They are filled rather
 * than hollow because they are not targets yet — they are the thing being placed.
 */
private fun DrawingSurfaceView.drawPendingPins(canvas: Canvas, kind: ShapeKind) {
    if (ink.current == null) return
    val box = layout.boxes.getOrNull(ink.currentPage) ?: return
    val draft = shapeDraft
    // A component's pins are the factory's placement rule; every other figure's are its own corners and
    // the middle of each side (`FigurePins`), which is what a connector will later attach to.
    val pins = if (CircuitElementFactory.isCircuitKind(kind)) {
        CircuitSnapping.previewTargets(kind, draft.startX, draft.startY, draft.endX, draft.endY)
            .map { it.absX to it.absY }
    } else {
        FigurePins.pinsOf(
            kind,
            ShapeBuilder.build(kind, draft.startX, draft.startY, draft.endX, draft.endY, draft.widthPt),
        )
    }
    for ((px, py) in pins) {
        val x = (px * box.scale + box.leftPx - scrollX).toFloat()
        val y = (py * box.scale + box.topPx - scrollY).toFloat()
        canvas.drawCircle(x, y, PIN_PENDING_PX, chrome.handle)
    }
}

/** Whether the gesture in progress has one of its ends on [target]. */
private fun DrawingSurfaceView.caughtByGesture(target: CircuitSnapping.PinTarget): Boolean {
    fun on(x: Double, y: Double) =
        hypot(target.absX - x, target.absY - y) <= CAUGHT_EPSILON_PT
    return on(shapeDraft.startX, shapeDraft.startY) || on(shapeDraft.endX, shapeDraft.endY)
}
