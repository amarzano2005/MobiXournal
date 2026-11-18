package com.mobixournal.desktop

import com.mobixournal.desktop.ink.shapeStroke
import com.mobixournal.desktop.sheet.pinUnderPress
import com.mobixournal.desktop.sheet.placementKindOf
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
import com.mobixournal.render.CircuitElementFactory
import com.mobixournal.render.ElementRef
import com.mobixournal.render.FigurePins
import com.mobixournal.render.LivingPlacement
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
 * The rule is the tablet's (`app/src/main/java/com/mobixournal/render/PlacementPick.kt`), read on the same
 * page geometry, so the two shells cannot drift: a figure slot claims the figure that names its kind, a part
 * slot claims every part of the family, the topmost object is the one picked, a figure written as several
 * strokes is never claimed, and a press on a pin is the gesture's rather than the pick's.
 */
class PlacementPickTest {

    private fun pageOf(vararg elements: Element): Page = Page(
        width = 612.0,
        height = 792.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    /** A 3 × 3 table whose box is 120 × 80 pt from (100, 100). */
    private fun table(): Element =
        LivingPlacement.table(Bounds(100.0, 100.0, 220.0, 180.0))

    /** A part spanning 40 pt from (200, 300) to (240, 300). */
    private fun resistor(bottom: Double = 300.0): CircuitElement = CircuitElement(
        symbol = ShapeKind.RESISTOR.name,
        positionX = 220.0,
        positionY = bottom,
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
            placementPick(page, ShapeKind.TABLE, 160.0, 140.0),
        )
        assertNull("paper holds no table there", placementPick(page, ShapeKind.TABLE, 500.0, 500.0))
        assertNull("a part is not a table", placementPick(page, ShapeKind.RESISTOR, 160.0, 140.0))
    }

    @Test
    fun `every part of the palette is the same family`() {
        val page = pageOf(resistor())
        // A capacitor slot over a resistor selects the resistor rather than placing a capacitor on top.
        for (kind in CircuitElementFactory.CIRCUIT_KINDS) {
            assertEquals(
                "$kind picks the part under the press",
                ElementRef(0, 0),
                placementPick(page, kind, 220.0, 300.0),
            )
        }
    }

    @Test
    fun `every figure slot picks its own figure`() {
        for (kind in figureKinds) {
            val page = pageOf(figure(kind))
            val first = ShapeBuilder.build(kind, 100.0, 100.0, 200.0, 160.0, 2.0).first()
            assertEquals(
                "$kind picks the figure it drew",
                ElementRef(0, 0),
                placementPick(page, kind, first.x, first.y),
            )
            // The same ink is nobody else's figure: the triangle slot leaves a rectangle alone.
            for (other in figureKinds - kind) {
                assertNull("$other must not claim a $kind", placementPick(page, other, first.x, first.y))
            }
        }
    }

    @Test
    fun `a press off the ink finds no figure`() {
        val page = pageOf(figure(ShapeKind.RECTANGLE))
        assertNull(
            "the middle of a rectangle is empty paper, not the rectangle",
            placementPick(page, ShapeKind.RECTANGLE, 150.0, 130.0),
        )
        assertEquals(
            "its own outline is where the press lands on it",
            ElementRef(0, 0),
            placementPick(page, ShapeKind.RECTANGLE, 150.0, 100.0),
        )
    }

    @Test
    fun `ink is nobody's object`() {
        val page = pageOf(ink())
        assertNull(placementPick(page, ShapeKind.TABLE, 430.0, 400.0))
        assertNull(placementPick(page, ShapeKind.RESISTOR, 430.0, 400.0))
        // The pen never claimed a kind, so no figure slot picks the stroke by its geometry alone.
        assertNull(placementPick(page, ShapeKind.LINE, 430.0, 400.0))
    }

    @Test
    fun `a figure written as several strokes is never claimed`() {
        // A dimension is two halves around its gap; the model gives the parts no identity to gather them by,
        // so picking one would select half an object and the slot goes on drawing instead.
        assertTrue(ShapeBuilder.isMultiStroke(ShapeKind.DIMENSION))
        for (kind in figureKinds) assertFalse("$kind is one stroke", ShapeBuilder.isMultiStroke(kind))
        assertNull(placementPick(pageOf(figure(ShapeKind.DIMENSION, 300.0, 500.0, 400.0, 500.0)), ShapeKind.DIMENSION, 300.0, 500.0))
    }

    @Test
    fun `the slot asks for the kind it places`() {
        assertEquals(ShapeKind.TABLE, placementKindOf(DesktopTool.TABLE, ShapeKind.RESISTOR))
        assertEquals(
            "any circuit slot stands for the family, so it asks for the part in hand",
            ShapeKind.RESISTOR,
            placementKindOf(DesktopTool.CIRCUIT, ShapeKind.RESISTOR),
            )
        assertEquals(ShapeKind.RECTANGLE, placementKindOf(DesktopTool.RECTANGLE, ShapeKind.RESISTOR))
        assertEquals(ShapeKind.LINE, placementKindOf(DesktopTool.LINE, ShapeKind.RESISTOR))
        assertNull("a plot is a recipe, not a figure a press can pick", placementKindOf(DesktopTool.PLOT, ShapeKind.RESISTOR))
        assertNull("the pen places no object", placementKindOf(DesktopTool.PEN, ShapeKind.RESISTOR))
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
