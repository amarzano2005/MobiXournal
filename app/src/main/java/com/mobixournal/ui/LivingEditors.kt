package com.mobixournal.ui

import com.mobixournal.render.LivingEditTarget
import com.mobixournal.render.Placement

/**
 * Open the editor a **living element** stands behind: the cell editor for a table, the anchored
 * inspector for a plot or a component, the readout panel for a parametric figure.
 *
 * One function rather than the same `when` at every entry point (the canvas double-tap and the
 * selection bar's **Edit** button), so a family cannot gain an editor in one place and miss it in the
 * other — the failure the user reports as "this thing can be edited, but not from here".
 */
fun EditorUiState.openLivingEditor(target: LivingEditTarget) {
    when (target) {
        is LivingEditTarget.TableCell ->
            openTableInspector(target.table, target.row, target.col)
        is LivingEditTarget.Plot -> openPlotInspector(target.plot)
        is LivingEditTarget.Circuit -> openCircuitInspector(target.circuit)
        is LivingEditTarget.Shape -> openShapeInspector(target.shape)
        is LivingEditTarget.MathBlock -> editingMathBlock = target.block
    }
}

/**
 * A **LaTeX tap** that landed on a formula: open *that* formula's editor, the same command the double-tap
 * gives it, and answer true. Paper answers false, and the slot asks for the source as it always did.
 *
 * The LaTeX slot stays in hand after it inserts, so its tap is how the next formula is put down — and without
 * this the tap that meant "this formula" asked for the dialog again and laid a second one on top
 * (`com.mobixournal.render.pickMathBlock`).
 */
fun EditorUiState.openPlacedTex(placement: Placement): Boolean {
    val block = placement.math ?: return false
    openLivingEditor(LivingEditTarget.MathBlock(block))
    return true
}
