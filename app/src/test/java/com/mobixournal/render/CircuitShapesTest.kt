package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.round

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
    fun `ground hangs three shrinking plates below the middle of the wire`() {
        val pts = CircuitShapes.ground(0.0, 0.0, 120.0, 0.0, widthPt = 1.0)
        assertEquals(0.0, pts.first().x, 1e-6)
        assertEquals(0.0, pts.first().y, 1e-6)
        assertEquals(120.0, pts.last().x, 1e-6)
        assertEquals(0.0, pts.last().y, 1e-6)
        assertTrue(pts.all { it.width == 1.0 })
        // The drag is the wire, so the symbol hangs *off* it: below the line (+v), never above it,
        // and never reaching past either end of the drag.
        assertTrue("the symbol hangs below the wire", pts.all { it.y >= -1e-9 })
        assertTrue("the symbol has depth", pts.any { it.y > 1.0 })
        assertTrue(pts.all { it.x >= -1e-9 && it.x <= 120.0 + 1e-9 })
        // Exactly three plates, each narrower than the one before, evenly spaced down the stem, the
        // widest one dropping from the wire's midpoint.
        val plates = pts.filter { it.y > 1e-9 }
            .groupBy { round(it.y * 1e6) / 1e6 }
            .toSortedMap()
        assertEquals("three plates", 3, plates.size)
        val widths = plates.values.map { pl -> pl.maxOf { it.x } - pl.minOf { it.x } }
        assertTrue("each plate is narrower than the one above it", widths[0] > widths[1] && widths[1] > widths[2])
        val step = plates.keys.toList()
        assertEquals("the plates are evenly spaced", step[1] - step[0], step[2] - step[1], 1e-6)
        // Plates closer together than the shortest one is wide would read as one blob.
        assertTrue("the plates do not crowd into each other", step[1] - step[0] < widths[2])
        assertEquals(
            "the stem drops from the wire's midpoint",
            60.0,
            (plates.values.first().minOf { it.x } + plates.values.first().maxOf { it.x }) / 2.0,
            1e-6,
        )
    }

    @Test
    fun `closed switch draws no wire between its two contacts`() {
        val strokes = CircuitShapes.switchClosed(0.0, 0.0, 120.0, 0.0, widthPt = 2.0)
        // The blade plus one stroke per contact tick: a single polyline through both ticks would have
        // to jump from the first to the second, and that jump is a wire drawn across the closed span.
        assertEquals(3, strokes.size)
        val (blade, near, far) = strokes
        assertEquals("the blade is one straight segment along the leads", 2, blade.size)
        assertEquals(0.0, blade[0].x, 1e-6)
        assertEquals(120.0, blade[1].x, 1e-6)
        assertTrue("the blade lies on the leads' own line", blade.all { abs(it.y) < 1e-9 })
        for (tick in listOf(near, far)) {
            assertEquals("a tick is a single bar", 2, tick.size)
            assertEquals("a tick crosses the leads", tick[0].x, tick[1].x, 1e-9)
            assertTrue("a tick sits on both sides of the leads", tick[0].y < 0 && tick[1].y > 0)
        }
        assertTrue("the ticks are at two different terminals", near[0].x < far[0].x)
    }

    @Test
    fun `transformer draws one stroke per part with no stray wire down the core`() {
        val strokes = CircuitShapes.transformer(0.0, 0.0, 120.0, 0.0, widthPt = 2.0)
        // Two coils and two core bars. The core used to be a single polyline through both bars, and
        // the segment joining them drew a diagonal straight down the middle of the symbol.
        assertEquals("a coil each side of the core, plus its two bars", 4, strokes.size)
        val (primary, secondary, topBar, bottomBar) = strokes
        for (bar in listOf(topBar, bottomBar)) {
            assertEquals("a core bar is one straight segment", 2, bar.size)
            assertEquals("the core bar runs along the coils", bar[0].y, bar[1].y, 1e-9)
        }
        assertTrue("the bars straddle the axis", topBar[0].y < 0 && bottomBar[0].y > 0)
        assertTrue("the coils sit on opposite sides of the core", primary.first().y < secondary.first().y)
        // Both windings hump *towards* the core, which is what reads as magnetic coupling.
        assertTrue(
            "the primary's humps face the core",
            primary.any { it.y > primary.first().y + 1.0 },
        )
        assertTrue(
            "the secondary's humps face the core",
            secondary.any { it.y < secondary.first().y - 1.0 },
        )
    }

    @Test
    fun `buffer gate draws nothing across its own interior`() {
        val len = 120.0
        val h = minOf(16.0, len * 0.26)
        val back = len * 0.28
        val tip = len * 0.72
        val pts = CircuitShapes.bufferGate(0.0, 0.0, len, 0.0, widthPt = 2.0)
        // Every segment must lie on an edge (or on the pins, which sit outside the body). One that
        // crosses the middle — the old trace reached the output straight from the back edge — puts
        // its own midpoint strictly inside the triangle.
        fun inside(x: Double, y: Double): Boolean {
            if (x <= back + 1e-9 || x >= tip - 1e-9) return false
            val halfHeight = h * (tip - x) / (tip - back)
            return abs(y) < halfHeight - 1e-9
        }
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            assertTrue(
                "segment ${i} crosses the body: ${a.x},${a.y} -> ${b.x},${b.y}",
                !inside((a.x + b.x) / 2, (a.y + b.y) / 2),
            )
        }
        assertTrue("the output leaves from the tip", pts.any { abs(it.x - len) < 1e-9 })
    }

    @Test
    fun `junction is a solid dot, not a ring around a hole`() {
        val width = 2.0
        val r = 5.0
        val pts = CircuitShapes.junction(0.0, 0.0, 120.0, 0.0, widthPt = width)
        assertTrue(pts.all { it.width == width })
        // The dot sits at the drag's start, sized only by the drag's length.
        assertTrue(
            "the dot is centred on the drag's start",
            pts.all { hypot(it.x, it.y) <= r + 1e-9 },
        )
        // A `.xopp` stroke cannot be filled, so "solid" can only mean the pen's own path paints every
        // part of the disc: sample its centre line and require a stroke segment within half a pen
        // width of each sample. A bare ring leaves the middle — and everything else off its
        // circumference — unpainted, which is exactly what the dot must not do.
        for (k in -9..9) {
            val y = r * k / 10.0
            val painted = (0 until pts.size - 1).any { i ->
                val a = pts[i]
                val b = pts[i + 1]
                val (cx, cy) = DrawingGuide.closestOnSegment(0.0, y, a.x, a.y, b.x, b.y)
                hypot(cx, cy - y) <= width / 2 + 1e-9
            }
            assertTrue("nothing paints the dot's centre line at y=$y", painted)
        }
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
