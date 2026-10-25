package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.Tool
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
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

/**
 * The magnet on a **selected** component: a part placed first and dragged into its place.
 *
 * This is the half a person actually uses after the first few parts: insert a resistor, then drag it until
 * it meets the wire. The window snapped the strip's drag and nothing else, so a moved part could only be
 * placed by eye and its pins never joined anything.
 */
class SelectionMagnetTest {

    private fun resistor(id: String, x: Double, y: Double) = CircuitElement(
        id = id,
        symbol = ShapeKind.RESISTOR.name,
        positionX = x,
        positionY = y,
        pins = listOf(CircuitPin("p1", -40.0, 0.0), CircuitPin("p2", 40.0, 0.0)),
    )

    private val anchors = resistor("c1", 200.0, 200.0)
    private val moved = resistor("c2", 600.0, 200.0)

    private val page = Page(
        width = 1000.0,
        height = 841.89,
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(Layer(listOf(anchors, moved))),
    )

    /** The second part, caught by a marquee around it. */
    private val selection = PageSelection(
        region = SelectionRegion.Rect(Bounds(550.0, 150.0, 650.0, 250.0)),
        refs = setOf(ElementRef(layerIndex = 0, elementIndex = 1)),
    )

    @Test
    fun `a dragged component's pin settles on a pin in reach`() {
        // Dragged left until its left pin is 3 pt off the anchor's right pin (240, 200) — the magnet takes
        // the last few points, so the moved pin lands exactly on it.
        val (dx, dy) = snapSelectionMove(page, selection, dx = -323.0, dy = 3.0, scale = 1f)
        val pin = moved.pins.first()
        assertEquals("p1 lands on the anchor's pin", 240.0, moved.positionX + dx + pin.relX, 1e-6)
        assertEquals(200.0, moved.positionY + dy + pin.relY, 1e-6)
    }

    /**
     * The part's **own** pin is not a target, so a small drag moves it instead of snapping it back.
     *
     * A pin can only be joined to *another* pin. Offering the moved part's own pins made the nearest target
     * its own position, so the first few points of every drag were pulled straight back — the part looked
     * stuck, and a nudge meant to line it up with a neighbour did nothing. A part read from a `.xopp` has
     * no id at all, which is why the exclusion is by position.
     */
    @Test
    fun `a part nudged a few points follows the hand instead of snapping onto itself`() {
        val lone = page.copy(layers = listOf(Layer(listOf(moved.copy(id = null)))))
        val caught = PageSelection(
            region = SelectionRegion.Rect(Bounds(550.0, 150.0, 650.0, 250.0)),
            refs = setOf(ElementRef(0, 0)),
        )
        val (dx, dy) = snapSelectionMove(lone, caught, dx = 6.0, dy = -4.0, scale = 1f)
        assertEquals("a nudge stays a nudge", 6.0, dx, 1e-9)
        assertEquals(-4.0, dy, 1e-9)
    }

    @Test
    fun `a drag out of reach is the hand's own translation, and a figure is never pulled`() {
        val (dx, dy) = snapSelectionMove(page, selection, dx = -100.0, dy = 120.0, scale = 1f)
        assertEquals("nothing in reach, so nothing moves it off the pointer", -100.0, dx, 1e-9)
        assertEquals(120.0, dy, 1e-9)

        val inkStroke = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = "round",
            points = listOf(StrokePoint(560.0, 190.0, 1.0), StrokePoint(640.0, 190.0, 1.0)),
            uniformWidth = true,
        )
        val inkyPage = page.copy(layers = listOf(Layer(listOf(anchors, inkStroke))))
        val caught = PageSelection(
            region = SelectionRegion.Rect(Bounds(550.0, 150.0, 650.0, 250.0)),
            refs = setOf(ElementRef(0, 1)),
        )
        val (fx, fy) = snapSelectionMove(inkyPage, caught, dx = -357.0, dy = 3.0, scale = 1f)
        assertEquals("a plain stroke keeps its drag", -357.0, fx, 1e-9)
        assertEquals(3.0, fy, 1e-9)
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
