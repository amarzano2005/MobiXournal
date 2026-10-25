package com.mobixournal.desktop

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's ink smoothing: a **freehand** stroke as a tangent-continuous curve, a **figure** exactly the
 * shape its corners make.
 *
 * The rule is pure arithmetic, so it is checked without a canvas: each curve ends at the midpoint of two
 * samples with the sample between them as its control point, the chain still starts on the first sample and
 * ends on the last, a gentle arc comes out shorter than the faceted one it replaces — and a corner (a
 * rectangle, a triangle, an arrow's head) is kept where the geometry put it, which is the difference
 * between a rectangle and a rounded blob. The page's samples are untouched either way, so this is a
 * painter's decision and not a format one.
 */
class DesktopInkSmoothingTest {

    private fun points(vararg xy: Double): List<StrokePoint> =
        xy.toList().chunked(2).map { (x, y) -> StrokePoint(x, y, 1.0) }

    /** The length of the drawn line: one quadratic per curve, sampled finely. */
    private fun length(points: List<StrokePoint>): Double {
        var total = 0.0
        var fromX = points.first().x
        var fromY = points.first().y
        for (curve in smoothedInk(points)) {
            var lastX = fromX
            var lastY = fromY
            for (step in 1..64) {
                val t = step / 64.0
                val u = 1 - t
                val x = u * u * fromX + 2 * u * t * curve.controlX + t * t * curve.toX
                val y = u * u * fromY + 2 * u * t * curve.controlY + t * t * curve.toY
                total += hypot(x - lastX, y - lastY)
                lastX = x
                lastY = y
            }
            fromX = curve.toX
            fromY = curve.toY
        }
        return total
    }

    /** The length of the faceted line: what `lineTo` drew. */
    private fun faceted(points: List<StrokePoint>): Double =
        (1 until points.size).sumOf { hypot(points[it].x - points[it - 1].x, points[it].y - points[it - 1].y) }

    @Test
    fun `nothing to smooth below two samples`() {
        assertEquals("a press is not a stroke", emptyList<InkCurve>(), smoothedInk(emptyList()))
        assertEquals(emptyList<InkCurve>(), smoothedInk(points(10.0, 10.0)))
    }

    @Test
    fun `two samples are one straight run to the tip`() {
        val line = points(0.0, 0.0, 100.0, 0.0)
        val curves = smoothedInk(line)
        assertEquals("one segment", 1, curves.size)
        assertEquals("ending on the sample the pen lifted at", 100.0, curves.single().toX, 1e-9)
    }

    @Test
    fun `a gentle arc is one smoothed run through its midpoints`() {
        val arc = (0..36).map { step ->
            val angle = step * 5.0 * PI / 180.0
            StrokePoint(100 + 100 * cos(angle), 100 + 100 * sin(angle), 1.0)
        }
        val curves = smoothedInk(arc)
        assertEquals("one curve per interior sample: no corner splits an arc", arc.size - 2, curves.size)
        assertTrue("the curves bend: the control point is not their end", curves[0].controlX != curves[0].toX)
        assertEquals(
            "the first curve runs to the midpoint, as the rule says",
            (arc[1].x + arc[2].x) / 2,
            curves.first().toX,
            1e-9,
        )
        assertEquals("ending on the last sample", arc.last().x, curves.last().toX, 1e-9)
        assertEquals(arc.last().y, curves.last().toY, 1e-9)

        val smooth = length(arc)
        val drawn = faceted(arc)
        assertTrue("the smoothed arc is shorter than the faceted one ($smooth vs $drawn)", smooth < drawn)
        assertTrue("but only just: it still follows the hand ($smooth vs $drawn)", smooth > drawn * 0.99)
    }

    @Test
    fun `a rectangle stays a rectangle`() {
        val rectangle = points(0.0, 0.0, 100.0, 0.0, 100.0, 60.0, 0.0, 60.0, 0.0, 0.0)
        val curves = smoothedInk(rectangle)
        assertEquals("one straight side per corner", 4, curves.size)
        for ((i, curve) in curves.withIndex()) {
            val corner = rectangle[i + 1]
            assertEquals("side ${i + 1} ends on its corner, x", corner.x, curve.toX, 1e-9)
            assertEquals("side ${i + 1} ends on its corner, y", corner.y, curve.toY, 1e-9)
            assertEquals("and is straight, not rounded (control x)", corner.x, curve.controlX, 1e-9)
            assertEquals("and is straight, not rounded (control y)", corner.y, curve.controlY, 1e-9)
        }
        assertEquals("so it is drawn exactly as long as it is", faceted(rectangle), length(rectangle), 1e-6)
    }

    @Test
    fun `a sharp turn keeps its vertex while the run into it is smoothed`() {
        // Freehand wobble, then an arrow's head: the samples before the turn curve, the turn itself does not.
        val stroke = points(0.0, 0.0, 10.0, 1.0, 20.0, 0.0, 30.0, 1.0, 40.0, 0.0, 40.0, 20.0)
        val curves = smoothedInk(stroke)
        val head = curves.last()
        assertEquals("the head's point is a vertex", 40.0, head.toX, 1e-9)
        assertEquals(20.0, head.toY, 1e-9)
        assertEquals("reached straight from the turn", 40.0, head.controlX, 1e-9)
        assertTrue(
            "while the wobbling run before it is curved",
            curves.first().controlX != curves.first().toX,
        )
    }
}
