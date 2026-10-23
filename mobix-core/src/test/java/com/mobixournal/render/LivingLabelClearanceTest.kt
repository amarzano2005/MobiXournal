package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitLabelSide
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotCurve
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.PlotKind
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TableCell
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TextElement
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * **No writing sits on the thing it describes.** Every living element may carry a label — a
 * component's value, a figure's dimension, a table's cell text, a plot's tick numbers and summary —
 * and each of them is placed around the element's own ink rather than across it
 * (`docs/mobix-elements.md`, the label rule).
 *
 * This is the guard for that rule: it compiles the four families through [LivingInk] and checks the
 * geometry the label boxes and the element's strokes actually have, on the JVM, so a placement change
 * that starts crossing a symbol, an outline or a rule fails the build instead of shipping a number
 * lying over a wire. It caught the two cases the rule was broken on: a circuit value dropped on a
 * rotated symbol (fixed in [CircuitInk]) and an angle label that a sharp corner had no room for
 * (fixed in [ShapeInk], where such a corner now writes its label outside).
 */
class LivingLabelClearanceTest {

    // ---- the rule, in geometry -------------------------------------------------------------------

    /**
     * True when the segment ([x1],[y1])–([x2],[y2]) touches [box], edges included: Liang–Barsky
     * clipping of the segment against the box, so a line that merely passes through is caught as
     * surely as one that ends inside it.
     */
    private fun segmentHitsBox(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        box: Bounds,
    ): Boolean {
        var t0 = 0.0
        var t1 = 1.0
        val dx = x2 - x1
        val dy = y2 - y1

        fun clip(p: Double, q: Double): Boolean {
            if (p == 0.0) return q >= 0.0
            val r = q / p
            if (p < 0.0) {
                if (r > t1) return false
                if (r > t0) t0 = r
            } else {
                if (r < t0) return false
                if (r < t1) t1 = r
            }
            return true
        }

        if (!clip(-dx, x1 - box.left)) return false
        if (!clip(dx, box.right - x1)) return false
        if (!clip(-dy, y1 - box.top)) return false
        if (!clip(dy, box.bottom - y1)) return false
        return t0 <= t1
    }

    /** The box a text element occupies, as [ElementBounds] estimates it — the app's own metric. */
    private fun textBox(text: TextElement): Bounds = ElementBounds.of(text)

    /** Every text box of [ink], with the element it belongs to named for the failure message. */
    private fun labelBoxes(ink: List<Element>): List<Bounds> =
        ink.filterIsInstance<TextElement>().map(::textBox)

    /** Every segment the strokes of [ink] draw. */
    private fun segments(ink: List<Element>): List<DoubleArray> {
        val out = mutableListOf<DoubleArray>()
        for (element in ink) {
            if (element !is Stroke) continue
            for (index in 0 until element.points.size - 1) {
                val a = element.points[index]
                val b = element.points[index + 1]
                out += doubleArrayOf(a.x, a.y, b.x, b.y)
            }
        }
        return out
    }

    /** Fails with [what] as the opening of the message when any label box touches any segment. */
    private fun assertLabelsClear(what: String, ink: List<Element>) {
        val labels = labelBoxes(ink)
        assertTrue("$what: nothing to check — the element compiled no label", labels.isNotEmpty())
        val lines = segments(ink)
        for (label in labels) {
            for (line in lines) {
                if (segmentHitsBox(line[0], line[1], line[2], line[3], label)) {
                    fail(
                        "$what: a label box $label is crossed by the line " +
                            "(${line[0]}, ${line[1]})–(${line[2]}, ${line[3]})",
                    )
                }
            }
        }
    }

    // ---- the four families -----------------------------------------------------------------------

    private fun resistor(side: CircuitLabelSide, rotation: Double) = CircuitElement(
        symbol = "RESISTOR",
        positionX = 260.0,
        positionY = 260.0,
        rotation = rotation,
        value = "10 kΩ",
        labelSide = side,
        pins = listOf(CircuitPin("p1", -30.0, 0.0), CircuitPin("p2", 30.0, 0.0)),
        id = "ckt_clearance",
    )

    @Test
    fun `a components value is clear of its symbol on every side and at every turn`() {
        for (side in CircuitLabelSide.entries) {
            for (rotation in listOf(0.0, 45.0, 90.0, 180.0, 270.0)) {
                assertLabelsClear("$side, rotation $rotation", CircuitInk.ink(resistor(side, rotation))!!)
            }
        }
    }

    @Test
    fun `a figures dimension labels are clear of its outline`() {
        // A right triangle (one square corner and one sharp one), a wide scalene triangle, a square and
        // a regular pentagon: between them, every label direction the ink has — a length outside its
        // edge, an angle inside a corner, and an angle outside a corner too sharp to hold it.
        val figures = mapOf(
            "right triangle" to listOf(ShapeVertex(100.0, 100.0), ShapeVertex(220.0, 100.0), ShapeVertex(100.0, 190.0)),
            "wide triangle" to listOf(ShapeVertex(100.0, 260.0), ShapeVertex(300.0, 260.0), ShapeVertex(150.0, 300.0)),
            "square" to listOf(
                ShapeVertex(340.0, 100.0), ShapeVertex(420.0, 100.0),
                ShapeVertex(420.0, 180.0), ShapeVertex(340.0, 180.0),
            ),
            "pentagon" to listOf(
                ShapeVertex(520.0, 140.0), ShapeVertex(556.0, 115.0), ShapeVertex(580.0, 155.0),
                ShapeVertex(564.0, 195.0), ShapeVertex(524.0, 185.0),
            ),
        )
        for ((name, vertices) in figures) {
            val ink = ShapeInk.ink(
                ParametricShapeElement(
                    shape = "TRIANGLE",
                    vertices = vertices,
                    strokeColor = 0xFF000000.toInt(),
                    showAngles = true,
                    showSideLengths = true,
                    id = "shape_clearance",
                ),
            )!!
            assertLabelsClear(name, ink)
        }
    }

    @Test
    fun `a tables cell text never crosses its own rules`() {
        val table = TableElement(
            left = 100.0,
            top = 100.0,
            colWidths = listOf(70.0, 70.0, 70.0),
            rowHeights = listOf(26.0, 26.0, 26.0),
            cells = listOf(
                listOf(TableCell("Resistance", bold = true), TableCell("Value"), TableCell("Tolerance")),
                listOf(TableCell("R1"), TableCell("10 kΩ"), TableCell("±5 %")),
                listOf(TableCell("R2"), TableCell("4.7 kΩ"), TableCell("±1 %")),
            ),
            headerRow = true,
            id = "table_clearance",
        )
        assertLabelsClear("table", TableInk.ink(table))
    }

    @Test
    fun `a plots writings sit outside its frame`() {
        val plot = PlotElement(
            left = 100.0,
            top = 100.0,
            right = 320.0,
            bottom = 250.0,
            curves = listOf(PlotCurve(PlotKind.CARTESIAN, "sin(x)", 0xFF000000.toInt())),
            xMin = -10.0,
            xMax = 10.0,
            summaryText = "roots: -6.28, 0, 6.28",
            id = "plot_clearance",
        )
        val ink = PlotInk.ink(plot).elements
        val frame = Bounds(plot.left, plot.top, plot.right, plot.bottom)
        val labels = labelBoxes(ink)
        assertTrue("the plot labelled something", labels.isNotEmpty())
        for (box in labels) {
            val inside = box.right > frame.left && box.left < frame.right &&
                box.bottom > frame.top && box.top < frame.bottom
            assertTrue("a plot label $box sits over the graph it describes", !inside)
        }
    }

    @Test
    fun `a box that only touches a corner of a line still counts as crossed`() {
        // The geometry helper is the whole guard's witness, so it is pinned: an inclusive test is what
        // makes a label "on the line" fail rather than pass.
        val box = Bounds(0.0, 0.0, 10.0, 10.0)
        assertTrue("a diagonal through the box", segmentHitsBox(-5.0, -5.0, 15.0, 15.0, box))
        assertTrue("a line ending on an edge", segmentHitsBox(-5.0, 5.0, 0.0, 5.0, box))
        assertTrue("a line along an edge", segmentHitsBox(0.0, 0.0, 0.0, 10.0, box))
        assertTrue("a line through a corner", segmentHitsBox(-5.0, 5.0, 5.0, -5.0, box))
        assertTrue("a segment wholly inside", segmentHitsBox(2.0, 2.0, 8.0, 8.0, box))
        assertTrue(
            "a segment clear of it is not caught",
            !segmentHitsBox(20.0, 0.0, 20.0, 10.0, box),
        )
        assertTrue(
            "nor is one that stops short",
            !segmentHitsBox(-10.0, 5.0, -1.0, 5.0, box),
        )
        // Guard against a helper that fails everything: the two numbers it is asked about are box
        // arithmetic, so this also pins that the box itself is read the way the ink reads it.
        assertTrue(min(box.left, box.right) == 0.0 && max(box.top, box.bottom) == 10.0)
    }
}
