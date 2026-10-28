package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * **The Save As question**: which of the window's three containers the document is written as.
 *
 * The system file dialog is a *name* box — on Windows its filter is ignored, and a suffix typed into it is
 * all a save had to go on — so Save As used to write whatever the document already was unless the reader
 * remembered to retype the extension. The container is a **document** decision, not a filename one
 * (`docs/data-path.md`), so the window asks it as its own question, in its own grammar
 * (`docs/interface-contract.md`: the floating glass, the accent on the answer that keeps the work), and the
 * native dialog then only names the file.
 *
 * The three answers are [DesktopNewDocumentFormat]'s — `.mobix`, `.xopp` and the zipped `.xopp` package —
 * the same three the Documents section offers for a *new* file, so a reader meets one list of formats in
 * both places. The one that matches the open document is picked when the card opens: the common answer is
 * "the one I already have", and Save As should not make it a decision every time.
 *
 * @property open whether the card is up. [SaveAsChoice] holds it outside the shell state because it is a
 *   question about the *next* write, not a preference the window remembers.
 */
internal class SaveAsChoice {

    var open by mutableStateOf(false)
        private set

    /** Ask the question — the menu's *Save As*, the header's button and `Ctrl + Shift + S`. */
    fun request() {
        open = true
    }

    /** Put it away — Cancel, `Esc`, or an answer already given. */
    fun dismiss() {
        open = false
    }
}

/**
 * The card itself: the three containers as rows, the picked one lit in the accent, and the two words that
 * end the question.
 *
 * A row carries its own sentence under its name because a reader choosing between `.xopp` and its zipped
 * form is asking what the difference **is** — the Save As card is the one place that has to answer it,
 * and the panel's help line is not drawn here.
 */
@Composable
internal fun SaveAsFormatDialog(
    initial: DesktopNewDocumentFormat,
    onChoose: (DesktopNewDocumentFormat) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picked by remember(initial) { mutableStateOf(initial) }
    Column(modifier.width(400.dp).glassSpring().glassSurface().padding(18.dp)) {
        Text("Save as", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(6.dp))
        Text(
            "Choose the file this document is written as.",
            color = DesktopTheme.inkSecondary,
            style = DesktopTheme.body,
        )
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (format in DesktopNewDocumentFormat.entries) {
                SaveFormatRow(
                    format = format,
                    picked = format == picked,
                    onPick = { picked = format },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
            Spacer(Modifier.width(8.dp))
            PanelButton(
                label = "Choose file…",
                primary = true,
                enabled = true,
                onClick = { onChoose(picked) },
            )
        }
    }
}

/** One answer of [SaveAsFormatDialog]: its name, its sentence, and the mark that says it is the picked one. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SaveFormatRow(format: DesktopNewDocumentFormat, picked: Boolean, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .hoverFill(
                DesktopTheme.railSlotShape,
                active = picked,
                base = if (picked) DesktopTheme.activeSlot else Color.Transparent,
            )
            .pointerInput(format) { detectTapGestures { onPick() } }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                format.label,
                color = if (picked) DesktopTheme.accent else DesktopTheme.ink,
                style = DesktopTheme.body,
            )
            Spacer(Modifier.height(2.dp))
            Text(format.summary, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(12.dp)
                .background(
                    if (picked) DesktopTheme.accent else Color.Transparent,
                    DesktopTheme.capsuleShape,
                )
                .border(
                    1.dp,
                    if (picked) DesktopTheme.accent else DesktopTheme.hairline,
                    DesktopTheme.capsuleShape,
                ),
        )
    }
}

/**
 * The shell's overlay for that card: the invisible blocker — nothing on the page behind a question may take
 * a stroke — and the card, held for its exit on the family's spring.
 *
 * With no document open there is nothing to save, so the question is not drawn at all rather than answered
 * with a disabled card.
 */
@Composable
internal fun BoxScope.SaveAsFormatHost(
    choice: SaveAsChoice,
    state: DesktopShellState,
    onChoose: (DesktopNewDocumentFormat) -> Unit,
) {
    val tab = state.active
    GlassPresence(visible = choice.open && tab != null) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            if (tab != null) {
                SaveAsFormatDialog(
                    initial = DesktopNewDocumentFormat.ofContainer(tab.document.container),
                    onChoose = { format ->
                        choice.dismiss()
                        onChoose(format)
                    },
                    onCancel = { choice.dismiss() },
                )
            }
        }
    }
}
