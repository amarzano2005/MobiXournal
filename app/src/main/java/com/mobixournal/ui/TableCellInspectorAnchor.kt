package com.mobixournal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.TableElementFactory
import com.mobixournal.render.replaceTableElement
import com.mobixournal.ui.theme.FloatingSurface

/**
 * Pure positioning helper for the anchored table cell inspector.
 */
internal fun tableCellInspectorOffset(
    cellLeft: Float?,
    cellTop: Float?,
    cellRight: Float?,
    cellBottom: Float?,
    inspectorSize: IntSize,
    canvasSize: IntSize,
    gapPx: Int,
    marginPx: Int,
): IntOffset {
    if (cellLeft == null || cellRight == null || cellTop == null || cellBottom == null || inspectorSize.width <= 0) {
        val cx = ((canvasSize.width - inspectorSize.width) / 2).coerceAtLeast(marginPx)
        val cy = ((canvasSize.height - inspectorSize.height) / 2).coerceAtLeast(marginPx)
        return IntOffset(cx, cy)
    }

    val cellCenterX = (cellLeft + cellRight) / 2f
    val idealX = (cellCenterX - inspectorSize.width / 2f).toInt()
    val maxX = canvasSize.width - inspectorSize.width - marginPx
    val clampedX = idealX.coerceIn(marginPx, maxX.coerceAtLeast(marginPx))

    val belowY = cellBottom.toInt() + gapPx
    val maxY = canvasSize.height - inspectorSize.height - marginPx
    val clampedY = if (belowY + inspectorSize.height <= canvasSize.height - marginPx) {
        belowY.coerceAtLeast(marginPx)
    } else {
        val aboveY = cellTop.toInt() - gapPx - inspectorSize.height
        if (aboveY >= marginPx) aboveY else belowY.coerceIn(marginPx, maxY.coerceAtLeast(marginPx))
    }
    return IntOffset(clampedX, clampedY)
}

/**
 * State holder for in-place table cell editing.
 */
internal class TableCellInspectorState(
    initialText: String,
    initialAlign: TableAlign,
    initialBold: Boolean,
) {
    var text by mutableStateOf(initialText)
    var align by mutableStateOf(initialAlign)
    var bold by mutableStateOf(initialBold)
}

@Composable
internal fun rememberTableCellInspectorState(cell: TableCell): TableCellInspectorState =
    remember(cell) {
        TableCellInspectorState(
            initialText = cell.text,
            initialAlign = cell.align,
            initialBold = cell.bold,
        )
    }

/**
 * In-place floating inspector anchored to an active table cell for editing its text and style.
 */
@Composable
fun TableCellInspectorAnchor(
    pane: PaneState,
    ui: EditorUiState,
    canvasSizePx: IntSize,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = { ui.editingTableCell = null },
) {
    val editing = ui.editingTableCell ?: return
    val table = editing.table
    val row = editing.row
    val col = editing.col
    val cell = table.cell(row, col)
    val state = rememberTableCellInspectorState(cell)

    var inspectorSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(LocalDensity.current) { 10.dp.roundToPx() }
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    val offset = tableCellInspectorOffset(
        cellLeft = pane.selectionRect?.left,
        cellTop = pane.selectionRect?.top,
        cellRight = pane.selectionRect?.right,
        cellBottom = pane.selectionRect?.bottom,
        inspectorSize = inspectorSize,
        canvasSize = canvasSizePx,
        gapPx = gapPx,
        marginPx = marginPx,
    )

    FloatingSurface(
        modifier = modifier
            .offset { offset }
            .onSizeChanged { inspectorSize = it },
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 260.dp, max = 340.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TableCellInspectorHeader(
                table = table,
                row = row,
                col = col,
                onNavigate = { nextR, nextC -> ui.editingTableCell = EditingTableCell(table, nextR, nextC) },
                onDismiss = onDismiss,
            )
            HorizontalDivider()
            TableCellInspectorBody(state)
            HorizontalDivider()
            TableCellInspectorActions(
                onApply = {
                    val updatedCell = cell.copy(text = state.text, align = state.align, bold = state.bold)
                    val updatedTable = TableElementFactory.updateCell(table, row, col, updatedCell)
                    pane.surface?.replaceTableElement(table, updatedTable)
                    ui.editingTableCell = null
                },
                onCancel = onDismiss,
            )
        }
    }
}

@Composable
private fun TableCellInspectorHeader(
    table: TableElement,
    row: Int,
    col: Int,
    onNavigate: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val totalRows = table.rowHeights.size
    val totalCols = table.colWidths.size
    val hasPrev = row > 0 || col > 0
    val hasNext = row < totalRows - 1 || col < totalCols - 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Cell (${row + 1}, ${col + 1})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    val (nR, nC) = if (col > 0) row to (col - 1) else (row - 1) to (totalCols - 1)
                    onNavigate(nR, nC)
                },
                enabled = hasPrev,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Previous cell")
            }
            IconButton(
                onClick = {
                    val (nR, nC) = if (col < totalCols - 1) row to (col + 1) else (row + 1) to 0
                    onNavigate(nR, nC)
                },
                enabled = hasNext,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "Next cell")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Close inspector")
            }
        }
    }
}

@Composable
private fun TableCellInspectorBody(state: TableCellInspectorState) {
    OutlinedTextField(
        value = state.text,
        onValueChange = { state.text = it },
        label = { Text("Cell text") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.align == TableAlign.LEFT,
            onClick = { state.align = TableAlign.LEFT },
            label = { Icon(Icons.AutoMirrored.Outlined.FormatAlignLeft, contentDescription = "Align Left") },
        )
        FilterChip(
            selected = state.align == TableAlign.CENTER,
            onClick = { state.align = TableAlign.CENTER },
            label = { Icon(Icons.Outlined.FormatAlignCenter, contentDescription = "Align Center") },
        )
        FilterChip(
            selected = state.align == TableAlign.RIGHT,
            onClick = { state.align = TableAlign.RIGHT },
            label = { Icon(Icons.AutoMirrored.Outlined.FormatAlignRight, contentDescription = "Align Right") },
        )
        FilterChip(
            selected = state.bold,
            onClick = { state.bold = !state.bold },
            label = { Icon(Icons.Outlined.FormatBold, contentDescription = "Bold") },
        )
    }
}

@Composable
private fun TableCellInspectorActions(
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
        Button(onClick = onApply) {
            Text("Done")
        }
    }
}
