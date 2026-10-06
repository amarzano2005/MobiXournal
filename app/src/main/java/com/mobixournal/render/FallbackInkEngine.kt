package com.mobixournal.render

import com.mobixournal.format.model.Stroke
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Pure Kotlin, offline-capable handwriting recognizer used as a robust fallback
 * when ML Kit models are downloading or in environments without ML Kit.
 */
object FallbackInkEngine {

    private const val N_POINTS = 32

    /**
     * Recognizes a handwritten word from a group of strokes, returning a [HandwrittenWord]
     * with bounds, confidence, and candidate hypotheses.
     */
    fun recognizeWord(wordStrokes: List<Stroke>, bounds: Bounds): HandwrittenWord? {
        if (wordStrokes.isEmpty()) return null

        val decomposedStrokes = wordStrokes.flatMap { segmentCursiveStroke(it) }
        val charClusters = segmentIntoCharClusters(decomposedStrokes)
        if (charClusters.isEmpty()) return null

        val clusterCandidates = mutableListOf<List<Pair<Char, Double>>>()

        for (cluster in charClusters) {
            val candidates = recognizeCharCluster(cluster)
            if (candidates.isNotEmpty()) {
                clusterCandidates.add(candidates)
            }
        }

        if (clusterCandidates.isEmpty()) return null

        val topWord = buildString {
            for (candidates in clusterCandidates) {
                append(candidates.first().first)
            }
        }.trim()

        if (topWord.isEmpty()) return null

        // Generate alternate candidate words by varying ambiguous letters
        val candidateWords = mutableListOf<String>()
        candidateWords.add(topWord)

        // Try alternative characters for each position
        for (i in clusterCandidates.indices) {
            val cands = clusterCandidates[i]
            for (cIdx in 1 until minOf(3, cands.size)) {
                val altChar = cands[cIdx].first
                val altWord = topWord.substring(0, i) + altChar + topWord.substring(i + 1)
                if (altWord !in candidateWords) {
                    candidateWords.add(altWord)
                }
                if (candidateWords.size >= 8) break
            }
            if (candidateWords.size >= 8) break
        }

        val avgConfidence = clusterCandidates.map { it.first().second }.average()

        return HandwrittenWord(
            text = topWord,
            bounds = bounds,
            confidence = avgConfidence,
            candidates = candidateWords,
        )
    }

    /**
     * Splits a wide continuous cursive stroke into letter sub-strokes by detecting
     * inter-character ligatures. Cursive letters like 'm', 'u', 'w' have internal valleys
     * which must NOT be split.
     */
    private fun segmentCursiveStroke(stroke: Stroke): List<Stroke> {
        val b = strokeBounds(stroke)
        val w = b.right - b.left
        val h = b.bottom - b.top
        val pts = stroke.points
        // Only split if the stroke is wide enough to contain multiple connected letters
        if (h < 5.0 || w / h < 1.35 || pts.size < 8) {
            return listOf(stroke)
        }

        val baselineY = b.top + 0.60 * h
        val minCharWidth = h * 0.45
        val splitIndices = mutableListOf<Int>()
        var lastSplitX = b.left

        for (i in 2 until pts.size - 2) {
            val p = pts[i]
            val prev = pts[i - 1]
            val next = pts[i + 1]

            // A ligature connects characters near or below the baseline and rises rightward
            val isValley = p.y >= baselineY && p.y >= prev.y && p.y >= next.y
            val movesRight = next.x >= prev.x
            val distFromLast = p.x - lastSplitX
            val distToEnd = b.right - p.x

            if (isValley && movesRight && distFromLast >= minCharWidth && distToEnd >= minCharWidth) {
                splitIndices.add(i)
                lastSplitX = p.x
            }
        }

        if (splitIndices.isEmpty()) return listOf(stroke)

        val subStrokes = mutableListOf<Stroke>()
        var startIdx = 0
        for (splitIdx in splitIndices) {
            val slice = pts.subList(startIdx, minOf(splitIdx + 1, pts.size))
            if (slice.size >= 2) {
                subStrokes.add(stroke.copy(points = slice))
            }
            startIdx = splitIdx
        }
        val tail = pts.subList(startIdx, pts.size)
        if (tail.size >= 2) {
            subStrokes.add(stroke.copy(points = tail))
        }

        return if (subStrokes.isNotEmpty()) subStrokes else listOf(stroke)
    }

    private fun segmentIntoCharClusters(wordStrokes: List<Stroke>): List<List<Stroke>> {
        val sorted = wordStrokes.sortedBy { strokeBounds(it).left }
        if (sorted.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<Stroke>>()
        var current = mutableListOf(sorted[0])
        var currentBounds = strokeBounds(sorted[0])

        for (i in 1 until sorted.size) {
            val s = sorted[i]
            val b = strokeBounds(s)
            val currentW = currentBounds.right - currentBounds.left

            val sameOrigin = b.left <= currentBounds.left + 2.5
            val deepOverlap = b.left < currentBounds.right - (currentW * 0.35).coerceAtLeast(3.5)

            if (sameOrigin || deepOverlap) {
                current.add(s)
                currentBounds = Bounds(
                    left = minOf(currentBounds.left, b.left),
                    top = minOf(currentBounds.top, b.top),
                    right = maxOf(currentBounds.right, b.right),
                    bottom = maxOf(currentBounds.bottom, b.bottom),
                )
            } else {
                clusters.add(current)
                current = mutableListOf(s)
                currentBounds = b
            }
        }
        clusters.add(current)
        return clusters
    }

    /**
     * Recognizes a character cluster, returning the top candidate characters sorted by score.
     */
    private fun recognizeCharCluster(strokes: List<Stroke>): List<Pair<Char, Double>> {
        val normStrokes = strokes.map { stroke ->
            stroke.points.map { NormPoint(it.x, it.y) }
        }
        val candidatePoints = HandwritingTemplates.resampleAndNormalize(normStrokes, N_POINTS)
        val candidateHasLoop = detectLoop(strokes)

        val scored = mutableListOf<Pair<Char, Double>>()

        for (tmpl in HandwritingTemplates.all) {
            var score = scoreMatch(candidatePoints, tmpl.points)

            if (tmpl.strokeCount == strokes.size) {
                score += 0.06
            } else if (abs(tmpl.strokeCount - strokes.size) > 2) {
                score -= 0.12
            }

            if (tmpl.hasLoop == candidateHasLoop) {
                score += 0.08
            } else {
                score -= 0.15
            }

            // In general handwritten text, favor alphabetic characters over digits
            if (tmpl.char.isDigit()) {
                score -= 0.08
            } else {
                score += 0.04
            }

            scored.add(tmpl.char to score)
        }

        // Return up to top 4 unique character hypotheses
        val topList = mutableListOf<Pair<Char, Double>>()
        val seenChars = mutableSetOf<Char>()
        for (item in scored.sortedByDescending { it.second }) {
            if (seenChars.add(item.first)) {
                topList.add(item.first to item.second.coerceIn(0.01, 1.0))
                if (topList.size >= 4) break
            }
        }
        return topList
    }

    private fun scoreMatch(candidate: List<NormPoint>, template: List<NormPoint>): Double {
        val dist = cloudDistance(candidate, template)
        return max(0.0, 1.0 - (dist / 0.55))
    }

    private fun cloudDistance(pts1: List<NormPoint>, pts2: List<NormPoint>): Double {
        var sum = 0.0
        val n = min(pts1.size, pts2.size)
        if (n == 0) return 1.0

        for (p1 in pts1) {
            var minD = Double.MAX_VALUE
            for (p2 in pts2) {
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val d = dx * dx + dy * dy
                if (d < minD) minD = d
            }
            sum += sqrt(minD)
        }
        for (p2 in pts2) {
            var minD = Double.MAX_VALUE
            for (p1 in pts1) {
                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val d = dx * dx + dy * dy
                if (d < minD) minD = d
            }
            sum += sqrt(minD)
        }
        return sum / (2 * n)
    }

    private fun detectLoop(strokes: List<Stroke>): Boolean {
        if (strokes.isEmpty()) return false
        val b = computeWordBounds(strokes)
        val diag = hypot(b.right - b.left, b.bottom - b.top)
        if (diag < 4.0) return false

        for (s in strokes) {
            if (s.points.size >= 4) {
                val first = s.points.first()
                val last = s.points.last()
                val endDist = hypot(last.x - first.x, last.y - first.y)
                if (endDist <= diag * 0.35) return true
                if (hasSelfIntersection(s)) return true
            }
        }

        if (strokes.size == 2) {
            val s1 = strokes[0]
            val s2 = strokes[1]
            val dStartStart = hypot(s1.points.first().x - s2.points.first().x, s1.points.first().y - s2.points.first().y)
            val dEndEnd = hypot(s1.points.last().x - s2.points.last().x, s1.points.last().y - s2.points.last().y)
            val dStartEnd = hypot(s1.points.first().x - s2.points.last().x, s1.points.first().y - s2.points.last().y)
            val dEndStart = hypot(s1.points.last().x - s2.points.first().x, s1.points.last().y - s2.points.first().y)
            if ((dStartStart <= diag * 0.35 && dEndEnd <= diag * 0.35) ||
                (dStartEnd <= diag * 0.35 && dEndStart <= diag * 0.35)) {
                return true
            }
        }
        return false
    }

    private fun hasSelfIntersection(stroke: Stroke): Boolean {
        val pts = stroke.points
        val step = max(1, pts.size / 24)
        for (i in 0 until pts.size - 2 * step step step) {
            val a1 = pts[i]
            val a2 = pts[i + step]
            for (j in (i + 2 * step) until pts.size - step step step) {
                val b1 = pts[j]
                val b2 = pts[j + step]
                if (segmentsIntersect(a1.x, a1.y, a2.x, a2.y, b1.x, b1.y, b2.x, b2.y)) {
                    return true
                }
            }
        }
        return false
    }

    private fun segmentsIntersect(
        x1: Double, y1: Double, x2: Double, y2: Double,
        x3: Double, y3: Double, x4: Double, y4: Double,
    ): Boolean {
        fun ccw(ax: Double, ay: Double, bx: Double, by: Double, cx: Double, cy: Double): Boolean =
            (cy - ay) * (bx - ax) > (by - ay) * (cx - ax)

        return (ccw(x1, y1, x3, y3, x4, y4) != ccw(x2, y2, x3, y3, x4, y4)) &&
            (ccw(x1, y1, x2, y2, x3, y3) != ccw(x1, y1, x2, y2, x4, y4))
    }

    fun strokeBounds(stroke: Stroke): Bounds {
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (p in stroke.points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return Bounds(minX, minY, maxX, maxY)
    }

    fun computeWordBounds(strokes: List<Stroke>): Bounds {
        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (s in strokes) {
            val b = strokeBounds(s)
            if (b.left < minX) minX = b.left
            if (b.top < minY) minY = b.top
            if (b.right > maxX) maxX = b.right
            if (b.bottom > maxY) maxY = b.bottom
        }
        return Bounds(minX, minY, maxX, maxY)
    }
}
