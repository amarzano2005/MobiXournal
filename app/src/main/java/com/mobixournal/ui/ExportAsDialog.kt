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
private enum class ExportAnswer { PNG, SVG }

/**
 * **Export As** — the hamburger's export question, asked in a card over the sheet.
 */
@Composable
private fun ExportAsDialog(
    onPng: () -> Unit,
    onSvg: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onPortable: () -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(ExportAnswer.PNG) }
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export As") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Format", style = MaterialTheme.typography.labelMedium)
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (picked) {
                        ExportAnswer.PNG -> onPng()
                        ExportAnswer.SVG -> onSvg()
                    }
                },
            ) { Text("Export") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
