package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement

/**
 * Moving **one element** by a page-space delta — the shared half of every "this moved" edit.
 *
 * It lives in `mobix-core` because two tools that are not the selection need it: the vertical-space
 * tool shifts a whole block of a page ([VerticalSpaceOps]) and a rotation's bounding box moves with its
 * contents. The app's `SelectionOps.translate` delegates here, so there is one statement of what it
 * means to move each element kind and no chance of a stroke moving but its text box staying behind.
 *
 * Per kind, and why:
 *  - a **stroke** moves its vertices (its width and pressure travel unchanged);
 *  - a **text box**, an **image**, a **TeX image** and a **math block** move their box;
 *  - a **plot** moves its page-local frame while its mathematical ranges (`xMin..xMax`) stay put — the
 *    function it draws is the same function;
 *  - a **table** moves its origin while column widths and row heights stay put (the grid is the table);
 *  - a **circuit** moves its position anchor, and its pins move with it because they are relative;
 *  - a **parametric shape** moves its vertices, which are its geometry — its editable corners travel with
 *    the figure;
 *  - a **raw** element is left alone: it is geometry this app does not model, and guessing would corrupt
 *    a file it can still round-trip byte for byte.
 *
 * Pure and Android-free, so it is a JVM test (`ElementShiftTest`) rather than a window to poke at.
 */
object ElementShift {

    /** [element] shifted by ([dx], [dy]) pt, or unchanged when it is an element kind without geometry. */
    fun translate(element: Element, dx: Double, dy: Double): Element = when (element) {
        is Stroke -> element.copy(points = element.points.map { StrokePoint(it.x + dx, it.y + dy, it.width) })
        is TextElement -> element.copy(x = element.x + dx, y = element.y + dy)
        is ImageElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        is TexImageElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        is MathBlockElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        // Elements we don't model carry geometry we can't interpret, so every op leaves them be.
        is RawElement -> element
        // PlotElement translates its page-local frame while preserving mathematical data ranges (xMin..xMax).
        is PlotElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
            right = element.right + dx, bottom = element.bottom + dy,
        )
        // TableElement translates its origin while colWidths/rowHeights stay put.
        is TableElement -> element.copy(
            left = element.left + dx, top = element.top + dy,
        )
        // CircuitElement translates its position anchor while pins move with it.
        is CircuitElement -> element.copy(
            positionX = element.positionX + dx, positionY = element.positionY + dy,
        )
        // ParametricShapeElement translates its vertices: they are the shape's geometry.
        is ParametricShapeElement -> element.copy(
            vertices = element.vertices.map { it.copy(x = it.x + dx, y = it.y + dy) },
        )
    }
}
