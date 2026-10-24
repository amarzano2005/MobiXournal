package com.mobixournal.desktop

import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The **grips on a living element** — the arithmetic the canvas asks when a press lands on a figure's
 * corner or a table's divider.
 *
 * Everything here is page geometry, so it is a JVM test: which grips a family offers, which one a press
 * catches (and that a press in open paper catches none), and what a drag of a given offset writes. The
 * rules protected are the ones a drag could break silently — a divider never swallows its neighbour, a
 * drag leaves every cell's text alone, and a gesture's offset is applied to the element as it was, never
 * compounded.
 */
class LivingGripsTest {

    private fun triangle(): ParametricShapeElement = ParametricShapeElement(
        shape = "triangle",
        vertices = listOf(ShapeVertex(100.0, 100.0), ShapeVertex(200.0, 100.0), ShapeVertex(150.0, 200.0)),
        strokeColor = 0xFF000000.toInt(),
    )

    private fun table(): TableElement = LivingPlacement.table(Bounds(100.0, 100.0, 340.0, 220.0), rows = 3, cols = 3)

    @Test
    fun `a figure offers a grip per corner`() {
        val grips = livingGripsOf(triangle())
        assertEquals(3, grips.size)
        assertTrue(grips.all { it.grip is LivingGrip.Vertex })
        assertEquals(150.0, grips[2].x, 1e-9)
        assertEquals(200.0, grips[2].y, 1e-9)
    }

    @Test
    fun `a table offers its interior dividers, not its outer edges`() {
        val grips = livingGripsOf(table())
        assertEquals("three columns and three rows leave two dividers each", 4, grips.size)
        val columns = grips.filter { it.grip is LivingGrip.ColumnEdge }
        val rows = grips.filter { it.grip is LivingGrip.RowEdge }
        assertEquals(2, columns.size)
        assertEquals(2, rows.size)
        // The first divider sits one column in, halfway down the grid.
        val width = 240.0 / 3
        assertEquals(100.0 + width, columns[0].x, 1e-6)
        assertEquals(160.0, columns[0].y, 1e-6)
        assertEquals(100.0 + width * 2, columns[1].x, 1e-6)
        assertEquals("a divider runs across the middle of the grid", 220.0, rows[0].x, 1e-6)
        assertEquals(100.0 + 120.0 / 3, rows[0].y, 1e-6)
    }

    @Test
    fun `a family without grips answers none`() {
        val formula = LivingPlacement.math(Bounds(0.0, 0.0, 60.0, 20.0))
        assertTrue(livingGripsOf(formula).isEmpty())
        assertNull(gripAt(formula, 0.0, 0.0, 10.0))
    }

    @Test
    fun `a press catches the nearest grip inside the radius and none outside it`() {
        val shape = triangle()
        assertEquals(LivingGrip.Vertex(1), gripAt(shape, 204.0, 103.0, tolerancePt = 10.0))
        assertNull("far from every corner", gripAt(shape, 300.0, 300.0, tolerancePt = 10.0))
        // Two grips within the radius: the nearer one wins rather than the first in the list.
        val close = ParametricShapeElement(
            shape = "sliver",
            vertices = listOf(ShapeVertex(100.0, 100.0), ShapeVertex(104.0, 100.0)),
            strokeColor = 0,
        )
        assertEquals(LivingGrip.Vertex(1), gripAt(close, 103.5, 100.0, tolerancePt = 10.0))
    }

    @Test
    fun `dragging a corner bends the figure and leaves the other corners alone`() {
        val shape = triangle()
        val moved = dragGrip(shape, LivingGrip.Vertex(0), dx = -20.0, dy = 30.0, snap = false)
        assertTrue(moved is ParametricShapeElement)
        moved as ParametricShapeElement
        assertEquals(80.0, moved.vertices[0].x, 1e-9)
        assertEquals(130.0, moved.vertices[0].y, 1e-9)
        assertEquals(shape.vertices[1], moved.vertices[1])
        assertEquals(shape.vertices[2], moved.vertices[2])
    }

    @Test
    fun `a corner snaps onto a forty-five degree side unless snapping is off`() {
        val shape = triangle()
        // A drag of (+100, +3) is nearly horizontal: the snapped side leaves the corner at exactly 0 degrees
        // and keeps the pointer's distance, so the corner's y comes back to its neighbour's.
        val snapped = dragGrip(shape, LivingGrip.Vertex(1), dx = 100.0, dy = 3.0) as ParametricShapeElement
        assertEquals(100.0, snapped.vertices[1].y, 1e-6)
        val free = dragGrip(shape, LivingGrip.Vertex(1), dx = 100.0, dy = 3.0, snap = false) as ParametricShapeElement
        assertEquals(103.0, free.vertices[1].y, 1e-9)
    }

    @Test
    fun `a divider widens its column, keeps the box's width and touches no cell`() {
        val table = table()
        val width = table.colWidths[0]
        val moved = dragGrip(table, LivingGrip.ColumnEdge(1), dx = 20.0, dy = 0.0)
        assertTrue(moved is TableElement)
        moved as TableElement
        assertEquals(width + 20.0, moved.colWidths[0], 1e-9)
        assertEquals(width - 20.0, moved.colWidths[1], 1e-9)
        assertEquals("the grid is still as wide", table.width, moved.width, 1e-9)
        assertEquals("and as high", table.rowHeights, moved.rowHeights)
        assertEquals("the cells are untouched", table.cells, moved.cells)
        assertEquals("the box's corner does not move", table.left, moved.left, 1e-9)
    }

    @Test
    fun `a divider stops before it swallows its neighbour`() {
        val table = table()
        val moved = dragGrip(table, LivingGrip.RowEdge(1), dx = 0.0, dy = 10_000.0) as TableElement
        assertEquals("the lower row keeps a readable height", GRIP_MIN_CELL_PT, moved.rowHeights[1], 1e-9)
        assertEquals(72.0, moved.rowHeights[0], 1e-9)
        assertEquals("the grid is still as high", table.height, moved.height, 1e-9)
        val other = dragGrip(table, LivingGrip.RowEdge(1), dx = 0.0, dy = -10_000.0) as TableElement
        assertEquals(GRIP_MIN_CELL_PT, other.rowHeights[0], 1e-9)
        assertEquals(table.height, other.height, 1e-9)
    }

    @Test
    fun `a grip the element does not have writes nothing`() {
        val table = table()
        assertSame(table, dragGrip(table, LivingGrip.Vertex(0), 10.0, 10.0))
        assertSame(table, dragGrip(table, LivingGrip.ColumnEdge(0), 10.0, 0.0))
        assertSame(table, dragGrip(table, LivingGrip.RowEdge(9), 0.0, 10.0))
    }

    @Test
    fun `two clicks in one place are a double-click and two clicks apart are not`() {
        val taps = TapTracker()
        assertFalse("the first click only remembers", taps.isSecond(nowMs = 1_000L, px = 100.0, py = 100.0))
        assertTrue("the second click inside the window", taps.isSecond(nowMs = 1_260L, px = 103.0, py = 101.0))
        assertFalse("a third click starts a new pair", taps.isSecond(nowMs = 1_300L, px = 103.0, py = 101.0))
        assertFalse("too late to be one gesture", taps.isSecond(nowMs = 1_900L, px = 103.0, py = 101.0))
        assertFalse("and too far away", taps.isSecond(nowMs = 2_100L, px = 400.0, py = 300.0))
    }
}
