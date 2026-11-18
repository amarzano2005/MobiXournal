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
 * A **part** resized from its handles: the stretch is measured on its own axis and applied about the anchor.
 *
 * A component is a symbol generated from its axis (`CircuitInk`), so the length of that axis is the one
 * geometry the model holds — there is no height field for a cross-axis stretch to write. Two things follow,
 * and both were wrong before: a stretch **along** the axis lengthens the part exactly, and one **across** it
 * must not move the part at all (it used to slide the symbol down the page, taking the anchor corner the
 * handle had pinned with it). A turned part reads the stretch along the axis it lies on, so a page-wide drag
 * lengthens the part lying across the page and leaves the one lying up it alone.
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

    @Test
    fun aTurnedPartTakesTheStretchAlongItsOwnAxis() {
        val wide = resized(turnedPart(), 2.0, 1.0)
        assertEquals("a page-wide drag lengthens a part that lies along the page", 60.0, wide.pins[1].relY, 1e-9)
        assertEquals("and leaves it where it was across that axis", 100.0, wide.positionX, 1e-9)
    }

    @Test
    fun aTurnedPartIsNotLengthenedAlongThePageItDoesNotLieOn() {
        val tall = resized(turnedPart(), 1.0, 2.0)
        assertEquals("a page-tall drag does not lengthen it", 30.0, tall.pins[1].relY, 1e-9)
        assertEquals("and its position stays on the anchor", 100.0, tall.positionY, 1e-9)
    }

    /** A part drawn horizontally is the case the old code got right, and it must stay right. */
    @Test
    fun aPartAlongThePageKeepsThePlainReading() {
        val scaled = resized(flatPart(), 2.0, 3.0)
        assertEquals("sx lengthens", 60.0, scaled.pins[1].relX, 1e-9)
        assertEquals("sy is not its axis", 0.0, scaled.pins[1].relY, 1e-9)
    }

    @Test
    fun aStretchAlongThePartLengthensItAboutTheAnchor() {
        // The left terminal is the anchor: it must not move, and the right one must double away from it.
        val scaled = resized(flatPart(), 2.0, 1.0, anchorX = 70.0, anchorY = 100.0)
        assertEquals("the anchored terminal stays", 70.0, abs(scaled, 0).first, 1e-9)
        assertEquals(100.0, abs(scaled, 0).second, 1e-9)
        assertEquals(190.0, abs(scaled, 1).first, 1e-9)
        assertEquals(100.0, abs(scaled, 1).second, 1e-9)
    }

    @Test
    fun aStretchAcrossThePartLeavesItWhereItWas() {
        // A drag down the page on a part that lies across it has no length to give: the part must not slide,
        // which is what moved the anchor corner the handle had pinned (`ElementBounds` is read off the ink
        // the pins place, so a part that drifts leaves its own box behind).
        val pulled = resized(flatPart(), 1.0, 3.0, anchorX = 70.0, anchorY = 87.0)
        assertEquals(100.0, pulled.positionX, 1e-9)
        assertEquals(100.0, pulled.positionY, 1e-9)
        assertEquals(130.0, abs(pulled, 1).first, 1e-9)
    }

    @Test
    fun aCrossAxisStretchKeepsThePartsProportions() {
        // A gate's pins spread on both of its own axes, and a similarity scales both: the symbol is drawn
        // from that box, so a part never turns into a sheared one.
        val gate = CircuitElement(
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
        val scaled = resized(gate, 2.0, 1.0, anchorX = 0.0, anchorY = 0.0)
        assertEquals(-40.0, scaled.pins[0].relX, 1e-9)
        assertEquals("the cross-axis spread follows the length", -20.0, scaled.pins[0].relY, 1e-9)
    }
}
