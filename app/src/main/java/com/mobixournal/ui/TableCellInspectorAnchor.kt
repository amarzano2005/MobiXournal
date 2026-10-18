package com.mobixournal.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.PageBox
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
 * Converts a table cell's document-space pt bounds to surface view px using the active [PageBox] and scroll.
 */
internal fun cellBoundsToViewPx(
    boundsPt: com.mobixournal.render.Bounds?,
    pageBox: PageBox?,
    scrollX: Float,
    scrollY: Float,
): com.mobixournal.render.Bounds? {
    if (boundsPt == null || pageBox == null) return null
    return com.mobixournal.render.Bounds(
        left = boundsPt.left * pageBox.scale + pageBox.leftPx - scrollX,
        top = boundsPt.top * pageBox.scale + pageBox.topPx - scrollY,
        right = boundsPt.right * pageBox.scale + pageBox.leftPx - scrollX,
        bottom = boundsPt.bottom * pageBox.scale + pageBox.topPx - scrollY,
    )
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
    val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    val marginPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    val surface = pane.surface
    val activeBox = surface?.let { s ->
        s.selection?.pageIndex?.let { s.layout.boxes.getOrNull(it) } ?: s.layout.boxes.getOrNull(s.currentPage)
    }
    val cellPx = cellBoundsToViewPx(
        boundsPt = editing.cellBoundsPt ?: com.mobixournal.render.TableDividers.cellBoundsPt(table, row, col),
        pageBox = activeBox,
        scrollX = surface?.scrollX ?: 0f,
        scrollY = surface?.scrollY ?: 0f,
    )

    val offset = tableCellInspectorOffset(
        cellLeft = cellPx?.left?.toFloat() ?: pane.selectionRect?.left,
        cellTop = cellPx?.top?.toFloat() ?: pane.selectionRect?.top,
        cellRight = cellPx?.right?.toFloat() ?: pane.selectionRect?.right,
        cellBottom = cellPx?.bottom?.toFloat() ?: pane.selectionRect?.bottom,
        inspectorSize = inspectorSize,
        canvasSize = canvasSizePx,
        gapPx = gapPx,
        marginPx = marginPx,
    )

    fun applyAndDismiss() {
        val updatedCell = cell.copy(text = state.text, align = state.align, bold = state.bold)
        val updatedTable = TableElementFactory.updateCell(table, row, col, updatedCell)
        pane.surface?.replaceTableElement(table, updatedTable)
        ui.editingTableCell = null
    }

    FloatingSurface(
        modifier = modifier
            .offset { offset }
            .onSizeChanged { inspectorSize = it }
            .pointerInput(Unit) {
                detectTapGestures { /* Swallow taps to prevent them leaking to the canvas below */ }
            },
    ) {
        TableCellInspectorContent(
            table = table,
            row = row,
            col = col,
            state = state,
            onNavigate = { nextR, nextC ->
                val nextBounds = com.mobixournal.render.TableDividers.cellBoundsPt(table, nextR, nextC)
                ui.editingTableCell = EditingTableCell(table, nextR, nextC, nextBounds)
            },
            onApply = ::applyAndDismiss,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun TableCellInspectorContent(
    table: TableElement,
    row: Int,
    col: Int,
    state: TableCellInspectorState,
    onNavigate: (Int, Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 260.dp, max = 340.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TableCellInspectorHeader(
            table = table,
            row = row,
            col = col,
            onNavigate = onNavigate,
            onDismiss = onDismiss,
        )
        OutlinedTextField(
            value = state.text,
            onValueChange = { state.text = it },
            placeholder = {
                Text(
                    text = "Cell text...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (state.bold) FontWeight.Bold else FontWeight.Normal,
                textAlign = when (state.align) {
                    TableAlign.LEFT -> TextAlign.Start
                    TableAlign.CENTER -> TextAlign.Center
                    TableAlign.RIGHT -> TextAlign.End
                },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
            ),
            trailingIcon = if (state.text.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { state.text = "" },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear text",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onApply() }),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCellInspectorStyleButtons(state)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onApply,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Done", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TableCellInspectorStyleButtons(state: TableCellInspectorState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconToggleButton(
            checked = state.align == TableAlign.LEFT,
            onCheckedChange = { state.align = TableAlign.LEFT },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.FormatAlignLeft,
                contentDescription = "Left",
                modifier = Modifier.size(18.dp),
            )
        }
        IconToggleButton(
            checked = state.align == TableAlign.CENTER,
            onCheckedChange = { state.align = TableAlign.CENTER },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Outlined.FormatAlignCenter,
                contentDescription = "Center",
                modifier = Modifier.size(18.dp),
            )
        }
        IconToggleButton(
            checked = state.align == TableAlign.RIGHT,
            onCheckedChange = { state.align = TableAlign.RIGHT },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.FormatAlignRight,
                contentDescription = "Right",
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        IconToggleButton(
            checked = state.bold,
            onCheckedChange = { state.bold = !state.bold },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Outlined.FormatBold,
                contentDescription = "Bold",
                modifier = Modifier.size(18.dp),
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = {
                    val (nR, nC) = if (col > 0) row to (col - 1) else (row - 1) to (totalCols - 1)
                    onNavigate(nR, nC)
                },
                enabled = hasPrev,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Previous cell",
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = {
                    val (nR, nC) = if (col < totalCols - 1) row to (col + 1) else (row + 1) to 0
                    onNavigate(nR, nC)
                },
                enabled = hasNext,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = "Next cell",
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Close inspector",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

