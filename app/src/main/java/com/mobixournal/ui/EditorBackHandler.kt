package com.mobixournal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import com.mobixournal.render.clearBackgroundRegion
import com.mobixournal.render.clearSelection

/**
 * The Android back button/gesture as a **navigation** control rather than a quit button.
 *
 * The editor is one screen with a stack of transient states layered on it — a selection, page-overview
 * edit mode, immersive full-page view, the pen-diagnostics panel. Back
 * peels off the topmost of those, one press at a time, and only leaves the app once none is left.
 *
 * Two kinds of layer are **not** handled here because they already handle themselves, and doing it
 * twice would eat two states per press:
 * - Compose dialogs ([androidx.compose.material3.AlertDialog]) and dropdown/popup menus dismiss on
 *   back through their own `onDismissRequest`.
 * - The Settings overlay owns a nested handler ([SettingsScreen]); being composed later, it wins the
 *   dispatcher and pops its section before closing itself.
 *
 * The order below is "most transient first" — see the cascade in [EditorBackHandler].
 */
@Composable
fun EditorBackHandler(
    ui: EditorUiState,
    pane: PaneState,
    /** A document transfer is in flight; back is swallowed rather than acted on. */
    busy: Boolean,
    /** Nothing left to dismiss: leave the app (the activity's default back). */
    onExit: () -> Unit,
) {
    // Always enabled: the surface-owned states (selection) live outside Compose and so can't gate
    // this, and the last branch has to be able to fall through to the real back.
    BackHandler(enabled = true) {
        if (busy) return@BackHandler
        val surface = pane.surface

        when {
            // An open table, circuit or shape editor is the most transient surface: back discards it.
            ui.editingTableCell != null -> ui.editingTableCell = null
            ui.editingCircuit != null -> ui.editingCircuit = null
            ui.editingShape != null -> ui.editingShape = null
            ui.editingMathBlock != null -> ui.editingMathBlock = null

            // Editing inside a text box, then a plain element selection.
            pane.hasTextSelection -> surface?.cancelTextEdit()
            pane.hasSelection -> surface?.clearSelection()
            pane.hasBackgroundRegion -> surface?.clearBackgroundRegion()

            // Immersive view: back brings the chrome back rather than closing the document.
            ui.fullPage -> ui.fullPage = false

            // The pen-diagnostics panel is a transient overlay like any other.
            ui.penDiagnostics -> ui.penDiagnostics = false

            else -> onExit()
        }
    }
}
