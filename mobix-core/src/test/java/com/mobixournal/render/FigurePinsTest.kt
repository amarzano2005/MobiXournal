package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The pins **every figure** carries, and which figure may join which — the matrix both shells paint from
 * (`FigurePins`).
 */
class FigurePinsTest {

    private fun part(x: Double, y: Double) = CircuitElement(
        symbol = "RESISTOR",
        positionX = x,
        positionY = y,
        pins = listOf(CircuitPin("p1", -40.0, 0.0), CircuitPin("p2", 40.0, 0.0)),
        id = "c1",
    )

    private fun stroke(points: List<StrokePoint>) =
        Stroke(Tool.PEN, 0xFF000000.toInt(), "round", points, true)

    private fun wire(x0: Double, y0: Double, x1: Double, y1: Double) =
        stroke(listOf(StrokePoint(x0, y0, 1.0), StrokePoint(x1, y1, 1.0)))

    private fun arrow(x0: Double, y0: Double, x1: Double, y1: Double) =
        stroke(ShapeBuilder.build(ShapeKind.ARROW, x0, y0, x1, y1, 2.0))

    private fun figure(x0: Double, y0: Double, x1: Double, y1: Double) =
        stroke(ShapeBuilder.build(ShapeKind.RECTANGLE, x0, y0, x1, y1, 2.0))

    private fun pageOf(vararg elements: Element) = Page(
        width = 612.0,
        height = 792.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    /** The rule as the owner stated it: circuit → line, line → circuit, line or figure, arrow → all. */
    @Test
    fun theAimMatrixIsTheXnotesOne() {
        assertTrue(FigurePins.aimsAt(PinFamily.CIRCUIT, PinFamily.CIRCUIT))
        assertTrue(FigurePins.aimsAt(PinFamily.CIRCUIT, PinFamily.WIRE))
        assertFalse("a part does not reach an arrow", FigurePins.aimsAt(PinFamily.CIRCUIT, PinFamily.ARROW))
        assertFalse("a part does not reach a drawn figure", FigurePins.aimsAt(PinFamily.CIRCUIT, PinFamily.FIGURE))

        assertTrue(FigurePins.aimsAt(PinFamily.WIRE, PinFamily.CIRCUIT))
        assertTrue(FigurePins.aimsAt(PinFamily.WIRE, PinFamily.WIRE))
        assertTrue("a line attaches to a figure's border", FigurePins.aimsAt(PinFamily.WIRE, PinFamily.FIGURE))
        assertFalse("a line does not reach an arrow", FigurePins.aimsAt(PinFamily.WIRE, PinFamily.ARROW))

        assertTrue(FigurePins.aimsAt(PinFamily.ARROW, PinFamily.CIRCUIT))
        assertTrue(FigurePins.aimsAt(PinFamily.ARROW, PinFamily.WIRE))
        assertTrue(FigurePins.aimsAt(PinFamily.ARROW, PinFamily.ARROW))
        assertTrue(FigurePins.aimsAt(PinFamily.ARROW, PinFamily.FIGURE))

        assertFalse("a figure is joined, it never joins", FigurePins.aimsAt(PinFamily.FIGURE, PinFamily.WIRE))
    }

    /** Every figure a hand draws has a family; a rectangle, a bond and free ink do not. */
    @Test
    fun aFiguresFamilyIsThePinsItCarries() {
        assertEquals(PinFamily.CIRCUIT, FigurePins.familyOf(ShapeKind.RESISTOR))
        assertEquals(PinFamily.WIRE, FigurePins.familyOf(ShapeKind.LINE))
        assertEquals(PinFamily.ARROW, FigurePins.familyOf(ShapeKind.ARROW))
        assertEquals(PinFamily.ARROW, FigurePins.familyOf(ShapeKind.DOUBLE_ARROW))
        assertEquals(PinFamily.FIGURE, FigurePins.familyOf(ShapeKind.RECTANGLE))
        assertEquals(PinFamily.FIGURE, FigurePins.familyOf(ShapeKind.TRIANGLE))
        assertNull(FigurePins.familyOf(ShapeKind.CHEM_BOND_SINGLE))
        assertNull("a figure has pins, but it is no connector", FigurePins.aimFamily(ShapeKind.RECTANGLE))

        assertEquals(PinFamily.CIRCUIT, FigurePins.familyOf(part(200.0, 200.0)))
        assertEquals(PinFamily.WIRE, FigurePins.familyOf(wire(0.0, 0.0, 100.0, 0.0)))
        assertEquals(PinFamily.ARROW, FigurePins.familyOf(arrow(0.0, 0.0, 100.0, 0.0)))
    }

    /** A headed figure traces its tip through the head, so its pins are found, not assumed. */
    @Test
    fun anArrowsPinsAreItsTailAndTip() {
        val ends = FigurePins.ends(arrow(100.0, 100.0, 300.0, 100.0).points)
        assertEquals(2, ends.size)
        assertEquals(100.0, ends[0].first, 1e-6)
        assertEquals(100.0, ends[0].second, 1e-6)
        assertEquals(300.0, ends[1].first, 1e-6)
        assertEquals(100.0, ends[1].second, 1e-6)
    }

    /** A part marks the parts' pins and the wires' ends, and nothing of an arrow's or a figure's. */
    @Test
    fun aComponentMarksWiresButNotArrows() {
        val page = pageOf(
            part(200.0, 200.0),
            wire(400.0, 100.0, 500.0, 100.0),
            arrow(100.0, 400.0, 200.0, 400.0),
            figure(300.0, 500.0, 400.0, 600.0),
        )
        assertEquals("two pins and two wire ends", 4, FigurePins.targets(page, PinFamily.CIRCUIT).size)
        assertEquals(
            "and the figure's eight border points, which a line does reach",
            12,
            FigurePins.targets(page, PinFamily.WIRE).size,
        )
        assertEquals("everything the page carries", 16, FigurePins.targets(page, PinFamily.ARROW).size)
    }

    /** A closed figure carries a pin at every corner **and** at the middle of every side. */
    @Test
    fun aFiguresPinsAreItsCornersAndSideMidpoints() {
        val rectangle = ShapeBuilder.build(ShapeKind.RECTANGLE, 100.0, 100.0, 200.0, 200.0, 2.0)
        val pins = FigurePins.pinsOf(ShapeKind.RECTANGLE, rectangle)
        assertEquals("four corners and four side midpoints", 8, pins.size)
        assertTrue("a side midpoint is a pin", pins.any { abs(it.first - 150.0) < 1e-6 && abs(it.second - 100.0) < 1e-6 })
        assertTrue("so is a corner", pins.any { abs(it.first - 200.0) < 1e-6 && abs(it.second - 200.0) < 1e-6 })
    }

    /** An arrow is the only figure that reaches another arrow's ends. */
    @Test
    fun aMovedArrowJoinsAnotherArrowsPins() {
        val page = pageOf(arrow(100.0, 100.0, 200.0, 100.0), arrow(300.0, 100.0, 400.0, 100.0))
        val moved = setOf(ElementRef(0, 0))
        assertEquals("the other arrow's tail, tip and two barbs", 4, CircuitSnapping.collectMoveTargets(page, moved).size)

        val movedPart = pageOf(part(200.0, 200.0), arrow(300.0, 100.0, 400.0, 100.0))
        assertTrue(
            "a part ignores an arrow",
            CircuitSnapping.collectMoveTargets(movedPart, setOf(ElementRef(0, 0))).isEmpty(),
        )
    }

    /** Snapping an arrow moves every vertex of its tip together, so the head cannot tear open. */
    @Test
    fun snappingATipMovesItsRepeatedVertex() {
        val a = arrow(100.0, 100.0, 300.0, 100.0)
        val targets = listOf(CircuitSnapping.PinTarget(null, CircuitSnapping.WIRE_END_PIN_ID, 305.0, 100.0, null))
        val snapped = FigurePins.snapEnds(a.points, ShapeKind.ARROW, targets, thresholdPt = 12.0)
        assertEquals(305.0, snapped[1].x, 1e-6)
        assertEquals("the head's second copy of the tip moves with it", 305.0, snapped[3].x, 1e-6)
        assertEquals("the tail is untouched", 100.0, snapped[0].x, 1e-6)
    }
}
