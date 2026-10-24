package com.mobixournal.desktop

import com.mobixournal.format.model.Element
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.LivingSize
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionOps
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.ElementBounds
import com.mobixournal.render.rectRegion

/**
 * The window's **living-slot dispatch**: which strip slot places a living element, what a press on it
 * builds, and the one commit path every family shares.
 *
 * The geometry and the builders are mobix-core's ([LivingPlacement]) — the tablet's own defaults — so a
 * plot placed here carries the formula and frame the tablet's insertion would have written. What belongs
 * to the window is only the dispatch and the commit: a slot that places is **not ink**
 * ([DesktopTool.inksByPackets] stays false, exactly as it does for a geometry slot), the press is taken
 * before the ink router, and the new element is appended to the page's top layer as **one** edit and one
 * undo step, left selected so the next gesture — a drag, a recolour, the pencil — acts on it.
 */

/** The living family [tool] places, or null for a slot that inks, selects or reflows instead. */
internal fun livingSlotOf(tool: DesktopTool): LivingSlot? = when (tool) {
    DesktopTool.PLOT -> LivingSlot.PLOT
    DesktopTool.TABLE -> LivingSlot.TABLE
    DesktopTool.CIRCUIT -> LivingSlot.CIRCUIT
    else -> null
}

/** The default box a click with [tool] places on a page [pageW] pt wide, or null for a slot that inks. */
internal fun livingDefaultSize(tool: DesktopTool, pageW: Double): LivingSize? =
    livingSlotOf(tool)?.let { LivingPlacement.defaultSize(it, pageW) }

/**
 * The element a placement commits for [slot] in [bounds], or null when the slot's product could not be
 * built (a symbol this build's shape library does not have).
 */
internal fun livingElementFor(
    slot: LivingSlot,
    bounds: Bounds,
    ink: DesktopInk,
    circuitKind: ShapeKind,
): Element? = when (slot) {
    LivingSlot.PLOT -> LivingPlacement.plot(bounds, ink.colour, ink.width.toDouble())
    LivingSlot.TABLE -> LivingPlacement.table(bounds)
    LivingSlot.CIRCUIT -> LivingPlacement.circuit(circuitKind, bounds, ink.colour, ink.width.toDouble())
}

/**
 * Append [element] to the active page's top layer, select it, and record **one** undo step.
 *
 * Appending (rather than editing in place) is what a fresh placement is; [SelectionOps.addToTopLayerOn]
 * is the shared helper the paste and duplicate commands already use, so the new element's refs — and the
 * selection built from them — are consistent across all three. A placement that writes nothing (an
 * element the visitor refused) records no undo step.
 */
internal fun placeLivingElement(
    tab: DesktopTab,
    element: Element,
    onEdited: (Int) -> Unit = {},
): Boolean {
    val index = tab.page
    val page = runCatching { tab.document.page(index) }.getOrNull() ?: return false
    val (appended, refs) = SelectionOps.addToTopLayerOn(page, listOf(element))
    if (refs.isEmpty()) return false
    val before = tab.document.snapshot()
    if (tab.document.editPage(index) { appended } == null) return false
    tab.record(before)
    val bounds = ElementBounds.of(element)
    tab.selection = PageSelection(rectRegion(bounds.left, bounds.top, bounds.right, bounds.bottom), refs)
    onEdited(index)
    return true
}

/** A component family's English name for the picker — `BJT_NPN` reads as `Bjt npn`, which is a worse name. */
internal fun circuitKindLabel(kind: ShapeKind): String {
    val words = kind.name.lowercase().split('_')
    val named = when (words.first()) {
        "bjt" -> "Bipolar transistor"
        "opamp" -> "Op-amp"
        "dc" -> "DC source"
        "led" -> "LED"
        "zener" -> "Zener diode"
        "and" -> "AND gate"
        "or" -> "OR gate"
        "not" -> "NOT gate"
        "nand" -> "NAND gate"
        "nor" -> "NOR gate"
        "xor" -> "XOR gate"
        "xnor" -> "XNOR gate"
        "buffer" -> "Buffer gate"
        else -> return words.joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
    }
    return when (words.size) {
        1 -> named
        2 -> "$named (${words[1].uppercase()})"
        else -> named
    }
}
