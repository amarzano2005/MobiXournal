package com.mobixournal.desktop.shell

import com.mobixournal.desktop.sheet.SelectionBarUi
import com.mobixournal.desktop.sheet.restyleSelection
import com.mobixournal.desktop.sheet.selectionBarUi

/**
 * The bar under the selection's outline: what it can do, and the commands it runs.
 *
 * It lives here rather than inline in the canvas's composable because that one is at its length ceiling
 * (`SourceStructureTest`, *no function past 100 lines*): the availability rules are `SelectionBarUi`'s and
 * the commands are the shell's, so the canvas needs neither of them to draw a bar.
 *
 * `onPickColour` is the **Recolour** strip's own path. `RECOLOUR` through [DesktopShellState.runSelection]
 * takes the strip's live ink — what the pen would write — while a swatch is an explicit colour the user
 * pointed at, and restyling what is caught is one undoable edit.
 */
internal fun selectionBarFor(state: DesktopShellState, tab: DesktopTab): SelectionBarUi =
    selectionBarUi(
        tab = tab,
        clipboard = state.clipboard,
        palette = state.palette,
        onPickColour = { colour ->
            restyleSelection(tab, colour = colour) { index -> state.edited(tab, index) }
        },
        onAction = { action -> state.runSelection(action) },
    )
