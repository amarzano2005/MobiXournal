package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.FloatingDialog

/**
 * Consent dialog shown when opening a .mobix document that requires a newer version of MobiXournal
 * (`minAppVersion > appVersion`). Explains that the document was written by a newer version and offers
 * to open it in read-only mode so existing data is preserved without corruption, or cancel.
 */
@Composable
fun NewerAppConsentDialog(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Newer version document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "\"$name\" was written by a newer version of MobiXournal and may contain features this version cannot safely save without losing data.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Open read-only to view the document? Drawing, saving, and exporting will be disabled.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Open read-only")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
