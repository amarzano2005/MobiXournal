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
 * The card an open shows before it disables writing: a file written for a newer build
 * (`minAppVersion`) or one requiring a feature this build cannot keep whole.
 *
 * It shows the **verdict's own sentence** ([body]) and names the document, because the alternative —
 * copy written here — is a second account of the same decision, free to disagree with the actions the
 * shell disables ([openVerdictNotice] is where that sentence and those actions are decided together).
 * The two outcomes differ in [title] and [confirmLabel]; the rest is the contract's wording.
 */
@Composable
fun NewerAppConsentDialog(
    name: String,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("\"$name\"", style = MaterialTheme.typography.bodyMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
