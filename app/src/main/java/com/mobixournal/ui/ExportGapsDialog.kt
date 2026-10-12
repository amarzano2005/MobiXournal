package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.mobixournal.render.Uncompilable

/**
 * What an export could not draw, said once, in the terms the user wrote it in: a symbol this build has
 * no drawing for, a formula that would not evaluate, an element from a newer build with no `.xopp`
 * spelling.
 *
 * The export still **succeeded** — everything else is in the file — so this is a report, not an error: it
 * names what is missing from the drawing and dismisses. Silence would be the worse choice, because a
 * table exported as nothing at all is a document that lies about its contents.
 *
 * It lives on its own rather than among the editor's other dialogs because the export path is the only
 * thing that opens it, and its caller is the activity (which does the writing), not the editor's own
 * state.
 */
@Composable
fun ExportGapsDialog(
    gaps: List<Uncompilable>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (gaps.size == 1) "One thing could not be drawn" else "${gaps.size} things could not be drawn")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The export is complete otherwise. These were left out:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                // A document can repeat one loss (a plot whose four curves all fail to parse), so the
                // same wording is shown once — the count in the title already says how many there are.
                gaps.distinctBy { it.type to it.reason }.forEach { gap ->
                    Text("• ${gap.reason}", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
