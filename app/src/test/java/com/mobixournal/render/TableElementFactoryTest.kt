package com.mobixournal.render

import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixAssetSource
import com.mobixournal.format.mobix.MobixElementCodec
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableAlign
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [TableElementFactory] and table authoring round trips.
 */
class TableElementFactoryTest {

    @Test
    fun dragInProducesTableWhoseColWidthsAndRowHeightsSumToBounds() {
        val table = TableElementFactory.tableElementFor(
            startX = 10.0,
            startY = 20.0,
            endX = 210.0,
            endY = 140.0,
            rows = 3,
            cols = 4,
            hasHeader = true,
            borderColor = 0xFF123456.toInt(),
            borderWidth = 1.5,
        )

        assertEquals(10.0, table.left, 1e-4)
        assertEquals(20.0, table.top, 1e-4)
        assertEquals(4, table.colWidths.size)
        assertEquals(3, table.rowHeights.size)
        assertEquals(200.0, table.width, 1e-4)
        assertEquals(120.0, table.height, 1e-4)
        assertEquals(200.0, table.colWidths.sum(), 1e-4)
        assertEquals(120.0, table.rowHeights.sum(), 1e-4)
        assertTrue(table.headerRow)
        assertEquals(0xFF123456.toInt(), table.borderColor)
        assertEquals(1.5, table.borderWidth, 1e-4)

        assertEquals(3, table.cells.size)
        table.cells.forEach { row ->
            assertEquals(4, row.size)
            row.forEach { cell ->
                assertEquals("", cell.text)
                assertEquals(TableAlign.LEFT, cell.align)
            }
        }
    }

    @Test
    fun reversedDragNormalizesOriginAndPositiveDimensions() {
        val table = TableElementFactory.tableElementFor(
            startX = 250.0,
            startY = 180.0,
            endX = 50.0,
            endY = 60.0,
            rows = 2,
            cols = 2,
        )

        assertEquals(50.0, table.left, 1e-4)
        assertEquals(60.0, table.top, 1e-4)
        assertEquals(200.0, table.width, 1e-4)
        assertEquals(120.0, table.height, 1e-4)
        assertEquals(200.0, table.colWidths.sum(), 1e-4)
        assertEquals(120.0, table.rowHeights.sum(), 1e-4)
    }

    @Test
    fun boxForClampsWithinPageBounds() {
        val page = Page(400.0, 400.0, Background.Solid(0xFFFFFFFF.toInt(), "plain"), emptyList())
        val box = TableElementFactory.boxFor(
            page = page,
            centreX = 20.0,
            centreY = 20.0,
            rows = 2,
            cols = 2,
            widthPt = 200.0,
            heightPt = 100.0,
        )

        assertEquals(0.0, box.left, 1e-4)
        assertEquals(0.0, box.top, 1e-4)
        assertEquals(200.0, box.width, 1e-4)
        assertEquals(100.0, box.height, 1e-4)
    }

    @Test
    fun updateCellAndDimensionsPreserveUneditedAttributes() {
        val original = TableElement(
            left = 50.0,
            top = 50.0,
            colWidths = listOf(60.0, 60.0),
            rowHeights = listOf(30.0, 30.0),
            headerRow = true,
            borderColor = 0xFF555555.toInt(),
            borderWidth = 2.0,
            id = "tbl_custom_42",
            extraAttrs = mapOf("custom" to "yes"),
        )

        val updatedCell = TableCell("Updated", TableAlign.CENTER, bold = true, fillColor = 0x20FF0000.toInt())
        val withCell = TableElementFactory.updateCell(original, 0, 1, updatedCell)

        assertEquals("tbl_custom_42", withCell.id)
        assertEquals(mapOf("custom" to "yes"), withCell.extraAttrs)
        assertEquals(0xFF555555.toInt(), withCell.borderColor)
        assertEquals(2.0, withCell.borderWidth, 1e-4)
        assertEquals(updatedCell, withCell.cell(0, 1))
        assertEquals("", withCell.cell(0, 0).text)

        val withCol = TableElementFactory.updateColWidth(withCell, 0, 85.0)
        assertEquals(listOf(85.0, 60.0), withCol.colWidths)
        assertEquals("tbl_custom_42", withCol.id)

        val withRow = TableElementFactory.updateRowHeight(withCol, 1, 45.0)
        assertEquals(listOf(30.0, 45.0), withRow.rowHeights)
        assertEquals("tbl_custom_42", withRow.id)
    }

    @Test
    fun tableSurvivesWriteAndReadThroughMobixTableCodec() {
        val table = TableElementFactory.tableElementFor(
            startX = 40.0,
            startY = 50.0,
            endX = 240.0,
            endY = 170.0,
            rows = 2,
            cols = 3,
            hasHeader = true,
        ).copy(id = "tbl_roundtrip_1")

        val withContent = TableElementFactory.updateCell(
            table,
            0,
            0,
            TableCell("Header 1", TableAlign.CENTER, bold = true),
        )

        val sink = MobixAssetSink()
        val jsonNode = MobixElementCodec.encode(withContent, sink)
        val source = MobixAssetSource { null }
        val decoded = MobixElementCodec.decode(jsonNode, source) as? TableElement

        assertNotNull("TableElement must decode successfully", decoded)
        assertEquals(withContent.left, decoded!!.left, 1e-4)
        assertEquals(withContent.top, decoded.top, 1e-4)
        assertEquals(withContent.colWidths, decoded.colWidths)
        assertEquals(withContent.rowHeights, decoded.rowHeights)
        assertEquals(withContent.headerRow, decoded.headerRow)
        assertEquals("tbl_roundtrip_1", decoded.id)
        assertEquals("Header 1", decoded.cell(0, 0).text)
        assertEquals(TableAlign.CENTER, decoded.cell(0, 0).align)
        assertTrue(decoded.cell(0, 0).bold)
    }

    @Test
    fun xoppSynthesisCompilesTableToStrokesAndText() {
        val table = TableElementFactory.tableElementFor(
            startX = 10.0,
            startY = 10.0,
            endX = 110.0,
            endY = 70.0,
            rows = 2,
            cols = 2,
        )
        val populated = TableElementFactory.updateCell(table, 0, 0, TableCell("Item A"))

        val doc = com.mobixournal.format.model.Document(
            pages = listOf(
                com.mobixournal.format.model.Page(
                    width = 500.0,
                    height = 500.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(com.mobixournal.format.model.Layer(listOf(populated))),
                ),
            ),
        )
        val synthesisResult = XoppSynthesis.synthesize(doc)
        val elements = synthesisResult.document.pages.first().layers.first().elements
        assertTrue("Synthesized elements must not be empty", elements.isNotEmpty())
        assertTrue("Must include border strokes", elements.any { it is Stroke })
        assertTrue("Must include cell text box", elements.any { it is TextElement && it.content == "Item A" })
    }
}
