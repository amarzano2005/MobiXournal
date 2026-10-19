package com.mobixournal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.IconButton
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.TableElementFactory
import com.mobixournal.render.replaceTableElement
import com.mobixournal.ui.theme.FloatingSurface
import com.mobixournal.ui.theme.liquidGlassSpringAnimation

/**
 * Rectangular cell grid an edit session works on: the model may carry short rows (they read as
 * empty cells, per `format/model/Living.kt`), but the editor always hands back a full grid.
 */
internal fun editorCellsOf(table: TableElement): List<List<TableCell>> =
    List(table.rowHeights.size) { r ->
        List(table.colWidths.size) { c -> table.cell(r, c) }
    }

/** Adds a row to the grid and row heights. */
internal fun rowAdded(
    cells: List<List<TableCell>>,
    rowHeights: List<Double>,
    afterRow: Int? = null,
    defaultHeight: Double = TableElementFactory.MIN_ROW_HEIGHT_PT,
): Pair<List<List<TableCell>>, List<Double>> {
    val cols = if (cells.isNotEmpty()) cells[0].size else 1
    val newRow = List(cols) { TableCell() }
    val insertIdx = if (afterRow != null) (afterRow + 1).coerceIn(0, cells.size) else cells.size
    val newCells = cells.toMutableList().also { it.add(insertIdx, newRow) }
    val newHeights = rowHeights.toMutableList().also {
        val h = rowHeights.getOrNull(afterRow ?: (rowHeights.size - 1)) ?: defaultHeight
        it.add(insertIdx, h)
    }
    return newCells to newHeights
}

/** Removes a row from the grid and row heights (minimum 1 row kept). */
internal fun rowRemoved(
    cells: List<List<TableCell>>,
    rowHeights: List<Double>,
    atRow: Int? = null,
): Pair<List<List<TableCell>>, List<Double>> {
    if (cells.size <= 1 || rowHeights.size <= 1) return cells to rowHeights
    val removeIdx = if (atRow != null) atRow.coerceIn(0, cells.size - 1) else cells.size - 1
    val newCells = cells.toMutableList().also { it.removeAt(removeIdx) }
    val newHeights = rowHeights.toMutableList().also { it.removeAt(removeIdx) }
    return newCells to newHeights
}

/** Adds a column to every row in the grid and col widths. */
internal fun columnAdded(
    cells: List<List<TableCell>>,
    colWidths: List<Double>,
    afterCol: Int? = null,
    defaultWidth: Double = TableElementFactory.MIN_COL_WIDTH_PT,
): Pair<List<List<TableCell>>, List<Double>> {
    val insertIdx = if (afterCol != null) (afterCol + 1).coerceIn(0, colWidths.size) else colWidths.size
    val newCells = cells.map { row ->
        row.toMutableList().also { it.add(insertIdx, TableCell()) }
    }
    val newWidths = colWidths.toMutableList().also {
        val w = colWidths.getOrNull(afterCol ?: (colWidths.size - 1)) ?: defaultWidth
        it.add(insertIdx, w)
    }
    return newCells to newWidths
}

/** Removes a column from every row in the grid and col widths (minimum 1 column kept). */
internal fun columnRemoved(
    cells: List<List<TableCell>>,
    colWidths: List<Double>,
    atCol: Int? = null,
): Pair<List<List<TableCell>>, List<Double>> {
    if (colWidths.size <= 1) return cells to colWidths
    val removeIdx = if (atCol != null) atCol.coerceIn(0, colWidths.size - 1) else colWidths.size - 1
    val newCells = cells.map { row ->
        if (removeIdx in row.indices) row.toMutableList().also { it.removeAt(removeIdx) } else row
    }
    val newWidths = colWidths.toMutableList().also { it.removeAt(removeIdx) }
    return newCells to newWidths
}

/**
 * Commits an edited cell grid and geometry onto [original] in one step — one element swap, one undo entry.
 * Turns into a no-op (returns [original]) if the edited grid is not rectangular in [rowHeights] and [colWidths],
 * so a malformed edit session can never corrupt a table.
 */
internal fun applyEditedTable(
    original: TableElement,
    editedCells: List<List<TableCell>>,
    rowHeights: List<Double>,
    colWidths: List<Double>,
): TableElement {
    val rectangular = editedCells.size == rowHeights.size &&
        rowHeights.isNotEmpty() && colWidths.isNotEmpty() &&
        editedCells.all { it.size == colWidths.size }
    if (!rectangular) return original
    return original.copy(
        cells = editedCells,
        rowHeights = rowHeights,
        colWidths = colWidths,
    )
}

/**
 * Commits an edited cell grid onto [original] in one step — one element swap, one undo entry.
 * Turns into a no-op (returns [original]) if the edited grid is not rectangular in the original's
 * dimensions, so a malformed edit session can never corrupt a table.
 */
internal fun applyEditedCells(original: TableElement, edited: List<List<TableCell>>): TableElement =
    applyEditedTable(original, edited, original.rowHeights, original.colWidths)

/** Replaces one cell's text, leaving every other cell — and the target's own styling — alone. */
internal fun cellTextEdited(
    cells: List<List<TableCell>>,
    row: Int,
    col: Int,
    text: String,
): List<List<TableCell>> = cells.mapIndexed { r, rowCells ->
    rowCells.mapIndexed { c, cell ->
        if (r == row && c == col) cell.copy(text = text) else cell
    }
}

/** Restyles one cell's alignment and/or bold flag, leaving text and every other cell alone. */
internal fun cellStyled(
    cells: List<List<TableCell>>,
    row: Int,
    col: Int,
    align: TableAlign? = null,
    bold: Boolean? = null,
): List<List<TableCell>> = cells.mapIndexed { r, rowCells ->
    rowCells.mapIndexed { c, cell ->
        if (r == row && c == col) {
            cell.copy(
                align = align ?: cell.align,
                bold = bold ?: cell.bold,
            )
        } else {
            cell
        }
    }
}

/**
 * One open editing session's working grid plus the cell the style strip currently acts on. The
 * grid lives only in this session: **Save** commits it onto the table wholesale, closing without
 * saving throws it away — there is no half-committed state in between.
 */
internal class TableCellEditorState(initial: TableElement, initialFocus: Pair<Int, Int> = 0 to 0) {
    var cells by mutableStateOf(editorCellsOf(initial))
    var rowHeights by mutableStateOf(initial.rowHeights)
    var colWidths by mutableStateOf(initial.colWidths)
    var currentRow by mutableStateOf(initialFocus.first.coerceIn(0, (initial.rowHeights.size - 1).coerceAtLeast(0)))
    var currentCol by mutableStateOf(initialFocus.second.coerceIn(0, (initial.colWidths.size - 1).coerceAtLeast(0)))
    var focusedCell by mutableStateOf<Pair<Int, Int>?>(currentRow to currentCol)

    fun setText(row: Int, col: Int, text: String) {
        cells = cellTextEdited(cells, row, col, text)
    }

    fun setAlign(row: Int, col: Int, align: TableAlign) {
        cells = cellStyled(cells, row, col, align = align)
    }

    fun toggleBold(row: Int, col: Int) {
        val now = cells.getOrNull(row)?.getOrNull(col)?.bold ?: return
        cells = cellStyled(cells, row, col, bold = !now)
    }

    fun addRow(afterRow: Int? = null) {
        val (newCells, newHeights) = rowAdded(cells, rowHeights, afterRow)
        cells = newCells
        rowHeights = newHeights
        val targetRow = if (afterRow != null) afterRow + 1 else newCells.size - 1
        currentRow = targetRow.coerceIn(0, newCells.size - 1)
        focusedCell = currentRow to currentCol
    }

    fun removeRow(atRow: Int? = null) {
        if (rowHeights.size <= 1) return
        val (newCells, newHeights) = rowRemoved(cells, rowHeights, atRow)
        cells = newCells
        rowHeights = newHeights
        currentRow = currentRow.coerceIn(0, newCells.size - 1)
        focusedCell = currentRow to currentCol
    }

    fun addColumn(afterCol: Int? = null) {
        val (newCells, newWidths) = columnAdded(cells, colWidths, afterCol)
        cells = newCells
        colWidths = newWidths
        val targetCol = if (afterCol != null) afterCol + 1 else newWidths.size - 1
        currentCol = targetCol.coerceIn(0, newWidths.size - 1)
        focusedCell = currentRow to currentCol
    }

    fun removeColumn(atCol: Int? = null) {
        if (colWidths.size <= 1) return
        val (newCells, newWidths) = columnRemoved(cells, colWidths, atCol)
        cells = newCells
        colWidths = newWidths
        currentCol = currentCol.coerceIn(0, newWidths.size - 1)
        focusedCell = currentRow to currentCol
    }

    fun moveToNextCell(row: Int, col: Int, autoAddRow: Boolean = true) {
        val totalCols = colWidths.size
        val totalRows = rowHeights.size
        if (totalCols == 0 || totalRows == 0) return
        val isLastCell = row == totalRows - 1 && col == totalCols - 1
        if (isLastCell && autoAddRow) {
            addRow()
            currentRow = totalRows
            currentCol = 0
            focusedCell = currentRow to currentCol
            return
        }
        val nextCol = (col + 1) % totalCols
        val nextRow = if (nextCol == 0) (row + 1) % totalRows else row
        currentRow = nextRow
        currentCol = nextCol
        focusedCell = nextRow to nextCol
    }

    fun moveToPrevCell(row: Int, col: Int) {
        val totalCols = colWidths.size
        val totalRows = rowHeights.size
        if (totalCols == 0 || totalRows == 0) return
        val prevCol = if (col - 1 < 0) totalCols - 1 else col - 1
        val prevRow = if (col - 1 < 0) (if (row - 1 < 0) totalRows - 1 else row - 1) else row
        currentRow = prevRow
        currentCol = prevCol
        focusedCell = prevRow to prevCol
    }
}

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FloatingSurface(
            modifier = modifier
                .widthIn(min = 320.dp, max = 560.dp)
                .fillMaxWidth(0.92f)
                .padding(horizontal = 12.dp, vertical = 16.dp)
                .liquidGlassSpringAnimation(origin = TransformOrigin(0.5f, 0.5f)),
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TableCellEditorHeader(state)
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

/** Header with title, table dimension badge, and quick row/column add and remove steppers. */
@Composable
private fun TableCellEditorHeader(state: TableCellEditorState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Modifica tabella",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${state.rowHeights.size}×${state.colWidths.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Righe:", style = MaterialTheme.typography.labelMedium)
                IconButton(
                    onClick = { state.removeRow() },
                    enabled = state.rowHeights.size > 1,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Rimuovi riga", modifier = Modifier.size(16.dp))
                }
                Text("${state.rowHeights.size}", style = MaterialTheme.typography.bodyMedium)
                IconButton(
                    onClick = { state.addRow() },
                    enabled = state.rowHeights.size < 50,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Aggiungi riga", modifier = Modifier.size(16.dp))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Colonne:", style = MaterialTheme.typography.labelMedium)
                IconButton(
                    onClick = { state.removeColumn() },
                    enabled = state.colWidths.size > 1,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Outlined.Remove, contentDescription = "Rimuovi colonna", modifier = Modifier.size(16.dp))
                }
                Text("${state.colWidths.size}", style = MaterialTheme.typography.bodyMedium)
                IconButton(
                    onClick = { state.addColumn() },
                    enabled = state.colWidths.size < 50,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Aggiungi colonna", modifier = Modifier.size(16.dp))
                }
            }
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
 * whose coordinates the strip names) and Annulla / Salva buttons at the right.
 */
@Composable
private fun TableCellEditorBottomBar(
    state: TableCellEditorState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val active = state.cells.getOrNull(state.currentRow)?.getOrNull(state.currentCol) ?: TableCell()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R${state.currentRow + 1}·C${state.currentCol + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.width(2.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TableCellEditorStyleIconButton(
                selected = active.align == TableAlign.LEFT,
                imageVector = Icons.AutoMirrored.Outlined.FormatAlignLeft,
                description = "Allinea a sinistra",
                onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.LEFT) },
            )
            TableCellEditorStyleIconButton(
                selected = active.align == TableAlign.CENTER,
                imageVector = Icons.Outlined.FormatAlignCenter,
                description = "Allinea al centro",
                onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.CENTER) },
            )
            TableCellEditorStyleIconButton(
                selected = active.align == TableAlign.RIGHT,
                imageVector = Icons.AutoMirrored.Outlined.FormatAlignRight,
                description = "Allinea a destra",
                onClick = { state.setAlign(state.currentRow, state.currentCol, TableAlign.RIGHT) },
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        )

        TableCellEditorStyleIconButton(
            selected = active.bold,
            imageVector = Icons.Outlined.FormatBold,
            description = "Grassetto",
            onClick = { state.toggleBold(state.currentRow, state.currentCol) },
        )

        Spacer(Modifier.weight(1f))

        TextButton(
            onClick = onCancel,
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text("Annulla", style = MaterialTheme.typography.labelMedium)
        }
        Button(
            onClick = onSave,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text("Salva", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun TableCellEditorStyleIconButton(
    selected: Boolean,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier
            .size(32.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
