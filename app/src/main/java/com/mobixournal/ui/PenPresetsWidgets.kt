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
import androidx.compose.material3.Checkbox
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
import kotlin.math.abs

/**
 * A compact, scrollable row of pen parameter presets.
 * Selection is tracked by preset ID so at most one preset is ever highlighted,
 * even when multiple presets have similar or identical parameter values.
 */
@Composable
fun PenPresetsRow(
    presets: List<PenPreset>,
    currentMinPressure: Float,
    currentMultiplier: Float,
    onSelectPreset: (PenPreset) -> Unit,
    onUpdatePresets: (List<PenPreset>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Explicitly tracked selected preset ID
    var selectedPresetId by remember {
        mutableStateOf(
            presets.firstOrNull {
                abs(it.minimumPressure - currentMinPressure) < 0.001f &&
                    abs(it.pressureMultiplier - currentMultiplier) < 0.005f
            }?.id ?: presets.firstOrNull()?.id,
        )
    }
    var renameTarget by remember { mutableStateOf<PenPreset?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val activePreset = presets.firstOrNull { it.id == selectedPresetId }
    val isExactMatch = activePreset != null &&
        abs(activePreset.minimumPressure - currentMinPressure) < 0.001f &&
        abs(activePreset.pressureMultiplier - currentMultiplier) < 0.005f

    // Only highlight if the active preset actually matches current slider values
    val highlightedId = if (isExactMatch) selectedPresetId else null

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
                        contentDescription = "Add current parameters as preset",
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
                val isSelected = preset.id == highlightedId

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedPresetId = preset.id
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
            currentMinPressure = currentMinPressure,
            currentMultiplier = currentMultiplier,
            canDelete = presets.size > 1,
            onConfirm = { newName, shouldUpdateValues ->
                val updatedPreset = if (shouldUpdateValues) {
                    target.copy(
                        name = newName,
                        minimumPressure = currentMinPressure,
                        pressureMultiplier = currentMultiplier,
                    )
                } else {
                    target.copy(name = newName)
                }
                selectedPresetId = updatedPreset.id
                onUpdatePresets(addOrUpdatePenPreset(presets, updatedPreset))
                renameTarget = null
            },
            onDelete = {
                onUpdatePresets(removePenPreset(presets, target.id))
                if (selectedPresetId == target.id) {
                    selectedPresetId = presets.firstOrNull { it.id != target.id }?.id
                }
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
                selectedPresetId = newPreset.id
                onUpdatePresets(addOrUpdatePenPreset(presets, newPreset))
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

/** Dialog to edit a preset's name and optionally update its values with current sliders. */
@Composable
fun RenamePenPresetDialog(
    preset: PenPreset,
    currentMinPressure: Float,
    currentMultiplier: Float,
    canDelete: Boolean,
    onConfirm: (newName: String, updateValues: Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(preset.name) }
    val valuesDiffer = abs(preset.minimumPressure - currentMinPressure) > 0.001f ||
        abs(preset.pressureMultiplier - currentMultiplier) > 0.005f
    var updateValues by remember { mutableStateOf(valuesDiffer) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit preset", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (valuesDiffer) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { updateValues = !updateValues },
                    ) {
                        Checkbox(
                            checked = updateValues,
                            onCheckedChange = { updateValues = it },
                        )
                        Text(
                            "Update values to current (%.2f× / %.2f×)"
                                .format(currentMinPressure, currentMultiplier),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim().ifEmpty { preset.name }, updateValues) },
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
