package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.Tool
import com.mobixournal.ui.EditorTool
import com.mobixournal.ui.LIVING_ELEMENT_TOOLS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The press rule of a top-bar **element** tool: the object of its own family under the point is the one
 * it selects, and only paper without one takes a new object.
 *
 * The pick is pure geometry over the page (`pickLivingElement`, `LivingEditFamily.matches`), so the rule
 * that the canvas applies is pinned on the JVM without a device: which family a tool places, that a
 * circuit tool reaches every part of the palette, that the topmost object is the one picked, and that a
 * plain drawn stroke is not an object of any family.
 */
class LivingPlacementPickTest {

    private fun pageOf(vararg elements: Element): Page = Page(
        width = 612.0,
        height = 792.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    /** A 3 × 3 table whose box is 120 × 80 pt from ([left], [top]). */
    private fun table(left: Double = 100.0, top: Double = 100.0): TableElement =
        TableElementFactory.tableElementFor(
            startX = left, startY = top, endX = left + 120.0, endY = top + 80.0,
            rows = 3, cols = 3, hasHeader = true,
            borderColor = TableElement.DEFAULT_BORDER,
            borderWidth = TableElementFactory.DEFAULT_BORDER_WIDTH,
        )

    /** A part spanning 40 pt from (200, 300) to (240, 300). */
    private fun resistor(): CircuitElement =
        CircuitElementFactory.circuitElementFor("RESISTOR", 200.0, 300.0, 240.0, 300.0)!!

    private fun ink(): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(400.0, 400.0, 1.0), StrokePoint(460.0, 400.0, 1.0)),
        uniformWidth = true,
    )

    @Test
    fun theTableToolPicksTheTableUnderThePress() {
        val page = pageOf(table())
        assertEquals(
            "the table under the press is the one picked",
            ElementRef(0, 0),
            pickLivingElement(page, 160.0, 140.0, ShapeKind.TABLE),
        )
        assertNull("paper holds no table there", pickLivingElement(page, 500.0, 500.0, ShapeKind.TABLE))
        assertNull("a part is not a table", pickLivingElement(page, 160.0, 140.0, ShapeKind.RESISTOR))
    }

    @Test
    fun everyPartOfThePaletteIsTheSameFamily() {
        val page = pageOf(resistor())
        // The tool picks the object, whichever part of the family it stands for: a capacitor tool over a
        // resistor selects the resistor rather than placing a capacitor on top of it.
        for (kind in CircuitElementFactory.CIRCUIT_KINDS) {
            assertEquals(
                "$kind picks the part under the press",
                ElementRef(0, 0),
                pickLivingElement(page, 220.0, 300.0, kind),
            )
        }
    }

    @Test
    fun theTopmostObjectOfTheFamilyIsTheOnePicked() {
        val page = pageOf(resistor(), table())
        assertEquals(
            "the table lies over nothing, but the layer order decides",
            ElementRef(0, 1),
            pickLivingElement(page, 160.0, 140.0, ShapeKind.TABLE),
        )
        assertEquals(
            "and the part is still where it was put",
            ElementRef(0, 0),
            pickLivingElement(page, 220.0, 300.0, ShapeKind.AND_GATE),
        )
    }

    @Test
    fun aDrawnStrokeIsNoElementsOfAnyFamily() {
        val page = pageOf(ink())
        assertNull(pickLivingElement(page, 430.0, 400.0, ShapeKind.TABLE))
        assertNull(pickLivingElement(page, 430.0, 400.0, ShapeKind.RESISTOR))
    }

    @Test
    fun onlyTheTableAndTheCircuitPartsPlaceALivingElement() {
        assertEquals(LivingEditFamily.TABLE, livingFamilyOf(ShapeKind.TABLE))
        for (kind in CircuitElementFactory.CIRCUIT_KINDS) {
            assertEquals("$kind is a circuit", LivingEditFamily.CIRCUIT, livingFamilyOf(kind))
        }
        // Everything else draws ink: no family, so no press of theirs selects anything first.
        for (kind in listOf(ShapeKind.LINE, ShapeKind.RECTANGLE, ShapeKind.ARROW, ShapeKind.CHEM_BOND_SINGLE)) {
            assertNull("$kind draws ink", livingFamilyOf(kind))
        }
    }

    @Test
    fun everyCircuitPartHasItsToolInTheElementFamily() {
        assertTrue(EditorTool.TABLE in LIVING_ELEMENT_TOOLS)
        for (tool in EditorTool.entries) {
            val circuit = CircuitElementFactory.CIRCUIT_KINDS.any { it.name == tool.name }
            if (circuit) assertTrue("$tool places a part", tool in LIVING_ELEMENT_TOOLS)
        }
        // A figure tool is not in it: a stroke has no family to select before drawing another.
        assertTrue(EditorTool.RECTANGLE !in LIVING_ELEMENT_TOOLS)
        assertTrue(EditorTool.LINE !in LIVING_ELEMENT_TOOLS)
    }

    @Test
    fun aTableIsItsOwnFamilyAndNothingElseIs() {
        assertTrue(LivingEditFamily.TABLE.matches(table()))
        assertTrue(LivingEditFamily.CIRCUIT.matches(resistor()))
        assertTrue("a part is not a table", !LivingEditFamily.TABLE.matches(resistor()))
        assertTrue("a stroke is no living object", !LivingEditFamily.CIRCUIT.matches(ink()))
    }
}
