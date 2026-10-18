package com.mobixournal.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.PageBox
import com.mobixournal.render.TableDividers
import com.mobixournal.render.TableElementFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [tableCellInspectorOffset], [TableDividers], and in-place table cell edits.
 */
class TableCellInspectorAnchorTest {

    private fun sampleTable() = TableElement(
        left = 100.0,
        top = 80.0,
        colWidths = listOf(60.0, 70.0, 80.0),
        rowHeights = listOf(30.0, 40.0),
        headerRow = true,
        borderColor = 0xFF123456.toInt(),
        borderWidth = 1.5,
        id = "tbl_cell_edit_1",
        cells = listOf(
            listOf(TableCell("A", TableAlign.LEFT), TableCell("B", TableAlign.CENTER), TableCell("C", TableAlign.RIGHT)),
            listOf(TableCell("D", TableAlign.LEFT), TableCell("E", TableAlign.CENTER), TableCell("F", TableAlign.RIGHT)),
        ),
        extraAttrs = mapOf("ts" to "12345"),
    )

    @Test
    fun tableDividersHitTestingDetectsColumnAndRowDividers() {
        val table = sampleTable()
        val ref = com.mobixournal.render.ElementRef(0, 0)
        val box = PageBox(
            index = 0,
            scale = 1f,
            leftPx = 0f,
            topPx = 0f,
            widthPx = 500f,
            heightPx = 500f,
            source = com.mobixournal.format.mobix.PageSource.of(emptyList()),
        )

        // Col 0 divider is at left (100) + colWidths[0] (60) = 160.0 pt -> 160 px
        val colHit = TableDividers.hitTest(
            table = table,
            ref = ref,
            pageBox = box,
            scrollX = 0f,
            scrollY = 0f,
            touchX = 162f,
            touchY = 100f,
        )
        assertNotNull("Must hit column divider 0", colHit)
        assertTrue(colHit!!.isColumn)
        assertEquals(0, colHit.index)
        assertEquals(100.0, colHit.startPt, 1e-4)

        // Row 0 divider is at top (80) + rowHeights[0] (30) = 110.0 pt -> 110 px
        val rowHit = TableDividers.hitTest(
            table = table,
            ref = ref,
            pageBox = box,
            scrollX = 0f,
            scrollY = 0f,
            touchX = 120f,
            touchY = 112f,
        )
        assertNotNull("Must hit row divider 0", rowHit)
        assertTrue(!rowHit!!.isColumn)
        assertEquals(0, rowHit.index)
        assertEquals(80.0, rowHit.startPt, 1e-4)

        // Touch outside table
        val outsideHit = TableDividers.hitTest(
            table = table,
            ref = ref,
            pageBox = box,
            scrollX = 0f,
            scrollY = 0f,
            touchX = 20f,
            touchY = 20f,
        )
        assertNull("Must not hit divider outside table", outsideHit)
    }

    @Test
    fun tableDividersCellAtAndCellBoundsPt() {
        val table = sampleTable()

        // Point inside cell (row 1, col 2)
        // row 1: y in [80+30, 80+30+40] = [110, 150]
        // col 2: x in [100+60+70, 100+60+70+80] = [230, 310]
        val cell = TableDividers.cellAt(table, 250.0, 130.0)
        assertEquals(1 to 2, cell)

        val bounds = TableDividers.cellBoundsPt(table, 1, 2)
        assertNotNull(bounds)
        assertEquals(230.0, bounds!!.left, 1e-4)
        assertEquals(110.0, bounds.top, 1e-4)
        assertEquals(310.0, bounds.right, 1e-4)
        assertEquals(150.0, bounds.bottom, 1e-4)
    }

    @Test
    fun tableCellInspectorOffsetPositionsAdjacentAndClamps() {
        val canvasSize = IntSize(800, 1000)
        val inspectorSize = IntSize(280, 150)
        val gapPx = 10
        val marginPx = 8

        // Cell in centre of screen
        val offset = tableCellInspectorOffset(
            cellLeft = 300f,
            cellTop = 200f,
            cellRight = 400f,
            cellBottom = 260f,
            inspectorSize = inspectorSize,
            canvasSize = canvasSize,
            gapPx = gapPx,
            marginPx = marginPx,
        )
        // Centered horizontally around cellCenterX (350): 350 - 140 = 210
        assertEquals(210, offset.x)
        // Placed below cellBottom (260 + 10 = 270)
        assertEquals(270, offset.y)

        // Cell near bottom edge: flips above cell
        val bottomOffset = tableCellInspectorOffset(
            cellLeft = 300f,
            cellTop = 900f,
            cellRight = 400f,
            cellBottom = 960f,
            inspectorSize = inspectorSize,
            canvasSize = canvasSize,
            gapPx = gapPx,
            marginPx = marginPx,
        )
        // Above: 900 - 10 - 150 = 740
        assertEquals(740, bottomOffset.y)
    }

    @Test
    fun inPlaceCellEditPreservesOtherCellsAndAttributes() {
        val table = sampleTable()
        val newCell = TableCell("Updated Content", TableAlign.CENTER, bold = true, fillColor = 0x30112233.toInt())

        val updated = TableElementFactory.updateCell(table, 0, 1, newCell)

        assertEquals("tbl_cell_edit_1", updated.id)
        assertEquals(table.borderColor, updated.borderColor)
        assertEquals(table.borderWidth, updated.borderWidth, 1e-4)
        assertEquals(table.headerRow, updated.headerRow)
        assertEquals(table.extraAttrs, updated.extraAttrs)
        assertEquals(table.colWidths, updated.colWidths)
        assertEquals(table.rowHeights, updated.rowHeights)

        // Cell (0, 1) updated
        assertEquals(newCell, updated.cell(0, 1))

        // Other cells untouched
        assertEquals("A", updated.cell(0, 0).text)
        assertEquals(TableAlign.LEFT, updated.cell(0, 0).align)
        assertEquals("C", updated.cell(0, 2).text)
        assertEquals("D", updated.cell(1, 0).text)
    }

    @Test
    fun dividerDragResizePreservesCellsAndUpdatesGridAuthority() {
        val table = sampleTable()

        val resizedCol = TableElementFactory.updateColWidth(table, 0, 95.0)
        assertEquals(listOf(95.0, 70.0, 80.0), resizedCol.colWidths)
        assertEquals(245.0, resizedCol.width, 1e-4)
        assertEquals(table.cells, resizedCol.cells)
        assertEquals(table.id, resizedCol.id)

        val resizedRow = TableElementFactory.updateRowHeight(resizedCol, 1, 55.0)
        assertEquals(listOf(30.0, 55.0), resizedRow.rowHeights)
        assertEquals(85.0, resizedRow.height, 1e-4)
        assertEquals(table.cells, resizedRow.cells)
        assertEquals(table.id, resizedRow.id)
    }
}
