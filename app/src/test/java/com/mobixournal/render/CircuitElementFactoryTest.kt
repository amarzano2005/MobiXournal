package com.mobixournal.render

import com.mobixournal.format.mobix.MobixCircuitCodec
import com.mobixournal.format.model.CircuitElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CircuitElementFactoryTest {

    @Test
    fun placementInGivesExpectedSymbolAndPins() {
        val circuit = CircuitElementFactory.circuitElementFor(
            symbol = "RESISTOR",
            startX = 100.0,
            startY = 200.0,
            endX = 200.0,
            endY = 200.0,
            strokeColor = 0xFF123456.toInt(),
            strokeWidth = 2.0,
            value = "10 kΩ",
        )

        assertNotNull(circuit)
        assertEquals("RESISTOR", circuit!!.symbol)
        assertEquals(150.0, circuit.positionX, 1e-6)
        assertEquals(200.0, circuit.positionY, 1e-6)
        assertEquals(0.0, circuit.rotation, 1e-6)
        assertEquals(0xFF123456.toInt(), circuit.strokeColor)
        assertEquals(2.0, circuit.strokeWidth, 1e-6)
        assertEquals("10 kΩ", circuit.value)

        // 2 terminals: p1 at -50, p2 at +50
        assertEquals(2, circuit.pins.size)
        assertEquals("p1", circuit.pins[0].id)
        assertEquals(-50.0, circuit.pins[0].relX, 1e-6)
        assertEquals(0.0, circuit.pins[0].relY, 1e-6)
        assertEquals("p2", circuit.pins[1].id)
        assertEquals(50.0, circuit.pins[1].relX, 1e-6)
        assertEquals(0.0, circuit.pins[1].relY, 1e-6)
    }

    @Test
    fun dragDownwardsGives90DegreeRotationAndLocalPins() {
        val circuit = CircuitElementFactory.circuitElementFor(
            symbol = "CAPACITOR",
            startX = 50.0,
            startY = 100.0,
            endX = 50.0,
            endY = 200.0,
        )

        assertNotNull(circuit)
        assertEquals(50.0, circuit!!.positionX, 1e-6)
        assertEquals(150.0, circuit.positionY, 1e-6)
        assertEquals(90.0, circuit.rotation, 1e-6)
        assertEquals(2, circuit.pins.size)
        assertEquals(-50.0, circuit.pins[0].relX, 1e-6)
        assertEquals(50.0, circuit.pins[1].relX, 1e-6)
    }

    @Test
    fun tapInsertionGivesDefaultLengthAndUnrotatedPlacement() {
        val circuit = CircuitElementFactory.circuitElementFor(
            symbol = "INDUCTOR",
            startX = 120.0,
            startY = 80.0,
            endX = 121.0,
            endY = 80.5,
        )

        assertNotNull(circuit)
        assertEquals(120.0, circuit!!.positionX, 1e-6)
        assertEquals(80.0, circuit.positionY, 1e-6)
        assertEquals(0.0, circuit.rotation, 1e-6)
        assertEquals(2, circuit.pins.size)
        assertEquals(-24.0, circuit.pins[0].relX, 1e-6)
        assertEquals(24.0, circuit.pins[1].relX, 1e-6)
    }

    @Test
    fun gatesReceiveInputAndOutputPins() {
        val gate = CircuitElementFactory.circuitElementFor(
            symbol = "AND_GATE",
            startX = 0.0,
            startY = 0.0,
            endX = 100.0,
            endY = 0.0,
        )

        assertNotNull(gate)
        assertEquals(3, gate!!.pins.size)
        val in1 = gate.pins.first { it.id == "in1" }
        val in2 = gate.pins.first { it.id == "in2" }
        val out = gate.pins.first { it.id == "out" }
        assertEquals(-50.0, in1.relX, 1e-6)
        assertEquals(-50.0, in2.relX, 1e-6)
        assertEquals(50.0, out.relX, 1e-6)
        assertTrue(in1.relY < 0.0)
        assertTrue(in2.relY > 0.0)
        assertEquals(abs(in1.relY), abs(in2.relY), 1e-6)
    }

    /**
     * Ground is the library's single-terminal symbol: one pin, and it is the element's own anchor,
     * because the earth hangs *from* the terminal the wire meets. The drag is a heading, so a ground
     * drawn downwards is a ground rotated 90° that starts where the pen started — not a two-ended
     * part straddling a wire.
     */
    @Test
    fun groundIsASingleTerminalAnchoredWhereTheDragStarted() {
        val ground = CircuitElementFactory.circuitElementFor(
            symbol = "GROUND",
            startX = 50.0,
            startY = 100.0,
            endX = 50.0,
            endY = 160.0,
        )!!

        assertEquals(1, ground.pins.size)
        assertEquals(0.0, ground.pins[0].relX, 1e-6)
        assertEquals(0.0, ground.pins[0].relY, 1e-6)
        assertEquals(50.0, ground.positionX, 1e-6)
        assertEquals(100.0, ground.positionY, 1e-6)
        assertEquals(90.0, ground.rotation, 1e-6)

        // A tap is an unrotated symbol at the tap point.
        val tapped = CircuitElementFactory.circuitElementFor(
            symbol = "GROUND",
            startX = 10.0,
            startY = 20.0,
            endX = 11.0,
            endY = 20.5,
        )!!
        assertEquals(1, tapped.pins.size)
        assertEquals(10.0, tapped.positionX, 1e-6)
        assertEquals(20.0, tapped.positionY, 1e-6)
        assertEquals(0.0, tapped.rotation, 1e-6)
    }

    @Test
    fun unknownSymbolIsRefusedRatherThanDrawn() {
        val unknown = CircuitElementFactory.circuitElementFor(
            symbol = "FLUX_CAPACITOR_FUTURE_TECH",
            startX = 0.0,
            startY = 0.0,
            endX = 100.0,
            endY = 0.0,
        )
        assertNull("an unknown symbol is refused by the factory", unknown)
    }

    @Test
    fun elementSurvivesWriteAndReadThroughMobixCircuitCodec() {
        val original = CircuitElementFactory.circuitElementFor(
            symbol = "DIODE",
            startX = 300.0,
            startY = 150.0,
            endX = 400.0,
            endY = 150.0,
            strokeColor = 0xFFD84315.toInt(),
            strokeWidth = 1.6,
            value = "1N4148",
        )!!

        val json = MobixCircuitCodec.encode(original)
        val decoded = MobixCircuitCodec.decode(json)

        assertNotNull(decoded)
        assertEquals(original.symbol, decoded!!.symbol)
        assertEquals(original.positionX, decoded.positionX, 1e-6)
        assertEquals(original.positionY, decoded.positionY, 1e-6)
        assertEquals(original.rotation, decoded.rotation, 1e-6)
        assertEquals(original.strokeColor, decoded.strokeColor)
        assertEquals(original.strokeWidth, decoded.strokeWidth, 1e-6)
        assertEquals(original.value, decoded.value)
        assertEquals(original.pins.size, decoded.pins.size)
        for (i in original.pins.indices) {
            assertEquals(original.pins[i].id, decoded.pins[i].id)
            assertEquals(original.pins[i].relX, decoded.pins[i].relX, 1e-6)
            assertEquals(original.pins[i].relY, decoded.pins[i].relY, 1e-6)
            assertEquals(original.pins[i].connectedTo, decoded.pins[i].connectedTo)
        }
    }
}
