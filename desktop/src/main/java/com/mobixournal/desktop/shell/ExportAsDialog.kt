package com.mobixournal.desktop.shell

import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.DesktopDialogFrame
import com.mobixournal.desktop.tools.DialogWidth
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.hoverFill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * **Export As** — the menu's one question with three answers, in a card over the sheet.
 *
 * The answers are a decision about a file that is about to be written, so they are asked here rather than
 * unfolding inside the menu: a menu that grows a submenu is still a menu, and the reader who opened this by
 * mistake puts it away with one `Cancel` instead of closing two panels. It is the window's own modal
 * ([DesktopDialogFrame]): the scrim, the opaque popup surface, the accent on the primary answer, English
 * copy (`docs/interface-contract.md`).
 *
 * The three answers are the page as a **raster**, the page as a **vector**, and the notebook as **`.pmobix`**
 * — an export that carries the pictures, PDFs and recordings the notebook only names, and never the file the
 * tab keeps editing (`docs/mobix-assets.md`).
 */
@Composable
internal fun ExportAsDialog(
    onPng: () -> Unit,
    onSvg: () -> Unit,
    onPortable: () -> Unit,
    onCancel: () -> Unit,
) {
    DesktopDialogFrame(
        title = "Export As",
        width = DialogWidth.Standard,
        subtitle = "Format",
        onDismiss = onCancel,
        footer = {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ExportAnswerRow("PNG", "One raster image per page, 2 px per point.", onPng)
            ExportAnswerRule()
            ExportAnswerRow("SVG", "One vector file per page.", onSvg)
            ExportAnswerRule()
            ExportAnswerRow(
                "Portable MobiXournal (.pmobix)",
                "The notebook as one file, with what it only names embedded.",
                onPortable,
            )
        }
    }
}

/**
 * The hairline between two answers — the rule the Settings pages draw between their rows, so the three
 * formats read as three separate commands instead of one list of text.
 */
@Composable
private fun ExportAnswerRule() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(1.dp)
            .background(DesktopTheme.hairline),
    )
}

/**
 * One answer of [ExportAsDialog]: its name and the one sentence that tells it from the other two.
 *
 * It is a control, so it wears the contract's control corner (4 dp, [DesktopTheme.controlShape]) rather
 * than the popup row's, and its focus ring is the accent hairline a keyboard reader needs to see which
 * answer a press would take.
 */
@Composable
private fun ExportAnswerRow(title: String, note: String, onPick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            // The light follows the row's shape and nothing is *picked* here: an answer is a command, so
            // the row is lit by the pointer alone (never the steel the picked tools wear).
            .hoverFill(DesktopTheme.controlShape, focused = focused)
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onPick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DesktopTheme.ink, style = DesktopTheme.body)
            Spacer(Modifier.height(2.dp))
            Text(note, color = DesktopTheme.inkMuted, style = DesktopTheme.label)
        }
    }
}

/**
 * The shell's overlay for the card: the card itself, on the family's spring. The frame supplies the
 * blocker — nothing on the page behind a question may take a stroke.
 */
@Composable
internal fun BoxScope.ExportAsHost(
    state: DesktopShellState,
    onPng: () -> Unit,
    onSvg: () -> Unit,
) {
    GlassPresence(visible = state.exportAsOpen) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ExportAsDialog(
                onPng = { state.closeExportAs(); onPng() },
                onSvg = { state.closeExportAs(); onSvg() },
                onPortable = { state.closeExportAs(); state.onPortableCopyPicker?.invoke() },
                onCancel = state::closeExportAs,
            )
        }
    }
}
