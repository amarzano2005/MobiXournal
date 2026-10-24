package com.mobixournal.render

import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.TableElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a press on a STEM slot puts its element, and what it builds.
 *
 * The window's Plot, Table and Circuit slots place a **living element**; the box is this object's answer,
 * so these tests are the whole of "a click gets the default size, a drag gets its own box, and neither
 * hangs off the sheet". The builders are checked for the fields a later edit depends on — a plot's
 * formula, a table's grid splitting the drag exactly, a component's canonical pins — because an element
 * that lands in the wrong place or with the wrong recipe cannot be corrected after the fact.
 */
class LivingPlacementTest {

    private val pageW = 595.0
    private val pageH = 842.0

    private fun table() = LivingPlacement.defaultSize(LivingSlot.TABLE, pageW)

    @Test
    fun `a click places the family's default size centred on the press`() {
        val box = LivingPlacement.bounds(pageW, pageH, 300.0, 400.0, 300.0, 400.0, table())
        assertEquals(180.0, box.left, 1e-9)
        assertEquals(320.0, box.top, 1e-9)
        assertEquals(420.0, box.right, 1e-9)
        assertEquals(480.0, box.bottom, 1e-9)
    }

    @Test
    fun `a drag is the box the element takes`() {
        val box = LivingPlacement.bounds(pageW, pageH, 350.0, 300.0, 100.0, 120.0, table())
        assertEquals(100.0, box.left, 1e-9)
        assertEquals(120.0, box.top, 1e-9)
        assertEquals(350.0, box.right, 1e-9)
        assertEquals(300.0, box.bottom, 1e-9)
    }

    @Test
    fun `a drag along one axis falls back to the default size`() {
        val box = LivingPlacement.bounds(pageW, pageH, 250.0, 300.0, 251.0, 300.0, table())
        assertEquals("a two-point-wide drag is a shaky hand, not a box", 240.0, box.width, 1e-9)
        assertEquals(160.0, box.height, 1e-9)
    }

    @Test
    fun `a placement is kept inside the page`() {
        val box = LivingPlacement.bounds(pageW, pageH, 590.0, 838.0, 590.0, 838.0, table())
        assertEquals("the box's right edge meets the sheet", pageW, box.right, 1e-9)
        assertEquals("and its bottom too", pageH, box.bottom, 1e-9)
        assertEquals(pageW - 240.0, box.left, 1e-9)
        assertEquals(pageH - 160.0, box.top, 1e-9)
    }

    @Test
    fun `a page smaller than the default shrinks the box`() {
        val box = LivingPlacement.bounds(200.0, 100.0, 100.0, 50.0, 100.0, 50.0, table())
        assertEquals(200.0, box.width, 1e-9)
        assertEquals(100.0, box.height, 1e-9)
        assertEquals(0.0, box.left, 1e-9)
        assertEquals(0.0, box.top, 1e-9)
    }

    @Test
    fun `a placed plot carries its box, its starter formula and the pen's ink`() {
        val box = Bounds(100.0, 200.0, 340.0, 344.0)
        val plot = LivingPlacement.plot(box, colour = 0xFFCC0000.toInt(), strokeWidthPt = 2.5)
        assertEquals(box.left, plot.left, 1e-9)
        assertEquals(box.top, plot.top, 1e-9)
        assertEquals(box.right, plot.right, 1e-9)
        assertEquals(box.bottom, plot.bottom, 1e-9)
        val curve = plot.curves.single()
        assertEquals(LivingPlacement.DEFAULT_PLOT_FORMULA, curve.source)
        assertEquals(0xFFCC0000.toInt(), curve.color)
        assertEquals(2.5, curve.strokeWidth, 1e-9)
        assertEquals(PlotElement.DEFAULT_X_MIN, plot.xMin, 1e-9)
        assertEquals(PlotElement.DEFAULT_X_MAX, plot.xMax, 1e-9)
        assertEquals(PlotElement.DEFAULT_SAMPLES, plot.samples)
        assertNull("identity is the codec's to mint", plot.id)
    }

    @Test
    fun `a placed table divides the drag's box exactly`() {
        val box = Bounds(40.0, 60.0, 340.0, 260.0)
        val table = LivingPlacement.table(box, rows = 3, cols = 4)
        assertEquals(4, table.colWidths.size)
        assertEquals(3, table.rowHeights.size)
        assertEquals("the grid is as wide as the drag", box.width, table.colWidths.sum(), 1e-9)
        assertEquals("and as tall", box.height, table.rowHeights.sum(), 1e-9)
        assertEquals(box.left, table.left, 1e-9)
        assertEquals(box.top, table.top, 1e-9)
        assertTrue("a fresh table has its header row", table.headerRow)
        assertEquals(3, table.cells.size)
        assertEquals(4, table.cells.first().size)
    }

    @Test
    fun `a placed component is the picked kind along the drag, with canonical pins`() {
        val box = Bounds(100.0, 90.0, 180.0, 130.0)
        val capacitor = LivingPlacement.circuit(ShapeKind.CAPACITOR, box, colour = 0xFF000000.toInt(), strokeWidthPt = 1.5)
        assertNotNull(capacitor)
        val part = capacitor!!
        assertEquals("CAPACITOR", part.symbol)
        assertEquals(0.0, part.rotation, 1e-9)
        assertEquals((box.left + box.right) / 2.0, part.positionX, 1e-9)
        assertEquals((box.top + box.bottom) / 2.0, part.positionY, 1e-9)
        assertEquals(2, part.pins.size)
        assertEquals("the drag's width is the part's length", box.width, part.pins.last().relX - part.pins.first().relX, 1e-9)
        assertNull("a placement writes the model's own value", part.value)
        assertNull(part.id)
    }

    @Test
    fun `the default circuit kind is a part this build can draw`() {
        assertTrue(LivingPlacement.DEFAULT_CIRCUIT_KIND in CircuitElementFactory.CIRCUIT_KINDS)
        assertTrue(CircuitInk.ink(LivingPlacement.circuit(
            LivingPlacement.DEFAULT_CIRCUIT_KIND,
            Bounds(0.0, 0.0, 48.0, 24.0),
            colour = 0xFF000000.toInt(),
            strokeWidthPt = 1.0,
        )!!)!!.isNotEmpty())
    }

    @Test
    fun `the family defaults are the sizes the tablet uses`() {
        val plot = LivingPlacement.defaultSize(LivingSlot.PLOT, pageW)
        assertEquals("45 % of the page, between 240 and 320 pt", 267.75, plot.width, 1e-9)
        assertEquals(plot.width * 0.6, plot.height, 1e-9)
        val tableSize = LivingPlacement.defaultSize(LivingSlot.TABLE, pageW)
        assertEquals(TableElementFactory.DEFAULT_WIDTH_PT, tableSize.width, 1e-9)
        assertEquals(TableElementFactory.DEFAULT_HEIGHT_PT, tableSize.height, 1e-9)
        val circuit = LivingPlacement.defaultSize(LivingSlot.CIRCUIT, pageW)
        assertEquals(CircuitInk.DEFAULT_LENGTH_PT, circuit.width, 1e-9)
    }
}
