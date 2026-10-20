package com.mobixournal.ui

import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the fixed table editor's grid model: [editorCellsOf],
 * [applyEditedCells], [cellTextEdited] and [cellStyled] — the seams a whole-table
 * Save commits through in exactly one element swap.
 */
class TableCellEditorDialogTest {

    private fun sampleTable() = TableElement(
        left = 100.0,
        top = 80.0,
        colWidths = listOf(60.0, 70.0, 80.0),
        rowHeights = listOf(30.0, 40.0),
        headerRow = true,
        borderColor = 0xFF123456.toInt(),
        borderWidth = 1.5,
        id = "tbl_editor_1",
        cells = listOf(
            listOf(
                TableCell("A", TableAlign.LEFT, fillColor = 0x11000000),
                TableCell("B", TableAlign.CENTER, bold = true),
                TableCell("C", TableAlign.RIGHT),
            ),
            // Second row keeps the model default (centred) on purpose, to pin that the edit
            // seams never silently restyle it.
            listOf(TableCell("D"), TableCell("E"), TableCell("F")),
        ),
        extraAttrs = mapOf("ts" to "12345"),
    )

    @Test
    fun editorCellsOfRectangularizesAndCopiesTheGrid() {
        val table = TableElement(
            left = 0.0,
            top = 0.0,
            colWidths = listOf(50.0, 50.0),
            rowHeights = listOf(30.0, 30.0),
            cells = listOf(listOf(TableCell("Only"))),
        )

        val grid = editorCellsOf(table)

        assertEquals("rectangular: rows = rowHeights size", 2, grid.size)
        assertTrue("rectangular: every row = colWidths size", grid.all { it.size == 2 })
        assertEquals("cell(0,0) kept", "Only", grid[0][0].text)
        assertEquals("missing cells read empty, not inherit", "", grid[0][1].text)
        assertEquals("missing cells read empty", "", grid[1][0].text)
    }

    @Test
    fun applyEditedCellsCommitsTheWholeGridInOneOriginal() {
        val table = sampleTable()
        val committed = applyEditedCells(
            table,
            listOf(
                listOf(TableCell("Annot one,", TableAlign.LEFT, fillColor = 0x11000000), TableCell("Nuovo B", TableAlign.CENTER, bold = true), TableCell("C", TableAlign.RIGHT)),
                listOf(TableCell("D"), TableCell("E"), TableCell("F")),
            ),
        )

        assertEquals("id survives the whole-grid commit", table.id, committed.id)
        assertEquals("geometry survives", table.colWidths, committed.colWidths)
        assertEquals("geometry survives", table.rowHeights, committed.rowHeights)
        assertEquals("headerRow survives", table.headerRow, committed.headerRow)
        assertEquals("border survives", table.borderColor, committed.borderColor)
        assertEquals("extraAttrs survive", table.extraAttrs, committed.extraAttrs)
        assertEquals("the whole text grid is what was committed", "Nuovo B", committed.cell(0, 1).text)
    }

    @Test
    fun applyEditedCellsRefusesAMalformedGrid() {
        val table = sampleTable()

        // A short row would silently blank cells: the commit refuses instead.
        val malformed = listOf(listOf(TableCell("x")))
        assertSame("a non-rectangular grid returns the original untouched", table, applyEditedCells(table, malformed))
    }

    @Test
    fun cellTextEditedTouchesOnlyItsCell() {
        val grid = editorCellsOf(sampleTable())

        val edited = cellTextEdited(grid, 1, 0, "D rinnovata")

        assertEquals("target cell's text is the new one", "D rinnovata", edited[1][0].text)
        assertEquals("neighbour untouched", "E", edited[1][1].text)
        assertEquals("other row untouched", "A", edited[0][0].text)
        assertEquals("styling of the target cell preserved", grid[1][0].align, edited[1][0].align)
    }

    @Test
    fun cellStyledMovesOnlyAlignmentAndBold() {
        val grid = editorCellsOf(sampleTable())

        val centred = cellStyled(grid, 0, 0, align = TableAlign.CENTER)
        assertEquals("alignment replaced", TableAlign.CENTER, centred[0][0].align)
        assertEquals("text untouched", "A", centred[0][0].text)
        assertEquals("fill kept", 0x11000000, centred[0][0].fillColor)
        assertEquals("other cells untouched", grid[1][0].align, centred[1][0].align)

        val bolded = cellStyled(centred, 0, 0, bold = true)
        assertTrue("bold set", bolded[0][0].bold)
        assertEquals("alignment kept from the previous step", TableAlign.CENTER, bolded[0][0].align)
    }

    @Test
    fun rowAddedAndRemovedPreserveInvariants() {
        val originalGrid = editorCellsOf(sampleTable())
        val originalHeights = listOf(30.0, 40.0)

        val (addedGrid, addedHeights) = rowAdded(originalGrid, originalHeights)
        assertEquals("3 rows after add", 3, addedGrid.size)
        assertEquals("3 heights after add", 3, addedHeights.size)
        assertEquals("new row inherits cols", 3, addedGrid[2].size)
        assertEquals("new row is empty cells", "", addedGrid[2][0].text)
        assertEquals("new height inherits last row", 40.0, addedHeights[2], 0.001)

        val (removedGrid, removedHeights) = rowRemoved(addedGrid, addedHeights)
        assertEquals("2 rows after remove", 2, removedGrid.size)
        assertEquals("2 heights after remove", 2, removedHeights.size)

        // Refuses to remove when down to 1 row
        val (singleGrid, singleHeights) = rowRemoved(listOf(listOf(TableCell("Only"))), listOf(30.0))
        assertEquals("1 row kept", 1, singleGrid.size)
        assertEquals("1 height kept", 1, singleHeights.size)
    }

    @Test
    fun columnAddedAndRemovedPreserveInvariants() {
        val originalGrid = editorCellsOf(sampleTable())
        val originalWidths = listOf(60.0, 70.0, 80.0)

        val (addedGrid, addedWidths) = columnAdded(originalGrid, originalWidths)
        assertEquals("4 columns in widths", 4, addedWidths.size)
        assertTrue("every row has 4 columns", addedGrid.all { it.size == 4 })
        assertEquals("new column has empty text", "", addedGrid[0][3].text)
        assertEquals("new width inherits last column", 80.0, addedWidths[3], 0.001)

        val (removedGrid, removedWidths) = columnRemoved(addedGrid, addedWidths)
        assertEquals("3 columns in widths after remove", 3, removedWidths.size)
        assertTrue("every row has 3 columns after remove", removedGrid.all { it.size == 3 })

        // Refuses to remove when down to 1 column
        val (singleGrid, singleWidths) = columnRemoved(listOf(listOf(TableCell("A"))), listOf(50.0))
        assertEquals("1 col kept", 1, singleWidths.size)
        assertEquals("1 col in cells kept", 1, singleGrid[0].size)
    }

    @Test
    fun addedColumnWearsTheTablesHeader() {
        // A header table: the new column's first-row cell is bold, every other new cell is plain.
        val headerGrid = listOf(
            listOf(TableCell("x", bold = true), TableCell("y", bold = true)),
            listOf(TableCell("1"), TableCell("2")),
        )
        val (gridWithHeader, _) = columnAdded(headerGrid, listOf(60.0, 60.0))
        assertTrue("header cell of the added column is bold", gridWithHeader[0][2].bold)
        assertFalse("body cell of the added column stays plain", gridWithHeader[1][2].bold)

        // The element's own flag is enough, even before anything has been typed into row 0.
        val blankGrid = List(2) { List(2) { TableCell() } }
        val (gridByFlag, _) = columnAdded(blankGrid, listOf(60.0, 60.0), headerRow = true)
        assertTrue("headerRow makes the new header cell bold", gridByFlag[0][2].bold)

        // A plain table stays plain: adding a column must not invent a header.
        val (plainGrid, _) = columnAdded(blankGrid, listOf(60.0, 60.0))
        assertFalse("no header, no bold", plainGrid[0][2].bold)

        // Inserted in the middle, the bold cell is still the row-0 one.
        val (middleGrid, widths) = columnAdded(headerGrid, listOf(60.0, 70.0), afterCol = 0)
        assertEquals(3, widths.size)
        assertTrue("inserted header cell is bold", middleGrid[0][1].bold)
        assertFalse("inserted body cell is plain", middleGrid[1][1].bold)
    }

    @Test
    fun applyEditedTableUpdatesGeometryAndCells() {
        val table = sampleTable()
        val newCells = listOf(
            listOf(TableCell("1"), TableCell("2")),
            listOf(TableCell("3"), TableCell("4")),
            listOf(TableCell("5"), TableCell("6")),
        )
        val newHeights = listOf(20.0, 20.0, 20.0)
        val newWidths = listOf(50.0, 50.0)

        val updated = applyEditedTable(table, newCells, newHeights, newWidths)

        assertEquals(3, updated.rowHeights.size)
        assertEquals(2, updated.colWidths.size)
        assertEquals("1", updated.cell(0, 0).text)
        assertEquals("6", updated.cell(2, 1).text)
        assertEquals(table.id, updated.id)
        assertEquals(table.borderColor, updated.borderColor)
    }

    @Test
    fun stateCellNavigationMovesAndAddsRowOnLastCell() {
        val table = sampleTable()
        val state = TableCellEditorState(table, initialFocus = 0 to 0)

        state.moveToNextCell(0, 0)
        assertEquals("moves to col 1", 0 to 1, state.currentRow to state.currentCol)

        state.moveToNextCell(0, 2)
        assertEquals("wraps to row 1 col 0", 1 to 0, state.currentRow to state.currentCol)

        state.moveToPrevCell(1, 0)
        assertEquals("wraps back to row 0 col 2", 0 to 2, state.currentRow to state.currentCol)

        // At last cell (row 1, col 2), moveToNextCell auto-adds a row
        state.moveToNextCell(1, 2, autoAddRow = true)
        assertEquals("added row: 3 rows now", 3, state.rowHeights.size)
        assertEquals("focus moved to new row 2 col 0", 2 to 0, state.currentRow to state.currentCol)
    }
}
