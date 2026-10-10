package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

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
    fun `diode draws a solid anode triangle the wire stops at`() {
        val pts = CircuitShapes.diode(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 20)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(100.0, pts.last().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue("diode has height span", pts.any { it.y < 0 } && pts.any { it.y > 0 })
        // The wire runs to the base and then up its edge — never across the triangle's interior.
        assertEquals(32.0, pts[1].x, 1e-6)
        assertEquals(32.0, pts[2].x, 1e-6)
        assertEquals(-16.0, pts[2].y, 1e-6)
        // The anode body is shaded solid, like the rail glyph, not left hollow.
        assertTrue("anode triangle is shaded", shadedRows(pts) > 10)
    }

    @Test
    fun `anode triangle walks its true outline so the slanted edge is not scalloped`() {
        val pts = CircuitShapes.diode(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        // The shaded rows alone would fringe the hypotenuse with the rows' round caps; the walk
        // closes with the true perimeter: apex -> top corner -> bottom corner -> apex.
        val bottomCorner = pts.indexOfFirst { abs(it.x - 32.0) < 1e-6 && abs(it.y - 16.0) < 1e-6 }
        assertTrue("the outline is traced", bottomCorner > 0 && bottomCorner < pts.size - 1)
        assertEquals("down the base from the top corner", -16.0, pts[bottomCorner - 1].y, 1e-6)
        assertEquals("back up the lower hypotenuse to the apex", 68.0, pts[bottomCorner + 1].x, 1e-6)
        assertEquals(0.0, pts[bottomCorner + 1].y, 1e-6)
    }

    @Test
    fun `led is a diode body plus two detached light rays`() {
        val strokes = CircuitShapes.led(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertEquals("body + one stroke per ray", 3, strokes.size)
        val body = strokes[0]
        assertEquals(0.0, body.first().x, 1e-6)
        assertEquals(100.0, body.last().x, 1e-6)
        assertTrue("body keeps the width", body.all { it.width == 2.0 })
        // Separate strokes are what keep the rays from trailing a wire across to each other and down
        // onto the body, so each one must stand on its own: tail on the hypotenuse, tip clear of it.
        for (ray in strokes.drop(1)) {
            assertEquals("tail, tip, barb, tip, barb", 5, ray.size)
            val tail = ray.first()
            assertTrue(
                "the tail sits on the body's top edge",
                abs(tail.y - anodeTopV(tail.x)) < 1e-6,
            )
            assertTrue("the tip points up and away from the body", ray[1].y < tail.y - 5.0 && ray[1].x > tail.x)
        }
        assertTrue("the two rays are spaced apart", strokes[2].first().x - strokes[1].first().x > 5.0)
    }

    @Test
    fun `zener diode bends its cathode bar into Z wings`() {
        val pts = CircuitShapes.zenerDiode(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertTrue(pts.size >= 20)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(100.0, pts.last().x, 1e-6)
        assertTrue(pts.all { it.width == 2.0 })
        assertTrue("anode triangle is shaded", shadedRows(pts) > 10)
        // The bar's top wing bends forward (towards the cathode), its bottom wing back.
        val bar = 100.0 * 0.68
        val h = minOf(16.0, 100.0 * 0.28)
        val bend = minOf(5.0, 100.0 * 0.08)
        assertTrue("top wing bends forward", pts.any { abs(it.y + h) < 1e-6 && abs(it.x - (bar + bend)) < 1e-6 })
        assertTrue("bottom wing bends back", pts.any { abs(it.y - h) < 1e-6 && abs(it.x - (bar - bend)) < 1e-6 })
    }

    @Test
    fun `opAmp floats its input signs clear of the pins`() {
        val strokes = CircuitShapes.opAmp(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        assertEquals("body + one stroke per input sign", 3, strokes.size)
        val (body, minus, plus) = strokes
        assertTrue(body.size >= 10)
        assertTrue(strokes.flatten().all { it.width == 2.0 })
        // Output pin reaches len
        assertTrue(body.any { abs(it.x - 100.0) < 1e-6 })
        // Inputs are situated at x = 0
        assertTrue(body.first().x < 1e-6)
        assertTrue(body.last().x < 1e-6)
        // Each sign is its own stroke inside the triangle, so no pin line runs on into the body.
        val back = 100.0 * 0.25
        assertTrue("the minus floats inside the body", minus.all { it.x > back + 1.0 })
        assertTrue("the plus floats inside the body", plus.all { it.x > back + 1.0 })
        assertTrue("minus marks the inverting input", minus.all { it.y < 0.0 })
        assertTrue("plus marks the non-inverting input", plus.all { it.y > 0.0 })
        assertEquals("the plus is an even cross", 5, plus.size)
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
    fun `dcSource produces two disconnected plates plus polarity marks`() {
        val strokes = CircuitShapes.dcSource(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        // Two plate strokes with their wires, then the +/- marks above them — each its own stroke so
        // no wire is drawn between them.
        assertEquals("dcSource decomposes into 4 strokes", 4, strokes.size)
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
        val led = CircuitShapes.led(10.0, 10.0, 10.0, 10.0, widthPt = 1.0)
        assertEquals(1, led.size)
        assertEquals(2, led[0].size)
        val opAmp = CircuitShapes.opAmp(10.0, 10.0, 10.0, 10.0, widthPt = 1.0)
        assertEquals(1, opAmp.size)
        assertEquals(2, opAmp[0].size)
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
        assertEquals(4, multiDc?.size)
        val multiLed = ShapeBuilder.buildMulti(ShapeKind.LED, 0.0, 0.0, 80.0, 0.0, 1.0)
        assertEquals(3, multiLed?.size)
        val multiOpAmp = ShapeBuilder.buildMulti(ShapeKind.OPAMP, 0.0, 0.0, 80.0, 0.0, 1.0)
        assertEquals(3, multiOpAmp?.size)
    }

    @Test
    fun `bjt envelope keeps enclosing the base bar however far the drag stretches`() {
        for (len in listOf(40.0, 100.0, 200.0, 400.0)) {
            val symbols = mapOf(
                "NPN" to CircuitShapes.bjtNpn(0.0, 0.0, len, 0.0, 2.0),
                "PNP" to CircuitShapes.bjtPnp(0.0, 0.0, len, 0.0, 2.0),
            )
            for ((name, pts) in symbols) {
                // The envelope is the only part reaching past the leads, so its topmost point pins
                // down both its centre and its radius.
                val top = pts.minBy { it.y }
                val centerX = top.x
                val radius = -top.y
                val barX = len * 0.42
                val barHalf = minOf(18.0, len * 0.30) * 0.75
                assertTrue(
                    "$name envelope encloses the base bar over a ${len}pt drag",
                    hypot(barX - centerX, barHalf) < radius,
                )
            }
        }
    }

    @Test
    fun `bjt emitter arrow points outwards for the NPN and inwards for the PNP`() {
        val npn = CircuitShapes.bjtNpn(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        val pnp = CircuitShapes.bjtPnp(0.0, 0.0, 100.0, 0.0, widthPt = 2.0)
        // Both symbols are one body: only the arrow's two barbs may differ between them.
        assertEquals(npn.size, pnp.size)
        val barbs = npn.indices.filter { npn[it] != pnp[it] }
        assertEquals("only the arrow head differs", 2, barbs.size)
        for (i in barbs) {
            assertTrue("the NPN's barbs sweep back towards its base", npn[i].x < pnp[i].x)
        }
    }

    /** The anode triangle's top edge (a 0→100pt drag): the v of the hypotenuse at [x]. */
    private fun anodeTopV(x: Double): Double {
        val base = 100.0 * 0.32
        val apex = 100.0 * 0.68
        return -minOf(16.0, 100.0 * 0.28) * (apex - x) / (apex - base)
    }

    /**
     * How many rungs the anode triangle's base carries. A hollow triangle has just its two corners;
     * a shaded one has a rung per scribbled row.
     */
    private fun shadedRows(pts: List<StrokePoint>): Int =
        pts.filter { abs(it.x - 100.0 * 0.32) < 1e-9 && abs(it.y) > 1e-9 }.map { it.y }.distinct().size
}
