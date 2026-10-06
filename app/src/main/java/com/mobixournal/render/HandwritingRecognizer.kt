package com.mobixournal.render

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.Tool
import kotlin.math.abs
import kotlin.math.max

/**
 * Recognizes handwritten text from drawn stroke ink on document pages.
 * Clusters strokes into lines and words, applies on-device neural recognition via
 * [MlKitInkEngine] (with robust [FallbackInkEngine] fallback), and produces [HandwrittenWord]
 * entries for search.
 */
object HandwritingRecognizer {

    private const val MIN_STROKE_POINTS = 2
    private const val MIN_STROKE_EXTENT = 2.0

    private class BoundedStroke(
        val stroke: Stroke,
        val bounds: Bounds,
    ) {
        val top: Double get() = bounds.top
        val bottom: Double get() = bounds.bottom
        val left: Double get() = bounds.left
        val right: Double get() = bounds.right
        val midY: Double = (bounds.top + bounds.bottom) / 2.0
        val height: Double = max(bounds.bottom - bounds.top, 5.0)
    }

    private class LineCluster {
        val strokes = mutableListOf<BoundedStroke>()
        var minTop = Double.MAX_VALUE
        var maxBottom = -Double.MAX_VALUE

        fun add(bs: BoundedStroke) {
            strokes.add(bs)
            if (bs.top < minTop) minTop = bs.top
            if (bs.bottom > maxBottom) maxBottom = bs.bottom
        }

        val height: Double get() = max(maxBottom - minTop, 5.0)
        val midY: Double get() = (minTop + maxBottom) / 2.0

        fun matches(strokeMidY: Double, strokeH: Double): Boolean {
            val h = max(strokeH, height)
            return abs(strokeMidY - midY) <= h * 0.75
        }
    }

    /** Indexes all pages in [doc], producing a [HandwritingIndex] for fast search. */
    fun index(doc: Document, isCancelled: () -> Boolean = { false }): HandwritingIndex {
        val pages = doc.pages.map { page ->
            if (isCancelled()) return HandwritingIndex(emptyList())
            val inkStrokes = page.layers.flatMap { layer ->
                layer.elements.filterIsInstance<Stroke>().filter {
                    it.tool != Tool.ERASER && it.points.size >= MIN_STROKE_POINTS
                }
            }
            recognizePage(inkStrokes, isCancelled)
        }
        if (isCancelled()) return HandwritingIndex(emptyList())
        return HandwritingIndex(pages)
    }

    private fun isHandwritingCandidate(stroke: Stroke, b: Bounds): Boolean {
        if (stroke.points.size < MIN_STROKE_POINTS) return false
        val w = b.right - b.left
        val h = b.bottom - b.top
        return w >= MIN_STROKE_EXTENT || h >= MIN_STROKE_EXTENT
    }

    /** Recognizes words from a list of strokes on a single page. */
    fun recognizePage(strokes: List<Stroke>, isCancelled: () -> Boolean = { false }): List<HandwrittenWord> {
        val bounded = ArrayList<BoundedStroke>(strokes.size)
        for (stroke in strokes) {
            val b = strokeBounds(stroke)
            if (isHandwritingCandidate(stroke, b)) {
                bounded.add(BoundedStroke(stroke, b))
            }
        }
        if (bounded.isEmpty() || isCancelled()) return emptyList()

        val lines = groupIntoLines(bounded)
        return buildList {
            for (line in lines) {
                if (isCancelled()) return emptyList()
                val words = groupLineIntoWords(line)
                for (wordStrokes in words) {
                    if (isCancelled()) return emptyList()
                    val word = recognizeWord(wordStrokes)
                    if (word != null && word.text.isNotEmpty()) {
                        add(word)
                    }
                }
            }
        }
    }

    private fun groupIntoLines(boundedStrokes: List<BoundedStroke>): List<LineCluster> {
        val sorted = boundedStrokes.sortedBy { it.top }
        val lines = mutableListOf<LineCluster>()

        for (bs in sorted) {
            val matchingLine = lines.firstOrNull { it.matches(bs.midY, bs.height) }
            if (matchingLine != null) {
                matchingLine.add(bs)
            } else {
                val newLine = LineCluster()
                newLine.add(bs)
                lines.add(newLine)
            }
        }
        return lines
    }

    private fun groupLineIntoWords(line: LineCluster): List<List<Stroke>> {
        val sorted = line.strokes.sortedBy { it.left }
        if (sorted.isEmpty()) return emptyList()

        val heights = sorted.map { it.bottom - it.top }.sorted()
        val medianH = heights[heights.size / 2].coerceAtLeast(10.0)
        val wordBreakGap = (medianH * 0.55).coerceIn(8.0, 35.0)

        val words = mutableListOf<MutableList<Stroke>>()
        var currentWord = mutableListOf(sorted[0].stroke)
        var currentRight = sorted[0].right

        for (i in 1 until sorted.size) {
            val bs = sorted[i]
            val gap = bs.left - currentRight

            if (gap > wordBreakGap) {
                words.add(currentWord)
                currentWord = mutableListOf(bs.stroke)
                currentRight = bs.right
            } else {
                currentWord.add(bs.stroke)
                currentRight = max(currentRight, bs.right)
            }
        }
        words.add(currentWord)
        return words
    }

    private fun recognizeWord(wordStrokes: List<Stroke>): HandwrittenWord? {
        if (wordStrokes.isEmpty()) return null
        val bounds = computeWordBounds(wordStrokes)

        // 1. Compute fallback word (fast offline template matcher)
        val fallbackWord = FallbackInkEngine.recognizeWord(wordStrokes, bounds)

        // 2. Try ML Kit Digital Ink Recognition if available on device
        if (MlKitInkEngine.isAvailable()) {
            val mlCandidates = MlKitInkEngine.recognizeWord(wordStrokes)
            if (mlCandidates.isNotEmpty()) {
                val combinedCandidates = (mlCandidates + (fallbackWord?.candidates ?: emptyList())).distinct()
                return HandwrittenWord(
                    text = mlCandidates.first(),
                    bounds = bounds,
                    confidence = 1.0,
                    candidates = combinedCandidates,
                )
            }
        }

        // 3. Fallback to robust offline template engine
        return fallbackWord
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
