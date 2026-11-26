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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import com.mobixournal.design.PenPalette
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.ui.theme.FloatingSurface
import com.mobixournal.ui.theme.LocalFloatingSurfaceClosing
import com.mobixournal.ui.theme.liquidGlassSpringAnimation
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

/**
 * Compact spreadsheet-style formula bar shown just above the open system IME so the sheet
 * above stays unobstructed and the user can jump between cells using directional arrows.
 */
@Composable
internal fun TableCompactFormulaBar(
    state: TableCellEditorState,
    onCommitLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.cells.getOrNull(state.currentRow)?.getOrNull(state.currentCol) ?: TableCell()
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Throwable) {}
    }

    FloatingSurface(
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .liquidGlassSpringAnimation(
                expanded = !LocalFloatingSurfaceClosing.current,
                origin = TransformOrigin(0.5f, 1f),
                role = DesignTokens.GlassMotion.SurfaceRole.POPOVER,
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FormulaCellBadge(row = state.currentRow, col = state.currentCol)
                BasicTextField(
                    value = active.text,
                    onValueChange = { text ->
                        state.setText(state.currentRow, state.currentCol, text)
                        onCommitLive()
                    },
                    singleLine = true,
                    cursorBrush = SolidColor(scheme.primary),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = scheme.onSurface,
                        fontWeight = if (active.bold) FontWeight.Bold else FontWeight.Normal,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrect = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surface)
                        .border(1.5.dp, scheme.primary, RoundedCornerShape(8.dp))
                        .focusRequester(focusRequester)
                        .padding(horizontal = 9.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() }
                    },
                )
                FormulaArrowButton(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous column") {
                    state.moveBy(0, -1)
                }
                FormulaArrowButton(Icons.Outlined.KeyboardArrowUp, "Previous row") {
                    state.moveBy(-1, 0)
                }
                FormulaArrowButton(Icons.Outlined.KeyboardArrowDown, "Next row") {
                    state.moveBy(1, 0)
                }
                FormulaArrowButton(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next column") {
                    state.moveBy(0, 1)
                }
                FormulaArrowButton(Icons.Outlined.KeyboardHide, "Hide keyboard") {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            }
            FormulaBarActionsRow(state = state, active = active, onCommitLive = onCommitLive)
        }
    }
}

@Composable
private fun FormulaCellBadge(row: Int, col: Int) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.28f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "R${row + 1} · ${columnLabel(col)}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FormulaArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.28f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp), tint = scheme.onSurface)
    }
}

@Composable
private fun FormulaBarActionsRow(
    state: TableCellEditorState,
    active: TableCell,
    onCommitLive: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.28f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableSegmentButton(
                selected = active.align == TableAlign.LEFT,
                imageVector = Icons.AutoMirrored.Outlined.FormatAlignLeft,
                description = "Align left",
                onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.LEFT); onCommitLive() },
            )
            TableSegmentButton(
                selected = active.align == TableAlign.CENTER,
                imageVector = Icons.Outlined.FormatAlignCenter,
                description = "Align center",
                onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.CENTER); onCommitLive() },
            )
            TableSegmentButton(
                selected = active.align == TableAlign.RIGHT,
                imageVector = Icons.AutoMirrored.Outlined.FormatAlignRight,
                description = "Align right",
                onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.RIGHT); onCommitLive() },
            )
        }
        TableBoldButton(
            selected = active.bold,
            onClick = { state.toggleBold(state.currentRow, state.currentCol); onCommitLive() },
        )
        InspectorChip(
            label = "+ Row",
            selected = false,
            onClick = {
                if (state.rowHeights.size < 50) {
                    state.addRow(afterRow = state.currentRow)
                    onCommitLive()
                }
            },
        )
        InspectorChip(
            label = "+ Col",
            selected = false,
            onClick = {
                if (state.colWidths.size < 50) {
                    state.addColumn(afterCol = state.currentCol)
                    onCommitLive()
                }
            },
        )
    }
}
