package com.mobixournal.desktop

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The colour panel, held for its exit.
 *
 * It is a transient surface: it may lie over the page (a band may not), and it is centred so the strip
 * that opened it stays visible. [GlassPresence] keeps it composed while its spring draws back — the
 * shell clears `editing` the instant Set or Cancel is pressed, and without the hold the panel would
 * vanish between two frames — and the target is remembered for the same reason: the closing surface
 * still has to know which colour it was editing.
 *
 * It lives beside the shell rather than inside it because it is the panel's own window behaviour, not the
 * window's own layout (`SourceStructureTest` keeps both files inside the budget).
 */
@Composable
internal fun BoxScope.ColourPanelHost(state: DesktopShellState) {
    var lastEdit by remember { mutableStateOf<ColourEditorTarget?>(null) }
    val editing = state.editing
    SideEffect { if (editing != null) lastEdit = editing }
    GlassPresence(visible = editing != null) {
        val target = editing ?: lastEdit ?: return@GlassPresence
        ColourPickerPanel(
            title = if (target == ColourEditorTarget.CUSTOM) "Custom colour" else "New colour",
            initial = if (target == ColourEditorTarget.CUSTOM) {
                state.paletteCustom ?: state.ink.colour
            } else {
                state.ink.colour
            },
            onSet = { colour -> state.commitColour(colour) },
            onCancel = { state.cancelColourEdit() },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
