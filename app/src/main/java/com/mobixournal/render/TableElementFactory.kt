package com.mobixournal.render

import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement

/**
 * Where a freshly inserted table sits on a page, and how big it is, in points.
 */
internal data class TableBox(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height
}

/**
 * Factory for creating and editing living [TableElement]s.
 *
 * Owns the conversion from user drag / viewport centre coordinates into the table's origin,
 * columns, rows and cells, ensuring consistent geometry across insertion paths.
 */
internal object TableElementFactory {

    /** Minimum width allocated to an individual column, in points. */
    const val MIN_COL_WIDTH_PT = 20.0

    /** Minimum height allocated to an individual row, in points. */
    const val MIN_ROW_HEIGHT_PT = 15.0

    /** Default width of a centered table insertion, in points. */
    const val DEFAULT_WIDTH_PT = 240.0

    /** Default height of a centered table insertion, in points. */
    const val DEFAULT_HEIGHT_PT = 160.0

    /** Default rule border width in points. */
    const val DEFAULT_BORDER_WIDTH = 1.0

    /**
     * Creates a living [TableElement] spanning from ([startX], [startY]) to ([endX], [endY]).
     */
    fun tableElementFor(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        rows: Int = 3,
        cols: Int = 3,
        hasHeader: Boolean = false,
        borderColor: Int = TableElement.DEFAULT_BORDER,
        borderWidth: Double = DEFAULT_BORDER_WIDTH,
    ): TableElement {
        val r = rows.coerceAtLeast(1)
        val c = cols.coerceAtLeast(1)
        val left = minOf(startX, endX)
        val top = minOf(startY, endY)
        val rawW = kotlin.math.abs(endX - startX)
        val rawH = kotlin.math.abs(endY - startY)
        val width = rawW.coerceAtLeast(c * MIN_COL_WIDTH_PT)
        val height = rawH.coerceAtLeast(r * MIN_ROW_HEIGHT_PT)
        val colWidths = List(c) { width / c }
        val rowHeights = List(r) { height / r }
        val cells = List(r) { List(c) { TableCell() } }
        return TableElement(
            left = left,
            top = top,
            colWidths = colWidths,
            rowHeights = rowHeights,
            headerRow = hasHeader,
            cells = cells,
            borderColor = borderColor,
            borderWidth = borderWidth,
        )
    }

    /**
     * Determines a clamped [TableBox] centered around ([centreX], [centreY]) on [page].
     */
    fun boxFor(
        page: Page,
        centreX: Double,
        centreY: Double,
        rows: Int = 3,
        cols: Int = 3,
        widthPt: Double = DEFAULT_WIDTH_PT,
        heightPt: Double = DEFAULT_HEIGHT_PT,
    ): TableBox {
        val r = rows.coerceAtLeast(1)
        val c = cols.coerceAtLeast(1)
        val minW = c * MIN_COL_WIDTH_PT
        val minH = r * MIN_ROW_HEIGHT_PT
        val w = widthPt.coerceAtLeast(minW).coerceAtMost(page.width)
        val h = heightPt.coerceAtLeast(minH).coerceAtMost(page.height)
        val left = (centreX - w / 2.0).coerceIn(0.0, (page.width - w).coerceAtLeast(0.0))
        val top = (centreY - h / 2.0).coerceIn(0.0, (page.height - h).coerceAtLeast(0.0))
        return TableBox(left = left, top = top, width = w, height = h)
    }

    /**
     * Updates the [TableCell] at ([row], [col]), keeping all other cells and table metadata intact.
     */
    fun updateCell(
        original: TableElement,
        row: Int,
        col: Int,
        newCell: TableCell,
    ): TableElement {
        if (row !in original.rowHeights.indices || col !in original.colWidths.indices) return original
        val currentCells = if (original.cells.size == original.rowHeights.size) {
            original.cells
        } else {
            List(original.rowHeights.size) { r ->
                List(original.colWidths.size) { c -> original.cell(r, c) }
            }
        }
        val updatedCells = currentCells.mapIndexed { r, rowList ->
            if (r == row) {
                rowList.mapIndexed { c, cell -> if (c == col) newCell else cell }
            } else {
                rowList
            }
        }
        return original.copy(cells = updatedCells)
    }

    /**
     * Updates the width of column [col], enforcing [MIN_COL_WIDTH_PT].
     */
    fun updateColWidth(
        original: TableElement,
        col: Int,
        newWidth: Double,
    ): TableElement {
        if (col !in original.colWidths.indices) return original
        val clamped = newWidth.coerceAtLeast(MIN_COL_WIDTH_PT)
        val newWidths = original.colWidths.toMutableList().also { it[col] = clamped }
        return original.copy(colWidths = newWidths)
    }

    /**
     * Updates the height of row [row], enforcing [MIN_ROW_HEIGHT_PT].
     */
    fun updateRowHeight(
        original: TableElement,
        row: Int,
        newHeight: Double,
    ): TableElement {
        if (row !in original.rowHeights.indices) return original
        val clamped = newHeight.coerceAtLeast(MIN_ROW_HEIGHT_PT)
        val newHeights = original.rowHeights.toMutableList().also { it[row] = clamped }
        return original.copy(rowHeights = newHeights)
    }
}
