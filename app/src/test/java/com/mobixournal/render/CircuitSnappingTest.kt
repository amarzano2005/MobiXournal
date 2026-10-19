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
}
