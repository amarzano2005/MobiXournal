package com.mobixournal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.render.replaceTableElement
import com.mobixournal.render.setActiveTableCellHighlight
import com.mobixournal.ui.theme.rememberClosingArgument

private enum class TableInspectorTab(val label: String) {
    CELLS("Cells"),
    LAYOUT_STYLE("Layout & Style"),
}

/**
 * Hosts the live anchored table inspector beside whichever table [EditorUiState.editingTableCell]
 * names, synchronizing cell content, structure and border style onto the canvas in real time with
 * single-session undo coalescing.
 */
@Composable
fun TableEditorHost(
    ui: EditorUiState,
    pane: PaneState,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
    safeInsets: PaddingValues = PaddingValues(0.dp),
) {
    val editing = rememberClosingArgument(ui.editingTableCell) ?: return
    val session = ui.tableInspectorSession
    val state = remember(session) {
        TableCellEditorState(editing.table, initialFocus = editing.row to editing.col)
    }
    var liveTable by remember(session) { mutableStateOf(editing.table) }
    var recordedUndo by remember(session) { mutableStateOf(false) }

    val commitLive: () -> Unit = {
        val updated = state.toTableElement(liveTable)
        if (updated != liveTable) {
            pane.surface?.replaceTableElement(liveTable, updated, recordUndo = !recordedUndo)
            recordedUndo = true
            liveTable = updated
            ui.editingTableCell = EditingTableCell(updated, state.currentRow, state.currentCol)
        }
    }

    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val imeVisible = imeBottomPx > 0

    DisposableEffect(pane.surface, liveTable, state.currentRow, state.currentCol) {
        pane.surface?.setActiveTableCellHighlight(liveTable, state.currentRow, state.currentCol)
        onDispose { pane.surface?.setActiveTableCellHighlight(null, 0, 0) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (imeVisible) {
            TableCompactFormulaBar(
                state = state,
                onCommitLive = commitLive,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = with(density) { imeBottomPx.toDp() } + 6.dp),
            )
        } else {
            LivingInspectorAnchor(
                sessionKey = session,
                anchorRect = pane.selectionRect,
                canvasSizePx = canvasSizePx,
                safeInsets = safeInsets,
                minWidth = 300.dp,
                maxWidth = 380.dp,
            ) { dragModifier ->
                TableInspectorPanel(
                    state = state,
                    session = session,
                    dragModifier = dragModifier,
                    onCommitLive = commitLive,
                    onDismiss = { ui.editingTableCell = null },
                )
            }
        }
    }
}

@Composable
private fun TableInspectorPanel(
    state: TableCellEditorState,
    session: Int,
    dragModifier: Modifier,
    onCommitLive: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember(session) { mutableStateOf(TableInspectorTab.CELLS) }
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TableInspectorHeader(
            rows = state.rowHeights.size,
            cols = state.colWidths.size,
            dragModifier = dragModifier,
            onDismiss = onDismiss,
        )
        InspectorTabBar(
            labels = TableInspectorTab.entries.map { it.label },
            selectedIndex = tab.ordinal,
            onSelect = { idx -> tab = TableInspectorTab.entries[idx] },
        )
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f))

        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(110)) },
            label = "tableInspectorTab",
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
        ) { activeTab ->
            when (activeTab) {
                TableInspectorTab.CELLS -> TableCellsTab(state = state, onCommitLive = onCommitLive)
                TableInspectorTab.LAYOUT_STYLE -> TableLayoutStyleTab(state = state, onCommitLive = onCommitLive)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = onDismiss) { Text("Done") }
        }
    }
}

@Composable
private fun TableInspectorHeader(
    rows: Int,
    cols: Int,
    dragModifier: Modifier,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = dragModifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.DragIndicator,
                contentDescription = "Drag to move",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
            Icon(
                Icons.Outlined.TableChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text("Table", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${rows}×${cols}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun TableCellsTab(
    state: TableCellEditorState,
    onCommitLive: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TableSpreadsheetGrid(
            state = state,
            onCommitLive = onCommitLive,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
        )
        TableCellContextToolbar(state = state, onCommitLive = onCommitLive)
    }
}

@Composable
private fun TableSpreadsheetGrid(
    state: TableCellEditorState,
    onCommitLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowIndexWidth = 24.dp
    val spacing = 3.dp
    BoxWithConstraints(modifier = modifier) {
        val minCellWidth = 76.dp
        val numCols = state.colWidths.size
        val totalSpacing = spacing * numCols.coerceAtLeast(1)
        val availForCells = (maxWidth - rowIndexWidth - totalSpacing).coerceAtLeast(minCellWidth)
        val naturalWidth = if (numCols > 0) availForCells / numCols else minCellWidth
        val cellWidth = maxOf(minCellWidth, naturalWidth)
        val hScroll = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(hScroll),
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(rowIndexWidth))
                for (col in state.colWidths.indices) {
                    Text(
                        text = columnLabel(col),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(cellWidth),
                    )
                }
            }
            state.cells.forEachIndexed { row, rowCells ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${row + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(rowIndexWidth),
                    )
                    rowCells.forEachIndexed { col, cell ->
                        TableCompactCellField(
                            state = state,
                            row = row,
                            col = col,
                            cell = cell,
                            onCommitLive = onCommitLive,
                            modifier = Modifier.width(cellWidth),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCompactCellField(
    state: TableCellEditorState,
    row: Int,
    col: Int,
    cell: TableCell,
    onCommitLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scheme = MaterialTheme.colorScheme
    val isFocused = state.focusedCell == (row to col)
    val isLastCell = row == state.rowHeights.size - 1 && col == state.colWidths.size - 1
    val shape = RoundedCornerShape(6.dp)

    val containerColor = when {
        isFocused -> scheme.surface
        state.headerRow && row == 0 -> scheme.primaryContainer.copy(alpha = 0.25f)
        else -> scheme.surfaceVariant.copy(alpha = 0.25f)
    }
    val borderWidth = if (isFocused) 1.5.dp else 1.dp
    val borderColor = if (isFocused) scheme.primary else scheme.outlineVariant.copy(alpha = 0.5f)

    BasicTextField(
        value = cell.text,
        onValueChange = { text ->
            state.setText(row, col, text)
            onCommitLive()
        },
        singleLine = true,
        cursorBrush = SolidColor(scheme.primary),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = scheme.onSurface,
            fontWeight = if (cell.bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = when (cell.align) {
                TableAlign.LEFT -> TextAlign.Start
                TableAlign.CENTER -> TextAlign.Center
                TableAlign.RIGHT -> TextAlign.End
            },
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            autoCorrect = false,
            imeAction = if (isLastCell) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { state.moveToNextCell(row, col, autoAddRow = false) },
            onDone = { focusManager.clearFocus() },
        ),
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(containerColor)
            .border(borderWidth, borderColor, shape)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                handleCellKeyEvent(event.type, event.key, event.isShiftPressed, row, col, state, onCommitLive)
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    state.currentRow = row
                    state.currentCol = col
                    state.focusedCell = row to col
                }
            }
            .padding(horizontal = 7.dp),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = when (cell.align) {
                    TableAlign.LEFT -> Alignment.CenterStart
                    TableAlign.CENTER -> Alignment.Center
                    TableAlign.RIGHT -> Alignment.CenterEnd
                },
            ) {
                innerTextField()
            }
        },
    )
}

private fun handleCellKeyEvent(
    type: KeyEventType,
    key: Key,
    shift: Boolean,
    row: Int,
    col: Int,
    state: TableCellEditorState,
    onCommitLive: () -> Unit,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return when (key) {
        Key.Tab -> {
            val prevRows = state.rowHeights.size
            if (shift) {
                state.moveToPrevCell(row, col)
            } else {
                state.moveToNextCell(row, col, autoAddRow = true)
            }
            if (state.rowHeights.size != prevRows) onCommitLive()
            true
        }
        Key.Enter -> {
            state.moveToNextCell(row, col, autoAddRow = false)
            true
        }
        else -> false
    }
}

@Composable
private fun TableCellContextToolbar(
    state: TableCellEditorState,
    onCommitLive: () -> Unit,
) {
    val active = state.cells.getOrNull(state.currentRow)?.getOrNull(state.currentCol) ?: TableCell()
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                    text = "R${state.currentRow + 1} · ${columnLabel(state.currentCol)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant,
                )
            }
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
        }
        TableRowColActionsBar(state = state, onCommitLive = onCommitLive)
    }
}

@Composable
private fun TableRowColActionsBar(
    state: TableCellEditorState,
    onCommitLive: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
            label = "− Row",
            selected = false,
            onClick = {
                if (state.rowHeights.size > 1) {
                    state.removeRow(atRow = state.currentRow)
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
        InspectorChip(
            label = "− Col",
            selected = false,
            onClick = {
                if (state.colWidths.size > 1) {
                    state.removeColumn(atCol = state.currentCol)
                    onCommitLive()
                }
            },
        )
    }
}

@Composable
internal fun TableSegmentButton(
    selected: Boolean,
    imageVector: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, scheme.primary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .semantics { role = Role.RadioButton },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = description,
            modifier = Modifier.size(15.dp),
            tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TableBoldButton(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.28f))
            .border(
                1.dp,
                if (selected) scheme.primary.copy(alpha = 0.6f) else scheme.outlineVariant.copy(alpha = 0.45f),
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .semantics { role = Role.Checkbox },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FormatBold,
            contentDescription = "Bold",
            modifier = Modifier.size(16.dp),
            tint = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
    }
}
