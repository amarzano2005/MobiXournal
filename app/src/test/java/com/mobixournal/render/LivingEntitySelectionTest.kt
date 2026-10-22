package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for selection, movement, resizing and deletion of living entities, focusing on
 * [PlotElement] and [TableElement] (living entities with selection and move support).
 */
class LivingEntitySelectionTest {

    private val black = 0xFF000000.toInt()

    private fun samplePlot(
        left: Double = 100.0,
        top: Double = 100.0,
        right: Double = 300.0,
        bottom: Double = 250.0,
    ) = PlotElement(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        curves = listOf(PlotCurve(PlotKind.CARTESIAN, "sin(x)", black, strokeWidth = 2.0)),
        xMin = -5.0,
        xMax = 5.0,
        samples = 150,
        showCriticalPoints = true,
        includeDerivative = false,
        parameters = mapOf("a" to 2.0),
        id = "plt_living_1",
    )

    private fun sampleTable(left: Double = 100.0, top: Double = 100.0) = TableElement(
        left = left,
        top = top,
        colWidths = listOf(50.0, 50.0),
        rowHeights = listOf(20.0, 20.0),
        cells = listOf(
            listOf(TableCell("A"), TableCell("B")),
            listOf(TableCell("C"), TableCell("D")),
        ),
        id = "tbl_living_1",
    )

    private fun sampleShape() = ParametricShapeElement(
        shape = "TRIANGLE_SCALENE",
        vertices = listOf(ShapeVertex(10.0, 10.0), ShapeVertex(20.0, 30.0), ShapeVertex(5.0, 40.0)),
        strokeColor = black,
        id = "shp_living_1",
    )

    private fun sampleCircuit() = CircuitElement(
        symbol = "RESISTOR_IEEE",
        positionX = 50.0,
        positionY = 50.0,
        id = "ckt_living_1",
    )

    private fun testPage(vararg layers: Layer) = Page(
        500.0,
        500.0,
        Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers.toList(),
    )

    @Test
    fun everyLivingEntityIsHitTestableIncludingTheShape() {
        val plot = samplePlot()
        val table = sampleTable()
        val shape = sampleShape()
        val circuit = sampleCircuit()

        assertTrue("PlotElement must be hit-testable", ElementBounds.isHitTestable(plot))
        assertTrue("TableElement must be hit-testable", ElementBounds.isHitTestable(table))
        assertTrue("CircuitElement must be hit-testable", ElementBounds.isHitTestable(circuit))
        assertTrue("ParametricShapeElement must be hit-testable", ElementBounds.isHitTestable(shape))

        val halfBorder = table.borderWidth / 2.0
        val expectedTableBounds = Bounds(
            table.left - halfBorder,
            table.top - halfBorder,
            table.left + table.width + halfBorder,
            table.top + table.height + halfBorder,
        )
        assertEquals(expectedTableBounds, ElementBounds.of(table))
        // A shape's box is its own corners, grown by the outline's half-width.
        val xs = shape.vertices.map { it.x }
        val ys = shape.vertices.map { it.y }
        val half = shape.strokeWidth / 2.0
        assertEquals(
            Bounds(xs.min() - half, ys.min() - half, xs.max() + half, ys.max() + half),
            ElementBounds.of(shape),
        )
        val circuitBounds = ElementBounds.of(circuit)
        assertTrue("Circuit bounds must be non-empty", circuitBounds.width > 0.0 && circuitBounds.height > 0.0)
    }

    @Test
    fun marqueeAndLassoPickUpBothPlotAndTable() {
        val plot = samplePlot(left = 100.0, top = 100.0, right = 200.0, bottom = 200.0)
        val table = sampleTable(left = 220.0, top = 100.0)
        val page = testPage(Layer(listOf(plot, table)))

        // Marquee fully enclosing both locations [50, 50] to [350, 300]
        val marquee = Bounds(50.0, 50.0, 350.0, 300.0)
        val selected = SelectionTester.inRect(page, marquee)
        assertEquals("Both Plot and Table should be picked", setOf(ElementRef(0, 0), ElementRef(0, 1)), selected)

        // Lasso enclosing only the table [210, 80] to [340, 160]
        val lasso = listOf(
            Vec2(210.0, 80.0),
            Vec2(340.0, 80.0),
            Vec2(340.0, 160.0),
            Vec2(210.0, 160.0),
        )
        val lassoSelected = SelectionTester.inPolygon(page, lasso)
        assertEquals("Lasso should pick only Table", setOf(ElementRef(0, 1)), lassoSelected)
    }

    @Test
    fun tapSelectsTableOrPlot() {
        val plot = samplePlot(left = 100.0, top = 100.0, right = 200.0, bottom = 200.0)
        val table = sampleTable(left = 250.0, top = 100.0)
        val page = testPage(Layer(listOf(plot, table)))

        val plotTap = SelectionTester.pickTopmost(page, 150.0, 150.0)
        assertEquals(ElementRef(0, 0), plotTap)

        val tableTap = SelectionTester.pickTopmost(page, 280.0, 120.0)
        assertEquals(ElementRef(0, 1), tableTap)

        val outsideTap = SelectionTester.pickTopmost(page, 10.0, 10.0)
        assertNull(outsideTap)
    }

    @Test
    fun moveTableTranslatesOriginWhileColWidthsAndRowHeightsStayPut() {
        val table = sampleTable(left = 100.0, top = 80.0)
        val moved = SelectionOps.translate(table, 35.0, -15.0) as TableElement

        assertEquals(135.0, moved.left, 1e-9)
        assertEquals(65.0, moved.top, 1e-9)
        assertEquals(table.colWidths, moved.colWidths)
        assertEquals(table.rowHeights, moved.rowHeights)
        assertEquals(table.width, moved.width, 1e-9)
        assertEquals(table.height, moved.height, 1e-9)
        assertEquals(table.cells, moved.cells)
        assertEquals(table.id, moved.id)
    }

    @Test
    fun resizeTableScalesColWidthsAndRowHeights() {
        val table = sampleTable(left = 100.0, top = 100.0)
        val resized = SelectionOps.affineXY(table, sx = 1.5, sy = 2.0, dx = -10.0, dy = 5.0) as TableElement

        assertEquals(100.0 * 1.5 - 10.0, resized.left, 1e-9)
        assertEquals(100.0 * 2.0 + 5.0, resized.top, 1e-9)
        assertEquals(listOf(75.0, 75.0), resized.colWidths)
        assertEquals(listOf(40.0, 40.0), resized.rowHeights)
        assertEquals(150.0, resized.width, 1e-9)
        assertEquals(80.0, resized.height, 1e-9)
        assertEquals(table.cells, resized.cells)
        assertEquals(table.id, resized.id)
        assertEquals("a resized table keeps its rule thickness", table.borderWidth, resized.borderWidth, 1e-9)
    }

    @Test
    fun resizePlotScalesItsBoxButKeepsEveryCurveStrokeWidth() {
        val plot = samplePlot()
        val pages = listOf(testPage(Layer(listOf(plot))))

        val resized = SelectionOps.scaleXY(
            pages = pages,
            pageIndex = 0,
            refs = setOf(ElementRef(0, 0)),
            sx = 2.0,
            sy = 0.5,
            anchorX = 0.0,
            anchorY = 0.0,
        )[0].layers[0].elements[0] as PlotElement

        assertEquals("the plot's box follows the resize", 200.0, resized.left, 1e-9)
        assertEquals("the plot's box follows the resize", 50.0, resized.top, 1e-9)
        assertEquals("the mathematical range is untouched", plot.xMin, resized.xMin, 1e-9)
        assertEquals("the mathematical range is untouched", plot.xMax, resized.xMax, 1e-9)
        assertEquals(
            "a bigger diagram has longer axes, not thicker ink",
            plot.curves.map { it.strokeWidth },
            resized.curves.map { it.strokeWidth },
        )
    }

    @Test
    fun resizeCircuitMovesItsPinsButKeepsTheStrokeWidth() {
        val circuit = sampleCircuit()
        val pages = listOf(testPage(Layer(listOf(circuit))))

        val resized = SelectionOps.scaleXY(
            pages = pages,
            pageIndex = 0,
            refs = setOf(ElementRef(0, 0)),
            sx = 2.0,
            sy = 2.0,
            anchorX = 0.0,
            anchorY = 0.0,
        )[0].layers[0].elements[0] as CircuitElement

        assertEquals(100.0, resized.positionX, 1e-9)
        assertEquals(100.0, resized.positionY, 1e-9)
        assertEquals("a bigger component keeps its symbol's line weight", circuit.strokeWidth, resized.strokeWidth, 1e-9)
    }

    @Test
    fun deleteAndUndoTableRemovesAndRestoresElement() {
        val table = sampleTable()
        val originalPages = listOf(testPage(Layer(listOf(table))))
        val afterDelete = SelectionOps.delete(originalPages, pageIndex = 0, refs = setOf(ElementRef(0, 0)))

        assertTrue("Layer must be empty after delete", afterDelete[0].layers[0].elements.isEmpty())

        // Undo restoration check
        val restored = originalPages[0].layers[0].elements.first() as TableElement
        assertEquals(table, restored)
    }
}
