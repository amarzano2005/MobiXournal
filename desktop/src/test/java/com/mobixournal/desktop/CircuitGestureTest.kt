package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.render.ShapeKind
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The **circuit magnet and the component's own line**, on the page: what a drag is snapped to and what
 * shape a component is placed in.
 *
 * Two facts are pinned here, and both were missing from the window:
 *  - a part is authored from the drag's **two ends**, so a diagonal drag is a rotated part (the box a
 *    drag used to be taken as laid every part out horizontally, whatever the hand did);
 *  - a wire's ends, and both ends of a placed part, are pulled onto the page's **pins** — the same
 *    reach the tablet previews with (`CircuitSnapping.thresholdFor`), so a committed wire really joins
 *    the component it was drawn onto.
 *
 * Pure: no window, no scene. The drag's plumbing is `DesktopCircuitPreviewTest`'s.
 */
class CircuitGestureTest {

    /** A resistor at (200, 200) with its two pins 40 pt either side — a page with something to join. */
    private fun pageWithResistor(): Page {
        val resistor = CircuitElement(
            symbol = ShapeKind.RESISTOR.name,
            positionX = 200.0,
            positionY = 200.0,
            pins = listOf(CircuitPin("p1", -40.0, 0.0), CircuitPin("p2", 40.0, 0.0)),
        )
        return page(listOf(resistor))
    }

    private fun page(elements: List<com.mobixournal.format.model.Element>): Page = Page(
        width = 595.276,
        height = 841.89,
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(Layer(elements)),
    )

    private val ink = DesktopInk(width = 2f, colour = 0xFF112233.toInt())

    @Test
    fun `a component's end is pulled onto a pin in reach`() {
        val span = circuitSpan(
            kind = ShapeKind.RESISTOR,
            page = pageWithResistor(),
            startX = 163.0,
            startY = 202.0,
            endX = 400.0,
            endY = 300.0,
            scale = 1f,
        )
        assertEquals("the press lands on p1", 160.0, span.startX, 1e-6)
        assertEquals(200.0, span.startY, 1e-6)
        assertEquals("the far end has no pin to aim at", 400.0, span.endX, 1e-6)
        assertEquals(300.0, span.endY, 1e-6)
    }

    @Test
    fun `a component drawn diagonally is rotated along the hand`() {
        val span = CircuitSpan(100.0, 100.0, 200.0, 200.0)
        val part = circuitElementForSpan(ShapeKind.RESISTOR, span, ink) as? CircuitElement
        assertNotNull("the resistor is in the library", part)
        part!!
        assertEquals("the drag's direction is the part's rotation", 45.0, part.rotation, 1e-6)
        assertEquals("and its length is the drag's", hypot(100.0, 100.0), span.pinSpan(part), 1e-6)
        assertEquals("drawn in the pen's ink", ink.colour, part.strokeColor)
        assertEquals(ink.width.toDouble(), part.strokeWidth, 1e-6)
    }

    @Test
    fun `a click still places the family's canonical length`() {
        val part = circuitElementForSpan(ShapeKind.RESISTOR, CircuitSpan(300.0, 300.0, 301.0, 300.0), ink)
            as? CircuitElement
        assertNotNull(part)
        assertEquals("a press that never travelled is horizontal", 0.0, part!!.rotation, 1e-6)
    }

    @Test
    fun `a wire's ends are pulled onto the pins, and nothing else is`() {
        val page = pageWithResistor()
        val wire = listOf(StrokePoint(162.0, 201.0, 1.0), StrokePoint(238.0, 199.0, 1.0))
        val snapped = snapWireEnds(ShapeKind.LINE, wire, page, scale = 1f)
        assertEquals(160.0, snapped.first().x, 1e-6)
        assertEquals(200.0, snapped.first().y, 1e-6)
        assertEquals(240.0, snapped.last().x, 1e-6)
        assertEquals(200.0, snapped.last().y, 1e-6)

        val rectangle = listOf(StrokePoint(162.0, 201.0, 1.0), StrokePoint(238.0, 199.0, 1.0))
        assertEquals(
            "a rectangle is the user's own geometry",
            rectangle,
            snapWireEnds(ShapeKind.RECTANGLE, rectangle, page, scale = 1f),
        )
        assertEquals(
            "and a page with no components has nothing to pull toward",
            wire,
            snapWireEnds(ShapeKind.LINE, wire, page(emptyList()), scale = 1f),
        )
    }

    /** The distance between the part's two pins — the length the drag asked for. */
    private fun CircuitSpan.pinSpan(part: CircuitElement): Double {
        val first = part.pins.first()
        val last = part.pins.last()
        return hypot(last.relX - first.relX, last.relY - first.relY)
    }
}

/** A tiny sanity check that the magnet's reach grows on a zoomed-out page, as the tablet's does. */
class CircuitReachTest {
    @Test
    fun `the magnet reaches further on screen when the page is zoomed out`() {
        val zoomedIn = com.mobixournal.render.CircuitSnapping.thresholdFor(4f)
        val zoomedOut = com.mobixournal.render.CircuitSnapping.thresholdFor(0.5f)
        assertTrue("a fit page's targets stay hittable", zoomedOut > zoomedIn)
        assertTrue(
            "but never across a visible fraction of the sheet",
            zoomedOut <= com.mobixournal.render.CircuitSnapping.MAX_SNAP_PT,
        )
    }
}
