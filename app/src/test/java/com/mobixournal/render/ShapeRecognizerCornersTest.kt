package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The half of the recogniser that decides *which* polygon the ink is: [PolygonCorners]' corner
 * fallback and the agreement test that stops a fit from closing one side short.
 *
 * The failure these exist for is not hypothetical — a squat rectangle drawn in one stroke was fitted
 * as three sides, the fourth became a chord, and the user watched their rectangle snap into a wrong
 * triangle (or into nothing at all). Each test below is one shape a person actually draws.
 */
class ShapeRecognizerCornersTest {

    private val w = 1.5

    private fun jitter(i: Int, amount: Double) = amount * sin(i * 12.9898)

    /** A closed ring walked corner to corner, its sides bowed like a hand's, [perEdge] samples each. */
    private fun ring(
        corners: List<Pair<Double, Double>>,
        perEdge: Int,
        wobble: Double = 1.0,
        bow: Double = 4.0,
    ): List<StrokePoint> {
        val out = ArrayList<StrokePoint>()
        for (c in corners.indices) {
            val (x0, y0) = corners[c]
            val (x1, y1) = corners[(c + 1) % corners.size]
            val nx = -(y1 - y0)
            val ny = x1 - x0
            val length = hypot(nx, ny)
            for (i in 0 until perEdge) {
                val t = i.toDouble() / perEdge
                val n = out.size + i
                val b = bow * sin(PI * t)
                out += StrokePoint(
                    x0 + (x1 - x0) * t + jitter(n, wobble) + nx / length * b,
                    y0 + (y1 - y0) * t + jitter(n * 7, wobble) + ny / length * b,
                    w,
                )
            }
        }
        out += StrokePoint(corners[0].first, corners[0].second, w)
        return out
    }

    /** [points] rotated so the stroke starts at index [at] — a pen that began mid-side. */
    private fun startingAt(points: List<StrokePoint>, at: Int): List<StrokePoint> {
        val body = points.dropLast(1)
        return List(body.size) { body[(at + it) % body.size] } + body[at]
    }

    @Test
    fun `a squat rectangle is a rectangle, not a triangle one corner short`() {
        for (height in listOf(60.0, 80.0, 100.0, 140.0)) {
            val stroke = ring(
                listOf(60.0 to 60.0, 360.0 to 60.0, 360.0 to (60.0 + height), 60.0 to (60.0 + height)),
                perEdge = 30,
            )
            val figure = ShapeRecognizer.classify(stroke, w)
            assertNotNull("300x$height must be recognised", figure)
            assertEquals(
                "300x$height is a four-sided figure",
                FigureKind.RECTANGLE,
                figure!!.kind,
            )
            assertEquals("four corners and the closing repeat", 5, figure.points.size)
        }
    }

    @Test
    fun `a rectangle drawn with only a few samples a side is still a rectangle`() {
        val stroke = ring(
            listOf(100.0 to 100.0, 260.0 to 100.0, 260.0 to 260.0, 100.0 to 260.0),
            perEdge = 3,
            wobble = 0.0,
            bow = 0.0,
        )
        val figure = ShapeRecognizer.classify(stroke, w)
        assertNotNull(figure)
        assertEquals(FigureKind.RECTANGLE, figure!!.kind)
    }

    @Test
    fun `a triangle begun mid-side closes on its three real corners`() {
        val drawn = ring(listOf(60.0 to 60.0, 320.0 to 60.0, 190.0 to 280.0), perEdge = 35)
        // Start the stroke part-way along the first side, which is where a hand often does: the start
        // point is then on a straight line, not a corner, and must not become one.
        val figure = ShapeRecognizer.classify(startingAt(drawn, 12), w)
        assertNotNull(figure)
        assertEquals(FigureKind.TRIANGLE, figure!!.kind)
        assertEquals("three corners plus the closing repeat", 4, figure.points.size)
    }

    @Test
    fun `a corner detector reads the four corners of a closed ring`() {
        val stroke = ring(
            listOf(60.0 to 60.0, 360.0 to 60.0, 360.0 to 180.0, 60.0 to 180.0),
            perEdge = 25,
        )
        val rings = PolygonCorners.rings(StrokeSmoothing.smooth(stroke))
        assertEquals("one ring", 1, rings.size)
        assertEquals("four sides", 4, rings.single().sides)
    }

    @Test
    fun `an open stroke has no ring`() {
        // Three sides of a rectangle, never closed: there is no ring to cut.
        val open = (0..60).map { i ->
            val t = i / 60.0
            when {
                t < 0.34 -> StrokePoint(60.0 + 300.0 * t, 60.0, w)
                t < 0.67 -> StrokePoint(360.0, 60.0 + 240.0 * (t - 0.34), w)
                else -> StrokePoint(360.0 - 300.0 * (t - 0.67), 300.0, w)
            }
        }
        assertTrue(PolygonCorners.rings(open).isEmpty())
    }

    @Test
    fun `a circle is never handed to the polygon classes`() {
        val circle = (0..80).map { i ->
            val a = 2 * PI * i / 80
            StrokePoint(220 + 100 * kotlin.math.cos(a), 220 + 100 * kotlin.math.sin(a), w)
        }
        assertTrue("a circle has no corners to cut", PolygonCorners.rings(circle).isEmpty())
        assertEquals(FigureKind.CIRCLE, ShapeRecognizer.classify(circle, w)?.kind)
    }
}
