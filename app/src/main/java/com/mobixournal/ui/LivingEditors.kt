package com.mobixournal.ui

import com.mobixournal.render.LivingEditTarget

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
            editingTableCell = EditingTableCell(target.table, target.row, target.col)
        is LivingEditTarget.Plot -> openPlotInspector(target.plot)
        is LivingEditTarget.Circuit -> editingCircuit = target.circuit
        is LivingEditTarget.Shape -> editingShape = target.shape
    }
}
