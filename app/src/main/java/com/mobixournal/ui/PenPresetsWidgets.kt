package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A compact, scrollable row of pen parameter presets.
 * Selection is based on [selectedPresetId]. Exactly one preset is selected.
 */
@Composable
fun PenPresetsRow(
    presets: List<PenPreset>,
    selectedPresetId: String,
    currentMinPressure: Float,
    currentMultiplier: Float,
    onSelectPreset: (PenPreset) -> Unit,
    onUpdatePresets: (presets: List<PenPreset>, newSelectedId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<PenPreset?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val activePreset = presets.firstOrNull { it.id == selectedPresetId } ?: presets.firstOrNull()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Presets", style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activePreset != null) {
                    IconButton(
                        onClick = { renameTarget = activePreset },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit ${activePreset.name}",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add new preset",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (preset in presets) {
                val isSelected = preset.id == selectedPresetId

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSelectPreset(preset)
                    },
                    label = {
                        Text(preset.name, style = MaterialTheme.typography.bodySmall)
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { renameTarget = preset },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Rename ${preset.name}",
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    } else null,
                )
            }
        }
    }

    if (renameTarget != null) {
        val target = renameTarget!!
        RenamePenPresetDialog(
            preset = target,
            canDelete = presets.size > 1,
            onConfirm = { newName ->
                onUpdatePresets(renamePenPreset(presets, target.id, newName), null)
                renameTarget = null
            },
            onDelete = {
                val updated = removePenPreset(presets, target.id)
                val fallbackId = updated.firstOrNull()?.id
                onUpdatePresets(updated, fallbackId)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (showAddDialog) {
        NewPenPresetDialog(
            currentMinPressure = currentMinPressure,
            currentMultiplier = currentMultiplier,
            onConfirm = { name ->
                val newPreset = PenPreset(
                    id = PenPreset.slugId(name) + "-" + System.currentTimeMillis().toString().takeLast(4),
                    name = name,
                    minimumPressure = currentMinPressure,
                    pressureMultiplier = currentMultiplier,
                )
                onUpdatePresets(addOrUpdatePenPreset(presets, newPreset), newPreset.id)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

/** Dialog to edit a preset's name (and delete custom presets). */
@Composable
fun RenamePenPresetDialog(
    preset: PenPreset,
    canDelete: Boolean,
    onConfirm: (newName: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(preset.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename preset", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim().ifEmpty { preset.name }) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

/** Dialog to name and save the current slider values as a new preset. */
@Composable
fun NewPenPresetDialog(
    currentMinPressure: Float,
    currentMultiplier: Float,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New pen preset", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Save current parameters (%.2f× / %.2f×) as a preset:"
                        .format(currentMinPressure, currentMultiplier),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset name") },
                    placeholder = { Text("e.g. Soft pencil") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
