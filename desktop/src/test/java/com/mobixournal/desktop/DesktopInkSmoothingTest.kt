package com.mobixournal.desktop

import com.mobixournal.format.model.StrokePoint
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's ink smoothing: the samples of a stroke as a **tangent-continuous** curve.
 *
 * The rule is pure arithmetic, so it is checked without a canvas: each curve ends at the midpoint of two
 * samples, its control point is the sample between them, the chain still starts on the first sample and
 * ends on the last, and the line it draws is shorter than the faceted one across a corner — which is the
 * whole claim. The page's samples are untouched, so this is a painter's decision and not a format one.
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
    fun `every interior sample is a control point, not a corner`() {
        val zigzag = points(0.0, 0.0, 10.0, 0.0, 10.0, 10.0, 0.0, 10.0)
        val curves = smoothedInk(zigzag)
        assertEquals("one curve per gap between samples", zigzag.size - 1, curves.size)

        // The curve through the corner's *neighbour* has the corner itself as its control point, and ends
        // half way to the next sample — so the drawn line never turns on the corner.
        assertEquals(10.0, curves[0].controlX, 1e-9)
        assertEquals(0.0, curves[0].controlY, 1e-9)
        assertEquals(10.0, curves[0].toX, 1e-9)
        assertEquals(5.0, curves[0].toY, 1e-9)

        assertEquals("the chain starts where the pen touched down", 0.0, zigzag.first().x, 1e-9)
        assertEquals("and ends on the last sample", 0.0, curves.last().toX, 1e-9)
        assertEquals(10.0, curves.last().toY, 1e-9)
    }

    @Test
    fun `the drawn line cuts the corner the faceted one turned on`() {
        val corner = points(0.0, 0.0, 100.0, 0.0, 100.0, 100.0)
        val faceted = 100.0 + 100.0
        val smooth = length(corner)
        assertTrue("a smoothed corner is shorter than two legs ($smooth vs $faceted)", smooth < faceted - 1.0)
        assertTrue("but it still goes the long way round ($smooth)", smooth > faceted * 0.75)
    }
}
