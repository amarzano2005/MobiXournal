package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mobixournal.ui.theme.FloatingDialog
import com.mobixournal.ui.theme.ModernTheme
import com.mobixournal.ui.theme.chromeIsDark
import com.mobixournal.ui.theme.rememberChromeInk

/**
 * Compact Liquid Glass dialog opened by long-pressing the Focus floating bar's drag handle so the
 * user can swap, reorder, add, or reset the bar's slots without leaving Focus mode.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocusBarCustomizeDialog(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val slots = resolveFocusBarSlots(settings.focusBarSlots)
    var picked by remember { mutableStateOf(0) }
    val index = picked.coerceIn(0, (slots.size - 1).coerceAtLeast(0))
    val selected = slots[index]

    FloatingDialog(
        onDismissRequest = onDismiss,
        title = { Text("Focus floating bar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Tap a slot to select it, then choose its tool or reorder it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FocusBarSlotPreviewStrip(
                    slots = slots,
                    selectedIndex = index,
                    onSelect = { picked = it },
                )
                FocusBarSlotOrderRow(
                    index = index,
                    total = slots.size,
                    selected = selected,
                    onMove = { delta ->
                        val moved = moveFocusBarSlot(slots, index, delta)
                        onSettingsChange(settings.copy(focusBarSlots = moved.map { it.id }))
                        picked = (index + delta).coerceIn(0, moved.lastIndex)
                    },
                    onRemove = {
                        val next = updateFocusBarSlotList(slots, index, null)
                        onSettingsChange(settings.copy(focusBarSlots = next.map { it.id }))
                        picked = index.coerceAtMost(next.lastIndex)
                    },
                )
                FocusBarSlotPickerGrid(
                    selected = selected,
                    onPick = { candidate ->
                        val next = updateFocusBarSlotList(slots, index, candidate)
                        onSettingsChange(settings.copy(focusBarSlots = next.map { it.id }))
                    },
                )
                if (slots.size < FOCUS_BAR_MAX_SLOTS) {
                    OutlinedButton(
                        onClick = {
                            val next = appendFocusBarSlot(slots)
                            onSettingsChange(settings.copy(focusBarSlots = next.map { it.id }))
                            picked = next.lastIndex
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add a slot")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (settings.focusBarSlots.isNotEmpty() || settings.focusBarAnchor.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onSettingsChange(settings.copy(focusBarSlots = emptyList(), focusBarAnchor = ""))
                        picked = 0
                    },
                ) {
                    Text("Reset to defaults")
                }
            }
        },
    )
}

@Composable
private fun FocusBarSlotOrderRow(
    index: Int,
    total: Int,
    selected: FocusBarSlotKind,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Slot ${index + 1}: ${selected.label}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        IconButton(enabled = index > 0, onClick = { onMove(-1) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "Move left")
        }
        IconButton(enabled = index < total - 1, onClick = { onMove(+1) }) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "Move right")
        }
        if (total > FOCUS_BAR_MIN_SLOTS) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove slot")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusBarSlotPickerGrid(
    selected: FocusBarSlotKind,
    onPick: (FocusBarSlotKind) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (candidate in FocusBarSlotKind.entries) {
            val isChosen = candidate == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isChosen) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    )
                    .border(
                        width = 1.dp,
                        color = if (isChosen) MaterialTheme.colorScheme.primary
                        else ModernTheme.hairlineBorder(chromeIsDark()),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onPick(candidate) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = candidate.label.substringBefore(" ("),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isChosen) MaterialTheme.colorScheme.onPrimaryContainer else rememberChromeInk(),
                )
            }
        }
    }
}
