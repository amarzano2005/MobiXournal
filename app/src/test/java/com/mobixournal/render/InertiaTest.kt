package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [Inertia] is the whole recognizer's measuring instrument: `det` is what decides "straight piece"
 * and "circle", so its two extremes are worth pinning down exactly rather than only through the
 * shapes that happen to be built on top of it.
 */
class InertiaTest {

    private fun straight(n: Int, fromX: Double, toX: Double, y: Double) =
        (0..n).map { i -> StrokePoint(fromX + (toX - fromX) * i / n, y, 1.0) }

    private fun circle(n: Int, cx: Double, cy: Double, r: Double) =
        (0..n).map { i ->
            val a = 2 * PI * i / n
            StrokePoint(cx + r * cos(a), cy + r * sin(a), 1.0)
        }

    @Test
    fun `a straight polyline has zero det and a circle has one`() {
        assertEquals(0.0, Inertia().also { it.calc(straight(50, 0.0, 200.0, 40.0)) }.det, 1e-12)
        assertTrue(
            "a well-sampled circle should read as maximally round",
            Inertia().also { it.calc(circle(720, 100.0, 100.0, 60.0)) }.det > 0.99,
        )
    }

    @Test
    fun `rad is the fitted radius for a circle`() {
        assertEquals(60.0, Inertia().also { it.calc(circle(720, 10.0, 20.0, 60.0)) }.rad, 1e-3)
    }

    @Test
    fun `amount is the total length walked`() {
        // A closed unit triangle-ish path: 3 + 4 + 5 = 12.
        val path = listOf(
            StrokePoint(0.0, 0.0, 1.0),
            StrokePoint(3.0, 0.0, 1.0),
            StrokePoint(3.0, 4.0, 1.0),
            StrokePoint(0.0, 0.0, 1.0),
        )
        assertEquals(12.0, Inertia().also { it.calc(path) }.amount, 1e-12)
    }

    @Test
    fun `moments are taken about each segment's start point`() {
        // Upstream's convention, and worth pinning down because it is not the polyline's geometric
        // centroid: `increase` arms its moment on p1, so a uniformly sampled run's centre sits half
        // a sample back from the middle. The recognizer is built on this starting point, so the
        // convention has to stay put even though the midpoint would look tidier.
        val inertia = Inertia().also { it.calc(straight(20, 10.0, 110.0, 7.0)) }
        assertEquals(100.0, inertia.amount, 1e-9)
        // Segment starts are 10, 15, …, 105 — their mean, not (10 + 110) / 2.
        assertEquals(57.5, inertia.centerX, 1e-9)
        assertEquals(7.0, inertia.centerY, 1e-9)
    }

    @Test
    fun `a negative increase takes a segment back out again`() {
        val path = straight(20, 0.0, 100.0, 0.0)
        val inertia = Inertia().also { it.calc(path) }
        val before = inertia.det
        // The fitter's grow-and-shrink loop relies on this being exactly reversible.
        inertia.increase(path[5], path[6], -1)
        inertia.increase(path[5], path[6], 1)
        assertEquals(before, inertia.det, 1e-12)
        assertEquals(100.0, inertia.amount, 1e-9)
    }

    @Test
    fun `copy is independent of the original`() {
        val original = Inertia().also { it.calc(straight(10, 0.0, 50.0, 0.0)) }
        val clone = original.copy()
        // Growing the copy must leave the source exactly as it was: the fitter grows candidate
        // pieces off a shared starting fit and would corrupt it otherwise.
        clone.increase(StrokePoint(0.0, 100.0, 1.0), StrokePoint(0.0, 200.0, 1.0), 1)
        assertEquals(50.0, original.amount, 1e-12)
        assertEquals(0.0, original.centerY, 1e-12)
        assertEquals(150.0, clone.amount, 1e-12)
        assertTrue(clone.centerY > 1.0)
    }
}
