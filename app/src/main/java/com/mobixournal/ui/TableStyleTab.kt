package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobixournal.design.PenPalette
import kotlin.math.abs

private val BORDER_WIDTH_PRESETS = listOf(0.5, 1.0, 1.5, 2.0, 3.0)

/**
 * Layout & Style tab inside the live table inspector: table dimensions, even column/row distribution,
 * header row toggle, border thickness presets and palette border color.
 */
@Composable
internal fun TableLayoutStyleTab(
    state: TableCellEditorState,
    onCommitLive: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Grid", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableDimensionStepper(
                label = "Rows",
                count = state.rowHeights.size,
                canDecrement = state.rowHeights.size > 1,
                canIncrement = state.rowHeights.size < 50,
                onDecrement = { state.removeRow(); onCommitLive() },
                onIncrement = { state.addRow(); onCommitLive() },
                modifier = Modifier.weight(1f),
            )
            TableDimensionStepper(
                label = "Cols",
                count = state.colWidths.size,
                canDecrement = state.colWidths.size > 1,
                canIncrement = state.colWidths.size < 50,
                onDecrement = { state.removeColumn(); onCommitLive() },
                onIncrement = { state.addColumn(); onCommitLive() },
                modifier = Modifier.weight(1f),
            )
        }
        InspectorChip(
            label = "Distribute evenly",
            selected = false,
            onClick = { state.distributeEvenly(); onCommitLive() },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Header row", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = state.headerRow,
                onCheckedChange = { enabled ->
                    state.toggleHeaderRow(enabled)
                    onCommitLive()
                },
            )
        }

        TableBorderControls(state = state, onCommitLive = onCommitLive)
    }
}

@Composable
private fun TableBorderControls(
    state: TableCellEditorState,
    onCommitLive: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Border width", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (preset in BORDER_WIDTH_PRESETS) {
                val label = if (preset == preset.toInt().toDouble()) {
                    "${preset.toInt()} pt"
                } else {
                    "$preset pt"
                }
                InspectorChip(
                    label = label,
                    selected = abs(state.borderWidth - preset) < 0.05,
                    onClick = {
                        state.setBorderWidth(preset)
                        onCommitLive()
                    },
                )
            }
        }

        Text("Border colour", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val selectedRgb = state.borderColor and 0x00FFFFFF
            for (swatch in PenPalette.COLORS) {
                ColorSwatch(
                    color = swatch,
                    selected = (swatch and 0x00FFFFFF) == selectedRgb,
                    onClick = {
                        state.setBorderColor(swatch)
                        onCommitLive()
                    },
                    compact = true,
                )
            }
            Spacer(Modifier.width(2.dp))
        }
    }
}

@Composable
private fun TableDimensionStepper(
    label: String,
    count: Int,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(shape)
            .background(scheme.surfaceVariant.copy(alpha = 0.28f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), shape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepperIconButton(
                icon = Icons.Outlined.Remove,
                contentDescription = "Decrease $label",
                enabled = canDecrement,
                onClick = onDecrement,
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            StepperIconButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Increase $label",
                enabled = canIncrement,
                onClick = onIncrement,
            )
        }
    }
}

@Composable
private fun StepperIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(14.dp),
            tint = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
        )
    }
}
