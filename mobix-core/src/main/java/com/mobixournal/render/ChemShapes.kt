package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Pure geometry for **chemical formulas**: the single/double/triple **bonds** of a carbon skeleton and
 * the aromatic **benzene ring**.
 *
 * Everything here is emitted as ordinary constant-width strokes, so a formula round-trips through the
 * `.xopp` file as normal ink — desktop Xournal++ records no chemistry of its own, and neither do we.
 * The shapes are oriented along the user's drag (start → end); a double bond is two parallel lines and
 * a triple bond three, the way they are drawn by hand.
 *
 * Kept free of Android types so it is unit-testable on the JVM ([ChemShapesTest]).
 */
object ChemShapes {

    /**
     * Perpendicular separation of a multiple bond's parallel lines, in pt, scaled off the stroke
     * width so the two lines of a thick pen are not drawn on top of each other.
     */
    fun bondGapPt(widthPt: Double): Double = (widthPt * 2.5).coerceIn(3.0, 9.0)

    /** How many line segments approximate the benzene ring's inner circle. */
    private const val CIRCLE_SEGMENTS = 48

    /** Radius of the aromatic ring's inner circle, as a fraction of the hexagon's circumradius. */
    private const val INNER_CIRCLE_RATIO = 0.58

    private fun p(x: Double, y: Double, w: Double) = StrokePoint(x, y, w)

    /** A single bond: one straight line from the drag's start to its end. */
    fun bondSingle(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> =
        listOf(listOf(p(sx, sy, widthPt), p(ex, ey, widthPt)))

    /** A double bond: two parallel lines, one on each side of the drag line. */
    fun bondDouble(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> =
        parallelBonds(sx, sy, ex, ey, widthPt, offsets = listOf(-1, 1))

    /** A triple bond: three parallel lines — one on the drag line, one to each side. */
    fun bondTriple(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> =
        parallelBonds(sx, sy, ex, ey, widthPt, offsets = listOf(-1, 0, 1))

    /**
     * A benzene (aromatic) ring: a regular hexagon with a vertex at the top, inscribed in the drag's
     * box, plus the inner **aromatic circle**. Two strokes — the ring and the circle — so each is a
     * clean unbroken curve.
     */
    fun benzeneRing(sx: Double, sy: Double, ex: Double, ey: Double, widthPt: Double): List<List<StrokePoint>> {
        val cx = (sx + ex) / 2.0
        val cy = (sy + ey) / 2.0
        val r = minOf(abs(ex - sx), abs(ey - sy)) / 2.0
        if (r <= 1e-6) return bondSingle(sx, sy, ex, ey, widthPt)

        // A vertex points straight up, which is how a benzene ring is conventionally drawn.
        val corners = TriangularLattice.hexagon(cx, cy, r, startRad = -PI / 2.0)
        val ring = corners.map { (x, y) -> p(x, y, widthPt) } + p(corners[0].first, corners[0].second, widthPt)

        val ir = r * INNER_CIRCLE_RATIO
        val circle = (0..CIRCLE_SEGMENTS).map { i ->
            val a = 2.0 * PI * i / CIRCLE_SEGMENTS
            p(cx + ir * kotlin.math.cos(a), cy + ir * kotlin.math.sin(a), widthPt)
        }
        return listOf(ring, circle)
    }

    /**
     * [offsets] parallel lines a gap apart across the drag line, each spanning the drag's full length
     * (offset 0 is the drag line itself). A zero-length drag degenerates to a single point stroke.
     */
    private fun parallelBonds(
        sx: Double,
        sy: Double,
        ex: Double,
        ey: Double,
        widthPt: Double,
        offsets: List<Int>,
    ): List<List<StrokePoint>> {
        val len = hypot(ex - sx, ey - sy)
        if (len < 1e-6) return bondSingle(sx, sy, ex, ey, widthPt)
        // Perpendicular to the drag, unit length.
        val nx = -(ey - sy) / len
        val ny = (ex - sx) / len
        val gap = bondGapPt(widthPt)
        return offsets.map { k ->
            val ox = nx * gap * k
            val oy = ny * gap * k
            listOf(p(sx + ox, sy + oy, widthPt), p(ex + ox, ey + oy, widthPt))
        }
    }
}
