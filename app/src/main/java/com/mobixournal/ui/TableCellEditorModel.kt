package com.mobixournal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.TableElementFactory

/**
 * Rectangular cell grid an edit session works on: the model may carry short rows (they read as
 * empty cells, per `format/model/Living.kt`), but the editor always hands back a full grid.
 */
internal fun editorCellsOf(table: TableElement): List<List<TableCell>> =
    List(table.rowHeights.size) { r ->
        List(table.colWidths.size) { c -> table.cell(r, c) }
    }

/** Spreadsheet column label (`0 -> "A"`, `25 -> "Z"`, `26 -> "AA"`). */
internal fun columnLabel(index: Int): String {
    var n = index.coerceAtLeast(0)
    val sb = StringBuilder()
    do {
        sb.append(('A' + (n % 26)))
        n = (n / 26) - 1
    } while (n >= 0)
    return sb.reverse().toString()
}

/** Sets bold styling on row 0 cells when toggling the table's header row flag. */
internal fun headerRowToggled(
    cells: List<List<TableCell>>,
    enabled: Boolean,
): List<List<TableCell>> {
    if (cells.isEmpty()) return cells
    return cells.mapIndexed { r, rowCells ->
        if (r == 0) rowCells.map { it.copy(bold = enabled) } else rowCells
    }
}

/**
 * Redistributes total width evenly across columns and total height evenly across rows while
 * respecting [TableElementFactory.MIN_COL_WIDTH_PT] and [TableElementFactory.MIN_ROW_HEIGHT_PT].
 */
internal fun tableDistributedEvenly(
    colWidths: List<Double>,
    rowHeights: List<Double>,
): Pair<List<Double>, List<Double>> {
    val evenCols = if (colWidths.isEmpty()) {
        colWidths
    } else {
        val w = (colWidths.sum() / colWidths.size).coerceAtLeast(TableElementFactory.MIN_COL_WIDTH_PT)
        List(colWidths.size) { w }
    }
    val evenRows = if (rowHeights.isEmpty()) {
        rowHeights
    } else {
        val h = (rowHeights.sum() / rowHeights.size).coerceAtLeast(TableElementFactory.MIN_ROW_HEIGHT_PT)
        List(rowHeights.size) { h }
    }
    return evenCols to evenRows
}

/** Adds a row to the grid and row heights. */
internal fun rowAdded(
    cells: List<List<TableCell>>,
    rowHeights: List<Double>,
    afterRow: Int? = null,
    defaultHeight: Double = TableElementFactory.MIN_ROW_HEIGHT_PT,
): Pair<List<List<TableCell>>, List<Double>> {
    val cols = if (cells.isNotEmpty()) cells[0].size else 1
    val insertIdx = if (afterRow != null) (afterRow + 1).coerceIn(0, cells.size) else cells.size
    val newCells = cells.toMutableList().also { it.add(insertIdx, List(cols) { TableCell() }) }
    val newHeights = rowHeights.toMutableList().also {
        it.add(insertIdx, rowHeights.getOrNull(afterRow ?: (rowHeights.size - 1)) ?: defaultHeight)
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

/**
 * Adds a column to every row in the grid and col widths.
 *
 * The new cell in the **header row** is bold whenever the table was authored with a header
 * ([headerRow]) or its first row already reads as one (any cell bold) — a column added mid-edit must
 * arrive wearing the same header as the columns beside it, instead of landing plain next to bold ones.
 * Every other row's new cell is plain, and the caller's own styling is otherwise untouched.
 */
internal fun columnAdded(
    cells: List<List<TableCell>>,
    colWidths: List<Double>,
    afterCol: Int? = null,
    defaultWidth: Double = TableElementFactory.MIN_COL_WIDTH_PT,
    headerRow: Boolean = false,
): Pair<List<List<TableCell>>, List<Double>> {
    val insertIdx = if (afterCol != null) (afterCol + 1).coerceIn(0, colWidths.size) else colWidths.size
    val header = headerRow || cells.firstOrNull()?.any { it.bold } == true
    val newCells = cells.mapIndexed { row, cellsInRow ->
        cellsInRow.toMutableList().also { it.add(insertIdx, TableCell(bold = header && row == 0)) }
    }
    val newWidths = colWidths.toMutableList().also {
        it.add(insertIdx, colWidths.getOrNull(afterCol ?: (colWidths.size - 1)) ?: defaultWidth)
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
 * One open editing session's working grid, styling, geometry and active cell focus. Edits mutate
 * this state and immediately synchronize onto the canvas via `toTableElement`.
 */
internal class TableCellEditorState(initial: TableElement, initialFocus: Pair<Int, Int> = 0 to 0) {
    var cells by mutableStateOf(editorCellsOf(initial))
    var rowHeights by mutableStateOf(initial.rowHeights)
    /** The table's own header-row flag, carried through so an added column can honour it. */
    var headerRow by mutableStateOf(initial.headerRow)
    private var _borderWidth by mutableStateOf(initial.borderWidth)
    val borderWidth: Double get() = _borderWidth
    private var _borderColor by mutableStateOf(initial.borderColor)
    val borderColor: Int get() = _borderColor
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

    fun toggleHeaderRow(enabled: Boolean) {
        headerRow = enabled
        cells = headerRowToggled(cells, enabled)
    }

    fun setBorderWidth(width: Double) {
        _borderWidth = width.coerceIn(0.5, 6.0)
    }

    fun setBorderColor(color: Int) {
        _borderColor = color
    }

    fun distributeEvenly() {
        val (evenCols, evenRows) = tableDistributedEvenly(colWidths, rowHeights)
        colWidths = evenCols
        rowHeights = evenRows
    }

    fun toTableElement(base: TableElement): TableElement =
        applyEditedTable(base, cells, rowHeights, colWidths).copy(
            headerRow = headerRow,
            borderWidth = borderWidth,
            borderColor = borderColor,
        )

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
        val (newCells, newWidths) = columnAdded(cells, colWidths, afterCol, headerRow = headerRow)
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
