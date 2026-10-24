package com.mobixournal.desktop

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.ElementRef
import com.mobixournal.render.ElementShift

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
