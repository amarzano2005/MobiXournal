package com.mobixournal.desktop.ink

import com.mobixournal.format.model.StrokePoint
import kotlin.math.hypot

/** Recompute only the two terminal control groups of an append-only freehand draft. */
internal class IncrementalSmoothing {
    val points = ArrayList<StrokePoint>()
    val curves = ArrayList<InkCurve>()
    var stablePrefix = 0
        private set
    var processedControls = 0L
        private set
    private val pointEnds = ArrayList<Int>()
    private val curveEnds = ArrayList<Int>()
    private var count = 0
    private var first: StrokePoint? = null
    private var last: StrokePoint? = null

    fun update(input: List<StrokePoint>) {
        val same = count <= input.size && first === input.firstOrNull() &&
            (count == 0 || last === input.getOrNull(count - 1))
        if (same && count == input.size) { stablePrefix = points.size; return }
        val start = if (same) maxOf(1, count - 2) else 1
        stablePrefix = if (same && start > 1) pointEnds[start - 1] else 0
        val retainedCurves = if (same && start > 1) curveEnds[start - 1] else 0
        while (points.size > stablePrefix) points.removeAt(points.lastIndex)
        while (curves.size > retainedCurves) curves.removeAt(curves.lastIndex)
        while (pointEnds.size > start) pointEnds.removeAt(pointEnds.lastIndex)
        while (curveEnds.size > start) curveEnds.removeAt(curveEnds.lastIndex)
        if (points.isEmpty() && input.isNotEmpty()) points += input[0]
        if (pointEnds.isEmpty()) { pointEnds += points.size; curveEnds += 0 }
        fun corner(i: Int): Boolean = i == 0 || i == input.lastIndex || turnDegrees(input, i) >= CORNER_DEGREES
        for (j in start until input.size) {
            if (corner(j - 1) && corner(j)) {
                val to = input[j]
                curves += InkCurve(to.x, to.y, to.x, to.y)
                points += to
            } else if (!corner(j)) {
                val from = if (corner(j - 1)) input[j - 1] else midpoint(input[j - 1], input[j])
                val to = if (corner(j + 1)) input[j + 1] else midpoint(input[j], input[j + 1])
                val control = input[j]
                curves += InkCurve(control.x, control.y, to.x, to.y)
                val steps = if (hypot(to.x - from.x, to.y - from.y) > 3.0) 3 else 2
                for (step in 1..steps) {
                    val t = step.toDouble() / steps; val a = 1.0 - t
                    points += StrokePoint(a * a * from.x + 2.0 * a * t * control.x + t * t * to.x,
                        a * a * from.y + 2.0 * a * t * control.y + t * t * to.y,
                        from.width + (to.width - from.width) * t)
                }
            }
            pointEnds += points.size; curveEnds += curves.size
            processedControls++
        }
        count = input.size; first = input.firstOrNull(); last = input.lastOrNull()
    }
}
