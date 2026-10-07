package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CircuitShapesTest {

    @Test
    fun `resistor generates zigzag with constant width and correct endpoints`() {
        val pts = CircuitShapes.resistor(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size > 10)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(0.0, pts.first().y, 1e-6)
        assertEquals(100.0, pts.last().x, 1e-6)
        assertEquals(0.0, pts.last().y, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        // Check zigzags deviate from y = 0
        val yDeviations = pts.filter { abs(it.y) > 1.0 }
        assertTrue("resistor has alternating peaks", yDeviations.any { it.y > 0 } && yDeviations.any { it.y < 0 })
    }

    @Test
    fun `inductor generates smooth loops oriented along vector`() {
        val pts = CircuitShapes.inductor(0.0, 0.0, 100.0, 0.0, widthPt = 1.5)
        assertTrue(pts.size > 20)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(100.0, pts.last().x, 1e-6)
        assertTrue(pts.all { it.width == 1.5 })
        // Loops arch upwards/outwards
        assertTrue(pts.any { abs(it.y) > 5.0 })
    }

    @Test
    fun `ground generates decreasing parallel bars`() {
        val pts = CircuitShapes.ground(0.0, 0.0, 50.0, 0.0, widthPt = 1.0)
        assertTrue(pts.size >= 10)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertTrue(pts.all { it.width == 1.0 })
    }

    @Test
    fun `capacitor produces two disconnected strokes with an open dielectric gap`() {
        val strokes = CircuitShapes.capacitor(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertEquals("capacitor decomposes into 2 strokes", 2, strokes.size)
        val s1 = strokes[0]
        val s2 = strokes[1]
        // Left terminal starts at 0, right terminal ends at 100
        assertEquals(0.0, s1.first().x, 1e-6)
        assertEquals(100.0, s2.last().x, 1e-6)
        // Plates have height span
        assertTrue("plate 1 has span", s1.any { it.y < 0 } && s1.any { it.y > 0 })
        assertTrue("plate 2 has span", s2.any { it.y < 0 } && s2.any { it.y > 0 })
        // There is a clear gap between the two plates
        val maxX1 = s1.maxOf { it.x }
        val minX2 = s2.minOf { it.x }
        assertTrue("gap between plates > 5pt", minX2 - maxX1 >= 5.0)
    }

    @Test
    fun `and gate produces body with inputs and output`() {
        val pts = CircuitShapes.andGate(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 15)
        assertTrue(pts.all { it.width == 2.0 })
        // Output pin reaches the end
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 })
    }

    @Test
    fun `or gate produces curved body with inputs and output`() {
        val pts = CircuitShapes.orGate(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 15)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 })
    }

    @Test
    fun `not gate produces triangular body with inversion bubble`() {
        val pts = CircuitShapes.notGate(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 15)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(100.0, pts.last().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
    }

    @Test
    fun `nand gate produces AND body with inversion bubble`() {
        val pts = CircuitShapes.nandGate(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 20)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 })
    }

    @Test
    fun `nor gate produces OR body with inversion bubble`() {
        val pts = CircuitShapes.norGate(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 20)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 })
    }

    @Test
    fun `xor gate produces OR body with secondary back arch`() {
        val pts = CircuitShapes.xorGate(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 25)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 })
    }

    @Test
    fun `xnor gate produces XOR body with inversion bubble`() {
        val pts = CircuitShapes.xnorGate(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 30)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 })
    }

    @Test
    fun `diode generates triangle and cathode bar with correct terminals`() {
        val pts = CircuitShapes.diode(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 10)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(100.0, pts.last().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue("diode has height span", pts.any { it.y < 0 } && pts.any { it.y > 0 })
    }

    @Test
    fun `led generates diode body and radiation rays`() {
        val pts = CircuitShapes.led(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size > 20)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(100.0, pts.last().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue("led has rays deviating in negative y", pts.any { it.y < -15.0 })
    }

    @Test
    fun `zener diode generates cathode bar with bent wings`() {
        val pts = CircuitShapes.zenerDiode(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 12)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(100.0, pts.last().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue("zener wings present", pts.any { it.y < 0 } && pts.any { it.y > 0 })
    }

    @Test
    fun `opAmp produces triangle with plus minus and terminals`() {
        val pts = CircuitShapes.opAmp(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 15)
        assertTrue(pts.all { it.width == 2.0 })
        // Output pin reaches len
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 })
        // Inputs are situated at x = 0
        assertTrue(pts.first().x < 1e-6)
        assertTrue(pts.last().x < 1e-6)
    }

    @Test
    fun `bjtNpn produces base collector and emitter with outward arrow`() {
        val pts = CircuitShapes.bjtNpn(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 15)
        assertEquals(0.0, pts.first().x, 1e-6)
        // Collector and emitter reach x = 100
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 && it.y < 0 })
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 && it.y > 0 })
        assertTrue(pts.all { it.width == 2.0 })
    }

    @Test
    fun `bjtPnp produces base collector and emitter with inward arrow`() {
        val pts = CircuitShapes.bjtPnp(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 15)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 && it.y < 0 })
        assertTrue(pts.any { abs(it.x - 100.0) < 1e-6 && it.y > 0 })
        assertTrue(pts.all { it.width == 2.0 })
    }

    @Test
    fun `dcSource produces two disconnected strokes with open gap and correct plate sizes`() {
        val strokes = CircuitShapes.dcSource(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertEquals("dcSource decomposes into 2 strokes", 2, strokes.size)
        val s1 = strokes[0]
        val s2 = strokes[1]
        assertEquals(0.0, s1.first().x, 1e-6)
        assertEquals(100.0, s2.last().x, 1e-6)
        val s1Height = s1.maxOf { it.y } - s1.minOf { it.y }
        val s2Height = s2.maxOf { it.y } - s2.minOf { it.y }
        assertTrue("positive plate is taller than negative plate", s1Height > s2Height)
        val maxX1 = s1.maxOf { it.x }
        val minX2 = s2.minOf { it.x }
        assertTrue("gap between plates > 0", minX2 - maxX1 > 0)
    }

    @Test
    fun `currentSource produces circle with directional arrow and terminals`() {
        val pts = CircuitShapes.currentSource(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 20)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(100.0, pts.last().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
    }

    @Test
    fun `zero length drag falls back gracefully`() {
        val r = CircuitShapes.resistor(10.0, 10.0, 10.0, 10.0, widthPt = 1.0)
        assertEquals(2, r.size)
        val c = CircuitShapes.capacitor(10.0, 10.0, 10.0, 10.0, widthPt = 1.0)
        assertEquals(1, c.size)
        assertEquals(2, c[0].size)
        val d = CircuitShapes.diode(10.0, 10.0, 10.0, 10.0, widthPt = 1.0)
        assertEquals(2, d.size)
        val dc = CircuitShapes.dcSource(10.0, 10.0, 10.0, 10.0, widthPt = 1.0)
        assertEquals(1, dc.size)
        assertEquals(2, dc[0].size)
    }

    @Test
    fun `shapeBuilder delegating works for all circuit kinds`() {
        val kinds = listOf(
            ShapeKind.RESISTOR, ShapeKind.CAPACITOR, ShapeKind.INDUCTOR,
            ShapeKind.GROUND,
            ShapeKind.DIODE, ShapeKind.LED, ShapeKind.ZENER_DIODE,
            ShapeKind.OPAMP, ShapeKind.BJT_NPN, ShapeKind.BJT_PNP,
            ShapeKind.DC_SOURCE, ShapeKind.CURRENT_SOURCE,
            ShapeKind.AND_GATE, ShapeKind.OR_GATE, ShapeKind.NOT_GATE,
            ShapeKind.NAND_GATE, ShapeKind.NOR_GATE, ShapeKind.XOR_GATE, ShapeKind.XNOR_GATE,
        )
        for (kind in kinds) {
            val pts = ShapeBuilder.build(kind, 0.0, 0.0, 80.0, 0.0, 1.0)
            assertTrue("ShapeKind $kind produced points", pts.size >= 2)
        }
        val multiCap = ShapeBuilder.buildMulti(ShapeKind.CAPACITOR, 0.0, 0.0, 80.0, 0.0, 1.0)
        assertEquals(2, multiCap?.size)
        val multiDc = ShapeBuilder.buildMulti(ShapeKind.DC_SOURCE, 0.0, 0.0, 80.0, 0.0, 1.0)
        assertEquals(2, multiDc?.size)
    }
}
