package com.mobixournal.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * The one question the window asks before it can lose work: **save, discard or cancel**.
 *
 * It is a **floating surface** (`docs/ui-modernization.md`): glass over the page, the 20 dp panel corner,
 * centred, with the accent on Save — because Save is the answer that keeps the work, and a dialog that
 * makes destruction the bright button is a dialog that deletes notebooks. The three labels are the same
 * three words everywhere in the product, in English, and the body names the tab so the user knows which
 * document is at stake when the window is closing and several are open.
 *
 * [CloseDialogHost] is the shell's half: it answers the state's [DesktopShellState.closeRequest] while
 * the window is asking, swallows the pointer so a stroke cannot land on the page behind it, and holds the
 * card for its exit on the family's spring — the state clears the request the instant a button is pressed,
 * and without the hold the card would vanish between two frames.
 */
@Composable
internal fun CloseConfirmDialog(
    tabTitle: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(360.dp).glassSpring().glassSurface().padding(18.dp)) {
        Text("Save your changes?", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "“$tabTitle” has edits that are not in the file yet.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            PanelButton(label = "Discard", primary = false, enabled = true, onClick = onDiscard)
            Spacer(Modifier.width(8.dp))
            PanelButton(label = "Save", primary = true, enabled = true, onClick = onSave)
        }
    }
}

/** The shell's overlay: the blocker and the card, held for the exit the spring draws. */
@Composable
internal fun BoxScope.CloseDialogHost(state: DesktopShellState) {
    var lastTitle by remember { mutableStateOf("") }
    val request = state.closeRequest
    SideEffect { if (request != null) lastTitle = request.tab.title }
    GlassPresence(visible = request != null) {
        Box(
            Modifier
                .fillMaxSize()
                // Nothing on the page behind a question may take a stroke: an invisible blocker, because
                // a scrim would be a fourth material the contract does not have.
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            CloseConfirmDialog(
                tabTitle = request?.tab?.title ?: lastTitle,
                onSave = { state.confirmCloseSave() },
                onDiscard = { state.confirmCloseDiscard() },
                onCancel = { state.cancelClose() },
            )
        }
    }
}
