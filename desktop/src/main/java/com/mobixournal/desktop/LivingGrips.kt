package com.mobixournal.desktop

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.ParametricShapes
import kotlin.math.hypot

/**
 * The **grips on a living element itself** — the on-page half of editing a recipe, as the tablet's
 * double-tapped editors do it with handles rather than fields.
 *
 * A figure has corners, and dragging one bends the figure; a table has dividers, and dragging one widens
 * the column beside it without touching a single cell's text. Both are *page* geometry, so this arithmetic
 * is pure and a JVM test pins it: the canvas only decides *which* grip a press caught, and
 * [dragGrip] decides what the drag writes.
 *
 * What is deliberately **not** a grip here: a plot's interval (its panel owns those numbers) and a
 * component (a symbol library's outline is not a shape the user bends). A family with no grips answers an
 * empty list, so the overlay and the gesture need no third opinion about what is draggable.
 */
internal sealed interface LivingGrip {

    /** A parametric figure's corner [index] — the figure bends with it. */
    data class Vertex(val index: Int) : LivingGrip

    /** The divider between columns [index] - 1 and [index] of a table. */
    data class ColumnEdge(val index: Int) : LivingGrip

    /** The divider between rows [index] - 1 and [index] of a table. */
    data class RowEdge(val index: Int) : LivingGrip
}

/** One grip's position on the sheet, in page points. */
internal data class GripPoint(val grip: LivingGrip, val x: Double, val y: Double)

/** The narrowest a column or a row may be dragged to, in points — a cell still has to be readable. */
internal const val GRIP_MIN_CELL_PT: Double = 8.0

/** Every grip [element] offers, in page points — empty for a family that has none. */
internal fun livingGripsOf(element: Element): List<GripPoint> = when (element) {
    is ParametricShapeElement ->
        element.vertices.mapIndexed { index, vertex -> GripPoint(LivingGrip.Vertex(index), vertex.x, vertex.y) }

    // Only the **interior** dividers: the outer edges are the box the selection already scales, and a
    // second handle on top of a corner square would make the corner ambiguous.
    is TableElement -> buildList {
        var x = element.left
        element.colWidths.dropLast(1).forEachIndexed { index, width ->
            x += width
            add(GripPoint(LivingGrip.ColumnEdge(index + 1), x, element.top + element.height / 2.0))
        }
        var y = element.top
        element.rowHeights.dropLast(1).forEachIndexed { index, height ->
            y += height
            add(GripPoint(LivingGrip.RowEdge(index + 1), element.left + element.width / 2.0, y))
        }
    }

    else -> emptyList()
}

/**
 * The grip within [tolerancePt] of ([x], [y]), nearest first, or null.
 *
 * The tolerance is the selection's own rule — a screen-pixel radius divided by zoom and density — so a
 * divider is as easy to catch zoomed out as in.
 */
internal fun gripAt(element: Element, x: Double, y: Double, tolerancePt: Double): LivingGrip? =
    livingGripsOf(element)
        .map { point -> point to hypot(point.x - x, point.y - y) }
        .filter { (_, distance) -> distance <= tolerancePt }
        .minByOrNull { (_, distance) -> distance }
        ?.first
        ?.grip

/**
 * [element] with [grip] dragged by ([dx], [dy]) pt — the whole gesture's offset, always applied to the
 * element **as it was at grab time**, so dragging back and forth cannot compound.
 *
 * A figure's corner snaps through `ParametricShapes.snappedVertex` (the tablet's 0/30/45/90 directions
 * within its 7 degree tolerance) unless snapping is switched off; a table's divider stops at
 * [GRIP_MIN_CELL_PT] rather than swallowing its neighbour, and moving a divider leaves `left`, `top` and
 * every cell's text alone.
 */
internal fun dragGrip(element: Element, grip: LivingGrip, dx: Double, dy: Double, snap: Boolean = true): Element {
    return when {
        element is ParametricShapeElement && grip is LivingGrip.Vertex -> {
            val vertex = element.vertices.getOrNull(grip.index) ?: return element
            val (x, y) = if (snap) {
                ParametricShapes.snappedVertex(element, grip.index, vertex.x + dx, vertex.y + dy)
            } else {
                (vertex.x + dx) to (vertex.y + dy)
            }
            ParametricShapes.moveVertex(element, grip.index, x, y)
        }

        element is TableElement && grip is LivingGrip.ColumnEdge -> {
            val before = element.colWidths.getOrNull(grip.index - 1) ?: return element
            val after = element.colWidths.getOrNull(grip.index) ?: return element
            val delta = dx.coerceIn(GRIP_MIN_CELL_PT - before, after - GRIP_MIN_CELL_PT)
            if (delta == 0.0) return element
            val widths = element.colWidths.toMutableList().also {
                it[grip.index - 1] = before + delta
                it[grip.index] = after - delta
            }
            element.copy(colWidths = widths)
        }

        element is TableElement && grip is LivingGrip.RowEdge -> {
            val before = element.rowHeights.getOrNull(grip.index - 1) ?: return element
            val after = element.rowHeights.getOrNull(grip.index) ?: return element
            val delta = dy.coerceIn(GRIP_MIN_CELL_PT - before, after - GRIP_MIN_CELL_PT)
            if (delta == 0.0) return element
            val heights = element.rowHeights.toMutableList().also {
                it[grip.index - 1] = before + delta
                it[grip.index] = after - delta
            }
            element.copy(rowHeights = heights)
        }

        else -> element
    }
}

/** The living element the active tab has selected, or null when the selection is empty or not one entity. */
internal fun selectedLivingElement(tab: DesktopTab): Element? {
    val ref = livingTargetOf(tab) ?: return null
    val page = runCatching { tab.document.page(tab.page) }.getOrNull() ?: return null
    return page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex)
}
