package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A **part** resized from its handles, in **both** of its dimensions: the length of its axis and the
 * height across it.
 *
 * A component is a symbol generated from its axis (`CircuitInk`), so the axis states its length and
 * `CircuitElement.height` the extent across it — the two sizes a part placed by a drag already has. A
 * page stretch is read on the part's own axes, so a side handle makes the symbol wider or taller
 * without touching the other dimension, a corner gives the two sizes a ratio each, and a part lying on
 * the page's axes puts its ink exactly where the dragged corner went. A part nobody has stretched
 * stores no height at all: the symbol library's own size for its length is the authority there.
 */
class CircuitResizeTest {

    /** A part whose own axis runs along `relY`, turned a quarter turn — so it lies along the page's x. */
    private fun turnedPart(): CircuitElement = CircuitElement(
        symbol = "RESISTOR",
        positionX = 100.0,
        positionY = 100.0,
        rotation = 90.0,
        pins = listOf(CircuitPin("p1", 0.0, -30.0), CircuitPin("p2", 0.0, 30.0)),
        id = "c1",
    )

    /** A part drawn along the page's x, its terminals at (70, 100) and (130, 100). */
    private fun flatPart(): CircuitElement = CircuitElement(
        symbol = "RESISTOR",
        positionX = 100.0,
        positionY = 100.0,
        rotation = 0.0,
        pins = listOf(CircuitPin("p1", -30.0, 0.0), CircuitPin("p2", 30.0, 0.0)),
        id = "c1",
    )

    /** A gate: its pins spread on both of the part's own axes. */
    private fun gate(): CircuitElement = CircuitElement(
        symbol = "AND",
        positionX = 100.0,
        positionY = 100.0,
        rotation = 0.0,
        pins = listOf(
            CircuitPin("in1", -20.0, -10.0),
            CircuitPin("in2", -20.0, 10.0),
            CircuitPin("out", 20.0, 0.0),
        ),
        id = "g1",
    )

    private fun pageOf(vararg elements: Element): Page = Page(
        width = 612.0,
        height = 792.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    /** [element] after a resize by ([sx], [sy]) that leaves ([anchorX], [anchorY]) fixed. */
    private fun resized(
        element: CircuitElement,
        sx: Double,
        sy: Double,
        anchorX: Double = 100.0,
        anchorY: Double = 100.0,
    ): CircuitElement = SelectionOps.scaleXY(
        listOf(pageOf(element)), 0, setOf(ElementRef(0, 0)), sx, sy, anchorX, anchorY,
    ).first().layers[0].elements[0] as CircuitElement

    /** A pin's position on the page. */
    private fun abs(part: CircuitElement, index: Int): Pair<Double, Double> =
        CircuitSnapping.pinAbsolutePosition(part, part.pins[index])

    /** The spread of the ink the part actually draws along the page's y — its height on the page. */
    private fun inkHeight(part: CircuitElement): Double {
        val points = CircuitInk.ink(part)!!.filterIsInstance<Stroke>().flatMap { it.points }
        return points.maxOf { it.y } - points.minOf { it.y }
    }

    /** The spread of the ink along the page's x — its length on the page. */
    private fun inkWidth(part: CircuitElement): Double {
        val points = CircuitInk.ink(part)!!.filterIsInstance<Stroke>().flatMap { it.points }
        return points.maxOf { it.x } - points.minOf { it.x }
    }

    @Test
    fun aSideStretchAcrossThePartGivesItAHeight() {
        val flat = flatPart()
        val tall = resized(flat, 1.0, 2.0, anchorX = 70.0, anchorY = 86.8)
        assertEquals("the length is untouched", 30.0, tall.pins[1].relX, 1e-9)
        assertEquals(
            "the drawing is twice as tall",
            2.0,
            inkHeight(tall) / inkHeight(flat),
            1e-9,
        )
        assertEquals(
            "and the height the handles gave it is stored",
            CircuitInk.naturalAcross(flat) * 2.0,
            CircuitInk.drawnAcross(tall),
            1e-9,
        )
    }

    @Test
    fun aSideStretchAlongThePartLengthensItAndStoresNoHeight() {
        val wide = resized(flatPart(), 2.0, 1.0)
        assertEquals("sx lengthens", 60.0, wide.pins[1].relX, 1e-9)
        assertNull("a part that was only made longer keeps the library's own height", wide.height)
        assertEquals(
            "so its height follows its new length, as the library draws it",
            CircuitInk.naturalAcross(wide),
            CircuitInk.drawnAcross(wide),
            1e-9,
        )
    }

    @Test
    fun aCornerGivesEachOfThePartsSizesItsOwnRatio() {
        val flat = flatPart()
        val corner = resized(flat, 2.0, 3.0, anchorX = 70.0, anchorY = 86.8)
        assertEquals("the length takes the width's ratio", 60.0, corner.pins[1].relX, 1e-9)
        assertEquals(
            "and the height the height's",
            CircuitInk.naturalAcross(flat) * 3.0,
            CircuitInk.drawnAcross(corner),
            1e-9,
        )
        assertEquals("both on the drawing itself", 2.0, inkWidth(corner) / inkWidth(flat), 1e-9)
    }

    @Test
    fun aTurnedPartTakesTheStretchAlongItsOwnAxis() {
        val wide = resized(turnedPart(), 2.0, 1.0)
        assertEquals("a page-wide drag lengthens a part that lies along the page", 60.0, wide.pins[1].relY, 1e-9)
        assertNull("and stores no height of its own", wide.height)
        assertEquals(
            "so the library still sizes it from its length",
            CircuitInk.naturalAcross(wide),
            CircuitInk.drawnAcross(wide),
            1e-9,
        )
    }

    @Test
    fun aTurnedPartTakesTheStretchAcrossItsOwnAxis() {
        // The part lies along the page's x, so its height is the page's y: a page-tall drag makes it
        // taller without lengthening it, which is the side handle it wears above and below.
        val tall = resized(turnedPart(), 1.0, 2.0, anchorX = 100.0, anchorY = 87.0)
        assertEquals("a page-tall drag does not lengthen it", 30.0, tall.pins[1].relY, 1e-9)
        assertEquals(
            "it makes it twice as tall",
            2.0,
            CircuitInk.drawnAcross(tall) / CircuitInk.drawnAcross(turnedPart()),
            1e-9,
        )
    }

    @Test
    fun aGatesPinsFollowEachOfItsOwnAxes() {
        val scaled = resized(gate(), 2.0, 3.0, anchorX = 0.0, anchorY = 0.0)
        assertEquals("the input moves out along the axis by its ratio", -40.0, scaled.pins[0].relX, 1e-9)
        assertEquals("and out across it by its own", -30.0, scaled.pins[0].relY, 1e-9)
        assertEquals("so the two inputs stay on the gate's own sides", 30.0, scaled.pins[1].relY, 1e-9)
    }

    @Test
    fun theAnchoredTerminalStaysPutWhileTheOtherTravels() {
        // The left terminal is the anchor: it must not move, and the right one must double away from it.
        val scaled = resized(flatPart(), 2.0, 1.0, anchorX = 70.0, anchorY = 100.0)
        assertEquals("the anchored terminal stays", 70.0, abs(scaled, 0).first, 1e-9)
        assertEquals(100.0, abs(scaled, 0).second, 1e-9)
        assertEquals(190.0, abs(scaled, 1).first, 1e-9)
        assertEquals(100.0, abs(scaled, 1).second, 1e-9)
    }

    @Test
    fun theAnchoredEdgeStaysPutAcrossTheAxis() {
        // The part's own box top is the anchor of a bottom-edge drag: the drawing grows downwards and
        // the edge the handle left behind stays exactly where it was.
        val flat = flatPart()
        val top = (CircuitInk.ink(flat)!!.filterIsInstance<Stroke>().flatMap { it.points }).minOf { it.y }
        val pulled = resized(flat, 1.0, 3.0, anchorX = 70.0, anchorY = top)
        val pulledTop = (CircuitInk.ink(pulled)!!.filterIsInstance<Stroke>().flatMap { it.points }).minOf { it.y }
        assertEquals(top, pulledTop, 1e-9)
    }
}
