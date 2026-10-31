package com.mobixournal.render

import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.audioRef
import kotlin.math.hypot

/**
 * **Which stroke a Play-object tap landed on** — the audio tap's own pick, shared by both shells.
 *
 * `SelectionTester.pickTopmost` answers a selection's question ("what is under the pointer?") with an
 * element's **box**, which is the right tolerance for grabbing an element and the wrong one for asking
 * where in a recording a tap landed. A diagonal stroke's box is a rectangle whose middle can be tens of
 * points from its ink, and handwriting overlaps constantly, so the box rule handed back the *last-drawn*
 * stroke whose rectangle happened to cover the point — often a stroke drawn somewhere else, with another
 * recording's offset or none at all. That is a tap which plays the wrong word, and it is why this looks
 * at the **path** first.
 *
 * The rule, in order:
 *
 * 1. the **topmost stroke whose ink** passes within reach of the point (its own half-width or
 *    [ElementBounds.TAP_PAD], whichever is greater);
 * 2. when that one carries no recording, the **recorded** stroke whose ink is nearest the point among
 *    those whose box covers it — the rescue for a tap inside a diagonal's box, and what keeps a stroke
 *    drawn over a recorded one from making the recording unreachable;
 * 3. otherwise the stroke from (1), so the caller can say *why* nothing plays instead of doing nothing.
 */
object AudioTap {

    /** The stroke a Play-object tap at the page point ([x], [y]) replays, or null when it hit no ink. */
    fun pickStroke(page: Page, x: Double, y: Double): Stroke? {
        val under = topmostInk(page, x, y)
        if (under != null && under.audioRef() != null) return under
        return nearestRecorded(page, x, y) ?: under
    }

    /** How far from a stroke's own path a tap still counts as *on* it. */
    private fun reach(stroke: Stroke): Double =
        maxOf(ElementBounds.TAP_PAD, (stroke.points.maxOfOrNull { it.width } ?: 0.0) / 2)

    /** Topmost-first sweep for a stroke whose ink, not whose box, is under the point. */
    private fun topmostInk(page: Page, x: Double, y: Double): Stroke? {
        for (li in page.layers.indices.reversed()) {
            val elements = page.layers[li].elements
            for (ei in elements.indices.reversed()) {
                val stroke = elements[ei] as? Stroke ?: continue
                if (!covers(stroke, x, y)) continue
                if (distanceToPath(stroke, x, y) <= reach(stroke)) return stroke
            }
        }
        return null
    }

    /**
     * The recorded stroke whose ink is nearest the point, among the ones whose padded box holds it.
     *
     * Nearest rather than topmost on purpose: with a box rule the topmost is whatever was drawn last, and
     * among the ink that is under the point at all the one the pointer is *closest* to is the one a person
     * means.
     */
    private fun nearestRecorded(page: Page, x: Double, y: Double): Stroke? {
        var best: Stroke? = null
        var bestDistance = Double.MAX_VALUE
        for (li in page.layers.indices.reversed()) {
            for (element in page.layers[li].elements) {
                val stroke = element as? Stroke ?: continue
                if (!covers(stroke, x, y)) continue
                if (stroke.audioRef() == null) continue
                val distance = distanceToPath(stroke, x, y)
                if (distance < bestDistance) {
                    best = stroke
                    bestDistance = distance
                }
            }
        }
        return best
    }

    /** Cheap prefilter: a stroke whose padded box misses the point cannot have ink within reach of it. */
    private fun covers(stroke: Stroke, x: Double, y: Double): Boolean =
        ElementBounds.isHitTestable(stroke) && ElementBounds.of(stroke).expand(ElementBounds.TAP_PAD).contains(x, y)

    /** Distance from ([x], [y]) to the stroke's polyline — segments, not vertices, so a long edge counts. */
    private fun distanceToPath(stroke: Stroke, x: Double, y: Double): Double {
        val points = stroke.points
        if (points.isEmpty()) return Double.MAX_VALUE
        if (points.size == 1) return hypot(points[0].x - x, points[0].y - y)
        var best = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val distance = distanceToSegment(x, y, points[i].x, points[i].y, points[i + 1].x, points[i + 1].y)
            if (distance < best) best = distance
        }
        return best
    }

    /** Distance from ([x], [y]) to the segment ([x0], [y0])–([x1], [y1]), in pt. */
    private fun distanceToSegment(
        x: Double,
        y: Double,
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
    ): Double {
        val dx = x1 - x0
        val dy = y1 - y0
        val length2 = dx * dx + dy * dy
        val t = if (length2 == 0.0) 0.0 else (((x - x0) * dx + (y - y0) * dy) / length2).coerceIn(0.0, 1.0)
        return hypot(x - (x0 + t * dx), y - (y0 + t * dy))
    }
}
