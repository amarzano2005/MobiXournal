package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Resizing a **part** reads the stretch on the part's own axes, not the page's.
 *
 * A component states its pins in its own frame and carries the turn that maps them onto the page
 * (`CircuitInk`), and the symbol is drawn from those pins. Scaling `relX` by the page's `sx` and `relY` by
 * its `sy` therefore read the wrong directions for every part that was dragged at an angle — which is every
 * part but a horizontal one: a page-wide drag lengthened a part lying across the page whichever way it was
 * turned, a page-tall one did not lengthen it at all, and the symbol jumped off the pins it is drawn from.
 * That is what "the circuit resizes unnaturally" was.
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

    private fun pageOf(vararg elements: Element): Page = Page(
        width = 612.0,
        height = 792.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    /** The part after a resize by ([sx], [sy]) about its own centre, which is the point left fixed. */
    private fun resized(sx: Double, sy: Double): CircuitElement = SelectionOps.scaleXY(
        listOf(pageOf(turnedPart())), 0, setOf(ElementRef(0, 0)), sx, sy, 100.0, 100.0,
    ).first().layers[0].elements[0] as CircuitElement

    @Test
    fun aTurnedPartTakesTheStretchAlongItsOwnAxis() {
        val wide = resized(2.0, 1.0)
        assertEquals("a page-wide drag lengthens a part that lies along the page", 60.0, wide.pins[1].relY, 1e-9)
        assertEquals("and leaves it where it was across that axis", 100.0, wide.positionX, 1e-9)
    }

    @Test
    fun aTurnedPartIsNotLengthenedAlongThePageItDoesNotLieOn() {
        val tall = resized(1.0, 2.0)
        assertEquals("a page-tall drag does not lengthen it", 30.0, tall.pins[1].relY, 1e-9)
        assertEquals("and its position stays on the anchor", 100.0, tall.positionY, 1e-9)
    }

    /** A part drawn horizontally is the case the old code got right, and it must stay right. */
    @Test
    fun aPartAlongThePageKeepsThePlainReading() {
        val flat = turnedPart().copy(rotation = 0.0, pins = listOf(CircuitPin("p1", -30.0, 0.0), CircuitPin("p2", 30.0, 0.0)))
        val scaled = SelectionOps.scaleXY(
            listOf(pageOf(flat)), 0, setOf(ElementRef(0, 0)), 2.0, 3.0, 100.0, 100.0,
        ).first().layers[0].elements[0] as CircuitElement
        assertEquals("sx lengthens", 60.0, scaled.pins[1].relX, 1e-9)
        assertEquals("sy is not its axis", 0.0, scaled.pins[1].relY, 1e-9)
    }
}
