package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A second opinion on **where a closed stroke's corners are**, for the polygon classes of
 * [ShapeRecognizer].
 *
 * The recogniser's main corner detector is desktop Xournal++'s recursive piecewise line fit, and it
 * is good at finding the breaks in a wobbly stroke. It is not exhaustive: its split strategy guesses
 * an even split first and grows one straight piece out of it, so a shape whose sides are unevenly
 * spaced from the start point — the wide, squat rectangle most people draw in one stroke — can run
 * out of search budget one corner short. A three-segment fit of a four-sided figure then closes into
 * a plausible-looking *triangle* whose third side runs corner-to-corner, which is the worst possible
 * answer: it snaps, and it is wrong.
 *
 * This file is the deterministic backup: Ramer–Douglas–Peucker over the closed ring, then every
 * vertex that is nearly collinear with its neighbours merged away (that is the seam — the stroke's
 * own start point, which sits mid-side whenever the user began drawing mid-side). RDP makes no
 * assumption about where the sides are, only about how far the ink strays from the chord, so it
 * finds the four corners of a squat rectangle and the three of a wide triangle at any sample density.
 *
 * It is deliberately *only* a corner finder. The lines are still fitted and intersected by
 * [ShapeRecognizer]'s own [RecoSegment] code, and the resulting polygon still has to pass the same
 * agreement test as the first detector's — so this cannot invent a polygon out of a circle or a
 * scribble, it can only offer a corner set the fit missed.
 */
internal object PolygonCorners {

    /**
     * How far (as a fraction of the stroke's bounding diagonal) the ink may stray from a chord and
     * still count as that chord. Loose enough that a bowed hand-drawn side is one side, tight enough
     * that a rounded corner is two.
     */
    private const val EPSILON_FRACTION = 0.02

    /** Smallest epsilon in pt, so a tiny shape's own tremor is not read as corners. */
    private const val MIN_EPSILON_PT = 1.5

    /**
     * A vertex this close to a straight line through its neighbours *is* on that line: it is the
     * seam, not a corner. 175° leaves every deliberate shallow corner alone.
     */
    private const val COLLINEAR_DEG = 175.0

    /** Fewest samples a ring may have before corners can be read from it at all. */
    private const val MIN_SAMPLES = 8

    /** A closed stroke's ring, re-cut so it begins at a real corner: its points and their breaks. */
    internal class Ring(val points: List<StrokePoint>, val breaks: IntArray) {
        /** How many sides the ring has (its break array holds one more index than that). */
        val sides: Int get() = breaks.size - 1
    }

    /**
     * The rings [points] supports: currently one, at [EPSILON_FRACTION] — or an empty list when the
     * stroke is too short, too open, or does not come out with three or four corners.
     *
     * The stroke is treated as closed only when it ends where it began (within the same epsilon the
     * corner search uses), because an open stroke's two ends are not corners of a ring.
     */
    fun rings(points: List<StrokePoint>): List<Ring> {
        if (points.size < MIN_SAMPLES) return emptyList()
        val diagonal = diagonal(points)
        if (diagonal <= 0.0) return emptyList()
        val epsilon = maxOf(MIN_EPSILON_PT, diagonal * EPSILON_FRACTION)
        if (hypot(points.last().x - points.first().x, points.last().y - points.first().y) > epsilon) {
            return emptyList()
        }

        // One sample of the seam is enough: the closing repeat is the first point again.
        val body = points.dropLast(1)
        val count = body.size
        if (count < MIN_SAMPLES - 1) return emptyList()
        val ring = body + body.first()

        var corners = simplify(ring, epsilon)
        if (corners.size < 4) return emptyList()
        corners = dropCollinear(ring, corners)
        // Not a polygon candidate any more (each merge removes a vertex, never adds one).
        if (corners.size !in 4..5) return emptyList()

        // RDP always keeps the stroke's own start, which is a corner only when the user began at
        // one. A seam that was merged away means the ring must be re-cut from the next corner.
        val offset = corners.first()
        val rotated = if (offset == 0) {
            ring
        } else {
            List(count) { ring[(offset + it) % count] } + ring[offset]
        }
        // The last entry is the closing repeat of the first, so it is expressed by the final `count`
        // break rather than by a modulo that would fold it back onto 0.
        val breaks = corners.dropLast(1).map { (it - offset + count) % count } + count
        if (breaks.first() != 0) return emptyList()
        for (i in 1 until breaks.size) if (breaks[i] <= breaks[i - 1]) return emptyList()
        return listOf(Ring(rotated, breaks.toIntArray()))
    }

    /** The stroke's bounding-box diagonal, its own size unit. */
    private fun diagonal(points: List<StrokePoint>): Double {
        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return hypot(maxX - minX, maxY - minY)
    }

    /** Ramer–Douglas–Peucker: the indices of [points] that survive at [epsilon], in order. */
    private fun simplify(points: List<StrokePoint>, epsilon: Double): List<Int> {
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)
        while (stack.isNotEmpty()) {
            val (start, end) = stack.removeLast()
            if (end <= start + 1) continue
            var farthest = -1
            var farthestDistance = epsilon
            for (i in start + 1 until end) {
                val d = perpendicularDistance(points[i], points[start], points[end])
                if (d > farthestDistance) {
                    farthest = i
                    farthestDistance = d
                }
            }
            if (farthest > 0) {
                keep[farthest] = true
                stack.addLast(start to farthest)
                stack.addLast(farthest to end)
            }
        }
        return keep.indices.filter { keep[it] }
    }

    /** Distance from [p] to the segment [a]–[b], in pt. */
    private fun perpendicularDistance(p: StrokePoint, a: StrokePoint, b: StrokePoint): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0.0) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    /**
     * [corners] without the vertices that lie on a straight line through their neighbours — the
     * seam above all, and any corner RDP split in two because a side bowed either side of it.
     *
     * The ring is tested **cyclically**: the seam is the first corner, so its neighbours are the last
     * and the second, and only a wrap-around test can see that it is not a corner at all.
     */
    private fun dropCollinear(points: List<StrokePoint>, corners: List<Int>): List<Int> {
        val ring = corners.dropLast(1).toMutableList()
        var removed = true
        while (removed && ring.size > 3) {
            removed = false
            for (i in ring.indices) {
                val previous = points[ring[(i - 1 + ring.size) % ring.size]]
                val vertex = points[ring[i]]
                val next = points[ring[(i + 1) % ring.size]]
                if (interiorAngle(previous, vertex, next) > COLLINEAR_DEG) {
                    ring.removeAt(i)
                    removed = true
                    break
                }
            }
        }
        return ring + corners.last()
    }

    /** The interior angle at [vertex], in degrees, between the rays to [from] and [to]. */
    private fun interiorAngle(from: StrokePoint, vertex: StrokePoint, to: StrokePoint): Double {
        val a = atan2(from.y - vertex.y, from.x - vertex.x)
        val b = atan2(to.y - vertex.y, to.x - vertex.x)
        val degrees = Math.toDegrees(abs(a - b))
        return if (degrees > 180.0) 360.0 - degrees else degrees
    }
}
