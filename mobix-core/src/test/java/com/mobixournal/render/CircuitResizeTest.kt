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

    /**
     * A gate as the tool places one: 40 pt of axis, its two inputs on the other axis — the family whose
     * pins leave the axis, and so the only one a stretch can pull off its own lead.
     */
    private fun gate(): CircuitElement = CircuitElementFactory.placedElementFor(
        symbol = "AND_GATE",
        startX = 80.0,
        startY = 100.0,
        endX = 120.0,
        endY = 100.0,
    )!!.copy(id = "g1")

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
        // The inputs were 6 pt off the axis and the drawing is three times as tall, so they are 18 pt off
        // it — the same ratio, read on the symbol's own drawn height.
        assertEquals("and out across it by its own", -18.0, scaled.pins[0].relY, 1e-9)
        assertEquals("so the two inputs stay on the gate's own sides", 18.0, scaled.pins[1].relY, 1e-9)
    }

    @Test
    fun aGateLengthenedKeepsItsInputsOnTheDrawnLeads() {
        // A gate's input leads are half of its drawn height apart (the library sizes them from the axis), so
        // a longer gate has them further out. Pins read off the page's own stretch were left where the short
        // gate had them — inside a body they no longer touched.
        val gate = gate()
        val longer = resized(gate, 2.0, 1.0, anchorX = 80.0, anchorY = 100.0)
        assertEquals("the length doubles", -40.0, longer.pins[0].relX, 1e-9)
        assertNull("and no height is stored for a part only made longer", longer.height)
        assertEquals("the library sizes it from its new length", 40.0, CircuitInk.drawnAcross(longer), 1e-9)
        assertEquals(
            "the input sits on its lead, a quarter of the drawn height off the axis",
            -CircuitInk.drawnAcross(longer) / 4.0,
            longer.pins[0].relY,
            1e-9,
        )
        assertEquals("and so does the other one", CircuitInk.drawnAcross(longer) / 4.0, longer.pins[1].relY, 1e-9)
    }

    @Test
    fun aGateStretchedTallKeepsItsAxis() {
        // A gate's pins spread on both axes, so its box of pins can end up taller than it is long. The axis
        // is the line the symbol was drawn from, not the longer side of that box: reading the box turned the
        // whole gate a quarter turn in the middle of a resize.
        val gate = gate()
        val tall = resized(gate, 1.0, 6.0, anchorX = 100.0, anchorY = 88.0)
        assertEquals("the length is untouched", -20.0, tall.pins[0].relX, 1e-9)
        assertEquals("the drawing is six times as tall", 6.0, inkHeight(tall) / inkHeight(gate), 1e-9)
        assertEquals("and exactly as long as it was — the symbol did not turn", inkWidth(gate), inkWidth(tall), 1e-9)
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
