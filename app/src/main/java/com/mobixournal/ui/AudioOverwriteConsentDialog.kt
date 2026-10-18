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
 * Consent dialog shown when attempting to record audio on a tab/page that already contains an existing recording.
 * Warns that continuing will overwrite or replace the previous audio binding.
 * Wears the standard frosted-glass floating material (`FloatingDialog`).
 */
@Composable
fun AudioOverwriteConsentDialog(
    existingClip: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Existing recording") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This note already has an associated audio recording (\"$existingClip\").",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Recording now will overwrite the previous audio for this tab/page. Do you want to continue?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Continue", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
