package com.mobixournal.render

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** 2D point used for stroke normalization and template matching. */
data class NormPoint(val x: Double, val y: Double)

/** A normalized gesture template for a character. */
data class CharTemplate(
    val char: Char,
    val points: List<NormPoint>,
    val strokeCount: Int,
    val hasLoop: Boolean,
)

/** Canonical templates for Latin alphanumeric handwriting. */
object HandwritingTemplates {

    private const val N_POINTS = 32

    val all: List<CharTemplate> by lazy { buildTemplates() }

    private fun pts(vararg coords: Double): List<NormPoint> {
        require(coords.size % 2 == 0)
        return (coords.indices step 2).map { NormPoint(coords[it], coords[it + 1]) }
    }

    private fun stroke(vararg coords: Double): List<NormPoint> = pts(*coords)

    private fun template(char: Char, strokes: List<List<NormPoint>>, hasLoop: Boolean = false): CharTemplate {
        val resampled = resampleAndNormalize(strokes, N_POINTS)
        return CharTemplate(char, resampled, strokes.size, hasLoop)
    }

    private fun buildTemplates(): List<CharTemplate> = listOf(
        // Uppercase
        template('A', listOf(stroke(0.1, 1.0, 0.5, 0.0, 0.9, 1.0), stroke(0.25, 0.6, 0.75, 0.6)), hasLoop = true),
        template('B', listOf(stroke(0.15, 0.0, 0.15, 1.0), stroke(0.15, 0.0, 0.75, 0.25, 0.15, 0.5, 0.8, 0.75, 0.15, 1.0)), hasLoop = true),
        template('C', listOf(stroke(0.85, 0.15, 0.5, 0.0, 0.15, 0.25, 0.1, 0.5, 0.15, 0.75, 0.5, 1.0, 0.85, 0.85))),
        template('D', listOf(stroke(0.15, 0.0, 0.15, 1.0), stroke(0.15, 0.0, 0.8, 0.25, 0.8, 0.75, 0.15, 1.0)), hasLoop = true),
        template('E', listOf(stroke(0.85, 0.0, 0.15, 0.0, 0.15, 1.0, 0.85, 1.0), stroke(0.15, 0.5, 0.65, 0.5))),
        template('F', listOf(stroke(0.15, 0.0, 0.15, 1.0), stroke(0.15, 0.0, 0.85, 0.0), stroke(0.15, 0.5, 0.65, 0.5))),
        template('G', listOf(stroke(0.85, 0.15, 0.5, 0.0, 0.15, 0.25, 0.1, 0.5, 0.15, 0.75, 0.5, 1.0, 0.85, 0.85, 0.85, 0.5, 0.5, 0.5))),
        template('H', listOf(stroke(0.15, 0.0, 0.15, 1.0), stroke(0.85, 0.0, 0.85, 1.0), stroke(0.15, 0.5, 0.85, 0.5))),
        template('I', listOf(stroke(0.5, 0.0, 0.5, 1.0))),
        template('J', listOf(stroke(0.7, 0.0, 0.7, 0.8, 0.45, 1.0, 0.2, 0.8))),
        template('K', listOf(stroke(0.15, 0.0, 0.15, 1.0), stroke(0.85, 0.0, 0.15, 0.55), stroke(0.15, 0.55, 0.85, 1.0))),
        template('L', listOf(stroke(0.15, 0.0, 0.15, 1.0, 0.85, 1.0))),
        template('M', listOf(stroke(0.1, 1.0, 0.1, 0.0, 0.5, 0.65, 0.9, 0.0, 0.9, 1.0))),
        template('N', listOf(stroke(0.15, 1.0, 0.15, 0.0, 0.85, 1.0, 0.85, 0.0))),
        template('O', listOf(stroke(0.5, 0.0, 0.15, 0.25, 0.1, 0.5, 0.15, 0.75, 0.5, 1.0, 0.85, 0.75, 0.9, 0.5, 0.85, 0.25, 0.5, 0.0)), hasLoop = true),
        template('P', listOf(stroke(0.15, 0.0, 0.15, 1.0), stroke(0.15, 0.0, 0.8, 0.25, 0.15, 0.5)), hasLoop = true),
        template('Q', listOf(stroke(0.5, 0.0, 0.15, 0.25, 0.1, 0.5, 0.15, 0.75, 0.5, 1.0, 0.85, 0.75, 0.9, 0.5, 0.85, 0.25, 0.5, 0.0), stroke(0.6, 0.7, 0.9, 1.0)), hasLoop = true),
        template('R', listOf(stroke(0.15, 0.0, 0.15, 1.0), stroke(0.15, 0.0, 0.8, 0.25, 0.15, 0.5, 0.85, 1.0)), hasLoop = true),
        template('S', listOf(stroke(0.8, 0.15, 0.5, 0.0, 0.2, 0.25, 0.5, 0.5, 0.8, 0.75, 0.5, 1.0, 0.15, 0.85))),
        template('T', listOf(stroke(0.1, 0.0, 0.9, 0.0), stroke(0.5, 0.0, 0.5, 1.0))),
        template('U', listOf(stroke(0.15, 0.0, 0.15, 0.75, 0.5, 1.0, 0.85, 0.75, 0.85, 0.0))),
        template('V', listOf(stroke(0.15, 0.0, 0.5, 1.0, 0.85, 0.0))),
        template('W', listOf(stroke(0.1, 0.0, 0.3, 1.0, 0.5, 0.35, 0.7, 1.0, 0.9, 0.0))),
        template('X', listOf(stroke(0.15, 0.0, 0.85, 1.0), stroke(0.85, 0.0, 0.15, 1.0))),
        template('Y', listOf(stroke(0.15, 0.0, 0.5, 0.5, 0.5, 1.0), stroke(0.85, 0.0, 0.5, 0.5))),
        template('Z', listOf(stroke(0.15, 0.0, 0.85, 0.0, 0.15, 1.0, 0.85, 1.0))),

        // Digits
        template('0', listOf(stroke(0.5, 0.0, 0.15, 0.25, 0.1, 0.5, 0.15, 0.75, 0.5, 1.0, 0.85, 0.75, 0.9, 0.5, 0.85, 0.25, 0.5, 0.0)), hasLoop = true),
        template('1', listOf(stroke(0.3, 0.25, 0.5, 0.0, 0.5, 1.0))),
        template('2', listOf(stroke(0.2, 0.25, 0.5, 0.0, 0.8, 0.25, 0.15, 1.0, 0.85, 1.0))),
        template('3', listOf(stroke(0.2, 0.15, 0.5, 0.0, 0.8, 0.25, 0.5, 0.5, 0.8, 0.75, 0.5, 1.0, 0.2, 0.85))),
        template('4', listOf(stroke(0.75, 0.0, 0.2, 0.65, 0.85, 0.65), stroke(0.75, 0.2, 0.75, 1.0))),
        template('5', listOf(stroke(0.8, 0.0, 0.25, 0.0, 0.2, 0.45, 0.75, 0.55, 0.8, 0.8, 0.5, 1.0, 0.2, 0.85))),
        template('6', listOf(stroke(0.75, 0.15, 0.2, 0.5, 0.2, 0.8, 0.5, 1.0, 0.8, 0.8, 0.8, 0.5, 0.2, 0.5)), hasLoop = true),
        template('7', listOf(stroke(0.15, 0.0, 0.85, 0.0, 0.45, 1.0))),
        template('8', listOf(stroke(0.5, 0.5, 0.2, 0.25, 0.5, 0.0, 0.8, 0.25, 0.5, 0.5, 0.2, 0.75, 0.5, 1.0, 0.8, 0.75, 0.5, 0.5)), hasLoop = true),
        template('9', listOf(stroke(0.8, 0.5, 0.2, 0.5, 0.2, 0.2, 0.5, 0.0, 0.8, 0.2, 0.8, 0.8, 0.5, 1.0)), hasLoop = true),

        // Common lowercase
        template('a', listOf(stroke(0.8, 0.4, 0.5, 0.2, 0.2, 0.5, 0.5, 0.9, 0.8, 0.7, 0.8, 0.2, 0.8, 1.0)), hasLoop = true),
        template('b', listOf(stroke(0.2, 0.0, 0.2, 1.0, 0.5, 1.0, 0.8, 0.7, 0.8, 0.5, 0.5, 0.3, 0.2, 0.4)), hasLoop = true),
        template('c', listOf(stroke(0.8, 0.3, 0.5, 0.2, 0.2, 0.5, 0.2, 0.7, 0.5, 1.0, 0.8, 0.9))),
        template('d', listOf(stroke(0.8, 0.0, 0.8, 1.0), stroke(0.8, 0.7, 0.5, 1.0, 0.2, 0.7, 0.2, 0.5, 0.5, 0.3, 0.8, 0.4)), hasLoop = true),
        template('e', listOf(stroke(0.2, 0.6, 0.8, 0.6, 0.5, 0.2, 0.2, 0.5, 0.2, 0.8, 0.5, 1.0, 0.8, 0.9)), hasLoop = true),
        template('h', listOf(stroke(0.2, 0.0, 0.2, 1.0), stroke(0.2, 0.5, 0.5, 0.3, 0.8, 0.5, 0.8, 1.0))),
        template('i', listOf(stroke(0.5, 0.35, 0.5, 1.0), stroke(0.5, 0.05, 0.5, 0.1))),
        template('l', listOf(stroke(0.5, 0.0, 0.5, 1.0))),
        template('m', listOf(stroke(0.1, 0.3, 0.1, 1.0), stroke(0.1, 0.5, 0.5, 0.3, 0.5, 1.0), stroke(0.5, 0.5, 0.9, 0.3, 0.9, 1.0))),
        template('n', listOf(stroke(0.2, 0.3, 0.2, 1.0), stroke(0.2, 0.5, 0.5, 0.3, 0.8, 0.5, 0.8, 1.0))),
        template('o', listOf(stroke(0.5, 0.2, 0.2, 0.5, 0.5, 1.0, 0.8, 0.5, 0.5, 0.2)), hasLoop = true),
        template('p', listOf(stroke(0.2, 0.3, 0.2, 1.2), stroke(0.2, 0.4, 0.5, 0.3, 0.8, 0.5, 0.8, 0.7, 0.5, 0.9, 0.2, 0.8)), hasLoop = true),
        template('r', listOf(stroke(0.2, 0.3, 0.2, 1.0), stroke(0.2, 0.5, 0.5, 0.3, 0.8, 0.4))),
        template('s', listOf(stroke(0.8, 0.3, 0.5, 0.2, 0.2, 0.4, 0.5, 0.6, 0.8, 0.8, 0.5, 1.0, 0.2, 0.9))),
        template('t', listOf(stroke(0.45, 0.0, 0.45, 0.9, 0.7, 1.0), stroke(0.2, 0.3, 0.7, 0.3))),
        template('u', listOf(stroke(0.2, 0.3, 0.2, 0.8, 0.5, 1.0, 0.8, 0.8, 0.8, 0.3, 0.8, 1.0))),
        template('v', listOf(stroke(0.2, 0.3, 0.5, 1.0, 0.8, 0.3))),
        template('w', listOf(stroke(0.1, 0.3, 0.3, 1.0, 0.5, 0.5, 0.7, 1.0, 0.9, 0.3))),
        template('f', listOf(stroke(0.7, 0.1, 0.4, 0.0, 0.3, 0.2, 0.3, 1.0), stroke(0.15, 0.4, 0.65, 0.4))),
        template('g', listOf(stroke(0.7, 0.3, 0.4, 0.2, 0.2, 0.4, 0.4, 0.7, 0.7, 0.5, 0.7, 1.1, 0.4, 1.3, 0.2, 1.1)), hasLoop = true),
        template('j', listOf(stroke(0.6, 0.2, 0.6, 0.9, 0.3, 1.1, 0.1, 0.9), stroke(0.6, 0.0, 0.6, 0.05))),
        template('k', listOf(stroke(0.2, 0.0, 0.2, 1.0), stroke(0.8, 0.4, 0.2, 0.6), stroke(0.3, 0.55, 0.8, 1.0))),
        template('q', listOf(stroke(0.8, 0.4, 0.5, 0.2, 0.2, 0.5, 0.5, 0.9, 0.8, 0.7, 0.8, 0.2, 0.8, 1.3)), hasLoop = true),
        template('x', listOf(stroke(0.2, 0.3, 0.8, 1.0), stroke(0.8, 0.3, 0.2, 1.0))),
        template('y', listOf(stroke(0.2, 0.3, 0.4, 0.7, 0.7, 0.3), stroke(0.7, 0.3, 0.3, 1.2))),
        template('z', listOf(stroke(0.2, 0.3, 0.8, 0.3, 0.2, 1.0, 0.8, 1.0))),

        // Cursive letter shapes
        template('e', listOf(stroke(0.1, 0.8, 0.3, 0.4, 0.6, 0.3, 0.8, 0.6, 0.5, 0.8, 0.2, 0.6, 0.7, 0.9)), hasLoop = true),
        template('l', listOf(stroke(0.2, 0.9, 0.4, 0.3, 0.5, 0.0, 0.6, 0.3, 0.5, 0.9, 0.8, 1.0)), hasLoop = true),
        template('o', listOf(stroke(0.15, 0.6, 0.4, 0.3, 0.7, 0.3, 0.8, 0.6, 0.6, 0.9, 0.3, 0.8, 0.2, 0.6, 0.5, 0.3, 0.8, 0.3)), hasLoop = true),
        template('a', listOf(stroke(0.15, 0.7, 0.4, 0.3, 0.7, 0.3, 0.8, 0.6, 0.7, 0.9, 0.4, 0.9, 0.2, 0.7, 0.7, 0.5, 0.8, 1.0)), hasLoop = true),
        template('u', listOf(stroke(0.1, 0.4, 0.25, 0.8, 0.4, 1.0, 0.55, 0.8, 0.55, 0.4, 0.7, 0.8, 0.85, 1.0))),
        template('n', listOf(stroke(0.1, 0.8, 0.2, 0.4, 0.35, 0.3, 0.5, 0.6, 0.5, 1.0, 0.6, 0.5, 0.75, 0.3, 0.9, 0.6, 0.9, 1.0))),
        template('m', listOf(stroke(0.1, 0.8, 0.2, 0.4, 0.3, 0.3, 0.4, 0.7, 0.4, 1.0, 0.5, 0.5, 0.65, 0.3, 0.75, 0.7, 0.75, 1.0, 0.85, 0.5, 0.95, 0.4, 0.95, 1.0))),
        template('i', listOf(stroke(0.15, 0.8, 0.35, 0.4, 0.5, 0.35, 0.5, 0.85, 0.8, 1.0), stroke(0.5, 0.1, 0.5, 0.15))),
        template('c', listOf(stroke(0.15, 0.8, 0.35, 0.4, 0.6, 0.3, 0.75, 0.4, 0.4, 0.6, 0.3, 0.8, 0.5, 1.0, 0.85, 0.9))),
    )

    fun resampleAndNormalize(strokes: List<List<NormPoint>>, n: Int): List<NormPoint> {
        val totalLength = strokes.sumOf { strokeLength(it) }
        val points = ArrayList<NormPoint>(n)
        if (totalLength <= 1e-6) {
            val first = strokes.firstOrNull()?.firstOrNull() ?: NormPoint(0.0, 0.0)
            return List(n) { first }
        }

        val step = totalLength / (n - 1)
        var accumulated = 0.0
        var currentStrokeIndex = 0
        var currentPointIndex = 0

        // Always add the first point of the first non-empty stroke
        val firstPt = strokes.first { it.isNotEmpty() }.first()
        points.add(firstPt)

        for (stroke in strokes) {
            if (stroke.size < 2) continue
            for (i in 0 until stroke.size - 1) {
                val p1 = stroke[i]
                val p2 = stroke[i + 1]
                val d = hypot(p2.x - p1.x, p2.y - p1.y)
                if (d < 1e-6) continue
                var dist = d
                var tPrev = 0.0

                while (accumulated + dist >= step && points.size < n) {
                    val remaining = step - accumulated
                    val t = tPrev + (remaining / d)
                    val x = p1.x + t * (p2.x - p1.x)
                    val y = p1.y + t * (p2.y - p1.y)
                    points.add(NormPoint(x, y))
                    dist -= remaining
                    accumulated = 0.0
                    tPrev = t
                }
                accumulated += dist
            }
        }

        while (points.size < n) {
            points.add(strokes.last { it.isNotEmpty() }.last())
        }

        return normalizePoints(points)
    }

    private fun strokeLength(pts: List<NormPoint>): Double {
        if (pts.size < 2) return 0.0
        var len = 0.0
        for (i in 0 until pts.size - 1) {
            len += hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
        }
        return len
    }

    private fun normalizePoints(pts: List<NormPoint>): List<NormPoint> {
        val meanX = pts.map { it.x }.average()
        val meanY = pts.map { it.y }.average()
        val minX = pts.minOf { it.x }
        val maxX = pts.maxOf { it.x }
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        val w = max(maxX - minX, 1e-4)
        val h = max(maxY - minY, 1e-4)

        // For very tall narrow letters (like 'I' or '1' or 'l'), preserve the narrow aspect ratio
        val scale = if (w / h < 0.25) 1.0 / h else 1.0 / max(w, h)

        return pts.map { NormPoint((it.x - meanX) * scale, (it.y - meanY) * scale) }
    }
}
