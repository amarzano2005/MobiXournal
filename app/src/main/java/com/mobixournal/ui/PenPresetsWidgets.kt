package com.mobixournal.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    selectedPresetId: String?,
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
                if (activePreset != null && presets.size > 1) {
                    val activeIndex = presets.indexOf(activePreset)
                    IconButton(
                        onClick = {
                            if (activeIndex > 0) {
                                onUpdatePresets(movePenPreset(presets, activeIndex, -1), activePreset.id)
                            }
                        },
                        enabled = activeIndex > 0,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move ${activePreset.name} earlier",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (activeIndex < presets.lastIndex) {
                                onUpdatePresets(movePenPreset(presets, activeIndex, 1), activePreset.id)
                            }
                        },
                        enabled = activeIndex < presets.lastIndex,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move ${activePreset.name} later",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
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
        val targetIdx = presets.indexOf(target)
        RenamePenPresetDialog(
            preset = target,
            canDelete = presets.size > 1,
            canMoveEarlier = targetIdx > 0,
            canMoveLater = targetIdx in 0 until presets.lastIndex,
            onMove = { delta ->
                val curIdx = presets.indexOf(target)
                if (curIdx >= 0) {
                    val updated = movePenPreset(presets, curIdx, delta)
                    onUpdatePresets(updated, target.id)
                    renameTarget = updated.firstOrNull { it.id == target.id }
                }
            },
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
            onConfirm = { name ->
                val newPreset = PenPreset(
                    id = PenPreset.slugId(name) + "-" + System.currentTimeMillis().toString().takeLast(4),
                    name = name,
                    minimumPressure = PenPreset.FACTORY_DEFAULT_MIN_PRESSURE,
                    pressureMultiplier = PenPreset.FACTORY_DEFAULT_MULTIPLIER,
                )
                onUpdatePresets(addOrUpdatePenPreset(presets, newPreset), newPreset.id)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

/** Dialog to edit a preset's name and order (and delete custom presets). */
@Composable
fun RenamePenPresetDialog(
    preset: PenPreset,
    canDelete: Boolean,
    canMoveEarlier: Boolean = false,
    canMoveLater: Boolean = false,
    onMove: ((delta: Int) -> Unit)? = null,
    onConfirm: (newName: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(preset.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit preset", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onMove != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Order:", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { onMove(-1) },
                                enabled = canMoveEarlier,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Earlier", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { onMove(1) },
                                enabled = canMoveLater,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("Later", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
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

/** Dialog to name a new preset initialized with default parameters (0.05× / 1.00×). */
@Composable
fun NewPenPresetDialog(
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
                    "Creates a preset with default parameters (0.05× / 1.00×). You can adjust them at any time.",
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
