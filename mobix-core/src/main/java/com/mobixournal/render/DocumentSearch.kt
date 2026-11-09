package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.MathBlockElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TextElement
import java.text.Normalizer
import java.util.Locale

/**
 * The document's **search engine**, shared by both shells: what a query matches, where the match sits
 * in page points, and the snippet the chrome shows for it.
 *
 * It belongs here rather than beside either shell because search is a fact about the *document*, not
 * about a window: the tablet's field and the PC's band must find the same things at the same places,
 * or "search the document" means two different documents. Everything in this file is platform-free —
 * pages come through a [PageSource], so a `.mobix` opened page by page is searched one page at a time
 * and never inflated whole (`docs/desktop-workstation.md`, the lazy walk).
 *
 * Four kinds of text are searched: authored text boxes, **math blocks** (a formula's source, so a
 * written `\frac{1}{2}` can be found again), an imported background PDF's text layer, and handwritten ink
 * ([SearchableHandwriting]). Only the *reading* of ink is platform-specific ([InkWordEngine]): the tablet
 * plugs ML Kit's model in, the window keeps the pure fallback, and this engine has no opinion about which
 * one answered.
 */

/** One search match in page-local point geometry, ready for highlight painting and navigation. */
data class SearchHit(
    val pageIndex: Int,
    val boxes: List<Bounds>,
    /** What the match reads as — the line, the words or the formula, trimmed for the chrome's list. */
    val snippet: String = "",
)

/** Surface-facing count state for the editor chrome. [current] is one-based, or zero when empty. */
data class SearchStatus(
    val current: Int = 0,
    val total: Int = 0,
)

/**
 * Finds text in authored text boxes, in math blocks' sources, in the extracted background-PDF text
 * layer, and in handwritten ink strokes. Typed text uses the same rough box metrics as element
 * hit-testing; PDF text and handwriting highlight whole words that overlap the matched character span.
 */
object DocumentSearch {

    private const val TEXT_LINE_H = 1.3

    /** How long a [SearchHit.snippet] may be, in characters, before it is cut — one row of a list. */
    private const val SNIPPET_MAX = 80

    /** How many strokes [doc] holds — walked through its page surface, a page at a time. */
    fun totalStrokeCount(doc: Document): Int = totalStrokeCount(PageSource.of(doc.pages))

    /** How many strokes a document behind [source] holds, counting one page at a time. */
    fun totalStrokeCount(source: PageSource): Int {
        var total = 0
        for (pageIndex in 0 until source.pageCount) {
            val page = source.page(pageIndex)
            total += page.layers.sumOf { layer -> layer.elements.count { it is Stroke } }
        }
        return total
    }

    fun find(
        doc: Document,
        pdfTextIndex: PdfTextIndex?,
        rawQuery: String,
        handwriting: SearchableHandwriting? = null,
        checkCancelled: () -> Unit = {},
    ): List<SearchHit> = find(PageSource.of(doc.pages), pdfTextIndex, rawQuery, handwriting, checkCancelled)

    /**
     * The same search over a document's **page surface** (`PageSource`): pages are read one at a
     * time, so a `.mobix` opened page by page is searched without being materialised whole — the
     * source's window holds the pages being looked at and releases them behind the walk.
     */
    fun find(
        source: PageSource,
        pdfTextIndex: PdfTextIndex?,
        rawQuery: String,
        handwriting: SearchableHandwriting? = null,
        checkCancelled: () -> Unit = {},
    ): List<SearchHit> =
        find(source, { pageIndex -> pdfTextIndex?.words(pageIndex).orEmpty() }, rawQuery, handwriting, checkCancelled)

    /**
     * The same search when the PDF's words are **not** indexed by document page number.
     *
     * A shell that can **reorder** pages cannot hand over a [PdfTextIndex] whose page *n* is document page
     * *n*: a page carries its own `Background.Pdf.pageNo`, and after a reorder that number is not where the
     * page sits in the document. [pdfWordsFor] answers the words of a **document** page, so the caller owns
     * that mapping and the engine keeps its one walk over the pages.
     */
    fun find(
        source: PageSource,
        pdfWordsFor: (Int) -> List<PdfWord>,
        rawQuery: String,
        handwriting: SearchableHandwriting? = null,
        checkCancelled: () -> Unit = {},
    ): List<SearchHit> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        val needle = query.lowercase(Locale.ROOT)
        return buildList {
            for (pageIndex in 0 until source.pageCount) {
                checkCancelled()
                val page = source.page(pageIndex)
                addAll(pdfHits(pdfWordsFor(pageIndex), pageIndex, needle))
                checkCancelled()
                handwriting?.let { addAll(handwritingHits(it, pageIndex, needle)) }
                for (layer in page.layers) {
                    for (element in layer.elements) {
                        checkCancelled()
                        when (element) {
                            is TextElement -> addAll(textHits(pageIndex, element, needle))
                            is MathBlockElement -> addAll(mathHits(pageIndex, element, needle))
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    private fun textHits(pageIndex: Int, text: TextElement, needle: String): List<SearchHit> =
        buildList {
            val size = text.size
            val lineH = size * TEXT_LINE_H
            for ((lineIndex, line) in text.content.split("\n").withIndex()) {
                val haystack = line.lowercase(Locale.ROOT)
                var start = haystack.indexOf(needle)
                while (start >= 0) {
                    val top = text.y + lineIndex * lineH
                    // The highlight starts and ends where the drawn glyphs do: the width before the
                    // match and the width of the match, both from the measured table.
                    val left = text.x + TextAdvance.pt(line.substring(0, start), size)
                    add(
                        SearchHit(
                            pageIndex = pageIndex,
                            boxes = listOf(
                                Bounds(
                                    left = left,
                                    top = top,
                                    right = left + TextAdvance.pt(line.substring(start, start + needle.length), size),
                                    bottom = top + lineH,
                                ),
                            ),
                            snippet = trimSnippet(line),
                        ),
                    )
                    start = haystack.indexOf(needle, start + 1)
                }
            }
        }

    /**
     * A **math block** matched on its source.
     *
     * A formula is text the reader typed, so leaving it out of search would make the field powerless
     * exactly where a notebook is most likely to hold the thing being looked for. Its box is the
     * block's own box — the rendered formula's ink is not mapped back to characters, so the whole block
     * is the match, which is also what makes the highlight read as "this formula".
     */
    private fun mathHits(pageIndex: Int, block: MathBlockElement, needle: String): List<SearchHit> {
        val haystack = block.source.lowercase(Locale.ROOT)
        if (haystack.isEmpty()) return emptyList()
        val box = Bounds(block.left, block.top, block.right, block.bottom)
        return buildList {
            var start = haystack.indexOf(needle)
            while (start >= 0) {
                add(SearchHit(pageIndex = pageIndex, boxes = listOf(box), snippet = trimSnippet(block.source)))
                start = haystack.indexOf(needle, start + 1)
            }
        }
    }

    private fun pdfHits(words: List<PdfWord>, pageIndex: Int, needle: String): List<SearchHit> {
        if (words.isEmpty()) return emptyList()
        val text = StringBuilder()
        val ranges = ArrayList<IntRange>(words.size)
        for (word in words) {
            if (text.isNotEmpty()) text.append(' ')
            val start = text.length
            text.append(word.text)
            ranges += start until text.length
        }
        val haystack = text.toString().lowercase(Locale.ROOT)
        return buildList {
            var start = haystack.indexOf(needle)
            while (start >= 0) {
                val span = start until (start + needle.length)
                val matched = words.indices.filter { ranges[it].overlaps(span) }
                val boxes = matched.map { words[it].toBounds() }
                if (boxes.isNotEmpty()) {
                    add(
                        SearchHit(
                            pageIndex = pageIndex,
                            boxes = boxes,
                            snippet = trimSnippet(matched.joinToString(" ") { words[it].text }),
                        ),
                    )
                }
                start = haystack.indexOf(needle, start + 1)
            }
        }
    }

    private fun handwritingHits(index: SearchableHandwriting, pageIndex: Int, rawNeedle: String): List<SearchHit> {
        val words = index.words(pageIndex)
        if (words.isEmpty()) return emptyList()

        val needleNorm = normalizeText(rawNeedle)
        if (needleNorm.isEmpty()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        val matchedWordIndices = mutableSetOf<Int>()

        // 1. Phrase / substring matching across the page word sequence
        val normalizedWords = words.map { normalizeText(it.text) }
        val phraseText = StringBuilder()
        val wordRanges = ArrayList<IntRange>(words.size)
        for (w in normalizedWords) {
            if (phraseText.isNotEmpty()) phraseText.append(' ')
            val start = phraseText.length
            phraseText.append(w)
            wordRanges += start until phraseText.length
        }
        val haystack = phraseText.toString()
        var start = haystack.indexOf(needleNorm)
        while (start >= 0) {
            val span = start until (start + needleNorm.length)
            val matchingIndices = words.indices.filter { wordRanges[it].overlaps(span) }
            val boxes = matchingIndices.map { words[it].bounds }
            if (boxes.isNotEmpty()) {
                hits.add(
                    SearchHit(
                        pageIndex = pageIndex,
                        boxes = boxes,
                        snippet = trimSnippet(matchingIndices.joinToString(" ") { words[it].text }),
                    ),
                )
                matchedWordIndices.addAll(matchingIndices)
            }
            start = haystack.indexOf(needleNorm, start + 1)
        }

        // 2. Candidate & tightly bounded fuzzy matching for individual words
        for (i in words.indices) {
            if (i in matchedWordIndices) continue
            val word = words[i]
            val primaryNorm = normalizeText(word.text)

            // A. Check candidates for exact match, prefix match, or targeted substring match
            val matchesCandidate = word.candidates.any { candidate ->
                val candNorm = normalizeText(candidate)
                when {
                    candNorm == needleNorm -> true
                    needleNorm.length >= 3 && candNorm.startsWith(needleNorm) -> true
                    needleNorm.length >= 4 && candNorm.contains(needleNorm) && candNorm.length <= needleNorm.length + 3 -> true
                    else -> false
                }
            }

            // B. Tightly bounded fuzzy matching (distance 1) applied ONLY to primary recognized text
            val matchesPrimaryFuzzily = needleNorm.length >= 3 &&
                kotlin.math.abs(primaryNorm.length - needleNorm.length) <= 1 &&
                levenshtein(primaryNorm, needleNorm) <= 1

            if (matchesCandidate || matchesPrimaryFuzzily) {
                hits.add(
                    SearchHit(
                        pageIndex = pageIndex,
                        boxes = listOf(word.bounds),
                        snippet = trimSnippet(word.text),
                    ),
                )
            }
        }
        return hits
    }

    /** Levenshtein distance, with the two-row form: only a distance of 0 or 1 is ever asked for. */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    /**
     * A word as the reader meant it: lower-case and without diacritics, so `caffe` finds `caffè`.
     *
     * A recogniser guesses at the ink and is rarely certain about an accent; the accent is not what
     * the person is searching for, so it must not be what stops the match.
     */
    private fun normalizeText(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return buildString(decomposed.length) {
            for (ch in decomposed) {
                if (!isDiacritic(ch)) append(ch)
            }
        }.lowercase(Locale.ROOT).trim()
    }

    /** [text] as one row of a results list: trimmed, and cut with an ellipsis past [SNIPPET_MAX]. */
    private fun trimSnippet(text: String): String {
        val line = text.trim().replace('\n', ' ')
        return if (line.length <= SNIPPET_MAX) line else line.take(SNIPPET_MAX - 1) + "…"
    }

    private fun PdfWord.toBounds(): Bounds = Bounds(left, top, right, bottom)

    private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last
}

/** `true` for the combining marks a diacritic decomposes into; `é` is `e` plus one of these. */
private fun isDiacritic(ch: Char): Boolean {
    val type = Character.getType(ch)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt()
}
