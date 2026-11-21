package com.mobixournal.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.DialogPresence
import com.mobixournal.ui.theme.FloatingDialog

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
                    "Export failed: ${e.localizedMessage ?: "Unable to save backup file"}",
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
                    "Import failed: ${e.localizedMessage ?: "Unable to read backup file"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    SettingsGroup("Backup & reset") {
        BackupActionRow(
            title = "Export to JSON",
            subtitle = "Save all settings to a backup file.",
            icon = Icons.Outlined.FileDownload,
            onClick = { exportLauncher.launch("mobixournal-settings.json") },
        )
        SettingsDivider()
        BackupActionRow(
            title = "Import from JSON",
            subtitle = "Restore settings from a previous backup file.",
            icon = Icons.Outlined.FileUpload,
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        )
        SettingsDivider()
        BackupActionRow(
            title = "Reset to Defaults",
            subtitle = "Restore all settings to factory defaults.",
            icon = Icons.Outlined.RestartAlt,
            tint = MaterialTheme.colorScheme.error,
            onClick = { showResetDialog = true },
        )
    }

    DialogPresence(showResetDialog) {
        FloatingDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset all settings?") },
            text = {
                Text(
                    "All settings will be restored to their factory defaults. Document files will not be affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    onChange(settings.factoryDefaults())
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

@Composable
private fun BackupActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val reach = LocalSettingsCardInset.current
    Row(
        modifier = Modifier
            .fullBleedRow(reach)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = reach, vertical = settingsCellInset(reach)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        ControlHeader(title, subtitle, Modifier.weight(1f))
    }
}
