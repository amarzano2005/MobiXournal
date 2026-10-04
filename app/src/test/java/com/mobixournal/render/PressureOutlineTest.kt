package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The pure [pressureOutlinePoints] geometry behind the pen's variable-width fill. The outline must
 * span the stroke's **full** point-to-point width (not one mean width), taper along the stroke, and
 * cap both ends — the whole point of replacing the old mean-width path.
 */
class PressureOutlineTest {

    private fun point(x: Double, y: Double, w: Double) = StrokePoint(x, y, w)

    /** The X coordinates of an outline's vertices. */
    private fun xs(o: FloatArray): List<Float> = (o.indices step 2).map { o[it] }

    /** The Y coordinates of an outline's vertices. */
    private fun ys(o: FloatArray): List<Float> = (1 until o.size step 2).map { o[it] }

    @Test
    fun `fewer than two points yields no outline`() {
        assertEquals(0, pressureOutlinePoints(emptyList(), 1f, 0f, 0f).size)
        assertEquals(0, pressureOutlinePoints(listOf(point(1.0, 1.0, 2.0)), 1f, 0f, 0f).size)
    }

    @Test
    fun `a constant-width stroke makes a stadium as wide as the stroke, capped at both ends`() {
        val o = pressureOutlinePoints(
            listOf(point(0.0, 0.0, 4.0), point(10.0, 0.0, 4.0)), 1f, 0f, 0f,
        )
        // Half-width 2, so the band runs y=-2..2, and each round cap adds half a width (2) past the end.
        assertEquals(-2f, xs(o).minOrNull()!!, 0.01f)
        assertEquals(12f, xs(o).maxOrNull()!!, 0.01f)
        assertEquals(-2f, ys(o).minOrNull()!!, 0.01f)
        assertEquals(2f, ys(o).maxOrNull()!!, 0.01f)
    }

    @Test
    fun `the width tapers point to point instead of taking a mean`() {
        val o = pressureOutlinePoints(
            listOf(point(0.0, 0.0, 2.0), point(10.0, 0.0, 8.0)), 1f, 0f, 0f,
        )
        val thin = o.indices.step(2).filter { o[it] < 1f }.map { o[it + 1] }
        val fat = o.indices.step(2).filter { o[it] > 9f }.map { o[it + 1] }
        // Near the thin end the outline stays within ±1; near the fat end it reaches about ±4.
        assertTrue(thin.isNotEmpty())
        assertTrue(fat.isNotEmpty())
        assertTrue(thin.all { abs(it) <= 1.01f })
        assertTrue(fat.any { abs(it) >= 3.9f })
        assertTrue(ys(o).all { it.isFinite() })
    }

    @Test
    fun `repeated points do not produce NaN or infinity`() {
        val o = pressureOutlinePoints(
            listOf(point(3.0, 3.0, 2.0), point(3.0, 3.0, 2.0), point(6.0, 3.0, 2.0)), 1f, 0f, 0f,
        )
        assertTrue(o.isNotEmpty())
        assertTrue(o.all { it.isFinite() })
    }
}
