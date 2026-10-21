package com.mobixournal.render

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.round
import kotlin.math.sqrt

/**
 * The **triangular lattice of nodes at 60°**: rows of nodes a bond length apart, each row offset by
 * half a bond from the one above, so that every node's six nearest neighbours form a regular hexagon
 * of edge [spacingPt].
 *
 * It is the geometry a carbon skeleton lives on. Two consecutive nodes are joined by a bond whose
 * direction is a multiple of 60°, and every nearest-neighbour bond is the same length, so snapping a
 * drawn vertex onto the nearest node makes a hand-sketched skeletal formula come out with **exact
 * 60°/120° angles and equal bond lengths** — which is what the chemistry drawing guide is for. Six
 * nodes stepped around a shared node are a benzene ring.
 *
 * Kept free of Android types so it is unit-testable on the JVM. [DrawingGuide.Chemistry] wraps
 * [nearestNode] as the guide the user places; [ChemShapes] draws its bonds and rings.
 */
object TriangularLattice {

    /** √3, as a constant — the ratio between a row's height and a node's horizontal step. */
    private val ROOT3 = sqrt(3.0)

    /** Row height of a lattice of bond length [spacingPt]: a node's vertical step, `s·√3/2`. */
    fun rowHeight(spacingPt: Double): Double = spacingPt * ROOT3 / 2.0

    /**
     * The lattice node nearest ([x], [y]) on a lattice of bond length [spacingPt] anchored at
     * ([anchorX], [anchorY]) — itself a node, so it is the lattice's own origin.
     *
     * A non-positive [spacingPt] leaves the point alone (a guide with no bond length rules nothing).
     * The search rounds the point's lattice coordinates and then checks the surrounding **3×3** of
     * nodes: rounding alone can land on the wrong side of a Voronoi boundary near a cell's corners.
     */
    fun nearestNode(
        x: Double,
        y: Double,
        spacingPt: Double,
        anchorX: Double = 0.0,
        anchorY: Double = 0.0,
    ): Pair<Double, Double> {
        if (spacingPt <= 0.0) return x to y
        val h = rowHeight(spacingPt)
        val dx = x - anchorX
        val dy = y - anchorY
        // Basis u = (s, 0), v = (s/2, h): dx = a·s + b·s/2, dy = b·h.
        val bf = dy / h
        val af = dx / spacingPt - bf / 2.0
        val b0 = round(bf).toLong()
        val a0 = round(af).toLong()
        var bestX = x
        var bestY = y
        var bestDist = Double.MAX_VALUE
        for (db in -1L..1L) {
            val b = b0 + db
            for (da in -1L..1L) {
                val a = a0 + da
                val nx = anchorX + (a + b / 2.0) * spacingPt
                val ny = anchorY + b * h
                val d = hypot(x - nx, y - ny)
                if (d < bestDist) {
                    bestDist = d
                    bestX = nx
                    bestY = ny
                }
            }
        }
        return bestX to bestY
    }

    /**
     * The lattice's **bond mesh** covering the [widthPt] × [heightPt] sheet, as clipped segments
     * `(x1, y1, x2, y2)` in page pt for the guide's overlay. The three families are the three bond
     * directions — horizontal (0°), and ±60° — each drawn through every node the sheet contains, so
     * what is drawn is exactly the set of straight edges the guide will pull ink onto.
     *
     * Lines are generated generously around the sheet and clipped to it ([clipToRect]), so a segment
     * is never drawn outside the paper. [maxSegments] caps a pathological, near-zero spacing.
     */
    fun mesh(
        widthPt: Double,
        heightPt: Double,
        spacingPt: Double,
        anchorX: Double = 0.0,
        anchorY: Double = 0.0,
        maxSegments: Int = 6000,
    ): List<DoubleArray> {
        if (spacingPt <= 0.0 || widthPt <= 0.0 || heightPt <= 0.0) return emptyList()
        val h = rowHeight(spacingPt)
        val out = ArrayList<DoubleArray>()

        // Family A — horizontal bonds (a line of constant row b): y = anchorY + b·h.
        var b = ceil((0.0 - anchorY) / h).toLong()
        val bEnd = floor((heightPt - anchorY) / h).toLong()
        while (b <= bEnd && out.size < maxSegments) {
            val y = anchorY + b * h
            out += doubleArrayOf(0.0, y, widthPt, y)
            b++
        }

        // Family B — the +60° bonds (a line of constant a): through (anchorX + a·s, anchorY) with
        // direction (1/2, √3/2). As y grows by the sheet's height the line moves right by height/√3.
        var a = ceil((-heightPt / ROOT3 - anchorX) / spacingPt).toLong()
        val aEnd = floor((widthPt - anchorX) / spacingPt).toLong()
        while (a <= aEnd && out.size < maxSegments) {
            val x0 = anchorX + a * spacingPt
            clipToRect(x0, anchorY, 0.5, ROOT3 / 2.0, widthPt, heightPt)?.let { out += it }
            a++
        }

        // Family C — the −60° bonds (a line of constant a+b): through (anchorX + k·s, anchorY) with
        // direction (−1/2, √3/2), moving left by height/√3 as y grows.
        var k = ceil(-anchorX / spacingPt).toLong()
        val kEnd = floor((widthPt + heightPt / ROOT3 - anchorX) / spacingPt).toLong()
        while (k <= kEnd && out.size < maxSegments) {
            val x0 = anchorX + k * spacingPt
            clipToRect(x0, anchorY, -0.5, ROOT3 / 2.0, widthPt, heightPt)?.let { out += it }
            k++
        }

        return out
    }

    /**
     * The regular hexagon of edge [spacingPt] centred on ([cx], [cy]) — the benzene ring — as its six
     * vertices in order, first vertex at angle [startRad]. Pure geometry, shared by [ChemShapes] and
     * the ring's own overlay so a ring the guide offers is the ring the tool draws.
     */
    fun hexagon(
        cx: Double,
        cy: Double,
        spacingPt: Double,
        startRad: Double = 0.0,
    ): List<Pair<Double, Double>> = (0 until 6).map { i ->
        val a = startRad + i * Math.PI / 3.0
        (cx + spacingPt * kotlin.math.cos(a)) to (cy + spacingPt * kotlin.math.sin(a))
    }

    /**
     * Clip the line through ([px], [py]) with direction ([dx], [dy]) to the rectangle
     * `[0, widthPt] × [0, heightPt]` (Liang–Barsky), or null when it misses the sheet entirely.
     * Returns the in-sheet segment as `(x1, y1, x2, y2)`.
     */
    private fun clipToRect(
        px: Double,
        py: Double,
        dx: Double,
        dy: Double,
        widthPt: Double,
        heightPt: Double,
    ): DoubleArray? {
        var t0 = Double.NEGATIVE_INFINITY
        var t1 = Double.POSITIVE_INFINITY
        val p = doubleArrayOf(-dx, dx, -dy, dy)
        val q = doubleArrayOf(px, widthPt - px, py, heightPt - py)
        for (i in 0..3) {
            if (abs(p[i]) < 1e-12) {
                // Parallel to this edge: inside only when the line already lies within it.
                if (q[i] < 0.0) return null
            } else {
                val r = q[i] / p[i]
                if (p[i] < 0.0) {
                    if (r > t1) return null
                    if (r > t0) t0 = r
                } else {
                    if (r < t0) return null
                    if (r < t1) t1 = r
                }
            }
        }
        if (t1 < t0) return null
        return doubleArrayOf(px + t0 * dx, py + t0 * dy, px + t1 * dx, py + t1 * dy)
    }
}
