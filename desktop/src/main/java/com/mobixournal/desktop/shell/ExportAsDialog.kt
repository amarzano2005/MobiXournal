package com.mobixournal.desktop.shell

import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.GlassPresence
import com.mobixournal.desktop.tools.PanelButton
import com.mobixournal.desktop.tools.glassSpring
import com.mobixournal.desktop.tools.glassSurface
import com.mobixournal.desktop.tools.hoverFill

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * **Export As** — the menu's one question with three answers, in a card over the sheet.
 *
 * The answers are a decision about a file that is about to be written, so they are asked here rather than
 * unfolding inside the menu: a menu that grows a submenu is still a menu, and the reader who opened this by
 * mistake puts it away with one `Cancel` instead of closing two panels. It is the window's own grammar — the
 * floating glass, the accent on the primary answer, English copy (`docs/interface-contract.md`).
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
    Column(Modifier.width(420.dp).glassSpring().glassSurface().padding(18.dp)) {
        Text("Export As", color = DesktopTheme.ink, style = DesktopTheme.title)
        Spacer(Modifier.height(8.dp))
        // The card asks one thing, so it says one thing: the answers under this word are the formats.
        Text("Format", color = DesktopTheme.inkSecondary, style = DesktopTheme.label)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ExportAnswerRow("PNG", "One raster image per page, 2 px per point.", onPng)
            ExportAnswerRow("SVG", "One vector file per page.", onSvg)
            ExportAnswerRow(
                "Portable Mobix (.pmobix)",
                "The notebook as one file, with what it only names embedded.",
                onPortable,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PanelButton(label = "Cancel", primary = false, enabled = true, onClick = onCancel)
        }
    }
}

/** One answer of [ExportAsDialog]: its name and the one sentence that tells it from the other two. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ExportAnswerRow(title: String, note: String, onPick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // The light follows the row's shape and nothing is *picked* here: an answer is a command, so
            // the row is lit by the pointer alone (never the steel the picked tools wear).
            .hoverFill(DesktopTheme.railSlotShape)
            .pointerInput(title) { detectTapGestures { onPick() } }
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
 * The shell's overlay for the card: the invisible blocker — nothing on the page behind a question may take a
 * stroke — and the card itself, on the family's spring, as the import question is raised.
 */
@Composable
internal fun BoxScope.ExportAsHost(
    state: DesktopShellState,
    onPng: () -> Unit,
    onSvg: () -> Unit,
) {
    GlassPresence(visible = state.exportAsOpen) {
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            ExportAsDialog(
                onPng = { state.closeExportAs(); onPng() },
                onSvg = { state.closeExportAs(); onSvg() },
                onPortable = { state.closeExportAs(); state.onPortableCopyPicker?.invoke() },
                onCancel = state::closeExportAs,
            )
        }
    }
}
