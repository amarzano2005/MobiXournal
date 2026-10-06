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

        // 1. Try ML Kit Digital Ink Recognition if available on device
        if (MlKitInkEngine.isAvailable()) {
            val mlCandidates = MlKitInkEngine.recognizeWord(wordStrokes)
            if (mlCandidates.isNotEmpty()) {
                return HandwrittenWord(
                    text = mlCandidates.first(),
                    bounds = bounds,
                    confidence = 1.0,
                    candidates = mlCandidates,
                )
            }
        }

        // 2. Fallback to robust offline template engine
        return FallbackInkEngine.recognizeWord(wordStrokes, bounds)
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
