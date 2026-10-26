package com.mobixournal.desktop

import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingSlot

/**
 * The shell's **living placement**: what a lifted slot drag means before the element exists.
 *
 * [DesktopShellState] is the window's whole command surface in one class, and `DesktopShell.kt` is kept
 * under the module's 700-line ratchet (`SourceStructureTest`); placing a plot, a table or a component is a
 * self-contained decision — ask, answer, or drop — so it reads better here than in the middle of the key
 * handling. These are extension functions, so every call site still says `state.placeOrAsk(…)`, and the
 * state they touch is the shell's own surface (`tabs`, `shapeInk`, `pendingPlacement`).
 */

/**
 * What a lifted living-slot drag means: the family's question for a plot or a table, the placement
 * itself for a component (whose question was the palette).
 *
 * A placement that lands hands the window **to the selection**, exactly as the tablet does: the new
 * element is already what stays selected, so a tool still armed to place a second one would make the
 * very next drag place another plot instead of moving the one just made. The marquee is therefore
 * picked, and the next press drags, resizes or opens the pencil on what was placed.
 */
fun DesktopShellState.placeOrAsk(tab: DesktopTab, slot: LivingSlot, bounds: Bounds) {
    if (slot == LivingSlot.CIRCUIT) {
        val element = livingElementFor(slot, bounds, shapeInk, activeCircuitKind) ?: return
        if (placeLivingElement(tab, element) { index -> edited(tab, index) }) handToSelection()
        return
    }
    // A graph's first equation starts in the pen's ink until a curve is given its own colour.
    pendingPlacement = PendingPlacement(slot, bounds, tab.key, shapeInk.colour)
}

/** Pick the selection tool, so the element just placed can be moved with the next press. */
internal fun DesktopShellState.handToSelection() {
    selectTool(DesktopTool.MARQUEE)
}

/** The question's answer: build the element from it and write it as **one** edit and one undo step. */
fun DesktopShellState.confirmPlacement() {
    val pending = pendingPlacement ?: return
    val tab = tabs.active ?: return
    // The question was asked on a page; answering it on another tab would place the box of one
    // document on the sheet of another.
    if (tab.key != pending.tabKey) {
        pendingPlacement = null
        return
    }
    val element = pending.element(shapeInk, activeCircuitKind) ?: return
    if (placeLivingElement(tab, element) { index -> edited(tab, index) }) {
        pendingPlacement = null
        // The same hand-over the circuit slot makes: the placed element is what stays selected, so
        // the next drag moves it rather than placing a second one.
        handToSelection()
    }
}

/** Drop the question without placing anything — the page is exactly what it was before the drag. */
fun DesktopShellState.cancelPlacement() {
    pendingPlacement = null
}
