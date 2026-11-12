package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The magnet's **hold** (`PinMagnet`): a caught target survives the jitter on the edge of the reach, and
 * is let go once the hand really leaves. Plus the one thing the spatial filter must not change — which
 * target wins.
 */
class PinMagnetTest {

    private val reach = 20.0

    private fun part(x: Double, y: Double, id: String): CircuitElement = CircuitElement(
        symbol = "RESISTOR",
        positionX = x,
        positionY = y,
        pins = listOf(CircuitPin("p1", 0.0, 0.0)),
        id = id,
    )

    private fun pageOf(vararg elements: com.mobixournal.format.model.Element): Page = Page(
        width = 612.0,
        height = 792.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    private fun wire(x0: Double, y0: Double, x1: Double, y1: Double): Stroke = Stroke(
        Tool.PEN,
        0xFF000000.toInt(),
        "round",
        listOf(StrokePoint(x0, y0, 1.0), StrokePoint(x1, y1, 1.0)),
        true,
    )

    /** A mover at (180, 200) whose pin is exactly on the edge of a reach from an anchor at (200, 200). */
    private fun edgePage(): Page = pageOf(part(200.0, 200.0, "anchor"), part(180.0, 200.0, "mover"))

    @Test
    fun aCaughtTargetHoldsWhileTheHandStaysInsideTheReleaseRatio() {
        val magnet = PinMagnet()
        val refs = setOf(ElementRef(0, 1))

        val (dx, dy) = magnet.translation(edgePage(), refs, 0.0, 0.0, reach)
        assertEquals("the pin lands on the anchor", 20.0, dx, 1e-9)
        assertEquals(0.0, dy, 1e-9)
        assertEquals("and the anchor is what it caught", "anchor", magnet.caught?.circuitId)

        // 22.4 pt away is past the reach but inside 1.5 × it: the hold keeps the pin attached.
        val (heldX, heldY) = magnet.translation(edgePage(), refs, 0.0, 10.0, reach)
        assertEquals("a jitter on the edge does not drop the target", 20.0, heldX, 1e-9)
        assertEquals(0.0, heldY, 1e-9)
        assertEquals("anchor", magnet.caught?.circuitId)
    }

    @Test
    fun aTargetIsLetGoPastTheHoldAndTheHandKeepsItsOwnMove() {
        val magnet = PinMagnet()
        val refs = setOf(ElementRef(0, 1))

        magnet.translation(edgePage(), refs, 0.0, 0.0, reach)
        // 44.7 pt away: well past 1.5 × 20, so nothing holds.
        val (dx, dy) = magnet.translation(edgePage(), refs, 0.0, 40.0, reach)
        assertEquals("the hand's own move", 0.0, dx, 1e-9)
        assertEquals(40.0, dy, 1e-9)
        assertNull("and the magnet holds nothing", magnet.caught)
    }

    @Test
    fun aResetDropsTheHold() {
        val magnet = PinMagnet()
        val refs = setOf(ElementRef(0, 1))
        magnet.translation(edgePage(), refs, 0.0, 0.0, reach)
        magnet.reset()
        assertNull(magnet.caught)
        // The same frame that was held before the reset is now judged on its own: 22.4 pt > 20.
        val (dx, dy) = magnet.translation(edgePage(), refs, 0.0, 10.0, reach)
        assertEquals(0.0, dx, 1e-9)
        assertEquals(10.0, dy, 1e-9)
    }

    /**
     * The magnet on a **shape gesture**: a wire's end (or a part's end being placed) holds the pin it caught
     * across the frames of the drag, exactly as a dragged selection does, and lets go past the hold.
     */
    @Test
    fun aShapeEndHoldsItsPinAcrossFrames() {
        val magnet = EndsMagnet()
        val pin = CircuitSnapping.PinTarget(null, "p1", 200.0, 200.0, null)
        val targets = listOf(pin)

        val (sx, sy) = magnet.snapStart(200.0, 200.0, targets, reach)
        assertEquals(200.0, sx, 1e-9)
        assertEquals(200.0, sy, 1e-9)
        assertEquals("p1", magnet.caught.first()?.pinId)

        // 28 pt away: outside the reach, inside 1.5 × it.
        val (hx, hy) = magnet.snapStart(228.0, 200.0, targets, reach)
        assertEquals("the caught pin still wins", 200.0, hx, 1e-9)
        assertEquals(200.0, hy, 1e-9)
        assertEquals("p1", magnet.caught.first()?.pinId)

        // 40 pt away: past the hold, so the hand's own point is the answer.
        val (fx, fy) = magnet.snapStart(240.0, 200.0, targets, reach)
        assertEquals(240.0, fx, 1e-9)
        assertEquals(200.0, fy, 1e-9)
        assertNull("and nothing is held", magnet.caught.first())
    }

    /** The two ends are independent: aiming one at a pin must not pull the other onto it. */
    @Test
    fun theTwoEndsOfAShapeGestureAreIndependent() {
        val magnet = EndsMagnet()
        val left = CircuitSnapping.PinTarget(null, "left", 100.0, 100.0, null)
        val right = CircuitSnapping.PinTarget(null, "right", 400.0, 100.0, null)
        val targets = listOf(left, right)

        magnet.snapStart(101.0, 100.0, targets, reach)
        magnet.snapEnd(399.0, 101.0, targets, reach)
        assertEquals("left", magnet.caught.first()?.pinId)
        assertEquals("right", magnet.caught[1]?.pinId)

        // Only the start is moved away: the end keeps its own pin.
        magnet.snapStart(300.0, 300.0, targets, reach)
        assertNull("the start let go", magnet.caught.first())
        assertEquals("right", magnet.caught[1]?.pinId)
    }

    /** A wire is a connector, not a terminal: two lines dragged near each other stay two lines. */
    @Test
    fun aMovedWireNeverJoinsAnotherWiresEnd() {
        val page = pageOf(wire(170.0, 100.0, 250.0, 100.0), wire(100.0, 100.0, 150.0, 100.0))
        val magnet = PinMagnet()

        val (dx, dy) = magnet.translation(page, setOf(ElementRef(0, 1)), 10.0, 0.0, reach)
        assertEquals("the end is 10 pt from the other wire's end and still not caught", 10.0, dx, 1e-9)
        assertEquals(0.0, dy, 1e-9)
        assertNull(magnet.caught)
    }

    /**
     * The spatial filter is an optimisation, not a rule: a pin far across the page is collected away, and
     * the answer is still the near anchor — the diff a prefilter must never introduce.
     */
    @Test
    fun aTargetAcrossThePageDoesNotChangeWhichPinWins() {
        val page = pageOf(
            part(500.0, 500.0, "far"),
            part(200.0, 200.0, "anchor"),
            part(180.0, 200.0, "mover"),
        )
        val magnet = PinMagnet()
        val refs = setOf(ElementRef(0, 2))

        val (dx, dy) = magnet.translation(page, refs, 0.0, 0.0, reach)
        assertEquals("the near anchor still wins", 20.0, dx, 1e-9)
        assertEquals(0.0, dy, 1e-9)
        assertEquals("anchor", magnet.caught?.circuitId)
    }
}
