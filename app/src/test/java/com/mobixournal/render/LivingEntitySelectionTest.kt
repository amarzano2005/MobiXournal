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
import com.mobixournal.format.model.TableElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for selection, movement, resizing and deletion of living entities, focusing on
 * [PlotElement] (the first family with selection and move support).
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
    fun otherThreeLivingFamiliesRemainNonHitTestable() {
        val table = sampleTable()
        val shape = sampleShape()
        val circuit = sampleCircuit()

        assertFalse("TableElement must not be hit-testable", ElementBounds.isHitTestable(table))
        assertFalse("ParametricShapeElement must not be hit-testable", ElementBounds.isHitTestable(shape))
        assertFalse("CircuitElement must not be hit-testable", ElementBounds.isHitTestable(circuit))

        assertEquals(Bounds(0.0, 0.0, 0.0, 0.0), ElementBounds.of(table))
        assertEquals(Bounds(0.0, 0.0, 0.0, 0.0), ElementBounds.of(shape))
        assertEquals(Bounds(0.0, 0.0, 0.0, 0.0), ElementBounds.of(circuit))
    }

    @Test
    fun marqueeOverPlotPicksItUpWhileTableIsIgnored() {
        val plot = samplePlot(left = 100.0, top = 100.0, right = 300.0, bottom = 250.0)
        val table = sampleTable(left = 120.0, top = 120.0)
        val page = testPage(
            Layer(listOf(plot, table)),
        )

        // Marquee fully enclosing both locations [50, 50] to [350, 300]
        val marquee = Bounds(50.0, 50.0, 350.0, 300.0)
        val selected = SelectionTester.inRect(page, marquee)

        // Index 0 is PlotElement, index 1 is TableElement
        assertEquals("Only PlotElement should be picked", setOf(ElementRef(0, 0)), selected)

        // Lasso enclosing the same area
        val lasso = listOf(
            Vec2(50.0, 50.0),
            Vec2(350.0, 50.0),
            Vec2(350.0, 300.0),
            Vec2(50.0, 300.0),
        )
        val lassoSelected = SelectionTester.inPolygon(page, lasso)
        assertEquals("Lasso should also pick only PlotElement", setOf(ElementRef(0, 0)), lassoSelected)

        // Tap inside plot selects it
        val tappedRef = SelectionTester.pickTopmost(page, 200.0, 180.0)
        assertEquals(ElementRef(0, 0), tappedRef)

        // Tap strictly inside table bounds (where table is at [120, 120] to [220, 160]) does not pick table
        val emptyPageWithTableOnly = testPage(Layer(listOf(table)))
        val tableTapRef = SelectionTester.pickTopmost(emptyPageWithTableOnly, 130.0, 130.0)
        assertEquals(null, tableTapRef)
    }

    @Test
    fun moveShiftsBoxExactlyAndPreservesEverythingElse() {
        val plot = samplePlot()
        val moved = SelectionOps.translate(plot, 45.0, -20.0) as PlotElement

        assertEquals(145.0, moved.left, 1e-9)
        assertEquals(80.0, moved.top, 1e-9)
        assertEquals(345.0, moved.right, 1e-9)
        assertEquals(230.0, moved.bottom, 1e-9)

        // copy() equality assertion on everything else
        val expected = plot.copy(left = 145.0, top = 80.0, right = 345.0, bottom = 230.0)
        assertEquals(expected, moved)
        assertEquals(plot.xMin, moved.xMin, 1e-9)
        assertEquals(plot.xMax, moved.xMax, 1e-9)
        assertEquals(plot.curves, moved.curves)
        assertEquals(plot.parameters, moved.parameters)
        assertEquals(plot.samples, moved.samples)
    }

    @Test
    fun resizeChangesOnlyTheBoxAndLeavesRangesIntact() {
        val plot = samplePlot(left = 100.0, top = 100.0, right = 300.0, bottom = 200.0)
        // Uniform or non-uniform affineXY: sx = 1.5, sy = 2.0, dx = -10.0, dy = 5.0
        val resized = SelectionOps.affineXY(plot, sx = 1.5, sy = 2.0, dx = -10.0, dy = 5.0) as PlotElement

        assertEquals(100.0 * 1.5 - 10.0, resized.left, 1e-9)
        assertEquals(100.0 * 2.0 + 5.0, resized.top, 1e-9)
        assertEquals(300.0 * 1.5 - 10.0, resized.right, 1e-9)
        assertEquals(200.0 * 2.0 + 5.0, resized.bottom, 1e-9)

        val expected = plot.copy(
            left = 140.0,
            top = 205.0,
            right = 440.0,
            bottom = 405.0,
        )
        assertEquals(expected, resized)
        assertEquals(plot.xMin, resized.xMin, 1e-9)
        assertEquals(plot.xMax, resized.xMax, 1e-9)
    }

    @Test
    fun deleteRemovesPlotFromLayer() {
        val plot = samplePlot()
        val pages = listOf(
            testPage(Layer(listOf(plot))),
        )

        val afterDelete = SelectionOps.delete(pages, pageIndex = 0, refs = setOf(ElementRef(0, 0)))
        assertTrue(afterDelete[0].layers[0].elements.isEmpty())
    }
}
