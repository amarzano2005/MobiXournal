package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.TableElementFactory
import com.mobixournal.render.replaceTableElement
import com.mobixournal.ui.theme.FloatingSurface
import com.mobixournal.ui.theme.liquidGlassSpringAnimation


/**
 * The fixed, **compact** table editor: every cell's text editable at once, on the same frosted
 * glass the rest of the floating chrome wears ([FloatingSurface] fill + rim + the family's fluid
 * entry spring) instead of a full-width material card. Save applies the whole grid to the table
 * in a single `replaceTableElement` (one undo step).
 *
 * Opened by **Edit table** on a selected table (`SelectionActionAnchor`) or a double-tap on a
 * cell (`DrawingSurfaceInput`); the tapped (or first) cell starts focused. Align/bold act on the
 * cell the user last focused (`TableCellEditorState.current*`).
 */
@Composable
fun TableCellEditorDialog(
    ui: EditorUiState,
    pane: PaneState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { ui.editingTableCell = null },
) {
    val editing = ui.editingTableCell ?: return
    val table = editing.table
    val state = remember(table.id ?: table.hashCode()) {
        TableCellEditorState(table, initialFocus = editing.row to editing.col)
    }

    var dragDelta by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FloatingSurface(
            modifier = modifier
                .offset { IntOffset(dragDelta.x.roundToInt(), dragDelta.y.roundToInt()) }
                .widthIn(min = 320.dp, max = 560.dp)
                .fillMaxWidth(0.92f)
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .liquidGlassSpringAnimation(origin = TransformOrigin(0.5f, 0.5f)),
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TableCellEditorHeader(
                    state = state,
                    onDrag = { delta -> dragDelta += delta },
                )
                TableCellEditorGrid(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                )
                TableCellEditorBottomBar(
                    state = state,
                    onCancel = onDismiss,
                    onSave = {
                        val updated = applyEditedTable(table, state.cells, state.rowHeights, state.colWidths)
                        if (updated != table) {
                            pane.surface?.replaceTableElement(table, updated)
                        }
                        onDismiss()
                    },
                )
            }
        }
    }
}

/** Header with title, drag handle, table dimension badge, and quick row/column add and remove steppers. */
@Composable
private fun TableCellEditorHeader(
    state: TableCellEditorState,
    onDrag: (Offset) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.DragIndicator,
                    contentDescription = "Drag popup",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Edit table",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "${state.rowHeights.size}×${state.colWidths.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableDimensionStepper(
                label = "Rows",
                count = state.rowHeights.size,
                canDecrement = state.rowHeights.size > 1,
                canIncrement = state.rowHeights.size < 50,
                onDecrement = { state.removeRow() },
                onIncrement = { state.addRow() },
            )
            TableDimensionStepper(
                label = "Columns",
                count = state.colWidths.size,
                canDecrement = state.colWidths.size > 1,
                canIncrement = state.colWidths.size < 50,
                onDecrement = { state.removeColumn() },
                onIncrement = { state.addColumn() },
            )
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
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = scheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp),
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = canDecrement, onClick = onDecrement),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Remove,
                contentDescription = "Decrease",
                modifier = Modifier.size(13.dp),
                tint = if (canDecrement) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = canIncrement, onClick = onIncrement),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "Increase",
                modifier = Modifier.size(13.dp),
                tint = if (canIncrement) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}

/** Every row and every cell of the session grid, scrollable both horizontally and vertically. */
@Composable
private fun TableCellEditorGrid(
    state: TableCellEditorState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val minCellWidth = 84.dp
        val spacing = 4.dp
        val numCols = state.colWidths.size
        val totalSpacing = spacing * (numCols - 1).coerceAtLeast(0)
        val availableForCells = maxWidth - totalSpacing
        val naturalCellWidth = if (numCols > 0) availableForCells / numCols else minCellWidth
        val cellWidth = maxOf(minCellWidth, naturalCellWidth)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            state.cells.forEachIndexed { row, rowCells ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowCells.forEachIndexed { col, cell ->
                        TableCellEditorField(
                            state = state,
                            row = row,
                            col = col,
                            cell = cell,
                            modifier = Modifier.width(cellWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCellEditorField(
    state: TableCellEditorState,
    row: Int,
    col: Int,
    cell: TableCell,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.focusedCell) {
        if (state.focusedCell == row to col) {
            try {
                focusRequester.requestFocus()
            } catch (_: Throwable) {}
        }
    }
    OutlinedTextField(
        value = cell.text,
        onValueChange = { text -> state.setText(row, col, text) },
        modifier = modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Tab -> {
                            if (event.isShiftPressed) {
                                state.moveToPrevCell(row, col)
                            } else {
                                state.moveToNextCell(row, col)
                            }
                            true
                        }
                        Key.Enter -> {
                            state.moveToNextCell(row, col)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .onFocusChanged {
                if (it.isFocused) {
                    state.currentRow = row
                    state.currentCol = col
                    state.focusedCell = row to col
                }
            },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            imeAction = if (row == state.rowHeights.size - 1 && col == state.colWidths.size - 1) {
                ImeAction.Done
            } else {
                ImeAction.Next
            },
        ),
        keyboardActions = KeyboardActions(
            onNext = { state.moveToNextCell(row, col) },
            onDone = { state.moveToNextCell(row, col) },
        ),
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = when (cell.align) {
                TableAlign.LEFT -> androidx.compose.ui.text.style.TextAlign.Start
                TableAlign.CENTER -> androidx.compose.ui.text.style.TextAlign.Center
                TableAlign.RIGHT -> androidx.compose.ui.text.style.TextAlign.End
            },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        ),
    )
}

/**
 * One strip instead of two: the align/bold pills at the left (acting on the last-focused cell,
 * whose coordinates the strip names) and Cancel / Save buttons at the right.
 */
@Composable
private fun TableCellEditorBottomBar(
    state: TableCellEditorState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val active = state.cells.getOrNull(state.currentRow)?.getOrNull(state.currentCol) ?: TableCell()
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.35f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                .padding(horizontal = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R${state.currentRow + 1} · C${state.currentCol + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.surfaceVariant.copy(alpha = 0.35f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCellEditorSegmentButton(
                selected = active.align == TableAlign.LEFT,
                imageVector = Icons.AutoMirrored.Outlined.FormatAlignLeft,
                description = "Align left",
                onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.LEFT) },
            )
            TableCellEditorSegmentButton(
                selected = active.align == TableAlign.CENTER,
                imageVector = Icons.Outlined.FormatAlignCenter,
                description = "Align center",
                onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.CENTER) },
            )
            TableCellEditorSegmentButton(
                selected = active.align == TableAlign.RIGHT,
                imageVector = Icons.AutoMirrored.Outlined.FormatAlignRight,
                description = "Align right",
                onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.RIGHT) },
            )
        }

        TableCellEditorBoldButton(
            selected = active.bold,
            onClick = { state.toggleBold(state.currentRow, state.currentCol) },
        )

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 10.dp)) {
            Text("Cancel", style = MaterialTheme.typography.labelMedium)
        }
        Button(onClick = onSave, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
            Text("Save", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TableCellEditorSegmentButton(
    selected: Boolean,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { this.role = Role.RadioButton },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = description,
            modifier = Modifier.size(16.dp),
            tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TableCellEditorBoldButton(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                1.dp,
                if (selected) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant.copy(alpha = 0.45f),
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .semantics { this.role = Role.Checkbox },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FormatBold,
            contentDescription = "Bold",
            modifier = Modifier.size(17.dp),
            tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}
