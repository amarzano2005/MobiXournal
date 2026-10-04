package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ported recognizer's contract. The wobble helper perturbs every sample deterministically, so
 * these are hand-drawn strokes rather than the exact geometry the recognizer emits — which is the
 * only thing worth testing, since a recogniser that needs perfect input is useless on a tablet.
 *
 * The recognised set is upstream's: line, triangle, rectangle, circle. An oval, an arrow and a plain
 * polyline are *not* recognised, on purpose (desktop Xournal++ doesn't either).
 */
class ShapeRecognizerTest {

    private val w = 1.5

    /** A repeatable pseudo-random jitter, so "hand-drawn" input stays the same run to run. */
    private fun wobble(i: Int, amount: Double) = amount * sin(i * 12.9898)

    private fun pts(vararg xy: Double): List<StrokePoint> =
        xy.toList().chunked(2).map { StrokePoint(it[0], it[1], w) }

    private fun sample(n: Int, jitter: Double = 0.0, f: (Double) -> Pair<Double, Double>) =
        (0..n).map { i ->
            val (x, y) = f(i.toDouble() / n)
            StrokePoint(x + wobble(i, jitter), y + wobble(i * 7, jitter), w)
        }

    /** A closed polygon walked from corner to corner, [perEdge] samples along each side. */
    private fun loop(corners: List<Pair<Double, Double>>, perEdge: Int, jitter: Double = 0.0): List<StrokePoint> {
        val out = ArrayList<StrokePoint>()
        for (c in corners.indices) {
            val (x0, y0) = corners[c]
            val (x1, y1) = corners[(c + 1) % corners.size]
            for (i in 0 until perEdge) {
                val t = i.toDouble() / perEdge
                val n = out.size + i
                out += StrokePoint(
                    x0 + (x1 - x0) * t + wobble(n, jitter),
                    y0 + (y1 - y0) * t + wobble(n * 7, jitter),
                    w,
                )
            }
        }
        out += StrokePoint(corners[0].first, corners[0].second, w)
        return out
    }

    /** An open path walked corner to corner, [perEdge] samples along each leg. */
    private fun openPolyline(corners: List<Pair<Double, Double>>, perEdge: Int): List<StrokePoint> =
        corners.zipWithNext().flatMap { (a, b) ->
            (0 until perEdge).map { i ->
                val t = i.toDouble() / perEdge
                StrokePoint(
                    a.first + (b.first - a.first) * t,
                    a.second + (b.second - a.second) * t,
                    w,
                )
            }
        } + StrokePoint(corners.last().first, corners.last().second, w)

    @Test
    fun `a wobbly line becomes a two-point axis-aligned line`() {
        val out = ShapeRecognizer.recognize(sample(60, jitter = 1.0) { t -> 20.0 + 220 * t to 60.0 + 6 * t }, w)
        assertNotNull(out)
        assertEquals(2, out!!.size)
        assertEquals("a near-horizontal fitting is flattened", out[0].y, out[1].y, 1e-9)
        assertEquals(20.0, minOf(out[0].x, out[1].x), 6.0)
        assertEquals(240.0, maxOf(out[0].x, out[1].x), 6.0)
    }

    @Test
    fun `a diagonal line keeps its own direction`() {
        val out = ShapeRecognizer.recognize(sample(60, jitter = 0.6) { t -> 40.0 + 160 * t to 40.0 + 160 * t }, w)
        assertNotNull(out)
        assertEquals(2, out!!.size)
        // Roughly 45°, so it must not have been flattened onto an axis.
        val slope = (out[1].y - out[0].y) / (out[1].x - out[0].x)
        assertEquals(1.0, slope, 0.05)
    }

    @Test
    fun `a wobbly circle is rebuilt as a circle of the fitted radius`() {
        val out = ShapeRecognizer.recognize(
            sample(144, jitter = 1.2) { t -> 200 + 90 * cos(2 * PI * t) to 200 + 90 * sin(2 * PI * t) },
            w,
        )
        assertNotNull(out)
        // Closed: the last vertex is the first one again (upstream re-evaluates cos 2π rather than
        // copying, so the two agree to a few ULP, not bit for bit).
        assertEquals(out!!.first().x, out.last().x, 1e-9)
        assertEquals(out.first().y, out.last().y, 1e-9)
        val cx = out.sumOf { it.x } / out.size
        val cy = out.sumOf { it.y } / out.size
        assertTrue(
            "every vertex sits on the fitted circle",
            out.all { abs(hypot(it.x - cx, it.y - cy) - 90.0) < 1.0 },
        )
        // Upstream samples a circle at 2·radius points, so a big one is drawn smoothly.
        assertTrue("expected a densely sampled circle, got ${out.size}", out.size > 90)
    }

    @Test
    fun `a wobbly rectangle snaps to four clean square corners`() {
        val out = ShapeRecognizer.recognize(
            loop(
                listOf(10.0 to 10.0, 260.0 to 10.0, 260.0 to 150.0, 10.0 to 150.0),
                perEdge = 30,
                jitter = 1.0,
            ),
            w,
        )
        assertNotNull(out)
        assertEquals(5, out!!.size) // four corners plus the closing repeat
        assertEquals(out.first().x, out.last().x, 1e-9)
        assertEquals(out.first().y, out.last().y, 1e-9)
        // Every corner is at a bounding-box corner -> the emitted sides are truly square.
        assertTrue(out.all { abs(it.x - 10.0) < 6.0 || abs(it.x - 260.0) < 6.0 })
        assertTrue(out.all { abs(it.y - 10.0) < 6.0 || abs(it.y - 150.0) < 6.0 })
        // Square, not merely close: the corners collapse to exactly two columns and two rows. (The
        // tolerance is for the trig noise in sin/cos at multiples of π/2, ~1e-14.)
        assertEquals("two corner columns", 2, distinctCount(out.map { it.x }, 1e-6))
        assertEquals("two corner rows", 2, distinctCount(out.map { it.y }, 1e-6))
    }

    /** How many distinct values a list holds, to within [eps] — a tolerance-aware `distinct`. */
    private fun distinctCount(values: List<Double>, eps: Double): Int =
        values.fold(emptyList<Double>()) { acc, v ->
            if (acc.any { abs(it - v) < eps }) acc else acc + v
        }.size

    @Test
    fun `a closed triangle is rebuilt from its three sides`() {
        val out = ShapeRecognizer.recognize(
            loop(listOf(0.0 to 0.0, 260.0 to 0.0, 130.0 to 220.0), perEdge = 40, jitter = 0.8),
            w,
        )
        assertNotNull(out)
        assertEquals("three corners plus the closing repeat", 4, out!!.size)
        assertEquals(out.first(), out.last())
        // The three drawn corners are still there, each within a few pt of where it was drawn.
        val drawn = listOf(0.0 to 0.0, 260.0 to 0.0, 130.0 to 220.0)
        for ((x, y) in drawn) {
            assertTrue(
                "no emitted corner near ($x, $y)",
                out.any { hypot(it.x - x, it.y - y) < 12.0 },
            )
        }
    }

    @Test
    fun `every emitted vertex carries the requested width`() {
        val out = ShapeRecognizer.recognize(sample(40, jitter = 0.4) { t -> 0.0 + 180 * t to 0.0 }, w)
        assertNotNull(out)
        assertTrue(out!!.all { it.width == w })
    }

    @Test
    fun `handwriting is left alone`() {
        // A many-cornered squiggle matches nothing; the caller must keep the freehand stroke.
        val squiggle = sample(160) { t -> 20 + 300 * t to 60 + 25 * sin(18 * PI * t) }
        assertNull(ShapeRecognizer.recognize(squiggle, w))
    }

    @Test
    fun `an oval is not turned into a circle`() {
        // Recognising ellipses was the old hand-rolled recognizer's job; upstream does circles only,
        // and a 5:1 oval is nowhere near round enough.
        val oval = sample(200, jitter = 0.5) { t -> 250 + 200 * cos(2 * PI * t) to 250 + 40 * sin(2 * PI * t) }
        assertNull(ShapeRecognizer.recognize(oval, w))
    }

    @Test
    fun `an open zig-zag is left alone, not straightened or closed`() {
        val zigZag = openPolyline(
            listOf(0.0 to 0.0, 100.0 to 0.0, 100.0 to 100.0, 200.0 to 100.0),
            perEdge = 30,
        )
        assertNull(ShapeRecognizer.recognize(zigZag, w))
    }

    @Test
    fun `a stroke too small to be a shape is never snapped`() {
        // Under upstream's 40 pt minimum-size budget: a tick, not a shape.
        val tick = loop(listOf(0.0 to 0.0, 25.0 to 0.0, 25.0 to 25.0, 0.0 to 25.0), perEdge = 12)
        assertNull(ShapeRecognizer.recognize(tick, w))
    }

    @Test
    fun `a two-point stroke is not a shape`() {
        assertNull(ShapeRecognizer.recognize(pts(0.0, 0.0, 200.0, 200.0), w))
    }
}
