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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CircuitSnappingTest {

    @Test
    fun unrotatedPinAbsolutePositionAddsRelativeOffsets() {
        val circuit = CircuitElement(
            symbol = "RESISTOR",
            positionX = 200.0,
            positionY = 150.0,
            rotation = 0.0,
            pins = listOf(CircuitPin("p1", -25.0, 0.0)),
        )
        val (x, y) = CircuitSnapping.pinAbsolutePosition(circuit, circuit.pins[0])
        assertEquals(175.0, x, 1e-6)
        assertEquals(150.0, y, 1e-6)
    }

    @Test
    fun rotatedPinAbsolutePositionRotatesAroundAnchor() {
        val circuit = CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            rotation = 90.0,
            pins = listOf(CircuitPin("p1", 50.0, 0.0)),
        )
        val (x, y) = CircuitSnapping.pinAbsolutePosition(circuit, circuit.pins[0])
        // (50, 0) rotated 90 deg clockwise: dx = 0, dy = 50.
        assertEquals(100.0, x, 1e-6)
        assertEquals(150.0, y, 1e-6)
    }

    @Test
    fun relativePositionInvertsRotation() {
        val circuit = CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            rotation = 90.0,
        )
        val (relX, relY) = CircuitSnapping.pinRelativePosition(circuit, 100.0, 150.0)
        assertEquals(50.0, relX, 1e-6)
        assertEquals(0.0, relY, 1e-6)
    }

    @Test
    fun magneticSnappingSnapsWithinThresholdAndAssignsNet() {
        val resistor1 = CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p2", 50.0, 0.0, connectedTo = "net_vdd")),
            id = "c1",
        )
        val resistor2 = CircuitElement(
            symbol = "RESISTOR",
            positionX = 200.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p1", -50.0, 0.0)),
            id = "c2",
        )

        // Resistor1 p2 is at (150, 100) with net "net_vdd".
        // Drag Resistor2 p1 to (153, 102), which is within threshold (dist ~ 3.6 pt < 14 pt).
        val targets = CircuitSnapping.collectTargets(listOf(resistor1), excludeCircuitId = "c2")
        val snap = CircuitSnapping.snapPinDrag(resistor2, resistor2.pins[0], 153.0, 102.0, targets)

        assertNotNull(snap.targetPin)
        assertEquals("p2", snap.targetPin!!.pinId)
        assertEquals("net_vdd", snap.targetNet)
        assertEquals("net_vdd", snap.snappedPin.connectedTo)
        // Snapped to exact target location (relX = 150 - 200 = -50, relY = 100 - 100 = 0)
        assertEquals(-50.0, snap.snappedPin.relX, 1e-6)
        assertEquals(0.0, snap.snappedPin.relY, 1e-6)
    }

    @Test
    fun pullingPinFreeBeyondThresholdClearsNet() {
        val resistor1 = CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p2", 50.0, 0.0, connectedTo = "net_vdd")),
            id = "c1",
        )
        val resistor2 = CircuitElement(
            symbol = "RESISTOR",
            positionX = 200.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p1", -50.0, 0.0, connectedTo = "net_vdd")),
            id = "c2",
        )

        val targets = CircuitSnapping.collectTargets(listOf(resistor1), excludeCircuitId = "c2")
        // Drag to (180, 100) — distance is 30 pt, which exceeds threshold 14 pt.
        val snap = CircuitSnapping.snapPinDrag(resistor2, resistor2.pins[0], 180.0, 100.0, targets)

        assertNull("no target in reach", snap.targetPin)
        assertNull("net disconnected", snap.targetNet)
        assertNull("pin disconnected", snap.snappedPin.connectedTo)
        assertEquals(-20.0, snap.snappedPin.relX, 1e-6)
        assertEquals(0.0, snap.snappedPin.relY, 1e-6)
    }

    @Test
    fun snapPointSnapsToNearbyPin() {
        val resistor = CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p1", -50.0, 0.0), CircuitPin("p2", 50.0, 0.0)),
            id = "c1",
        )
        val page = com.mobixournal.format.model.Page(
            width = 612.0,
            height = 792.0,
            background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
            layers = listOf(com.mobixournal.format.model.Layer(listOf(resistor))),
        )
        // p2 is at (150, 100). Point (152, 102) is within 14 pt threshold.
        val (sx, sy) = CircuitSnapping.snapPoint(
            page, 152.0, 102.0, snapToGrid = false, magnetic = true,
        )
        assertEquals(150.0, sx, 1e-6)
        assertEquals(100.0, sy, 1e-6)
    }

    /**
     * The magnet belongs to connector gestures only: a rectangle, bond or recognised freehand figure
     * drawn beside a pin keeps the geometry the user dragged.
     */
    @Test
    fun snapPointWithoutTheMagnetLeavesAnOrdinaryFigureAlone() {
        val circuit = CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p2", 50.0, 0.0)),
            id = "c1",
        )
        val page = com.mobixournal.format.model.Page(
            width = 612.0,
            height = 792.0,
            background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
            layers = listOf(com.mobixournal.format.model.Layer(listOf(circuit))),
        )
        // p2 is at (150, 100); the point is well within reach of it.
        val (x, y) = CircuitSnapping.snapPoint(page, 152.0, 102.0, snapToGrid = false)
        assertEquals(152.0, x, 1e-6)
        assertEquals(102.0, y, 1e-6)
    }

    /**
     * A pen stroke is never a magnetic target — not even a perfectly straight one, and not even on a
     * page that holds components. Only a component's pin is a terminal: ink lying near a wire is the
     * user's own drawing and must not be pulled into a connection.
     */
    @Test
    fun penStrokesAreNeverMagneticTargets() {
        val wire = com.mobixournal.format.model.Stroke(
            com.mobixournal.format.model.Tool.PEN,
            0xFF000000.toInt(),
            "round",
            listOf(
                com.mobixournal.format.model.StrokePoint(50.0, 50.0, 1.0),
                com.mobixournal.format.model.StrokePoint(200.0, 50.0, 1.0),
            ),
            true,
        )
        val page = com.mobixournal.format.model.Page(
            width = 612.0,
            height = 792.0,
            background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
            layers = listOf(com.mobixournal.format.model.Layer(listOf(wire))),
        )
        assertEquals(0, CircuitSnapping.collectAllTargets(page).size)

        // With a component on the page the only target is that component's pin.
        val resistor = CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p1", -50.0, 0.0)),
            id = "c1",
        )
        val circuitPage = page.copy(layers = page.layers + com.mobixournal.format.model.Layer(listOf(resistor)))
        val targets = CircuitSnapping.collectAllTargets(circuitPage)
        assertEquals(1, targets.size)
        assertEquals("c1", targets.single().circuitId)

        // A line tool stroke drawn near the other stroke's end stays where the user put it.
        val (x, y) = CircuitSnapping.snapPoint(page, 201.0, 51.0, snapToGrid = false, magnetic = true)
        assertEquals(201.0, x, 1e-6)
        assertEquals(51.0, y, 1e-6)
    }

    /**
     * A wire is one straight segment. A closed figure (its last point back on its first) is ink whose
     * corners must never act as terminals, and a straight line with redundant collinear vertices is
     * still a wire.
     */
    @Test
    fun onlyAStraightSegmentCountsAsAWire() {
        val triangle = com.mobixournal.format.model.Stroke(
            com.mobixournal.format.model.Tool.PEN,
            0xFF000000.toInt(),
            "round",
            listOf(
                com.mobixournal.format.model.StrokePoint(50.0, 50.0, 1.0),
                com.mobixournal.format.model.StrokePoint(150.0, 50.0, 1.0),
                com.mobixournal.format.model.StrokePoint(100.0, 120.0, 1.0),
                com.mobixournal.format.model.StrokePoint(50.0, 50.0, 1.0),
            ),
            true,
        )
        val straight = triangle.copy(
            points = listOf(
                com.mobixournal.format.model.StrokePoint(50.0, 50.0, 1.0),
                com.mobixournal.format.model.StrokePoint(100.0, 50.0, 1.0),
                com.mobixournal.format.model.StrokePoint(200.0, 50.0, 1.0),
            ),
        )
        assertFalse("a closed figure is not a wire", CircuitSnapping.isWire(triangle))
        assertTrue("a straight line is a wire", CircuitSnapping.isWire(straight))

        // Neither figure contributes a target: only the component's pin does.
        val circuit = CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p1", -50.0, 0.0)),
            id = "c1",
        )
        val page = com.mobixournal.format.model.Page(
            width = 612.0,
            height = 792.0,
            background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
            layers = listOf(
                com.mobixournal.format.model.Layer(listOf(triangle, straight)),
                com.mobixournal.format.model.Layer(listOf(circuit)),
            ),
        )
        assertEquals(1, CircuitSnapping.collectAllTargets(page).size) // the pin, and nothing else
    }

    /**
     * The reach has to stay big enough to aim at on screen: a fit-to-width page draws a point at well
     * under a pixel, so a fixed 14pt reach left a target three pixels wide — which is why wiring a
     * component felt like it worked only now and then.
     */
    @Test
    fun theMagnetStaysAimableAtEveryZoom() {
        assertEquals("at 100% the reach is the fingertip on screen", 22.0, CircuitSnapping.thresholdFor(1f), 1e-6)
        assertEquals("zoomed in, the document-space reach takes over", 14.0, CircuitSnapping.thresholdFor(3f), 1e-6)
        assertEquals("zoomed out, it grows to keep 22 screen px", 22.0 / 0.8, CircuitSnapping.thresholdFor(0.8f), 1e-6)
        assertEquals(
            "but a heavily zoomed-out page never reaches across the sheet",
            CircuitSnapping.MAX_SNAP_PT,
            CircuitSnapping.thresholdFor(0.1f),
            1e-6,
        )
        // A page that is not laid out yet must not produce an infinite or zero reach.
        assertEquals(CircuitSnapping.SNAP_THRESHOLD_PT, CircuitSnapping.thresholdFor(0f), 1e-6)
        assertEquals(CircuitSnapping.SNAP_THRESHOLD_PT, CircuitSnapping.thresholdFor(Float.NaN), 1e-6)
    }

    /** Dragging ink on a page with no circuit is a free move: there is no connector to snap to. */
    @Test
    fun snapTranslationLeavesAnOrdinaryStrokeAlone() {
        val stroke = com.mobixournal.format.model.Stroke(
            com.mobixournal.format.model.Tool.PEN,
            0xFF000000.toInt(),
            "round",
            listOf(
                com.mobixournal.format.model.StrokePoint(50.0, 50.0, 1.0),
                com.mobixournal.format.model.StrokePoint(200.0, 50.0, 1.0),
            ),
            true,
        )
        val page = com.mobixournal.format.model.Page(
            width = 612.0,
            height = 792.0,
            background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
            layers = listOf(com.mobixournal.format.model.Layer(listOf(stroke))),
        )
        val (dx, dy) = CircuitSnapping.snapTranslation(page, setOf(ElementRef(0, 0)), 3.0, 2.0)
        assertEquals(3.0, dx, 1e-6)
        assertEquals(2.0, dy, 1e-6)
    }

    @Test
    fun snapTranslationSnapsMovingCircuitPinToTarget() {
        val resistor1 = CircuitElement(
            symbol = "RESISTOR",
            positionX = 100.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p2", 50.0, 0.0)),
            id = "c1",
        )
        val resistor2 = CircuitElement(
            symbol = "RESISTOR",
            positionX = 200.0,
            positionY = 100.0,
            pins = listOf(CircuitPin("p1", -50.0, 0.0)),
            id = "c2",
        )
        val page = com.mobixournal.format.model.Page(
            width = 612.0,
            height = 792.0,
            background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
            layers = listOf(com.mobixournal.format.model.Layer(listOf(resistor1, resistor2))),
        )
        val refs = setOf(ElementRef(0, 1)) // resistor2

        // Resistor2 p1 is at (150, 100). Resistor1 p2 is at (150, 100).
        // Suppose user moves resistor2 by (-48.0, 2.0).
        // Resistor2 p1 candidate would be (150 - 48, 100 + 2) = (102, 102).
        // That is within 14 pt of resistor1 p2 (at (150, 100))? Wait, resistor1 p2 is at 150.
        // If user moves resistor2 by (-2.0, 2.0), candidate is (148, 102), dist to (150, 100) is ~2.8 pt < 14 pt.
        val (dx, dy) = CircuitSnapping.snapTranslation(page, refs, -2.0, 2.0)
        // Pin should snap to 150.0 -> target (150) - orig (150) = 0.0
        assertEquals(0.0, dx, 1e-6)
        assertEquals(0.0, dy, 1e-6)
    }

    @Test
    fun snapStrokeEndpointsSnapsLineEndpointsToPins() {
        val targets = listOf(
            CircuitSnapping.PinTarget("c1", "p1", 50.0, 50.0, null),
            CircuitSnapping.PinTarget("c2", "p2", 200.0, 50.0, null),
        )
        val stroke = listOf(
            com.mobixournal.format.model.StrokePoint(52.0, 51.0, 1.0),
            com.mobixournal.format.model.StrokePoint(198.0, 49.0, 1.0),
        )
        val snapped = CircuitSnapping.snapStrokeEndpoints(stroke, targets)
        assertEquals(50.0, snapped.first().x, 1e-6)
        assertEquals(50.0, snapped.first().y, 1e-6)
        assertEquals(200.0, snapped.last().x, 1e-6)
        assertEquals(50.0, snapped.last().y, 1e-6)
    }

    // --- placing a component: aiming at a wire that is already drawn --------------------------

    private fun wirePage(stroke: Stroke) = Page(
        width = 612.0,
        height = 792.0,
        background = Background.Solid(-1, "plain"),
        layers = listOf(Layer(listOf(stroke))),
    )

    private fun wire(x1: Double, y1: Double, x2: Double, y2: Double) = Stroke(
        Tool.PEN,
        0xFF000000.toInt(),
        "round",
        listOf(StrokePoint(x1, y1, 1.0), StrokePoint(x2, y2, 1.0)),
        true,
    )

    /**
     * The flow that used to fail: **the wire is drawn first, then the part is placed on its end.**
     * A pin could only ever be a target, never the seeker's own end, so the two never met — the magnet
     * quiet on a page whose only circuit was the one still under the stylus.
     */
    @Test
    fun aComponentBeingPlacedAimsAtAFinishedWiresEnd() {
        val page = wirePage(wire(50.0, 50.0, 200.0, 50.0))
        // The wire's ends are only offered to a component being placed, never to a wire.
        assertEquals(0, CircuitSnapping.collectAllTargets(page).size)
        val placement = CircuitSnapping.collectPlacementTargets(page)
        assertEquals(2, placement.size)
        assertTrue(placement.all { it.pinId == "wire-end" })

        val (x, y) = CircuitSnapping.snapPoint(
            page, 203.0, 52.0, snapToGrid = false, magnetic = true, scale = 1f, targets = placement,
        )
        assertEquals("the drag end lands on the wire's end", 200.0, x, 1e-6)
        assertEquals(50.0, y, 1e-6)

        // The line tool is unchanged: it aims at pins only, so it is not pulled onto a wire's end.
        val (wx, wy) = CircuitSnapping.snapPoint(page, 203.0, 52.0, snapToGrid = false, magnetic = true)
        assertEquals(203.0, wx, 1e-6)
        assertEquals(52.0, wy, 1e-6)
    }

    /**
     * Not every stroke end is a terminal: only a **wire** ([CircuitSnapping.isWire]) is a connector, so a
     * triangle's corner or a rectangle's edge never attracts the part being placed.
     */
    @Test
    fun onlyAWiresEndIsOfferedToAPlacedComponent() {
        val figure = Stroke(
            Tool.PEN,
            0xFF000000.toInt(),
            "round",
            listOf(
                StrokePoint(50.0, 50.0, 1.0),
                StrokePoint(150.0, 50.0, 1.0),
                StrokePoint(100.0, 120.0, 1.0),
                StrokePoint(50.0, 50.0, 1.0),
            ),
            true,
        )
        assertEquals(0, CircuitSnapping.collectPlacementTargets(wirePage(figure)).size)
    }

    /**
     * What the live preview shows is what the commit mints: the preview's pins come from the factory's
     * own placement rule, so a ring the user lined up against a wire end is a pin that exists after
     * lift-off — not an estimate near it.
     */
    @Test
    fun thePinsThePreviewShowsAreThePinsTheComponentGets() {
        val preview = CircuitSnapping.previewTargets(ShapeKind.RESISTOR, 100.0, 100.0, 200.0, 100.0)
        assertEquals(listOf("p1", "p2"), preview.map { it.pinId })
        assertEquals(100.0, preview[0].absX, 1e-6)
        assertEquals(200.0, preview[1].absX, 1e-6)

        val committed = CircuitElementFactory.circuitElementFor("RESISTOR", 100.0, 100.0, 200.0, 100.0)!!
        val committedPins = CircuitSnapping.collectTargets(listOf(committed))
        assertEquals(committedPins.map { it.pinId }, preview.map { it.pinId })
        for ((p, c) in preview.zip(committedPins)) {
            assertEquals(p.absX, c.absX, 1e-9)
            assertEquals(p.absY, c.absY, 1e-9)
        }

        // A drag shorter than a tap's threshold previews the default-size part a tap would drop,
        // centred on the finger: those are the pins a tap mints too.
        val tap = CircuitSnapping.previewTargets(ShapeKind.RESISTOR, 300.0, 300.0, 300.5, 300.0)
        assertEquals(300.0 - CircuitElementFactory.DEFAULT_LENGTH_PT / 2, tap[0].absX, 1e-6)
        assertEquals(300.0 + CircuitElementFactory.DEFAULT_LENGTH_PT / 2, tap[1].absX, 1e-6)
    }

    /**
     * The whole point, end to end: a resistor dragged onto the end of a wire that was drawn **before**
     * it keeps its pin exactly on that end once committed, so the joint is closed and not a hairline
     * short of one.
     */
    @Test
    fun aComponentPlacedOnAFinishedWireKeepsItsPinOnTheWire() {
        val page = wirePage(wire(50.0, 50.0, 200.0, 50.0))
        val targets = CircuitSnapping.collectPlacementTargets(page)
        val (endX, endY) = CircuitSnapping.snapPoint(
            page, 198.0, 53.0, snapToGrid = false, magnetic = true, scale = 1f, targets = targets,
        )
        val circuit = CircuitElementFactory.circuitElementFor("RESISTOR", 100.0, 50.0, endX, endY)!!
        val p2 = CircuitSnapping.pinAbsolutePosition(circuit, circuit.pins.last())
        assertEquals("the far pin sits on the wire's end", 200.0, p2.first, 1e-9)
        assertEquals(50.0, p2.second, 1e-9)
    }
}
