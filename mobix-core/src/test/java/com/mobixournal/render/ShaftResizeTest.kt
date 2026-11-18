package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dragging a line's end **onto a pin**: the end is the thing that moves, not the point the hand went down
 * on.
 *
 * A hand lands within its own reach of an end, so a ratio read from the press scaled the shaft by
 * `length / pressDistance` and the tip came out a few points short of the pin the sheet had lit — the join
 * that "a little off the pin" was. The end now travels with the hand's own travel from where it started, and
 * the similarity carries it onto the pin exactly.
 */
class ShaftResizeTest {

    /** A wire from (100, 100) to (200, 100), minted by the line tool, so it is a connector. */
    private fun wire(): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0,
        capStyle = null,
        points = listOf(StrokePoint(100.0, 100.0, 1.0), StrokePoint(200.0, 100.0, 1.0)),
        uniformWidth = true,
        lineStyle = LineStyle.PLAIN,
        extraAttrs = FigurePins.FIGURE_ATTRS,
    )

    /** A part whose single terminal is at (300, 160) — the pin the drag is aimed at. */
    private fun part(): CircuitElement = CircuitElement(
        symbol = "GROUND",
        positionX = 300.0,
        positionY = 160.0,
        rotation = 0.0,
        pins = listOf(CircuitPin("p1", 0.0, 0.0)),
        id = "c1",
    )

    private fun pageOf(vararg elements: Element): Page = Page(
        width = 612.0,
        height = 792.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    /** The wire after a drag whose press was at ([pressX], [pressY]) and whose hand is now at the pointer. */
    private fun dragged(
        pressX: Double,
        pressY: Double,
        pointerX: Double,
        pointerY: Double,
    ): Stroke {
        val pages = shaftResizeToPin(
            pages = listOf(pageOf(wire(), part())),
            pageIndex = 0,
            refs = setOf(ElementRef(0, 0)),
            anchorX = 100.0,
            anchorY = 100.0,
            pressX = pressX,
            pressY = pressY,
            pointerX = pointerX,
            pointerY = pointerY,
            scale = 1f,
            magnet = PointMagnet(),
            minFactor = 0.05,
            maxFactor = 20.0,
        )
        return pages[0].layers[0].elements[0] as Stroke
    }

    @Test
    fun `an end aimed at a pin lands on the pin however far the press was from it`() {
        // The press is 10 pt short of the tip and 10 pt below it; the hand then travels (+100, +60), which
        // puts the *tip* on the part's terminal at (300, 160).
        val out = dragged(pressX = 190.0, pressY = 110.0, pointerX = 290.0, pointerY = 170.0)
        assertEquals("the tail is the anchor and stays", 100.0, out.points[0].x, 1e-6)
        assertEquals(100.0, out.points[0].y, 1e-6)
        assertEquals("the tip is on the pin, not short of it", 300.0, out.points[1].x, 1e-6)
        assertEquals(160.0, out.points[1].y, 1e-6)
    }

    @Test
    fun `with nothing in reach the end lands under the hand's own travel`() {
        val out = dragged(pressX = 190.0, pressY = 110.0, pointerX = 240.0, pointerY = 110.0)
        assertEquals("the tip travelled the hand's 50 pt", 250.0, out.points[1].x, 1e-6)
        assertEquals("and kept its height", 100.0, out.points[1].y, 1e-6)
    }

    @Test
    fun `the anchored end never moves`() {
        val out = dragged(pressX = 205.0, pressY = 104.0, pointerX = 105.0, pointerY = 204.0)
        assertEquals(100.0, out.points[0].x, 1e-6)
        assertEquals(100.0, out.points[0].y, 1e-6)
        assertEquals("the pen's width is untouched", 1.0, out.points[1].width, 1e-9)
    }
}
