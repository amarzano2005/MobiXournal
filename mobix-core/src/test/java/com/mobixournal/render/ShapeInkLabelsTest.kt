package com.mobixournal.render

import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.ShapeVertex
import com.mobixournal.format.model.TextElement
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a figure's *side lengths* and *angles* are written: outside the side they measure, inside the
 * corner they name.
 *
 * Both labels used to be dropped a flat 6 pt down-right of their midpoint or vertex, which put them
 * on the outline — on a narrow triangle, three numbers stacked on the ink. These tests pin the two
 * things that make them readable: the side label's box is clear of its own edge, and the angle label
 * sits within the corner.
 */
class ShapeInkLabelsTest {

    private fun shape(
        vertices: List<Pair<Double, Double>>,
        sides: Boolean = true,
        angles: Boolean = true,
    ) = ParametricShapeElement(
        shape = ParametricShapes.TRIANGLE,
        vertices = vertices.map { ShapeVertex(it.first, it.second) },
        strokeColor = 0xFF000000.toInt(),
        strokeWidth = 1.5,
        showSideLengths = sides,
        showAngles = angles,
    )

    private fun labels(shape: ParametricShapeElement): List<TextElement> =
        ShapeInk.ink(shape)!!.filterIsInstance<TextElement>()

    /** The label box, as the bounds code estimates it. */
    private data class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        fun contains(x: Double, y: Double) = x >= left && x <= right && y >= top && y <= bottom
        fun distanceTo(p: Pair<Double, Double>): Double {
            val dx = maxOf(left - p.first, 0.0, p.first - right)
            val dy = maxOf(top - p.second, 0.0, p.second - bottom)
            return hypot(dx, dy)
        }
    }

    private fun boxOf(text: TextElement): Box {
        val width = text.content.length * text.size * InkTokens.FIGURE_LABEL_CHAR_W
        val height = text.size * InkTokens.FIGURE_LABEL_LINE_H
        return Box(text.x, text.y, text.x + width, text.y + height)
    }

    @Test
    fun `a side length is written clear of the figure`() {
        // A steep-sided triangle, where an axis-aligned label box is the worst case: its horizontal
        // extent reaches back across an edge that is nowhere near horizontal.
        val corners = listOf(100.0 to 120.0, 360.0 to 120.0, 230.0 to 340.0)
        val labels = labels(shape(corners, angles = false))
        assertEquals("one label per side", 3, labels.size)
        for (label in labels) {
            val box = boxOf(label)
            // Every corner of the box, and its centre, must fall outside the figure: no label may
            // sit on the outline or on the ink inside it.
            val probes = listOf(
                box.left to box.top,
                box.right to box.top,
                box.left to box.bottom,
                box.right to box.bottom,
                (box.left + box.right) / 2 to (box.top + box.bottom) / 2,
            )
            for ((x, y) in probes) {
                assertTrue(
                    "${label.content} at ($x, $y) is inside the figure",
                    !inside(corners, x, y),
                )
            }
        }
    }

    /** True when ([x], [y]) is inside the convex [corners] polygon. */
    private fun inside(corners: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
        var sign = 0
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val cross = (b.first - a.first) * (y - a.second) - (b.second - a.second) * (x - a.first)
            if (cross > 1e-6) {
                if (sign < 0) return false
                sign = 1
            } else if (cross < -1e-6) {
                if (sign > 0) return false
                sign = -1
            }
        }
        return true
    }

    @Test
    fun `a side label is centred on its edge and clear of it`() {
        val triangle = shape(listOf(100.0 to 100.0, 400.0 to 100.0, 100.0 to 300.0), angles = false)
        val labels = labels(triangle)
        // The horizontal edge y=100 from x=100..400: its label sits just above the line.
        val horizontal = labels.first { it.content.endsWith("mm") && it.x > 90 && it.x < 410 && it.y < 100 }
        val box = boxOf(horizontal)
        assertTrue("above the edge", box.bottom <= 100.0)
        assertTrue("and close to it", 100.0 - box.bottom <= 6.0)
        assertEquals("centred on the midpoint", 250.0, (box.left + box.right) / 2, 1.0)
    }

    @Test
    fun `an angle label sits inside its corner, not across it`() {
        val corners = listOf(100.0 to 100.0, 400.0 to 100.0, 100.0 to 300.0)
        val labels = labels(shape(corners, sides = false))
        assertEquals("one label per corner", 3, labels.size)
        for (label in labels) {
            val box = boxOf(label)
            val cx = (box.left + box.right) / 2
            val cy = (box.top + box.bottom) / 2
            assertTrue("${label.content} is outside the figure", cx < 400.0 && cy < 300.0 && cx > 100.0 && cy > 100.0)
            // It belongs to *a* corner: nearer to it than to the figure's middle.
            val nearest = corners.minOf { hypot(cx - it.first, cy - it.second) }
            val middle = hypot(cx - 200.0, cy - 500.0 / 3)
            assertTrue("${label.content} is nearer a corner than the centre", nearest < middle)
            assertTrue("${label.content} belongs to the corner ($nearest pt away)", nearest < 100.0)
        }
    }

    @Test
    fun `a sharp corner keeps its angle label inside the sides`() {
        // A 20° apex: the bisector is long, so the inset has to be capped rather than run into the
        // middle of the figure.
        val spike = shape(
            listOf(200.0 to 60.0, 260.0 to 400.0, 140.0 to 400.0),
            sides = false,
        )
        val apex = labels(spike).minByOrNull { it.y }!!
        val box = boxOf(apex)
        assertTrue("the label stays near the apex", box.top < 140.0)
        assertTrue("and does not reach the base", box.bottom < 400.0)
    }

    @Test
    fun `no label is written when both readouts are off`() {
        val plain = shape(listOf(100.0 to 100.0, 300.0 to 100.0, 200.0 to 280.0), sides = false, angles = false)
        assertTrue(labels(plain).isEmpty())
    }

    @Test
    fun `a rectangle labels both sides and all four corners without stacking`() {
        val rectangle = shape(listOf(100.0 to 100.0, 400.0 to 100.0, 400.0 to 300.0, 100.0 to 300.0))
        val labels = labels(rectangle)
        assertEquals("four sides and four angles", 8, labels.size)
        for (i in labels.indices) {
            for (j in i + 1 until labels.size) {
                val a = boxOf(labels[i])
                val b = boxOf(labels[j])
                val overlapX = minOf(a.right, b.right) - maxOf(a.left, b.left)
                val overlapY = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
                assertTrue(
                    "labels ${labels[i].content} and ${labels[j].content} overlap",
                    overlapX <= 0.0 || overlapY <= 0.0,
                )
            }
        }
    }
}
