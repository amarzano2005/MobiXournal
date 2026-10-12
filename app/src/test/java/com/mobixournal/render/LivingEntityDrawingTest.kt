package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a living entity is **drawn** (`docs/mobix-elements.md`): the canvas, the PDF painter and the
 * SVG/PNG writers all go through [LivingInk], which is a thin dispatch over the same compilers the
 * `.xopp` export writes from.
 *
 * That is the property worth testing, because the alternative is the failure mode: two drawings of one
 * file — a table that looks one way on screen and another way in the PDF — is worse than a table that is
 * simply invisible, since nothing tells the user which of the two is real. So the first test compares the
 * drawing against the *compilers*, not against a snapshot of the geometry, and the last one follows the
 * ink all the way out of a real SVG export.
 */
class LivingEntityDrawingTest {

    private val black = 0xFF000000.toInt()
    private val red = 0xFFD32F2F.toInt()

    private fun table(cell: String = "Parametro") = TableElement(
        left = 72.0, top = 120.0,
        colWidths = listOf(120.0, 180.0),
        rowHeights = listOf(24.0),
        cells = listOf(listOf(TableCell(cell, bold = true), TableCell("Formula"))),
        headerRow = true,
        id = "tbl_1",
    )

    private fun plot(vararg formulas: String = arrayOf("sin(x)")) = PlotElement(
        left = 100.0, top = 200.0, right = 300.0, bottom = 320.0,
        curves = formulas.map { PlotCurve(PlotKind.CARTESIAN, it, red, strokeWidth = 1.5) },
        xMin = -6.28, xMax = 6.28, samples = 60,
        id = "plt_1",
    )

    private fun shape() = ParametricShapeElement(
        shape = "HEXAGON",
        vertices = listOf(ShapeVertex(200.0, 400.0), ShapeVertex(240.0, 420.0), ShapeVertex(200.0, 440.0)),
        strokeColor = black, strokeWidth = 1.5,
        id = "shp_1",
    )

    private fun circuit(symbol: String = "RESISTOR") = CircuitElement(
        symbol = symbol,
        positionX = 300.0, positionY = 150.0,
        strokeColor = red, strokeWidth = 1.5,
        value = "10 kΩ",
        pins = listOf(CircuitPin("p1", relX = -24.0), CircuitPin("p2", relX = 24.0)),
        id = "ckt_1",
    )

    private fun stroke() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(StrokePoint(10.0, 10.0, 1.0), StrokePoint(40.0, 60.0, 1.0)),
        uniformWidth = true,
    )

    private fun page(vararg elements: Element) = Page(
        width = 595.27559100,
        height = 841.88976400,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    @Test
    fun everyFamilyDrawsAsTheInkItsOwnCompilerProduces() {
        val table = table()
        val plot = plot()
        val shape = shape()
        val circuit = circuit()

        // Against the compilers themselves: the drawing cannot drift from what an export writes, because
        // there is only one definition to drift from.
        assertEquals(TableInk.ink(table), LivingInk.of(table).elements)
        assertEquals(PlotInk.ink(plot).elements, LivingInk.of(plot).elements)
        assertEquals(ShapeInk.ink(shape), LivingInk.of(shape).elements)
        assertEquals(CircuitInk.ink(circuit), LivingInk.of(circuit).elements)
    }

    @Test
    fun theSameElementIsCompiledOnceAndReused() {
        val entity = table()

        // Identity, not equality: the model is immutable, so compiling per frame would be the only thing
        // this could buy, and per-frame compilation of a table is per-frame garbage.
        assertSame(LivingInk.of(entity), LivingInk.of(entity))
    }

    @Test
    fun anElementThatIsNotLivingIsItsOwnDrawing() {
        val ink = stroke()

        assertEquals(listOf<Element>(ink), LivingInk.of(ink).elements)
        assertTrue("and there is nothing to report about it", LivingInk.of(ink).uncompiled.isEmpty())
    }

    @Test
    fun aSymbolWithNoDrawingYieldsNoInkAndSaysWhichOne() {
        val ink = LivingInk.of(circuit(symbol = "FLUX_CAPACITOR"))

        assertTrue("nothing is invented for a symbol we cannot draw", ink.elements.isEmpty())
        assertEquals("circuit", ink.uncompiled.single().type)
        assertTrue(ink.uncompiled.single().reason.contains("FLUX_CAPACITOR"))
    }

    @Test
    fun aFormulaThatWillNotEvaluateLeavesTheRestOfThePlotDrawn() {
        val ink = LivingInk.of(plot("sin(x)", "cos("))

        assertTrue("the curve that parses is drawn", ink.elements.isNotEmpty())
        assertEquals("plotCurve", ink.uncompiled.single().type)
    }

    @Test
    fun anSvgOfAPageHoldingATableHasTheTableInIt() {
        val withTable = PageSvgWriter.write(page(stroke(), table()))
        val withoutTable = PageSvgWriter.write(page(stroke()))

        // The cell text is the strongest signal that the recipe was really compiled into the page: the
        // grid alone could be coincidence with a background ruling.
        assertTrue("the cell text is in the SVG", withTable.contains("Parametro"))
        assertTrue(
            "and the grid with it, which the same page without the table does not have",
            countOf(withTable, "<polyline") > countOf(withoutTable, "<polyline"),
        )
    }

    private fun countOf(text: String, needle: String): Int =
        Regex(Regex.escape(needle)).findAll(text).count()
}
