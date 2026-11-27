package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.Tool
import com.mobixournal.ui.EditorTool
import com.mobixournal.ui.LIVING_ELEMENT_TOOLS
import com.mobixournal.ui.SHAPE_TOOLS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The press rule of a top-bar **placement** tool: the object that tool made under the point is the one it
 * selects, and only paper without one takes a new object.
 *
 * The pick is pure geometry over the page (`pickLivingElement`, `pickFigureElement`,
 * `LivingEditFamily.matches`, `FigurePins.isFigureOf`), so the rule the canvas applies is pinned on the JVM
 * without a device: which family or kind a tool claims, that a circuit tool reaches every part of the
 * palette, that the topmost object is the one picked, that a figure tool claims its own figure and no
 * other, and that plain ink is nobody's object.
 */
class PlacementPickTest {

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

    /** A living plot 200 × 120 pt from ([left], [top]) — what the *Function graph* slot writes. */
    private fun plot(left: Double = 120.0, top: Double = 220.0): PlotElement =
        PlotElementFactory.plotElementIn(
            box = PlotBox(left, top, 200.0, 120.0),
            curves = listOf(PlotCurve(source = "sin(x)", color = 0xFF000000.toInt(), lineStyle = LineStyle.PLAIN)),
            xMin = -10.0,
            xMax = 10.0,
        )

    /** A formula block 180 × 60 pt from ([left], [top]). */
    private fun formula(left: Double = 100.0, top: Double = 500.0): MathBlockElement = MathBlockElement(
        left, top, left + 180.0, top + 60.0, "\\frac{a}{b}", 0xFF000000.toInt(),
    )

    /** A part spanning 40 pt from (200, 300) to (240, 300). */
    private fun resistor(): CircuitElement =
        CircuitElementFactory.circuitElementFor("RESISTOR", 200.0, 300.0, 240.0, 300.0)!!

    /** What the **pen** writes: a stroke with no claim on it, so no tool may pick it. */
    private fun ink(): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(400.0, 400.0, 1.0), StrokePoint(460.0, 400.0, 1.0)),
        uniformWidth = true,
    )

    /** A stroke [kind]'s tool minted: the geometry plus the kind it names (`FigurePins.figureAttrs`). */
    private fun figure(
        kind: ShapeKind,
        left: Double = 100.0,
        top: Double = 100.0,
        right: Double = 200.0,
        bottom: Double = 160.0,
    ): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = ShapeBuilder.build(kind, left, top, right, bottom, 2.0),
        uniformWidth = true,
        extraAttrs = FigurePins.figureAttrs(kind),
    )

    /** The single-stroke figures a top-bar slot can put in hand; a dimension is written as two. */
    private val FIGURE_KINDS = listOf(
        ShapeKind.LINE, ShapeKind.ARROW, ShapeKind.DOUBLE_ARROW, ShapeKind.COORDINATE_AXIS,
        ShapeKind.RECTANGLE, ShapeKind.ELLIPSE, ShapeKind.TRIANGLE, ShapeKind.SQUARE,
        ShapeKind.TRAPEZOID, ShapeKind.POLYGON,
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

    /**
     * The **LaTeX** slot's own press: a formula it made under the tap is the one picked, so the editor opens
     * on that block instead of a second formula landing on it. The slot stays in hand after it inserts, which
     * is exactly why the tap cannot be read as "place another one" (`pickMathBlock`).
     */
    @Test
    fun theLatexSlotPicksTheFormulaUnderTheTap() {
        val page = pageOf(formula())
        assertEquals(
            "the block under the tap is the one picked",
            100.0,
            pickMathBlock(page, 150.0, 520.0)?.left ?: Double.NaN,
            1e-6,
        )
        assertNull("paper holds no formula there", pickMathBlock(page, 500.0, 700.0))
        assertNull("a table is not a formula", pickMathBlock(pageOf(table()), 160.0, 140.0))
        assertNull("and ink is nobody's formula", pickMathBlock(pageOf(ink()), 430.0, 400.0))
    }

    /**
     * The **Plot** slot's own press: a plot it made under the tap is the one picked, so the slot selects it
     * instead of asking for a formula again and laying a second graph on the first. The slot keeps its picked
     * state across an insertion, which is exactly why that press cannot be read as "place another one"
     * (`pickPlotRef`, read by `beginPlacementPress` on the way down).
     */
    @Test
    fun thePlotSlotPicksThePlotUnderTheTap() {
        val page = pageOf(plot())
        assertEquals(
            "the graph under the press is the one picked",
            120.0,
            pickPlot(page, 200.0, 260.0)?.left ?: Double.NaN,
            1e-6,
        )
        assertEquals("and the reference names it", ElementRef(0, 0), pickPlotRef(page, 200.0, 260.0))
        assertNull("paper holds no graph there", pickPlot(page, 500.0, 700.0))
        assertNull("a table is not a graph", pickPlot(pageOf(table()), 160.0, 140.0))
        assertNull("and ink is nobody's graph", pickPlot(pageOf(ink()), 430.0, 400.0))
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
        // Ink is not a figure either: the pen never claimed a kind, so no figure tool picks it up (`LINE`
        // would otherwise claim any straight two-point stroke by its geometry alone).
        assertNull(pickFigureElement(page, 430.0, 400.0, ShapeKind.LINE))
    }

    @Test
    fun everyFigureToolPicksItsOwnFigure() {
        for (kind in FIGURE_KINDS) {
            val page = pageOf(figure(kind))
            val first = ShapeBuilder.build(kind, 100.0, 100.0, 200.0, 160.0, 2.0).first()
            assertEquals(
                "$kind picks the figure it drew",
                ElementRef(0, 0),
                pickFigureElement(page, first.x, first.y, kind),
            )
            // The same ink is nobody else's figure: the triangle tool leaves a rectangle alone.
            for (other in FIGURE_KINDS - kind) {
                assertNull("$other must not claim a $kind", pickFigureElement(page, first.x, first.y, other))
            }
        }
    }

    @Test
    fun aPressOnAClosedFigureOutlinePicksItWhileEmptyInteriorAndCornersMiss() {
        val rectPage = pageOf(figure(ShapeKind.RECTANGLE))
        assertNull(
            "a closed figure misses its empty interior",
            pickFigureElement(rectPage, 150.0, 130.0, ShapeKind.RECTANGLE),
        )
        assertEquals(
            ElementRef(0, 0),
            pickFigureElement(rectPage, 150.0, 100.0, ShapeKind.RECTANGLE),
        )
        val triPts = ShapeBuilder.build(ShapeKind.TRIANGLE, 100.0, 100.0, 200.0, 200.0, 2.0)
        val livingTri = ParametricShapes.elementForShapeTool(ShapeKind.TRIANGLE, triPts, 0xFF000000.toInt(), 2.0)!!
        val triPage = pageOf(livingTri)
        assertNull(
            "the triangle tool misses the empty interior of a living ParametricShapeElement",
            pickFigureElement(triPage, 150.0, 170.0, ShapeKind.TRIANGLE),
        )
        val edgeMidX = (livingTri.vertices[0].x + livingTri.vertices[1].x) / 2.0
        val edgeMidY = (livingTri.vertices[0].y + livingTri.vertices[1].y) / 2.0
        assertEquals(
            "the triangle tool picks a living ParametricShapeElement on its edge",
            ElementRef(0, 0),
            pickFigureElement(triPage, edgeMidX, edgeMidY, ShapeKind.TRIANGLE),
        )
        assertNull(
            "the empty corner of a triangle's bounding box does not select it",
            pickFigureElement(triPage, 105.0, 105.0, ShapeKind.TRIANGLE),
        )
    }

    @Test
    fun aStrokeWrittenBeforeTheKindWasAnswersByItsGeometry() {
        // A claim written by an older build says only "a tool drew me", so the shape has to speak for itself:
        // a straight two-point stroke is the line tool's, and it is not a rectangle's.
        val legacy = Stroke(
            Tool.PEN, 0xFF000000.toInt(), "round",
            listOf(StrokePoint(400.0, 420.0, 1.0), StrokePoint(480.0, 420.0, 1.0)),
            true, extraAttrs = FigurePins.FIGURE_ATTRS,
        )
        val page = pageOf(legacy)
        assertEquals(ElementRef(0, 0), pickFigureElement(page, 440.0, 420.0, ShapeKind.LINE))
        assertNull(pickFigureElement(page, 440.0, 420.0, ShapeKind.RECTANGLE))
    }

    @Test
    fun aFigureWrittenAsSeveralStrokesIsNeverClaimed() {
        // A dimension is two halves around its gap; the model gives the parts no identity to gather them by,
        // so picking one would select half an object and the tool goes on drawing instead.
        assertTrue(ShapeBuilder.isMultiStroke(ShapeKind.DIMENSION))
        for (kind in FIGURE_KINDS) assertFalse("$kind is one stroke", ShapeBuilder.isMultiStroke(kind))

        val half = ShapeBuilder.buildMulti(ShapeKind.DIMENSION, 300.0, 500.0, 400.0, 500.0, 2.0)!!.first()
        val page = pageOf(
            Stroke(Tool.PEN, 0xFF000000.toInt(), "round", half, true, extraAttrs = FigurePins.figureAttrs(ShapeKind.DIMENSION)),
        )
        assertNull(pickFigureElement(page, half.first().x, half.first().y, ShapeKind.DIMENSION))
    }

    @Test
    fun everyFigureToolEitherClaimsItsFigureOrIsWrittenAsSeveralStrokes() {
        // Ties the app's tool set to the rule: a slot that draws a figure must be one the press can claim,
        // unless its figure is several strokes — no tool may silently draw over its own work again.
        for (tool in SHAPE_TOOLS - LIVING_ELEMENT_TOOLS) {
            val kind = ShapeKind.entries.first { it.name == tool.name }
            if (kind == ShapeKind.DIMENSION) {
                assertTrue("$tool is written as several strokes", ShapeBuilder.isMultiStroke(kind))
            } else {
                assertFalse("$tool must claim the figure it draws", ShapeBuilder.isMultiStroke(kind))
            }
        }
    }

    @Test
    fun onlyTheTableAndTheCircuitPartsPlaceALivingElement() {
        assertEquals(LivingEditFamily.TABLE, livingFamilyOf(ShapeKind.TABLE))
        for (kind in CircuitElementFactory.CIRCUIT_KINDS) {
            assertEquals("$kind is a circuit", LivingEditFamily.CIRCUIT, livingFamilyOf(kind))
        }
        // A figure tool places a stroke, not a living element: its object is matched by kind instead.
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
        // A figure tool is not in it: a stroke is claimed by its kind, not by a living family.
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

    @Test
    fun switchingAwayFromAFigureOrPlacementToolClearsSelection() {
        assertTrue(
            "switching from rectangle to ellipse clears selection",
            shouldClearSelectionOnToolChange(
                wasSelecting = false, nowSelecting = false,
                wasShapeKind = ShapeKind.RECTANGLE, nowShapeKind = ShapeKind.ELLIPSE,
                wasPlaceKind = null, nowPlaceKind = null,
            ),
        )
        assertTrue(
            "switching from rectangle to pen clears selection",
            shouldClearSelectionOnToolChange(
                wasSelecting = false, nowSelecting = false,
                wasShapeKind = ShapeKind.RECTANGLE, nowShapeKind = null,
                wasPlaceKind = null, nowPlaceKind = null,
            ),
        )
        assertTrue(
            "switching from rectangle to select tool clears figure selection",
            shouldClearSelectionOnToolChange(
                wasSelecting = false, nowSelecting = true,
                wasShapeKind = ShapeKind.RECTANGLE, nowShapeKind = null,
                wasPlaceKind = null, nowPlaceKind = null,
            ),
        )
        assertTrue(
            "switching away from plot placement clears selection",
            shouldClearSelectionOnToolChange(
                wasSelecting = false, nowSelecting = false,
                wasShapeKind = null, nowShapeKind = null,
                wasPlaceKind = PlaceKind.PLOT, nowPlaceKind = null,
            ),
        )
        assertFalse(
            "re-applying the same figure tool on touch keeps selection",
            shouldClearSelectionOnToolChange(
                wasSelecting = false, nowSelecting = false,
                wasShapeKind = ShapeKind.RECTANGLE, nowShapeKind = ShapeKind.RECTANGLE,
                wasPlaceKind = null, nowPlaceKind = null,
            ),
        )
    }

    /** A wire from (100, 100) to (200, 100): a figure whose two ends are pins a connector may join. */
    private fun wire(): Stroke = figure(ShapeKind.LINE, 100.0, 100.0, 200.0, 100.0)

    /**
     * A press **on a pin** is the gesture's, not the pick's: the stroke starts on the terminal.
     *
     * The list is the draft's own (what its magnet snaps the first sample to), asked with the stroke's reach,
     * so a wire pressed on a wire's end draws from that end instead of selecting the wire the end belongs to.
     */
    @Test
    fun aPressOnAWiresEndIsTheGesturesNotThePicks() {
        val page = pageOf(wire())
        assertEquals(
            "the wire's start",
            100.0,
            pinUnderPress(page, ShapeKind.LINE, 101.0, 100.5, 12.0)?.absX,
        )
        assertEquals(
            "and its far end",
            200.0,
            pinUnderPress(page, ShapeKind.LINE, 199.0, 100.0, 12.0)?.absX,
        )
    }

    /** A press the magnet would not catch is no pin press: the part under it is picked as it always was. */
    @Test
    fun aPressOffTheMarkStillPicks() {
        val page = pageOf(wire())
        assertNull(
            "44 pt from the end is outside the reach",
            pinUnderPress(page, ShapeKind.LINE, 144.0, 140.0, 12.0),
        )
    }

    /** An armed **figure** tool is offered the ends it may be paired with, so it starts there too. */
    @Test
    fun anArmedFigureStartsOnAWiresEnd() {
        val page = pageOf(wire())
        assertEquals(
            "a rectangle begins on the wire's end",
            200.0,
            pinUnderPress(page, ShapeKind.RECTANGLE, 200.5, 100.5, 12.0)?.absX,
        )
    }
}
