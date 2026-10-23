/**
 * What a **double-tap on a living element** opens: the one dispatch that answers "which editor does
 * this element have?", so the canvas, the selection bar and the tests cannot each carry a list of
 * four types that drifts.
 *
 * A living entity is a recipe rather than a drawing (`LivingInk`), so its editor is what makes it
 * worth keeping alive: a table opens its cells, a plot its formula, a component its value and label
 * side, a parametric figure its readout flags. `docs/mobix-elements.md` states the requirement — a
 * living element is editable — and this file is the code's answer to it: [livingEditTarget] names the
 * editor for every family [isLivingEntity] claims, and [LivingEditFamily] exists so a coverage test
 * can require one of each (`LivingEditCoverageTest`).
 *
 * The hit test is pure: page points in, a target out — no `MotionEvent`, no view, no Compose — so a
 * double-tap on a component can be tested on the JVM without a device.
 */
package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.isLivingEntity

/** The editor a living element opens, with the element it came from. */
sealed interface LivingEditTarget {

    /** The element whose editor this is. */
    val element: Element

    /** The family this target belongs to; see [LivingEditFamily]. */
    val family: LivingEditFamily

    /**
     * A table cell — the table's editor opens focused on the cell that was double-tapped, because a
     * table has no single value of its own to edit.
     */
    data class TableCell(
        val table: TableElement,
        val row: Int,
        val col: Int,
    ) : LivingEditTarget {
        override val element: Element get() = table
        override val family: LivingEditFamily get() = LivingEditFamily.TABLE
    }

    /** A plot's formula, domain and display flags (the anchored inspector). */
    data class Plot(val plot: PlotElement) : LivingEditTarget {
        override val element: Element get() = plot
        override val family: LivingEditFamily get() = LivingEditFamily.PLOT
    }

    /** A component's value, the side that value is written on, and its rotation. */
    data class Circuit(val circuit: CircuitElement) : LivingEditTarget {
        override val element: Element get() = circuit
        override val family: LivingEditFamily get() = LivingEditFamily.CIRCUIT
    }

    /** A parametric figure's angle and side-length readouts. */
    data class Shape(val shape: ParametricShapeElement) : LivingEditTarget {
        override val element: Element get() = shape
        override val family: LivingEditFamily get() = LivingEditFamily.SHAPE
    }
}

/**
 * The living families that have an editor.
 *
 * One entry per type [isLivingEntity] claims: the coverage test walks this and every sealed `Element`
 * subclass, so a family added to the model without an entry here fails the build rather than
 * silently being the one element a double-tap cannot open.
 */
enum class LivingEditFamily { TABLE, PLOT, CIRCUIT, SHAPE }

/** A double-tap's answer: the element's reference on its page, and the editor to open. */
data class LivingEditHit(val ref: ElementRef, val target: LivingEditTarget)

/**
 * The living element at ([xPt], [yPt]) on [page] and the editor it opens, or null when the point is
 * over no living element.
 *
 * Layers are searched **top-most first** (the last layer, and the last element in it), which is what
 * the reader sees: a table lying over a plot takes the double-tap, exactly as it takes the click.
 */
fun livingEditHit(page: Page, xPt: Double, yPt: Double): LivingEditHit? {
    for (layerIndex in page.layers.indices.reversed()) {
        val elements = page.layers[layerIndex].elements
        for (elementIndex in elements.indices.reversed()) {
            val target = livingEditTarget(elements[elementIndex], xPt, yPt) ?: continue
            return LivingEditHit(ElementRef(layerIndex, elementIndex), target)
        }
    }
    return null
}

/**
 * The editor [element] opens when double-tapped at ([xPt], [yPt]), or null when it is not a living
 * entity or the point misses it.
 *
 * A table answers only for a point inside one of its cells (the editor is about a cell); every other
 * family answers for a point inside its own box, which is the box the selection draws and the
 * handles resize.
 */
fun livingEditTarget(element: Element, xPt: Double, yPt: Double): LivingEditTarget? {
    if (!element.isLivingEntity || !ElementBounds.isHitTestable(element)) return null
    return when (element) {
        is TableElement ->
            TableDividers.cellAt(element, xPt, yPt)?.let { (row, col) -> LivingEditTarget.TableCell(element, row, col) }
        is PlotElement -> LivingEditTarget.Plot(element).takeIf { ElementBounds.of(element).contains(xPt, yPt) }
        is CircuitElement ->
            LivingEditTarget.Circuit(element).takeIf { ElementBounds.of(element).contains(xPt, yPt) }
        is ParametricShapeElement ->
            LivingEditTarget.Shape(element).takeIf { ElementBounds.of(element).contains(xPt, yPt) }
        else -> null
    }
}
