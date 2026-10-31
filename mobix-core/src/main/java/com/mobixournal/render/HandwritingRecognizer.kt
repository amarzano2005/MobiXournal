package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.Tool
import kotlin.math.abs
import kotlin.math.max

/**
 * Recognizes handwritten text from drawn stroke ink on document pages.
 *
 * Clusters strokes into lines and words and asks an [InkWordEngine] what each word says — the
 * platform's model where there is one ([InkEngines.platform]), with [FallbackInkEngine] behind it —
 * and produces [HandwrittenWord] entries for search.
 *
 * The **segmentation** is shared and pure: which strokes form a line, where a word breaks, and the box
 * it occupies are facts about the ink, not about the model reading it. That is why this half lives in
 * `mobix-core` and the desktop window can search handwriting too, with the fallback engine.
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
        private var sumMidY = 0.0
        private var sumHeight = 0.0

        fun add(bs: BoundedStroke) {
            strokes.add(bs)
            sumMidY += bs.midY
            sumHeight += bs.height
        }

        val avgHeight: Double get() = if (strokes.isEmpty()) 15.0 else sumHeight / strokes.size
        val avgMidY: Double get() = if (strokes.isEmpty()) 0.0 else sumMidY / strokes.size

        fun matches(strokeMidY: Double, strokeH: Double): Boolean {
            val h = max(strokeH, avgHeight)
            return abs(strokeMidY - avgMidY) <= h * 0.65
        }
    }

    /** Indexes all pages in [doc], producing a [HandwritingIndex] for fast search. */
    fun index(
        doc: Document,
        onProgress: ((Int, Int) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
        engine: InkWordEngine = InkEngines.platform,
    ): HandwritingIndex {
        val total = doc.pages.size
        val pages = ArrayList<List<HandwrittenWord>>(total)
        for (pageIndex in 0 until total) {
            if (isCancelled()) return HandwritingIndex(emptyList())
            onProgress?.invoke(pageIndex + 1, total)
            pages.add(recognizePage(inkStrokesOf(doc.pages[pageIndex]), isCancelled, engine))
        }
        return HandwritingIndex(pages)
    }

    /**
     * Indexes [source] **one page at a time**, materialising each page only long enough to read its ink.
     *
     * This is the door a paged `.mobix` needs: a 200-page notebook is recognised page by page, so the
     * source's own cache window decides what stays in memory. Cancelling mid-walk answers an empty
     * index — a partial one would silently miss pages, and a caller that gave up has no use for it.
     */
    fun index(
        source: PageSource,
        onProgress: ((Int, Int) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
        engine: InkWordEngine = InkEngines.platform,
    ): HandwritingIndex {
        val total = source.pageCount
        val pages = ArrayList<List<HandwrittenWord>>(total)
        for (index in 0 until total) {
            if (isCancelled()) return HandwritingIndex(emptyList())
            onProgress?.invoke(index + 1, total)
            pages.add(recognizePage(inkStrokesOf(source.page(index)), isCancelled, engine))
        }
        if (isCancelled()) return HandwritingIndex(emptyList())
        return HandwritingIndex(pages)
    }

    /**
     * The words written on **one already-open page** — what a caller that walks a document itself needs,
     * so a paged `.mobix` is recognised one page at a time and a page already read is read once.
     */
    fun recognize(
        page: Page,
        isCancelled: () -> Boolean = { false },
        engine: InkWordEngine = InkEngines.platform,
    ): List<HandwrittenWord> = recognizePage(inkStrokesOf(page), isCancelled, engine)

    /** The strokes on [page] that can be handwriting: the pen's and the highlighter's, erasures never. */
    private fun inkStrokesOf(page: Page): List<Stroke> = page.layers.flatMap { layer ->
        layer.elements.filterIsInstance<Stroke>().filter {
            it.tool != Tool.ERASER && it.points.size >= MIN_STROKE_POINTS
        }
    }

    private fun isHandwritingCandidate(stroke: Stroke, b: Bounds): Boolean {
        if (stroke.points.size < MIN_STROKE_POINTS) return false
        val w = b.right - b.left
        val h = b.bottom - b.top
        return w >= MIN_STROKE_EXTENT || h >= MIN_STROKE_EXTENT
    }

    /** Recognizes words from a list of strokes on a single page. */
    fun recognizePage(
        strokes: List<Stroke>,
        isCancelled: () -> Boolean = { false },
        engine: InkWordEngine = InkEngines.platform,
    ): List<HandwrittenWord> {
        val bounded = ArrayList<BoundedStroke>(strokes.size)
        for (stroke in strokes) {
            val b = strokeBounds(stroke)
            if (isHandwritingCandidate(stroke, b)) {
                bounded.add(BoundedStroke(stroke, b))
            }
        }
        if (bounded.isEmpty() || isCancelled()) return emptyList()

        val lines = groupIntoLines(bounded).sortedBy { it.avgMidY }
        return buildList {
            for (line in lines) {
                if (isCancelled()) return emptyList()
                val words = groupLineIntoWords(line)
                // One call for the whole line where the engine can take it: a bridged model pays for
                // every call, and it reads a connected line better than an isolated word.
                val bulk = (engine as? InkLineEngine)?.let { reader ->
                    runCatching { reader.recognizeLine(words, words.map { computeWordBounds(it) }) }
                        .getOrNull()
                }
                words.forEachIndexed { index, wordStrokes ->
                    if (isCancelled()) return emptyList()
                    val read = bulk?.takeIf { it.size == words.size }?.get(index)
                    val word = read ?: recognizeWord(wordStrokes, if (bulk == null) engine else null)
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

    /**
     * What the word [wordStrokes] say: the engine's reading, or [FallbackInkEngine]'s when the engine
     * has none (a model still downloading, a runtime with no model at all).
     */
    fun recognizeWord(
        wordStrokes: List<Stroke>,
        engine: InkWordEngine? = InkEngines.platform,
    ): HandwrittenWord? {
        if (wordStrokes.isEmpty()) return null
        val bounds = computeWordBounds(wordStrokes)
        // A null engine is "a line call already answered this line and left this word unread": the
        // fallback is then the only reader left, and asking the model again would pay its call twice.
        val read = engine?.recognize(wordStrokes, bounds)
        if (read != null && read.text.isNotEmpty()) return read
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
