package com.mobixournal.desktop

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.ElementRef
import com.mobixournal.render.ElementShift
import com.mobixournal.render.LivingPlacement

/**
 * The window's **living-element bridge**: which element an editor is open on, what family it is, and the one
 * way an edit of it is written.
 *
 * Pure and free of Compose, so the rules are JVM tests: the pencil on the selection bar opens exactly this,
 * a double-click on the element opens exactly this, and a field's commit is one [replace] — the same undo
 * step shape as a stroke. Nothing here measures a box (that is `ElementBounds`) or invents a field the model
 * does not carry (`MobixElementCodec` decides what a save keeps), so an editor built on it cannot offer a
 * value the file would drop.
 */

/** A living element being edited, named by **the document it lives in**, its page, and its position. */
data class LivingTargetRef(val documentKey: String, val page: Int, val ref: ElementRef)

/** The families the window can edit, each with the words its panel prints. */
enum class LivingFamily(val title: String) {
    PLOT("Plot"),
    TABLE("Table"),
    SHAPE("Shape"),
    CIRCUIT("Component"),
    MATH("Formula"),
    ;

    companion object {
        /** The family of [element], or null for anything that is not a living entity (ink included). */
        fun of(element: Element): LivingFamily? = when (element) {
            is PlotElement -> PLOT
            is TableElement -> TABLE
            is ParametricShapeElement -> SHAPE
            is CircuitElement -> CIRCUIT
            is MathBlockElement -> MATH
            else -> null
        }
    }
}

/** One living element, read out of the page it lives on. */
data class LivingTarget(
    val family: LivingFamily,
    val ref: ElementRef,
    val element: Element,
) {
    /** The element as its own type, when the caller knows what it is looking at. */
    inline fun <reified T : Element> asType(): T? = element as? T
}

/**
 * The living element [target] names on [tab]'s page, or null when the document has moved on: the tab may
 * have closed, the page turned, or an undo taken the element away. Every reader asks this rather than
 * caching an element, so an editor can never show a value the document no longer holds.
 */
fun livingTarget(tab: DesktopTab, target: LivingTargetRef): LivingTarget? {
    if (target.documentKey != tab.key || target.page != tab.page) return null
    val page = runCatching { tab.document.page(target.page) }.getOrNull() ?: return null
    val element = page.layers.getOrNull(target.ref.layerIndex)
        ?.elements?.getOrNull(target.ref.elementIndex) ?: return null
    return LivingFamily.of(element)?.let { LivingTarget(it, target.ref, element) }
}

/**
 * Write [element] in place of the one [target] names, as **one undo step**, and report whether anything
 * changed. An edit that writes the same element records nothing, so pressing Set twice is not two undos.
 */
fun replaceLiving(
    tab: DesktopTab,
    target: LivingTargetRef,
    element: Element,
    onEdited: (Int) -> Unit = {},
): Boolean {
    if (target.documentKey != tab.key) return false
    val page = runCatching { tab.document.page(target.page) }.getOrNull() ?: return false
    val current = page.layers.getOrNull(target.ref.layerIndex)
        ?.elements?.getOrNull(target.ref.elementIndex) ?: return false
    if (current == element) return false
    val before = tab.document.snapshot()
    val edited = withElement(page, target.ref, element) ?: return false
    if (tab.document.editPage(target.page) { edited } == null) return false
    tab.record(before)
    onEdited(target.page)
    return true
}

/** [page] with [ref]'s element replaced — every other element (and every other page) untouched. */
fun withElement(page: Page, ref: ElementRef, element: Element): Page? {
    val layer = page.layers.getOrNull(ref.layerIndex) ?: return null
    if (ref.elementIndex !in layer.elements.indices) return null
    val layers = page.layers.toMutableList()
    layers[ref.layerIndex] = layer.copy(
        elements = layer.elements.toMutableList().also { it[ref.elementIndex] = element },
    )
    return page.copy(layers = layers)
}

/**
 * Move one living element by (dx, dy) pt — the page's own `ElementShift`, so a panel that nudges a table
 * and a drag on the canvas agree. Returns the same page when nothing moved.
 */
fun shiftElement(page: Page, ref: ElementRef, dx: Double, dy: Double): Page {
    if (dx == 0.0 && dy == 0.0) return page
    val element = page.layers.getOrNull(ref.layerIndex)?.elements?.getOrNull(ref.elementIndex) ?: return page
    val moved = ElementShift.translate(element, dx, dy)
    return withElement(page, ref, moved) ?: page
}

/** The family's name for a tooltip or a panel header, or null when the element is ink. */
fun livingFamilyOf(element: Element): LivingFamily? = LivingFamily.of(element)

/**
 * The one line that says **which** element a panel is editing.
 *
 * A family name is not enough: three plots on a page are all `Plot`, and a panel that says only that leaves
 * the user guessing which of them its fields will write into. The summary carries the element's own content
 * — the formula and its range, a table's grid, a figure's side count, a component's part and value — so the
 * header answers the question before the first field does.
 */
fun livingSummary(target: LivingTarget): String = when (val element = target.element) {
    is PlotElement -> element.curves.firstOrNull()?.source?.takeIf { it.isNotBlank() }?.let { source ->
        "$source · ${number(element.xMin)}…${number(element.xMax)}"
    } ?: "No formula"
    is TableElement ->
        "${element.colWidths.size} × ${element.rowHeights.size}" + if (element.headerRow) " · header" else ""
    is ParametricShapeElement ->
        "${element.vertices.size} sides · ${number(element.strokeWidth)} pt"
    is CircuitElement -> {
        val part = element.symbol.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        element.value?.takeIf { it.isNotBlank() }?.let { "$part · $it" } ?: part
    }
    is MathBlockElement -> {
        val language = element.flavor.name.lowercase().replaceFirstChar { it.uppercase() }
        val source = element.source.lineSequence().firstOrNull()?.trim().orEmpty()
        if (source.isEmpty()) language else "$language · $source"
    }
    else -> target.family.title
}

/**
 * The editor [editing] still names on [tab], or null once it does not.
 *
 * An inspector lane belongs to **the element it was opened on**: the lane is what editing that element
 * looks like, and it must not outlive the selection that named it. Every reason the handle can go stale is
 * covered by one of the two: the selection moved (it no longer names this one element) or the document
 * moved (the page turned, the element was undone away, the tab closed) — [livingTarget] answers the second.
 */
fun attachedLivingEditor(tab: DesktopTab, editing: LivingTargetRef?): LivingTargetRef? {
    if (editing == null) return null
    if (livingTargetOf(tab) != editing.ref) return null
    return editing.takeIf { livingTarget(tab, it) != null }
}

/**
 * [plot] with one more equation — the editor's **Add equation**, the row the tablet's graph dialog offers.
 *
 * A graph may carry several curves, and a lane that could only edit the ones a placement happened to
 * create was a lane where a second equation could not be written at all. The new curve starts on the
 * shared default formula in [colour], at the width the plot already draws with, and the analysis cache is
 * dropped: a summary computed from one curve is a lie beside two.
 */
fun plotWithEquation(plot: PlotElement, colour: Int): PlotElement = plot.copy(
    curves = plot.curves + PlotCurve(
        source = LivingPlacement.DEFAULT_PLOT_FORMULA,
        color = colour,
        lineStyle = LineStyle.PLAIN,
        kind = PlotKind.CARTESIAN,
        strokeWidth = plot.curves.firstOrNull()?.strokeWidth ?: 1.0,
    ),
    summaryText = null,
)

/** [plot] without equation [index]; the last one stays, because a graph with no equation is not a graph. */
fun plotWithoutEquation(plot: PlotElement, index: Int): PlotElement {
    if (plot.curves.size <= 1 || index !in plot.curves.indices) return plot
    return plot.copy(
        curves = plot.curves.toMutableList().also { it.removeAt(index) },
        summaryText = null,
    )
}

/** A number as a summary prints it: an integer without a pointless `.0`, one decimal otherwise. */
private fun number(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
