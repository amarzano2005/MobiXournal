package com.mobixournal.render

import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        val (sx, sy) = CircuitSnapping.snapPoint(page, 152.0, 102.0, snapToGrid = false)
        assertEquals(150.0, sx, 1e-6)
        assertEquals(100.0, sy, 1e-6)
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
}
