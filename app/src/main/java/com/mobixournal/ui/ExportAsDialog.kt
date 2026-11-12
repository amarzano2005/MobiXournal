package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog

/**
 * The **Export As** card's host: the floating flag on [EditorUiState.exportAsOpen], the card itself, and
 * the three commands its answers run.
 *
 * It lives here, next to the other chooser dialogs, rather than inside the hamburger — a menu's window is
 * torn down the moment the menu closes, so a card composed *in* it would leave with it. The flag is on
 * [ui] for the same reason: the answer outlives the panel that asked the question, exactly as `Save As`
 * and `Import PDF` do (`EditorOverlays` owns the screen's one list of what is open).
 *
 * `DialogPresence` keeps the card composed while its spring plays out, so an answer leaves on the same
 * animation every other surface here leaves on, and `FloatingDialog` gives it the family's material — the
 * glass fill at the floating alpha, the rim, the panel corner — rather than an opaque Material card.
 */
@Composable
internal fun ExportAsHost(
    open: Boolean,
    onPng: () -> Unit,
    onSvg: () -> Unit,
    onPortable: () -> Unit,
    onDismiss: () -> Unit,
) {
    DialogPresence(open) {
        ExportAsDialog(
            onPng = { onDismiss(); onPng() },
            onSvg = { onDismiss(); onSvg() },
            onPortable = { onDismiss(); onPortable() },
            onDismiss = onDismiss,
        )
    }
}

/** The three answers **Export As** offers: the page twice, then the whole notebook as one file. */
private enum class ExportAnswer { PNG, SVG, PORTABLE }

/**
 * **Export As** — the hamburger's one question with three answers, asked in a card over the sheet.
 *
 * It asks the way `Save As` and `Import PDF` ask: the answers are **radio rows** that say what each one
 * writes and stay readable until the reader commits, and one button runs the picked one. Pressing a row
 * used to run its export on the spot, which made a mis-tap into a folder dialog the reader never wanted;
 * here a row only moves the mark, and `Export` is the one thing that writes.
 *
 * The answers themselves are PNG (one raster image per page), SVG (one vector file per page) and
 * `.pmobix` (the whole notebook as a single file carrying the pictures, PDFs and recordings it otherwise
 * only names — `docs/mobix-assets.md`).
 */
@Composable
private fun ExportAsDialog(
    onPng: () -> Unit,
    onSvg: () -> Unit,
    onPortable: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(ExportAnswer.PNG) }
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export As") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "What should be written?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FormatOption(
                    selected = picked == ExportAnswer.PNG,
                    title = "PNG",
                    subtitle = "One raster image per page, two pixels per point.",
                    onClick = { picked = ExportAnswer.PNG },
                )
                FormatOption(
                    selected = picked == ExportAnswer.SVG,
                    title = "SVG",
                    subtitle = "One vector file per page, at the page's own size.",
                    onClick = { picked = ExportAnswer.SVG },
                )
                FormatOption(
                    selected = picked == ExportAnswer.PORTABLE,
                    title = ".pmobix",
                    subtitle = "The whole notebook as one file, with the files it only names embedded.",
                    onClick = { picked = ExportAnswer.PORTABLE },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (picked) {
                        ExportAnswer.PNG -> onPng()
                        ExportAnswer.SVG -> onSvg()
                        ExportAnswer.PORTABLE -> onPortable()
                    }
                },
            ) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
