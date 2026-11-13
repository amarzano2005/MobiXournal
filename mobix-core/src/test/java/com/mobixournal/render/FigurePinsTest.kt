package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex
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

    /** A stroke a **figure tool** minted: marked, which is what makes it a figure rather than ink. */
    private fun stroke(points: List<StrokePoint>) =
        Stroke(Tool.PEN, 0xFF000000.toInt(), "round", points, true, extraAttrs = FigurePins.FIGURE_ATTRS)

    /** What the **pen** writes: same shape of stroke, no marker, so nothing about it is a terminal. */
    private fun ink(x0: Double, y0: Double, x1: Double, y1: Double) =
        Stroke(
            Tool.PEN, 0xFF000000.toInt(), "round",
            listOf(StrokePoint(x0, y0, 1.0), StrokePoint(x1, y1, 1.0)),
            true,
        )

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

    /** An arrow attaches by its **tip** only: a head traces the tip through the head, and it is found. */
    @Test
    fun anArrowsOnlyPinIsItsTip() {
        val points = arrow(100.0, 100.0, 300.0, 100.0).points
        val pins = FigurePins.pinsOf(ShapeKind.ARROW, points)
        assertEquals("one pin, not tail and barbs", 1, pins.size)
        assertEquals(300.0, pins[0].first, 1e-6)
        assertEquals(100.0, pins[0].second, 1e-6)
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
        assertEquals(
            "everything the page carries: two pins, two wire ends, eight figure pins, one arrow tip",
            13,
            FigurePins.targets(page, PinFamily.ARROW).size,
        )
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
        assertEquals("the other arrow's tip", 1, CircuitSnapping.collectMoveTargets(page, moved).size)

        val movedPart = pageOf(part(200.0, 200.0), arrow(300.0, 100.0, 400.0, 100.0))
        assertTrue(
            "a part ignores an arrow",
            CircuitSnapping.collectMoveTargets(movedPart, setOf(ElementRef(0, 0))).isEmpty(),
        )
    }

    /**
     * The marks of an element **in flight** are only worth showing when the page already holds something it
     * could join — on an empty page they are dots on paper.
     */
    @Test
    fun `a mark in flight is drawn only when the page has company for it`() {
        assertFalse("an empty page joins nothing", FigurePins.hasCompany(pageOf(), ShapeKind.RECTANGLE))
        assertFalse(FigurePins.hasCompany(pageOf(), ShapeKind.RESISTOR))
        assertFalse(FigurePins.hasCompany(pageOf(), ShapeKind.LINE))
        // A figure is a target: a wire on the page is company for the rectangle being drawn.
        assertTrue(FigurePins.hasCompany(pageOf(wire(10.0, 10.0, 60.0, 10.0)), ShapeKind.RECTANGLE))
        // A connector asks the matrix it aims with: a part joins a wire, an arrow joins a closed figure.
        assertTrue(FigurePins.hasCompany(pageOf(wire(10.0, 10.0, 60.0, 10.0)), ShapeKind.RESISTOR))
        assertTrue(FigurePins.hasCompany(pageOf(figure(10.0, 10.0, 60.0, 60.0)), ShapeKind.ARROW))
        assertFalse("a part does not reach a closed figure", FigurePins.hasCompany(pageOf(figure(10.0, 10.0, 60.0, 60.0)), ShapeKind.RESISTOR))
    }

    /**
     * A written stroke is never a terminal, however straight it is: the line tool's two vertices are the very
     * two vertices a short written stroke leaves, so only the **marker** separates them. Reading the family
     * off the vertices is what made handwriting a magnetic target.
     */
    @Test
    fun `a written stroke carries no pins`() {
        assertNull(FigurePins.pinFamilyOf(ink(10.0, 10.0, 90.0, 10.0)))
        val page = pageOf(part(200.0, 200.0), ink(10.0, 10.0, 90.0, 10.0))
        assertEquals("the part's two pins, and nothing of the ink", 2, FigurePins.targets(page, PinFamily.CIRCUIT).size)
        assertTrue(
            "a wire reaches the part's pins, never the written stroke's ends",
            FigurePins.targets(page, PinFamily.WIRE).none { it.isWireEnd },
        )
        assertTrue(
            "and a written stroke never magnetises a move of its own",
            CircuitSnapping.collectMoveTargets(page, setOf(ElementRef(0, 1))).isEmpty(),
        )
    }

    /**
     * A living figure — the closed polygon the recogniser keeps as vertices — carries the same border pins a
     * drawn one does, so a rectangle the pen left as geometry can be aimed at by a wire.
     */
    @Test
    fun `a living figure carries the border pins of its kind`() {
        val living = ParametricShapeElement(
            shape = ShapeKind.RECTANGLE.name,
            vertices = listOf(
                ShapeVertex(100.0, 100.0), ShapeVertex(200.0, 100.0),
                ShapeVertex(200.0, 200.0), ShapeVertex(100.0, 200.0),
            ),
            strokeColor = 0xFF000000.toInt(),
        )
        assertEquals(PinFamily.FIGURE, FigurePins.pinFamilyOf(living))
        assertEquals("four corners and four side midpoints", 8, FigurePins.pinsOf(living).size)
        assertEquals(
            "a wire may reach its border",
            8,
            FigurePins.targets(pageOf(living), PinFamily.WIRE).size,
        )
        assertEquals(
            "and a selected one wears them next to a wire",
            8,
            FigurePins.selectionPins(pageOf(living, wire(10.0, 10.0, 60.0, 10.0)), living).size,
        )
    }

    /** A **selected figure** wears its own pins, but only while the page holds a connector that could join it. */
    @Test
    fun `a selected figure wears its pins only with company`() {
        val rectangle = figure(300.0, 500.0, 400.0, 600.0)
        assertEquals(
            "a figure alone on the page is bare",
            0,
            FigurePins.selectionPins(pageOf(rectangle), rectangle).size,
        )
        assertEquals(
            "a wire is company for it",
            8,
            FigurePins.selectionPins(pageOf(rectangle, wire(10.0, 10.0, 60.0, 10.0)), rectangle).size,
        )
        assertEquals(
            "an arrow too",
            8,
            FigurePins.selectionPins(pageOf(rectangle, arrow(10.0, 10.0, 60.0, 10.0)), rectangle).size,
        )
        assertEquals(
            "a part never reaches a figure",
            0,
            FigurePins.selectionPins(pageOf(rectangle, part(200.0, 200.0)), rectangle).size,
        )
        assertEquals(
            "a connector's marks are its targets, not its own ends",
            0,
            FigurePins.selectionPins(
                pageOf(wire(10.0, 10.0, 60.0, 10.0), figure(300.0, 500.0, 400.0, 600.0)),
                wire(10.0, 10.0, 60.0, 10.0),
            ).size,
        )
    }

    /** A **moved** element is the actor of its own drag: its own pins are never company for it. */
    @Test
    fun `a moved element marks nothing when it is all the page holds`() {
        val alone = pageOf(wire(10.0, 10.0, 60.0, 10.0))
        assertFalse(
            "its own pins are not a target of its own move",
            FigurePins.hasCompany(alone, ShapeKind.LINE, exclude = setOf(ElementRef(0, 0))),
        )
        val company = pageOf(wire(10.0, 10.0, 60.0, 10.0), wire(10.0, 40.0, 60.0, 40.0))
        assertTrue(
            FigurePins.hasCompany(company, ShapeKind.LINE, exclude = setOf(ElementRef(0, 0))),
        )
    }

    /** A drawn figure's pins are targets only: moving a rectangle offers no end to snap, as before. */
    @Test
    fun aMovedFigureHasNoMovingEnds() {
        val page = pageOf(figure(100.0, 100.0, 200.0, 200.0), figure(300.0, 300.0, 400.0, 400.0))
        assertTrue(CircuitSnapping.collectMoveTargets(page, setOf(ElementRef(0, 0))).isEmpty())
        val (dx, dy) = CircuitSnapping.snapTranslation(page, setOf(ElementRef(0, 0)), 4.0, 3.0)
        assertEquals("the corner keeps the hand's own translation", 4.0, dx, 1e-9)
        assertEquals(3.0, dy, 1e-9)
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
