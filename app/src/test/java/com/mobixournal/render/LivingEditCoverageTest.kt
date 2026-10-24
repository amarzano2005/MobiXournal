package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.isLivingEntity
import com.mobixournal.ui.EditorTool
import com.mobixournal.ui.EditorUiState
import com.mobixournal.ui.openLivingEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Every living element is openable by a double-tap in selection mode**, and every family has an
 * editor to open (`docs/mobix-elements.md`, the editing rule).
 *
 * The two halves of that promise live in separate places — the hit test ([livingEditHit]) and the UI
 * state that shows a panel ([openLivingEditor]) — so this test walks all four families through both,
 * which is what makes a family that is selectable but not editable — a circuit or a figure before the
 * label work, when only a table and a plot answered a double-tap — fail the build rather than the
 * user's patience.
 */
class LivingEditCoverageTest {

    private fun table() = TableElement(
        left = 100.0,
        top = 100.0,
        colWidths = listOf(60.0, 60.0),
        rowHeights = listOf(24.0, 24.0),
        cells = listOf(listOf(TableCell("R1"), TableCell("10 kΩ"))),
        id = "tbl_edit",
    )

    private fun plot() = PlotElement(
        left = 200.0,
        top = 120.0,
        right = 360.0,
        bottom = 240.0,
        curves = listOf(PlotCurve(PlotKind.CARTESIAN, "x*x", 0xFF000000.toInt())),
        id = "plot_edit",
    )

    private fun circuit() = CircuitElement(
        symbol = "RESISTOR",
        positionX = 300.0,
        positionY = 300.0,
        value = "10 kΩ",
        pins = listOf(CircuitPin("p1", -30.0, 0.0), CircuitPin("p2", 30.0, 0.0)),
        id = "ckt_edit",
    )

    private fun math() = MathBlockElement(
        left = 200.0,
        top = 400.0,
        right = 320.0,
        bottom = 432.0,
        source = "x^2 + y^2",
        id = "math_edit",
    )

    private fun shape() = ParametricShapeElement(
        shape = "TRIANGLE",
        vertices = listOf(ShapeVertex(400.0, 380.0), ShapeVertex(480.0, 380.0), ShapeVertex(440.0, 440.0)),
        strokeColor = 0xFF000000.toInt(),
        showAngles = true,
        showSideLengths = true,
        id = "shape_edit",
    )

    /** One element of each living family, which is the list every family must appear in. */
    private fun samples(): Map<LivingEditFamily, Element> = mapOf(
        LivingEditFamily.TABLE to table(),
        LivingEditFamily.PLOT to plot(),
        LivingEditFamily.CIRCUIT to circuit(),
        LivingEditFamily.SHAPE to shape(),
        LivingEditFamily.MATH to math(),
    )

    private fun page(vararg elements: Element) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    /** A point inside [element]'s own box, where a user would put the second tap. */
    private fun insidePoint(element: Element): Pair<Double, Double> {
        val box = ElementBounds.of(element)
        return (box.left + box.right) / 2 to (box.top + box.bottom) / 2
    }

    @Test
    fun `the editor families are exactly the living families the model names`() {
        // The list [samples] is the test's own copy of the model's living set: if a fifth living type
        // is ever added, `isLivingEntity` names it and this map — and the enum — must grow with it.
        for ((family, element) in samples()) {
            assertTrue("$family is a living entity", element.isLivingEntity)
            assertEquals("$family is the element's own family", family, livingEditTarget(element, insidePoint(element).first, insidePoint(element).second)?.family)
        }
        assertEquals(
            "one family per living type",
            5,
            LivingEditFamily.entries.size,
        )
    }

    @Test
    fun `a double-tap on any living element lands on its editor`() {
        for ((family, element) in samples()) {
            val page = page(element)
            val (x, y) = insidePoint(element)
            val hit = livingEditHit(page, x, y)
            assertNotNull("$family answers a double-tap", hit)
            assertEquals("$family opens its own editor", family, hit!!.target.family)
            assertTrue("$family is the element that was tapped", hit.target.element === element)
            assertEquals("and the hit names where it is", ElementRef(0, 0), hit.ref)
        }
    }

    @Test
    fun `a double-tap opens the top-most living element`() {
        // A component drawn over a plot takes the double-tap, exactly as it takes the click.
        val plot = plot()
        val circuit = CircuitElement(
            symbol = "CAPACITOR",
            positionX = 280.0,
            positionY = 180.0,
            pins = listOf(CircuitPin("p1", -20.0, 0.0), CircuitPin("p2", 20.0, 0.0)),
            id = "ckt_over",
        )
        val hit = livingEditHit(page(plot, circuit), circuit.positionX, circuit.positionY)
        assertEquals(LivingEditFamily.CIRCUIT, hit?.target?.family)
        assertEquals("the later element in the layer is on top", ElementRef(0, 1), hit?.ref)
    }

    @Test
    fun `a double-tap away from every living element opens nothing`() {
        // Ink is not a living element: a double-tap over a plain page belongs to the marquee.
        val hit = livingEditHit(page(table(), plot(), circuit(), shape()), 20.0, 800.0)
        assertNull(hit)
        assertNull(livingEditTarget(table(), 20.0, 20.0))
    }

    @Test
    fun `each family opens its own editor`() {
        // The UI half of the promise: the target must reach the panel that edits it. The slots are
        // separate fields, so "this one opens" is not evidence that "that one opens".
        for ((family, element) in samples()) {
            val (x, y) = insidePoint(element)
            val target = livingEditTarget(element, x, y)!!
            val ui = EditorUiState(EditorTool.SELECT, 0xFF000000.toInt(), 1.5f, 0xFFFFEB3B.toInt(), 6f)
            ui.openLivingEditor(target)
            val opened = when (family) {
                LivingEditFamily.TABLE -> ui.editingTableCell != null
                LivingEditFamily.PLOT -> ui.editingPlot != null
                LivingEditFamily.CIRCUIT -> ui.editingCircuit != null
                LivingEditFamily.SHAPE -> ui.editingShape != null
                LivingEditFamily.MATH -> ui.editingMathBlock != null
            }
            assertTrue("$family has no editor to open", opened)
        }
    }

    @Test
    fun `a table answers per cell, not for the whole grid`() {
        val table = table()
        val hit = livingEditTarget(table, table.left + 90.0, table.top + 36.0) as LivingEditTarget.TableCell
        assertEquals("the cell under the tap is the one opened", 1, hit.row)
        assertEquals(1, hit.col)
    }
}
