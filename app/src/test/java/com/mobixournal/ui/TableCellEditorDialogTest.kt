package com.mobixournal.ui

import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import org.junit.Assert.assertEquals
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
}
