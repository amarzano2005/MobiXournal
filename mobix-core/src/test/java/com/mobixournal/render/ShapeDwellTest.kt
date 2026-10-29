package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dwell rule, on its own: **when** a hand counts as resting, and **what** a resting stroke is
 * offered to the recogniser.
 *
 * Both shells hang a timer off this, so the rule is pinned here rather than in either one: a square
 * drawn and then held snaps, a line snaps, handwriting does not, and a tip that has moved 4 pt has
 * re-armed — which is exactly what gives the user their own ink back when they carry on drawing.
 *
 * The samples are built with the same repeatable jitter `ShapeRecognizerTest` uses, so the fixtures are
 * hand-drawn-shaped without being random from run to run.
 */
class ShapeDwellTest {

    private val w = 1.5

    private fun wobble(i: Int, amount: Double) = amount * sin(i * 12.9898)

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

    private fun square(): List<StrokePoint> = loop(
        listOf(10.0 to 10.0, 260.0 to 10.0, 260.0 to 150.0, 10.0 to 150.0),
        perEdge = 30,
        jitter = 1.0,
    )

    /** A drawn square is a rectangle — the caller decides that it has rested (silence, or a timer). */
    @Test
    fun `a drawn square is a rectangle`() {
        val figure = ShapeDwell.figureFor(square(), w)
        assertEquals("four corners plus the closing repeat", FigureKind.RECTANGLE, figure?.kind)
        assertEquals(5, figure?.points?.size)
    }

    /** A square that was drawn and then held **is** at rest: the samples after it sit on the corner. */
    @Test
    fun `a square held after it is drawn has rested`() {
        val drawn = square()
        val hold = drawn.last()
        val held = drawn + listOf(hold.copy(x = hold.x + 0.5), hold.copy(y = hold.y - 0.4))
        assertTrue(ShapeDwell.resting(held))
        assertEquals("the anchor is the corner the tip stopped on", drawn.lastIndex, ShapeDwell.restAnchor(held))
    }

    /** A straight line is a figure too — the dwell snaps it, and it stays an ordinary stroke. */
    @Test
    fun `a held line becomes a line`() {
        val drawn = sample(60, jitter = 1.0) { t -> 20.0 + 220 * t to 60.0 + 6 * t }
        assertEquals(FigureKind.LINE, ShapeDwell.figureFor(drawn, w)?.kind)
        assertFalse("a line is not a polygon a handle could drag", ShapeDwell.figureFor(drawn, w)!!.isClosedPolygon)
    }

    /** Handwriting is never a figure: the recogniser finds nothing, and the ink is left as drawn. */
    @Test
    fun `a letter-sized sweep is not a figure`() {
        val c = sample(40) { t ->
            val a = -PI / 2 + PI * t
            (40 + 18 * cos(a)) to (40 + 18 * sin(a))
        }
        assertNull("a letter-sized C is not an arc", ShapeDwell.figureFor(c, w))
    }

    /** A tip that has moved past the slop is moving again: no figure, and the timer is re-armed. */
    @Test
    fun `a sample past the slop re-arms the dwell`() {
        val drawn = square()
        val moved = drawn + drawn.last().copy(x = drawn.last().x + 4.0)
        assertFalse("4 pt is past the 3 pt slop", ShapeDwell.resting(moved))
        assertEquals("the last point it moved on is the only anchor", -1, ShapeDwell.restAnchor(moved))
    }

    /** The jitter a resting hand reports is *inside* the slop, so a wobbling hold is still a hold. */
    @Test
    fun `tremor inside the slop is stillness`() {
        val drawn = square()
        val anchor = drawn.last()
        val trembling = drawn + listOf(
            anchor.copy(x = anchor.x + 1.0, y = anchor.y - 1.0),
            anchor.copy(x = anchor.x - 2.0, y = anchor.y + 0.5),
        )
        assertTrue(ShapeDwell.resting(trembling))
        assertEquals(drawn.lastIndex, ShapeDwell.restAnchor(trembling))
    }

    /** The slop is a circle, not two axes: a diagonal 2.5 pt away is inside it. */
    @Test
    fun `the slop is measured as distance`() {
        val drawn = square()
        val last = drawn.last()
        val diagonal = drawn + last.copy(x = last.x + 2.5, y = last.y + 1.5)
        assertTrue("sqrt(2.5² + 1.5²) is under 3 pt", ShapeDwell.resting(diagonal))
        assertTrue(hypot(2.5, 1.5) < ShapeDwell.SLOP_PT)
    }
}
