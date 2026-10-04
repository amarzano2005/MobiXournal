package com.mobixournal.render

import com.mobixournal.format.model.StrokePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Turns a just-finished freehand stroke into a clean primitive when it clearly *is* one.
 *
 * This is a direct port of **desktop Xournal++'s shape recognizer**
 * (`src/core/control/shaperecognizer/`, GPL-2.0-or-later, © the Xournal++ authors), which is the
 * whole point: a stroke drawn here is classified by the same algorithm, with the same thresholds,
 * that classifies it on the desktop. The previous hand-rolled recognizer produced visibly different
 * results (it fitted ellipses to a bounding box, guessed arrows, and read rounded corners as extra
 * vertices); this one replaces it rather than being tuned to agree with it.
 *
 * The method, as upstream: the stroke is not simplified and its points are not inspected for
 * corners. Instead the whole point list is fitted by **piecewise straight segments** with the
 * least-squares-to-a-line test of [Inertia] (recursive splitting in [findPolygonal], then each break
 * nudged to its local optimum in [optimizePolygonal]). The resulting segment list is tried against
 * the known shapes — 3 segments within tolerance of a closed triangle, 4 of a rectangle, a single
 * straight segment of a line — each rebuilt from the *intersections* of the fitted lines, so the
 * emitted geometry is cleaner than anything the recognizer could have read off the samples. Only if
 * no polygon fits is the stroke tested as a circle by [CircleRecognizer].
 *
 * Everything is measured relative to the stroke's own size ([RecoSegment.radius], the inertia
 * radius) rather than in absolute pt, so the same wobble tolerance applies to a small circle and a
 * page-sized one.
 *
 * Output is an ordinary constant-width point list, so nothing new touches the `.xopp` format.
 * Android-free and pure ([ShapeRecognizerTest]).
 */
object ShapeRecognizer {

    /**
     * Most sides the segment fitter will look for (upstream `MAX_POLYGON_SIDES`). Four covers the
     * only polygons that get rebuilt (triangle, rectangle); the count also doubles as the recursion's
     * search budget, so raising it changes which strokes fit at all.
     */
    private const val MAX_POLYGON_SIDES = 4

    /**
     * Largest inertia `det` a piece may have and still count as straight (upstream
     * `SEGMENT_MAX_DET`); 0 is a perfect line. This is the recognizer's corner detector — there is
     * no angle test anywhere.
     */
    private const val SEGMENT_MAX_DET = 0.045

    /**
     * Stricter straightness for the *whole* stroke to be accepted as a line (upstream
     * `LINE_MAX_DET`). A one-segment fit that merely clears [SEGMENT_MAX_DET] is a shallow arc, not
     * a line, and is left as drawn.
     */
    private const val LINE_MAX_DET = 0.015

    /**
     * Squared distance (pt²) the stroke's last sample may sit from the fitted line before the line
     * is rejected in favour of the drawn endpoints (upstream `LINE_POINT_DIST2_THRESHOLD`). Guards
     * the diagonal case, where the fitted line's direction is trusted but its extent is not.
     */
    private const val LINE_POINT_DIST2_THRESHOLD = 15.0

    /** How far off square a rectangle's corners may be, in radians (upstream 15°). */
    private val RECTANGLE_ANGLE_TOLERANCE = 15.0 * PI / 180.0

    /**
     * A rectangle's sides are snapped to exactly square, but only once the average tilt is this
     * small a rotation from the page axes (upstream `SLANT_TOLERANCE`, 5°) — otherwise the
     * rectangle keeps its lean.
     */
    private val SLANT_TOLERANCE = 5.0 * PI / 180.0

    /** Gap allowance at a rectangle's vertices, as a fraction of the two pieces' radii. */
    private const val RECTANGLE_LINEAR_TOLERANCE = 0.20

    /** Gap allowance at a triangle's vertices, looser than a rectangle's (a triangle is floppier). */
    private const val TRIANGLE_LINEAR_TOLERANCE = 0.3

    /**
     * Shortest stroke (pt) that will be recognised at all, measured across its bounding box's
     * diagonal — upstream's `strokeRecognizerMinSize` default. Below it the user was jotting a tick
     * or a dot, and snapping would be destructive.
     */
    private const val MIN_STROKE_SIZE_PT = 40.0

    /**
     * The cleaned-up replacement for [points], or **null** when the stroke doesn't fit any primitive
     * (the caller then keeps the freehand stroke exactly as drawn). [widthPt] is the constant width
     * every emitted vertex carries.
     *
     * Recognises: a **line** (one straight segment), a **triangle** (three), a **rectangle** (four),
     * and a **circle**. Nothing else — an unmatched stroke, including handwriting, a V, an arrow or
     * an oval, comes back as `null`.
     */
    fun recognize(points: List<StrokePoint>, widthPt: Double): List<StrokePoint>? {
        if (points.size < 3) return null
        if (!largeEnough(points)) return null

        val breaks = IntArray(MAX_POLYGON_SIDES + 1)
        val fits = MutableList(MAX_POLYGON_SIDES) { Inertia() }

        val count = findPolygonal(points, 0, points.size - 1, MAX_POLYGON_SIDES, breaks, fits, 0)
        if (count > 0) {
            optimizePolygonal(points, count, breaks, fits)

            val segments = List(count) { i ->
                RecoSegment().also {
                    it.startpt = breaks[i]
                    it.endpt = breaks[i + 1]
                    it.calcSegmentGeometry(points, breaks[i], breaks[i + 1], fits[i])
                }
            }

            triangle(segments, widthPt)?.let { return it }
            rectangle(segments, widthPt)?.let { return it }
            if (count == 1 && fits[0].det < LINE_MAX_DET) {
                return line(segments[0], points, widthPt)
            }
        }

        return CircleRecognizer.recognize(points, widthPt)
    }

    /** True when the stroke spans enough of the page to be worth snapping. */
    private fun largeEnough(points: List<StrokePoint>): Boolean {
        var minX = points[0].x
        var maxX = minX
        var minY = points[0].y
        var maxY = minY
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return hypot(maxX - minX, maxY - minY) >= MIN_STROKE_SIZE_PT
    }

    // ---------------------------------------------------------------- polygon fitting

    /**
     * Fit at most [nsides] straight pieces to `points[start]..points[finish]`, writing the segment
     * boundaries into [breaks] and their inertia accumulators into [fits], both indexed from [base].
     * Returns how many pieces were found (`0` when the fit failed).
     *
     * The strategy is: guess an even split, take the first piece of it that tests straight, then grow
     * that piece outwards one point at a time for as long as growing it *improves* (lower `det`)
     * either end. That yields one confirmed segment and two leftovers, which are recurred into with
     * a smaller side budget. Returns `0` as soon as any leftover can't be fitted, so a partially
     * straight scribble isn't half-recognised.
     */
    private fun findPolygonal(
        points: List<StrokePoint>,
        start: Int,
        finish: Int,
        nsides: Int,
        breaks: IntArray,
        fits: MutableList<Inertia>,
        base: Int,
    ): Int {
        if (finish == start) return 0
        if (nsides <= 0) return 0

        // Too few points to split usefully: look for a single straight piece instead.
        // The side count is a search budget, so lowering it here is what makes short strokes work.
        var sides = nsides
        if (finish - start < 5) sides = 1

        // Look for one linear piece that's big enough to start from.
        var i1 = 0
        var i2 = 0
        val probe = Inertia()
        var k = 0
        while (k < sides) {
            i1 = start + (k * (finish - start)) / sides
            i2 = start + ((k + 1) * (finish - start)) / sides
            probe.calc(points, i1, i2)
            if (probe.det < SEGMENT_MAX_DET) break
            k++
        }
        if (k == sides) return 0

        // Grow the piece we found, outwards, while either end keeps looking straighter.
        var current = probe
        while (true) {
            var s1 = Inertia()
            var s2 = Inertia()
            val det1: Double
            val det2: Double
            if (i1 > start) {
                s1 = current.copy()
                s1.increase(points[i1 - 1], points[i1], 1)
                det1 = s1.det
            } else {
                det1 = 1.0
            }
            if (i2 < finish) {
                s2 = current.copy()
                s2.increase(points[i2], points[i2 + 1], 1)
                det2 = s2.det
            } else {
                det2 = 1.0
            }

            if (det1 < det2 && det1 < SEGMENT_MAX_DET) {
                i1--
                current = s1
            } else if (det2 < det1 && det2 < SEGMENT_MAX_DET) {
                i2++
                current = s2
            } else {
                break
            }
        }

        // Left leftovers (fewer sides left to spend if the piece ran to the end of the stroke).
        val n1 = if (i1 > start) {
            val found = findPolygonal(
                points, start, i1,
                if (i2 == finish) sides - 1 else sides - 2,
                breaks, fits, base,
            )
            if (found == 0) return 0
            found
        } else {
            0
        }

        breaks[base + n1] = i1
        breaks[base + n1 + 1] = i2
        fits[base + n1] = current

        // Right leftovers, appended after the pieces the left recursion produced.
        val n2 = if (i2 < finish) {
            val found = findPolygonal(points, i2, finish, sides - n1 - 1, breaks, fits, base + n1 + 1)
            if (found == 0) return 0
            found
        } else {
            0
        }

        return n1 + n2 + 1
    }

    /**
     * Nudge each interior break to the position that minimises the two adjacent pieces' combined
     * squared `det` — the polygonal analogue of tightening a spline's knots. Without this the
     * breaks land wherever the recursion's even split first guessed.
     */
    private fun optimizePolygonal(
        points: List<StrokePoint>,
        nsides: Int,
        breaks: IntArray,
        fits: MutableList<Inertia>,
    ) {
        for (i in 1 until nsides) {
            var cost = fits[i - 1].det * fits[i - 1].det + fits[i].det * fits[i].det
            var s1 = fits[i - 1].copy()
            var s2 = fits[i].copy()
            var improved = false

            while (breaks[i] > breaks[i - 1] + 1) {
                // Hand the point just before the break over to the right-hand piece.
                s1.increase(points[breaks[i] - 1], points[breaks[i] - 2], -1)
                s2.increase(points[breaks[i] - 1], points[breaks[i] - 2], 1)
                val newCost = s1.det * s1.det + s2.det * s2.det
                if (newCost >= cost) break
                improved = true
                cost = newCost
                breaks[i]--
                fits[i - 1] = s1
                fits[i] = s2
            }

            // Upstream only tries the other direction when moving left didn't help.
            if (improved) continue

            s1 = fits[i - 1].copy()
            s2 = fits[i].copy()
            while (breaks[i] < breaks[i + 1] - 1) {
                // Hand the point at the break over to the left-hand piece.
                s1.increase(points[breaks[i]], points[breaks[i] + 1], 1)
                s2.increase(points[breaks[i]], points[breaks[i] + 1], -1)
                val newCost = s1.det * s1.det + s2.det * s2.det
                if (newCost >= cost) break
                cost = newCost
                breaks[i]++
                fits[i - 1] = s1
                fits[i] = s2
            }
        }
    }

    // ---------------------------------------------------------------- shape rebuilding

    /**
     * A triangle, when the **last three** segments close up to one. The two orientation passes are
     * upstream's: the first picks which way each segment runs (whichever of its ends is nearer the
     * next segment's), the second measures the vertex gaps with the segments oriented.
     */
    private fun triangle(segments: List<RecoSegment>, widthPt: Double): List<StrokePoint>? {
        if (segments.size < 3) return null
        val base = segments.size - 3
        // Only a polygon the stroke began at is a candidate; a trailing piece can't start the ring.
        if (segments[base].startpt != 0) return null

        for (i in 0..2) {
            val r1 = segments[base + i]
            val r2 = segments[base + (i + 1) % 3]
            // r1 runs P→Q unless reversed; whichever of P and Q sits nearer r2's own ends is the
            // end that should meet r2, which tells us the direction to emit r1 in.
            val fromP = minOf(
                dist2(r1.x1, r1.y1, r2.x1, r2.y1),
                dist2(r1.x1, r1.y1, r2.x2, r2.y2),
            )
            val fromQ = minOf(
                dist2(r1.x2, r1.y2, r2.x1, r2.y1),
                dist2(r1.x2, r1.y2, r2.x2, r2.y2),
            )
            r1.reversed = fromP < fromQ
        }

        for (i in 0..2) {
            val r1 = segments[base + i]
            val r2 = segments[base + (i + 1) % 3]
            val endX = if (r1.reversed) r1.x1 else r1.x2
            val endY = if (r1.reversed) r1.y1 else r1.y2
            val startX = if (r2.reversed) r2.x2 else r2.x1
            val startY = if (r2.reversed) r2.y2 else r2.y1
            if (hypot(endX - startX, endY - startY) >
                TRIANGLE_LINEAR_TOLERANCE * (r1.radius + r2.radius)
            ) {
                return null
            }
        }

        val corners = ArrayList<StrokePoint>(4)
        for (i in 0..2) {
            corners += segments[base + i].calcEdgeIsect(segments[base + (i + 1) % 3]) ?: return null
        }
        corners += corners[0]
        return corners.map { it.copy(width = widthPt) }
    }

    /**
     * A rectangle, when the **last four** segments close up to one. Every corner must be within
     * [RECTANGLE_ANGLE_TOLERANCE] of square and every vertex gap within tolerance; the four fitted
     * lines are then rotated to one shared angle (so the emitted sides are exactly square) and
     * intersected. A tilt under [SLANT_TOLERANCE] is dropped to 0, which is what makes a
     * roughly axis-aligned hand rectangle come out properly axis-aligned.
     */
    private fun rectangle(segments: List<RecoSegment>, widthPt: Double): List<StrokePoint>? {
        if (segments.size < 4) return null
        val base = segments.size - 4
        if (segments[base].startpt != 0) return null

        var avgAngle = 0.0
        for (i in 0..3) {
            val r1 = segments[base + i]
            val r2 = segments[base + (i + 1) % 4]
            if (abs(abs(r1.angle - r2.angle) - PI / 2) > RECTANGLE_ANGLE_TOLERANCE) return null

            avgAngle += r1.angle
            // Accumulate the ring's winding: each corner adds or subtracts another quarter turn
            // depending on which way the direction of travel crossed the branch cut.
            avgAngle += if (r2.angle > r1.angle) (i + 1) * PI / 2 else -(i + 1) * PI / 2

            // Point r1 away from r2 rather than at it.
            r1.reversed = (r1.x2 - r1.x1) * (r2.xcenter - r1.xcenter) +
                (r1.y2 - r1.y1) * (r2.ycenter - r1.ycenter) < 0
        }

        for (i in 0..3) {
            val r1 = segments[base + i]
            val r2 = segments[base + (i + 1) % 4]
            val gap = hypot(
                (if (r1.reversed) r1.x1 else r1.x2) - (if (r2.reversed) r2.x2 else r2.x1),
                (if (r1.reversed) r1.y1 else r1.y2) - (if (r2.reversed) r2.y2 else r2.y1),
            )
            if (gap > RECTANGLE_LINEAR_TOLERANCE * (r1.radius + r2.radius)) return null
        }

        avgAngle /= 4
        if (abs(avgAngle) < SLANT_TOLERANCE) avgAngle = 0.0
        if (abs(avgAngle) > PI / 2 - SLANT_TOLERANCE) avgAngle = PI / 2

        for (i in 0..3) segments[base + i].angle = avgAngle + i * PI / 2

        val corners = ArrayList<StrokePoint>(5)
        for (i in 0..3) {
            corners += segments[base + i].calcEdgeIsect(segments[base + (i + 1) % 4]) ?: return null
        }
        corners += corners[0]
        return corners.map { it.copy(width = widthPt) }
    }

    /**
     * A single straight segment as a two-point line. A nearly horizontal or vertical fit is snapped
     * flat to the axis, which is the recognizer's most visible convenience; a diagonal one is left
     * at its fitted extent, *unless* the stroke's last sample strays more than
     * [LINE_POINT_DIST2_THRESHOLD] from the fitted line, in which case the drawn endpoints win.
     */
    private fun line(
        segment: RecoSegment,
        points: List<StrokePoint>,
        widthPt: Double,
    ): List<StrokePoint> {
        var aligned = true
        if (abs(segment.angle) < SLANT_TOLERANCE) {
            segment.angle = 0.0
            segment.y1 = segment.ycenter
            segment.y2 = segment.ycenter
        } else if (abs(segment.angle) > PI / 2 - SLANT_TOLERANCE) {
            segment.angle = if (segment.angle > 0) PI / 2 else -PI / 2
            segment.x1 = segment.xcenter
            segment.x2 = segment.xcenter
        } else {
            aligned = false
        }

        val out = ArrayList<StrokePoint>(2)
        if (aligned) {
            out += StrokePoint(segment.x1, segment.y1, widthPt)
            out += StrokePoint(segment.x2, segment.y2, widthPt)
            return out
        }

        val dx = segment.x2 - segment.x1
        val dy = segment.y2 - segment.y1
        val last = points.last()
        val num = dy * last.x - dx * last.y + segment.x2 * segment.y1 - segment.y2 * segment.x1
        val perpendicularSq = num * num / (dy * dy + dx * dx)

        if (perpendicularSq < LINE_POINT_DIST2_THRESHOLD) {
            out += StrokePoint(segment.x1, segment.y1, widthPt)
            out += StrokePoint(segment.x2, segment.y2, widthPt)
        } else {
            out += StrokePoint(points.first().x, points.first().y, widthPt)
            out += StrokePoint(last.x, last.y, widthPt)
        }
        return out
    }

    /** Squared distance between two points — the vertex-gap test's unit. */
    private fun dist2(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }
}
