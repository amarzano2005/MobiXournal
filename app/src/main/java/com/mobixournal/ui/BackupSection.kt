package com.mobixournal.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Backup and restore settings: export current configuration to a JSON file or import
 * from an existing backup. An import accepts **only** a compatible backup ([AppSettingsBackup.validate])
 * and reports the reason otherwise; older backups still import, newer ones are refused rather than
 * silently downgraded.
 */
@Composable
fun BackupSection(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(AppSettingsBackup.toJson(settings).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Settings exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Export failed: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalStateException("Could not open file")

                when (val check = AppSettingsBackup.validate(json)) {
                    is BackupCheck.Ok -> {
                        onChange(check.settings)
                        Toast.makeText(context, "Settings imported successfully", Toast.LENGTH_SHORT).show()
                    }
                    // Anything that isn't a compatible backup is refused outright, with the reason.
                    else -> Toast.makeText(context, check.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Import failed: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    Text("Export settings", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Save your preferences, colors, shortcuts, toolbars, and stylus settings to a JSON file. " +
            "The exported backup can be restored on another device or a different version of MobiXournal.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { exportLauncher.launch("mobixournal-settings.json") },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.FileDownload, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Export to JSON")
    }

    HorizontalDivider(Modifier.padding(vertical = 16.dp))

    Text("Import settings", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Load preferences from a previously exported JSON file. Missing settings will default to factory " +
            "values; a file that isn't a MobiXournal backup — or one written by a newer version of the app — " +
            "is refused with an error.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.FileUpload, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Import from JSON")
    }

    HorizontalDivider(Modifier.padding(vertical = 16.dp))

    Text("Reset settings", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "Restore all preferences back to factory defaults.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { showResetDialog = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.RestartAlt, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Reset to Defaults")
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all settings?") },
            text = { Text("This will restore all preferences, colors, shortcuts, and toolbars to factory defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onChange(AppSettings())
                    Toast.makeText(context, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
