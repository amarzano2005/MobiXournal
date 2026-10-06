package com.mobixournal.render

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.Tool
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Recognizes handwritten text from drawn stroke ink on document pages.
 * Clusters strokes into lines and words, classifies character glyphs using point-cloud matching
 * and geometric topology, and produces [HandwrittenWord] entries for search.
 */
object HandwritingRecognizer {

    private const val N_POINTS = 32
    private const val MIN_STROKE_POINTS = 2
    private const val MIN_STROKE_EXTENT = 2.0

    /** Indexes all pages in [doc], producing a [HandwritingIndex] for fast search. */
    fun index(doc: Document): HandwritingIndex {
        val pages = doc.pages.map { page ->
            val inkStrokes = page.layers.flatMap { layer ->
                layer.elements.filterIsInstance<Stroke>().filter {
                    it.tool != Tool.ERASER && it.points.size >= MIN_STROKE_POINTS
                }
            }
            recognizePage(inkStrokes)
        }
        return HandwritingIndex(pages)
    }

    /** Recognizes words from a list of strokes on a single page. */
    fun recognizePage(strokes: List<Stroke>): List<HandwrittenWord> {
        val validStrokes = strokes.filter { stroke ->
            val b = strokeBounds(stroke)
            b.right - b.left >= MIN_STROKE_EXTENT || b.bottom - b.top >= MIN_STROKE_EXTENT
        }
        if (validStrokes.isEmpty()) return emptyList()

        val lines = groupIntoLines(validStrokes)
        return buildList {
            for (line in lines) {
                val words = groupLineIntoWords(line)
                for (wordStrokes in words) {
                    val word = recognizeWord(wordStrokes)
                    if (word != null && word.text.isNotEmpty()) {
                        add(word)
                    }
                }
            }
        }
    }

    private fun groupIntoLines(strokes: List<Stroke>): List<List<Stroke>> {
        val sorted = strokes.sortedBy { strokeBounds(it).top }
        val lines = mutableListOf<MutableList<Stroke>>()

        for (stroke in sorted) {
            val sb = strokeBounds(stroke)
            val strokeMidY = (sb.top + sb.bottom) / 2.0
            val strokeH = max(sb.bottom - sb.top, 5.0)

            val matchingLine = lines.firstOrNull { line ->
                val lineH = line.map { strokeBounds(it) }.let { bs ->
                    bs.maxOf { it.bottom } - bs.minOf { it.top }
                }
                val lineMidY = line.map { strokeBounds(it) }.let { bs ->
                    (bs.minOf { it.top } + bs.maxOf { it.bottom }) / 2.0
                }
                abs(strokeMidY - lineMidY) <= max(strokeH, lineH) * 0.75
            }

            if (matchingLine != null) {
                matchingLine.add(stroke)
            } else {
                lines.add(mutableListOf(stroke))
            }
        }
        return lines
    }

    private fun groupLineIntoWords(lineStrokes: List<Stroke>): List<List<Stroke>> {
        val sorted = lineStrokes.sortedBy { strokeBounds(it).left }
        if (sorted.isEmpty()) return emptyList()

        val boundsList = sorted.map { strokeBounds(it) }
        val heights = boundsList.map { it.bottom - it.top }.sorted()
        val medianH = heights[heights.size / 2].coerceAtLeast(10.0)
        val wordBreakGap = (medianH * 0.55).coerceIn(8.0, 35.0)

        val words = mutableListOf<MutableList<Stroke>>()
        var currentWord = mutableListOf(sorted[0])
        var currentRight = boundsList[0].right

        for (i in 1 until sorted.size) {
            val s = sorted[i]
            val b = boundsList[i]
            val gap = b.left - currentRight

            if (gap > wordBreakGap) {
                words.add(currentWord)
                currentWord = mutableListOf(s)
                currentRight = b.right
            } else {
                currentWord.add(s)
                currentRight = max(currentRight, b.right)
            }
        }
        words.add(currentWord)
        return words
    }

    private fun recognizeWord(wordStrokes: List<Stroke>): HandwrittenWord? {
        if (wordStrokes.isEmpty()) return null
        val bounds = computeWordBounds(wordStrokes)

        val decomposedStrokes = wordStrokes.flatMap { segmentCursiveStroke(it) }
        val charClusters = segmentIntoCharClusters(decomposedStrokes)
        val sb = StringBuilder()
        var totalConfidence = 0.0

        for (cluster in charClusters) {
            val (char, confidence) = recognizeCharCluster(cluster)
            if (char != null) {
                sb.append(char)
                totalConfidence += confidence
            }
        }

        val recognizedText = sb.toString().trim()
        if (recognizedText.isEmpty()) return null

        val avgConfidence = totalConfidence / charClusters.size
        return HandwrittenWord(text = recognizedText, bounds = bounds, confidence = avgConfidence)
    }

    /**
     * Splits a wide continuous cursive stroke into letter sub-strokes by detecting
     * baseline valleys and ascending connecting ligatures.
     */
    private fun segmentCursiveStroke(stroke: Stroke): List<Stroke> {
        val b = strokeBounds(stroke)
        val w = b.right - b.left
        val h = b.bottom - b.top
        val pts = stroke.points
        if (h < 5.0 || w / h < 1.3 || pts.size < 8) {
            return listOf(stroke)
        }

        val baselineY = b.top + 0.55 * h
        val splitIndices = mutableListOf<Int>()
        var lastSplitX = b.left

        for (i in 2 until pts.size - 2) {
            val p = pts[i]
            val prev = pts[i - 1]
            val next = pts[i + 1]

            val isValley = p.y >= baselineY && p.y >= prev.y && p.y >= next.y
            val movesRight = next.x >= prev.x
            val distFromLast = p.x - lastSplitX
            val distToEnd = b.right - p.x

            if (isValley && movesRight && distFromLast >= h * 0.35 && distToEnd >= h * 0.35) {
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

            // A stroke belongs to the same character if it starts at the same horizontal origin
            // or deeply overlaps within the existing character width (like crossbars, dots, accents)
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

    private fun recognizeCharCluster(strokes: List<Stroke>): Pair<Char?, Double> {
        val normStrokes = strokes.map { stroke ->
            stroke.points.map { NormPoint(it.x, it.y) }
        }
        val candidatePoints = HandwritingTemplates.resampleAndNormalize(normStrokes, N_POINTS)
        val candidateHasLoop = detectLoop(strokes)

        var bestChar: Char? = null
        var bestScore = -1.0

        for (tmpl in HandwritingTemplates.all) {
            var score = scoreMatch(candidatePoints, tmpl.points)

            // Adjust score for stroke count similarity
            if (tmpl.strokeCount == strokes.size) {
                score += 0.06
            } else if (abs(tmpl.strokeCount - strokes.size) > 2) {
                score -= 0.12
            }

            // Adjust for loop presence
            if (tmpl.hasLoop == candidateHasLoop) {
                score += 0.08
            } else {
                score -= 0.15
            }

            if (score > bestScore) {
                bestScore = score
                bestChar = tmpl.char
            }
        }

        return if (bestScore >= 0.40) {
            bestChar to bestScore.coerceIn(0.0, 1.0)
        } else {
            null to 0.0
        }
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

        // Multi-stroke loop check: e.g. 2 strokes meeting at endpoints (like 'D', 'P', 'B', 'A')
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

    private fun strokeBounds(stroke: Stroke): Bounds {
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

    private fun computeWordBounds(strokes: List<Stroke>): Bounds {
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
