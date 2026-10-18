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

    @Test
    fun cellBoundsToViewPxConvertsPtToSurfacePx() {
        val box = PageBox(
            index = 0,
            scale = 2f,
            leftPx = 10f,
            topPx = 20f,
            widthPx = 500f,
            heightPx = 500f,
            source = com.mobixournal.format.mobix.PageSource.of(emptyList()),
        )
        val boundsPt = com.mobixournal.render.Bounds(left = 50.0, top = 60.0, right = 150.0, bottom = 100.0)

        val rect = cellBoundsToViewPx(boundsPt, box, scrollX = 5f, scrollY = 10f)
        assertNotNull(rect)
        // left: 50 * 2 + 10 - 5 = 105
        assertEquals(105.0, rect!!.left, 1e-4)
        // top: 60 * 2 + 20 - 10 = 130
        assertEquals(130.0, rect.top, 1e-4)
        // right: 150 * 2 + 10 - 5 = 305
        assertEquals(305.0, rect.right, 1e-4)
        // bottom: 100 * 2 + 20 - 10 = 210
        assertEquals(210.0, rect.bottom, 1e-4)
    }

    @Test
    fun emptyCellsDoNotInheritFromPreviousCell() {
        val table = TableElement(
            left = 0.0,
            top = 0.0,
            colWidths = listOf(50.0, 50.0),
            rowHeights = listOf(30.0),
            cells = listOf(listOf(TableCell("Cell0"), TableCell(""))),
        )
        // Cell 0 has content
        assertEquals("Cell0", table.cell(0, 0).text)
        // Cell 1 is strictly empty
        assertEquals("", table.cell(0, 1).text)

        // When updating cell 0, cell 1 remains completely empty
        val updated = TableElementFactory.updateCell(table, 0, 0, table.cell(0, 0).copy(text = "NewText"))
        assertEquals("NewText", updated.cell(0, 0).text)
        assertEquals("", updated.cell(0, 1).text)
    }

    // ---------------------------------------------------------------------
    // flushTableCellDraft — the seam that keeps typed text alive across a
    // clobber of the editing state (cell switch, back press, tool change).

    @Test
    fun flushTableCellDraftCommitsPendingTextIntoTheCell() {
        val table = sampleTable()
        val editing = EditingTableCell(
            table = table,
            row = 0,
            col = 1,
            cellBoundsPt = TableDividers.cellBoundsPt(table, 0, 1),
            pageIndex = 0,
            draftText = "B engraved",
        )
        val swaps = mutableListOf<Pair<TableElement, TableElement>>()

        val flushed = flushTableCellDraft(editing) { old, new -> swaps.add(old to new) }

        assertEquals("one swap: old table -> updated table", 1, swaps.size)
        assertEquals("flush swaps the very table that was editing", table, swaps[0].first)
        assertEquals(
            "the draft lands in the edited cell, and only there",
            "B engraved", swappedCellText(swaps[0].second, 0, 1),
        )
        assertEquals("A, untouched", "A", swappedCellText(swaps[0].second, 0, 0))
        assertEquals("F, untouched", "F", swappedCellText(swaps[0].second, 1, 2))
        assertEquals("the id survives the flush", table.id, swaps[0].second.id)
        assertNotNull("the editing target stays open after a flush", flushed)
        assertEquals("the committed table becomes the editing table", swaps[0].second, flushed!!.table)
        assertNull("the draft is absorbed after committing", flushed.draftText)
    }

    @Test
    fun flushTableCellDraftWithoutADraftIsANoOpThatClearsTheField() {
        val table = sampleTable()
        var called = false

        val flushed = flushTableCellDraft(
            EditingTableCell(table, 1, 2, null, 0, draftText = null),
        ) { _, _ -> called = true }

        assertTrue("no swap is applied when there is nothing pending", !called)
        assertEquals("the target passes through unchanged", "F", flushed!!.table.cell(1, 2).text)
        assertNull("the draft field stays clear", flushed.draftText)

        assertNull("a null editing state flushes to null", flushTableCellDraft(null) { _, _ -> called = true })
    }

    @Test
    fun flushTableCellDraftWithUnchangedTextAppliesNoSwap() {
        val table = sampleTable()
        var called = false

        val flushed = flushTableCellDraft(
            EditingTableCell(table, 0, 0, null, 0, draftText = "A"),
        ) { _, _ -> called = true }

        assertTrue("re-committing the cell's own text writes nothing", !called)
        assertNull("but the draft field is still cleared", flushed!!.draftText)
    }

    private fun swappedCellText(table: TableElement, row: Int, col: Int): String =
        table.cell(row, col).text
}
