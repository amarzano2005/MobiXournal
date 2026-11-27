package com.mobixournal.render

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.SplineElement
import com.mobixournal.format.model.SplineNode
import com.mobixournal.format.model.StrokePoint
import kotlin.math.hypot

/**
 * A [SplineElement] compiled into ink (`docs/mobix-format.md`) — and, through `LivingInk`, the
 * curve drawn on screen and exported to `.xopp`, PDF, SVG, and PNG.
 *
 * Each knot pair `(p0, p1)` is evaluated as a cubic Bézier segment using explicit `out`/`in`
 * handles when present, or Catmull-Rom smooth tangents `(next - prev) / 6` when omitted.
 */
object SplineInk {

    private const val MIN_NODES = 2
    private const val MIN_SEG_STEPS = 8
    private const val MAX_SEG_STEPS = 48
    private const val STEP_LENGTH_PT = 3.0
    private const val DEGENERATE_LENGTH_PT = 0.5

    /** [spline] as sampled ink, or null when fewer than two knots are present. */
    fun ink(spline: SplineElement): List<Element>? {
        val points = samplePoints(spline.nodes, spline.closed) ?: return null
        return listOf(
            InkPrimitives.stroke(
                points = points,
                color = spline.color,
                widthPt = spline.width,
                lineStyle = spline.lineStyle,
            ),
        )
    }

    /** Sampled polyline points along [nodes], or null when fewer than two knots exist. */
    fun samplePoints(nodes: List<SplineNode>, closed: Boolean): List<StrokePoint>? {
        val n = nodes.size
        if (n < MIN_NODES) return null
        val out = ArrayList<StrokePoint>(n * 16)
        out += StrokePoint(nodes[0].x, nodes[0].y, 0.0)
        val segCount = if (closed) n else n - 1
        for (i in 0 until segCount) {
            val j = (i + 1) % n
            val p0 = nodes[i]
            val p1 = nodes[j]
            val (_, c1) = handlesFor(nodes, closed, i)
            val (c2, _) = handlesFor(nodes, closed, j)
            sampleCubic(p0.x, p0.y, c1.first, c1.second, c2.first, c2.second, p1.x, p1.y, out)
        }
        return out
    }

    /**
     * Effective `(inHandle, outHandle)` in page coordinates for node [index], using explicit
     * handle coordinates when present and Catmull-Rom auto-tangents otherwise.
     */
    fun handlesFor(
        nodes: List<SplineNode>,
        closed: Boolean,
        index: Int,
    ): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        val n = nodes.size
        val node = nodes[index]
        val prev = when {
            n < 2 -> node
            closed -> nodes[(index - 1 + n) % n]
            index > 0 -> nodes[index - 1]
            else -> node
        }
        val next = when {
            n < 2 -> node
            closed -> nodes[(index + 1) % n]
            index < n - 1 -> nodes[index + 1]
            else -> node
        }
        val dx = (next.x - prev.x) / 6.0
        val dy = (next.y - prev.y) / 6.0
        val inHandle = if (node.inX != null && node.inY != null) {
            node.inX to node.inY
        } else if (!node.smooth) {
            node.x to node.y
        } else {
            (node.x - dx) to (node.y - dy)
        }
        val outHandle = if (node.outX != null && node.outY != null) {
            node.outX to node.outY
        } else if (!node.smooth) {
            node.x to node.y
        } else {
            (node.x + dx) to (node.y + dy)
        }
        return inHandle to outHandle
    }

    private fun sampleCubic(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        x3: Double,
        y3: Double,
        out: MutableList<StrokePoint>,
    ) {
        val polyLen = hypot(x1 - x0, y1 - y0) + hypot(x2 - x1, y2 - y1) + hypot(x3 - x2, y3 - y2)
        if (polyLen < DEGENERATE_LENGTH_PT) {
            out += StrokePoint(x3, y3, 0.0)
            return
        }
        val steps = (polyLen / STEP_LENGTH_PT).toInt().coerceIn(MIN_SEG_STEPS, MAX_SEG_STEPS)
        for (s in 1..steps) {
            val t = s.toDouble() / steps
            val u = 1.0 - t
            val uu = u * u
            val tt = t * t
            val b0 = uu * u
            val b1 = 3.0 * uu * t
            val b2 = 3.0 * u * tt
            val b3 = tt * t
            out += StrokePoint(
                x = b0 * x0 + b1 * x1 + b2 * x2 + b3 * x3,
                y = b0 * y0 + b1 * y1 + b2 * y2 + b3 * y3,
                width = 0.0,
            )
        }
    }

    fun scale(element: SplineElement, sx: Double, sy: Double, dx: Double, dy: Double, ink: Double): SplineElement =
        element.copy(
            nodes = element.nodes.map { n ->
                n.copy(
                    x = n.x * sx + dx, y = n.y * sy + dy,
                    inX = n.inX?.let { it * sx + dx }, inY = n.inY?.let { it * sy + dy },
                    outX = n.outX?.let { it * sx + dx }, outY = n.outY?.let { it * sy + dy },
                )
            },
            width = element.width * ink,
        )

    fun rotate(element: SplineElement, angle: Double, px: Double, py: Double): SplineElement {
        val c = kotlin.math.cos(angle)
        val s = kotlin.math.sin(angle)
        fun rot(x: Double, y: Double): Pair<Double, Double> {
            val rx = x - px
            val ry = y - py
            return (px + rx * c - ry * s) to (py + rx * s + ry * c)
        }
        return element.copy(
            nodes = element.nodes.map { n ->
                val (nx, ny) = rot(n.x, n.y)
                val inPt = if (n.inX != null && n.inY != null) rot(n.inX, n.inY) else null
                val outPt = if (n.outX != null && n.outY != null) rot(n.outX, n.outY) else null
                n.copy(x = nx, y = ny, inX = inPt?.first, inY = inPt?.second, outX = outPt?.first, outY = outPt?.second)
            },
        )
    }

    fun restyle(element: SplineElement, color: Int?, widthPt: Double?): SplineElement {
        var e = if (color != null) element.copy(color = color) else element
        if (widthPt != null) e = e.copy(width = widthPt)
        return e
    }
}
