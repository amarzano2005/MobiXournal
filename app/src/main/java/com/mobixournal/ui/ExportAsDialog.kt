package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

/**
 * **Export As** — the hamburger's one question with three answers, asked in a card over the sheet.
 *
 * An answer is a **command**, not a choice kept on screen: PNG writes one raster image per page, SVG one
 * vector file per page, and `.pmobix` the whole notebook as a single file that carries the pictures, PDFs
 * and recordings it otherwise only names (`docs/mobix-assets.md`). That is why they are buttons on the
 * card's own footer and not the radio rows `Save As` and `Import PDF` wear — nothing here stays selected
 * after the answer, the answer closes the card and asks for a destination.
 */
@Composable
private fun ExportAsDialog(
    onPng: () -> Unit,
    onSvg: () -> Unit,
    onPortable: () -> Unit,
    onDismiss: () -> Unit,
) {
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export As") },
        text = {
            Text(
                "PNG writes one image per page, SVG one vector file per page, and .pmobix the whole " +
                    "notebook with the files it only names.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPng) { Text("PNG") }
                TextButton(onClick = onSvg) { Text("SVG") }
                TextButton(onClick = onPortable) { Text(".pmobix") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
