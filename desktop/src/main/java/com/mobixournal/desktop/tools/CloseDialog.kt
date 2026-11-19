package com.mobixournal.desktop.tools

import com.mobixournal.desktop.io.save
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.cancelClose
import com.mobixournal.desktop.shell.confirmCloseDiscard
import com.mobixournal.desktop.shell.requestCloseSave

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * The one question the window asks before it can lose work: **save, discard or cancel**.
 *
 * It is a modal like every other ([DesktopDialogFrame]) — the window's scrim, the opaque popup surface,
 * the compact width — with the accent on Save, because Save is the answer that keeps the work; a dialog
 * that made destruction the bright button would be a dialog that deletes notebooks. `Discard` is the
 * refusal red, never the document red: it takes the edits away, which is what that colour is for
 * (`docs/interface-contract.md` §2). The three labels are the same three words everywhere in the product,
 * in English, and the body names the tab so the reader knows which document is at stake when the window is
 * closing and several are open.
 *
 * [CloseDialogHost] is the shell's half: it answers the state's [DesktopShellState.closeRequest] while the
 * window is asking and holds the card for its exit on the family's spring — the state clears the request
 * the instant a button is pressed, and without the hold the card would vanish between two frames.
 */
@Composable
internal fun CloseConfirmDialog(
    tabTitle: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopDialogFrame(
        title = "Save your changes?",
        width = DialogWidth.Compact,
        subtitle = "“$tabTitle” has edits that are not in the file yet.",
        onDismiss = onCancel,
        modifier = modifier,
        footer = {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
            PanelButton(label = "Discard", primary = false, enabled = true, destructive = true, onClick = onDiscard)
            PanelButton(label = "Save", primary = true, enabled = true, onClick = onSave)
        },
    ) {}
}

/** The shell's overlay: the card, held for the exit the spring draws — the frame draws its own scrim. */
@Composable
internal fun BoxScope.CloseDialogHost(state: DesktopShellState) {
    var lastTitle by remember { mutableStateOf("") }
    val request = state.closeRequest
    SideEffect { if (request != null) lastTitle = request.tab.title }
    GlassPresence(visible = request != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CloseConfirmDialog(
                tabTitle = request?.tab?.title ?: lastTitle,
                onSave = { state.requestCloseSave() },
                onDiscard = { state.confirmCloseDiscard() },
                onCancel = { state.cancelClose() },
            )
        }
    }
}
