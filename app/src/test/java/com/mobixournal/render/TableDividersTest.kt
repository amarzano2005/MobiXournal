package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [TableDividers]: divider hit-testing and cell geometry.
 */
class TableDividersTest {

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
        val ref = ElementRef(0, 0)
        val box = PageBox(
            index = 0,
            scale = 1f,
            leftPx = 0f,
            topPx = 0f,
            widthPx = 500f,
            heightPx = 500f,
            source = PageSource.of(emptyList()),
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
}
