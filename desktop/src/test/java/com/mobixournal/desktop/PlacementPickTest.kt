package com.mobixournal.desktop

import com.mobixournal.desktop.ink.shapeStroke
import com.mobixournal.desktop.sheet.Placement
import com.mobixournal.desktop.sheet.pinKind
import com.mobixournal.desktop.sheet.pinUnderPress
import com.mobixournal.desktop.sheet.placementOf
import com.mobixournal.desktop.sheet.placementPick
import com.mobixournal.desktop.tools.DesktopTool

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.FigurePins
import com.mobixournal.render.LivingPlacement
import com.mobixournal.render.LivingSlot
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's press rule for a **placement slot**: the object that slot makes under the point is the one it
 * selects, and only paper without one takes a new object.
 *
 * The rule reads the same page geometry as the tablet's (`app/.../render/PlacementPick.kt`), so the two shells
 * cannot drift: a figure slot claims the figure that names its kind, a part slot claims every part of the
 * family, the topmost object is the one picked, a figure written as several strokes is never claimed, and a
 * press on a pin is the gesture's rather than the pick's. A **plot and a formula** are claimed here and not on
 * the tablet, because the window keeps every placement slot in hand.
 */
class PlacementPickTest {

    private fun pageOf(vararg elements: Element): Page = Page(
        width = 612.0,
        height = 792.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    private fun pick(page: Page, kind: ShapeKind, x: Double, y: Double): ElementRef? =
        placementPick(page, Placement.Figure(kind), x, y)

    private fun pickLiving(page: Page, slot: LivingSlot, x: Double, y: Double): ElementRef? =
        placementPick(page, Placement.Living(slot), x, y)

    /** A 3 × 3 table whose box is 120 × 80 pt from (100, 100). */
    private fun table(): Element = LivingPlacement.table(Bounds(100.0, 100.0, 220.0, 180.0))

    /** A plot whose frame is 280 × 160 pt from (120, 300). */
    private fun plot(): Element =
        LivingPlacement.plot(Bounds(120.0, 300.0, 400.0, 460.0), 0xFF336699.toInt(), 1.0)

    /** A formula block whose box is 280 × 60 pt from (120, 500). */
    private fun formula(): Element = LivingPlacement.math(Bounds(120.0, 500.0, 400.0, 560.0), colour = 0xFF000000.toInt())

    /** A part spanning 40 pt from (200, 300) to (240, 300). */
    private fun resistor(y: Double = 300.0): CircuitElement = CircuitElement(
        symbol = ShapeKind.RESISTOR.name,
        positionX = 220.0,
        positionY = y,
        pins = listOf(CircuitPin("p1", -20.0, 0.0), CircuitPin("p2", 20.0, 0.0)),
    )

    /** What the **pen** writes: a stroke with no claim on it, so no slot picks it. */
    private fun ink(): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(400.0, 400.0, 1.0), StrokePoint(460.0, 400.0, 1.0)),
        uniformWidth = true,
    )

    /** A stroke [kind]'s slot minted: the geometry plus the kind it names (`FigurePins.figureAttrs`). */
    private fun figure(
        kind: ShapeKind,
        left: Double = 100.0,
        top: Double = 100.0,
        right: Double = 200.0,
        bottom: Double = 160.0,
    ): Stroke = shapeStroke(
        ShapeBuilder.build(kind, left, top, right, bottom, 2.0),
        0xFF000000.toInt(),
        2f,
        kind = kind,
    )!!

    private val figureKinds = listOf(
        ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.DOUBLE_ARROW, ShapeKind.COORDINATE_AXIS,
        ShapeKind.RECTANGLE, ShapeKind.ELLIPSE, ShapeKind.TRIANGLE, ShapeKind.SQUARE,
        ShapeKind.TRAPEZOID, ShapeKind.POLYGON,
    )

    @Test
    fun `the table slot picks the table under the press`() {
        val page = pageOf(table())
        assertEquals(
            "the table under the press is the one picked",
            ElementRef(0, 0),
            pickLiving(page, LivingSlot.TABLE, 160.0, 140.0),
        )
        assertNull("paper holds no table there", pickLiving(page, LivingSlot.TABLE, 500.0, 500.0))
    }

    @Test
    fun `every part of the palette is the same family`() {
        val page = pageOf(resistor())
        // A capacitor slot over a resistor selects the resistor rather than placing a capacitor on top.
        assertEquals(
            "any circuit slot picks the part under the press",
            ElementRef(0, 0),
            pickLiving(page, LivingSlot.CIRCUIT, 220.0, 300.0),
        )
        assertNull("a part is not a table", pickLiving(page, LivingSlot.TABLE, 220.0, 300.0))
    }

    @Test
    fun `a plot slot picks the plot it made`() {
        val page = pageOf(plot())
        assertEquals(
            "the plot under the press is the one picked",
            ElementRef(0, 0),
            pickLiving(page, LivingSlot.PLOT, 200.0, 360.0),
        )
        assertNull("paper holds no plot there", pickLiving(page, LivingSlot.PLOT, 60.0, 700.0))
    }

    @Test
    fun `a formula slot picks the formula it made`() {
        val page = pageOf(formula())
        assertEquals(
            "the block under the press is the one picked",
            ElementRef(0, 0),
            pickLiving(page, LivingSlot.MATH, 200.0, 530.0),
        )
        assertNull("paper holds no formula there", pickLiving(page, LivingSlot.MATH, 60.0, 700.0))
    }

    @Test
    fun `every figure slot picks its own figure`() {
        for (kind in figureKinds) {
            val page = pageOf(figure(kind))
            val first = ShapeBuilder.build(kind, 100.0, 100.0, 200.0, 160.0, 2.0).first()
            assertEquals(
                "$kind picks the figure it drew",
                ElementRef(0, 0),
                pick(page, kind, first.x, first.y),
            )
            // The same ink is nobody else's figure: the triangle slot leaves a rectangle alone.
            for (other in figureKinds - kind) {
                assertNull("$other must not claim a $kind", pick(page, other, first.x, first.y))
            }
        }
    }

    @Test
    fun `a press inside a closed figure or on its outline picks it`() {
        val page = pageOf(figure(ShapeKind.RECTANGLE))
        assertEquals(
            "a closed figure hits from its border through its interior",
            ElementRef(0, 0),
            pick(page, ShapeKind.RECTANGLE, 150.0, 130.0),
        )
        assertEquals(
            ElementRef(0, 0),
            pick(page, ShapeKind.RECTANGLE, 150.0, 100.0),
        )
    }

    @Test
    fun `ink is nobody's object`() {
        val page = pageOf(ink())
        assertNull(pickLiving(page, LivingSlot.TABLE, 430.0, 400.0))
        assertNull(pickLiving(page, LivingSlot.CIRCUIT, 430.0, 400.0))
        assertNull(pickLiving(page, LivingSlot.PLOT, 430.0, 400.0))
        // The pen never claimed a kind, so no figure slot picks the stroke by its geometry alone.
        assertNull(pick(page, ShapeKind.LINE, 430.0, 400.0))
    }

    @Test
    fun `a figure written as several strokes is never claimed`() {
        // A dimension is two halves around its gap; the model gives the parts no identity to gather them by,
        // so picking one would select half an object and the slot goes on drawing instead.
        assertTrue(ShapeBuilder.isMultiStroke(ShapeKind.DIMENSION))
        for (kind in figureKinds) assertFalse("$kind is one stroke", ShapeBuilder.isMultiStroke(kind))
        val page = pageOf(figure(ShapeKind.DIMENSION, 300.0, 500.0, 400.0, 500.0))
        assertNull(pick(page, ShapeKind.DIMENSION, 300.0, 500.0))
    }

    @Test
    fun `the slot asks for what it places`() {
        assertEquals(Placement.Living(LivingSlot.TABLE), placementOf(DesktopTool.TABLE))
        assertEquals(Placement.Living(LivingSlot.CIRCUIT), placementOf(DesktopTool.CIRCUIT))
        assertEquals(Placement.Living(LivingSlot.PLOT), placementOf(DesktopTool.PLOT))
        assertEquals(Placement.Living(LivingSlot.MATH), placementOf(DesktopTool.MATH))
        assertEquals(Placement.Figure(ShapeKind.RECTANGLE), placementOf(DesktopTool.RECTANGLE))
        assertEquals(Placement.Figure(ShapeKind.LINE), placementOf(DesktopTool.LINE))
        assertNull("the pen places no object", placementOf(DesktopTool.PEN))
    }

    /** A plot, a table and a formula carry no pins, so their press is never a pin press. */
    @Test
    fun `only a figure and a part aim at a pin`() {
        assertEquals(ShapeKind.RECTANGLE, Placement.Figure(ShapeKind.RECTANGLE).pinKind(ShapeKind.RESISTOR))
        assertEquals(
            "a circuit slot aims with the part in hand",
            ShapeKind.RESISTOR,
            Placement.Living(LivingSlot.CIRCUIT).pinKind(ShapeKind.RESISTOR),
        )
        assertNull(Placement.Living(LivingSlot.TABLE).pinKind(ShapeKind.RESISTOR))
        assertNull(Placement.Living(LivingSlot.PLOT).pinKind(ShapeKind.RESISTOR))
        assertNull(Placement.Living(LivingSlot.MATH).pinKind(ShapeKind.RESISTOR))
    }

    /** A wire from (100, 100) to (200, 100): a figure whose two ends are pins a connector may join. */
    private fun wire(): Stroke = figure(ShapeKind.LINE, 100.0, 100.0, 200.0, 100.0)

    /**
     * A press **on a pin** is the gesture's, not the pick's: the stroke starts on the terminal, so a wire
     * leaves the end instead of selecting the wire the end belongs to.
     */
    @Test
    fun `a press on a wires end is the gestures not the picks`() {
        val page = pageOf(wire())
        assertEquals("the wire's start", 100.0, pinUnderPress(page, ShapeKind.LINE, 101.0, 100.5, 12.0)?.absX)
        assertEquals("and its far end", 200.0, pinUnderPress(page, ShapeKind.LINE, 199.0, 100.0, 12.0)?.absX)
        assertNull(
            "44 pt away is outside the reach",
            pinUnderPress(page, ShapeKind.LINE, 144.0, 140.0, 12.0),
        )
    }

    /** An armed **figure** slot is offered the ends it may be paired with, so it starts there too. */
    @Test
    fun `an armed figure starts on a wires end`() {
        val page = pageOf(wire())
        assertEquals(
            "a rectangle begins on the wire's end",
            200.0,
            pinUnderPress(page, ShapeKind.RECTANGLE, 200.5, 100.5, 12.0)?.absX,
        )
    }

    /** The claim a committed figure carries is the kind, not just "a tool drew me". */
    @Test
    fun `a committed figure names its kind`() {
        assertEquals(ShapeKind.RECTANGLE, FigurePins.figureKindOf(figure(ShapeKind.RECTANGLE)))
        assertEquals(ShapeKind.TRIANGLE, FigurePins.figureKindOf(figure(ShapeKind.TRIANGLE)))
    }
}
