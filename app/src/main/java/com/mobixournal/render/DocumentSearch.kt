package com.mobixournal.render

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.TextElement
import java.util.Locale

/** One search match in page-local point geometry, ready for highlight painting and navigation. */
data class SearchHit(
    val pageIndex: Int,
    val boxes: List<Bounds>,
)

/** Surface-facing count state for the editor chrome. [current] is one-based, or zero when empty. */
data class SearchStatus(
    val current: Int = 0,
    val total: Int = 0,
)

/**
 * Finds text in authored text boxes, in the extracted background-PDF text layer, and in handwritten
 * ink strokes. Typed text uses the same rough box metrics as element hit-testing; PDF text and
 * handwriting highlight whole words that overlap the matched character span.
 */
object DocumentSearch {

    private const val TEXT_CHAR_W = 0.62
    private const val TEXT_LINE_H = 1.3

    fun find(
        doc: Document,
        pdfTextIndex: PdfTextIndex?,
        rawQuery: String,
        handwritingIndex: HandwritingIndex? = null,
    ): List<SearchHit> {
        val query = rawQuery.trim()
        if (query.isEmpty()) return emptyList()
        val needle = query.lowercase(Locale.ROOT)
        val hwIndex = handwritingIndex ?: HandwritingIndex.build(doc)
        return buildList {
            for ((pageIndex, page) in doc.pages.withIndex()) {
                pdfTextIndex?.let { addAll(pdfHits(it, pageIndex, needle)) }
                addAll(handwritingHits(hwIndex, pageIndex, needle))
                for (layer in page.layers) {
                    for (element in layer.elements) {
                        if (element is TextElement) addAll(textHits(pageIndex, element, needle))
                    }
                }
            }
        }
    }

    private fun textHits(pageIndex: Int, text: TextElement, needle: String): List<SearchHit> =
        buildList {
            val charW = text.size * TEXT_CHAR_W
            val lineH = text.size * TEXT_LINE_H
            for ((lineIndex, line) in text.content.split("\n").withIndex()) {
                val haystack = line.lowercase(Locale.ROOT)
                var start = haystack.indexOf(needle)
                while (start >= 0) {
                    val top = text.y + lineIndex * lineH
                    add(
                        SearchHit(
                            pageIndex = pageIndex,
                            boxes = listOf(
                                Bounds(
                                    left = text.x + start * charW,
                                    top = top,
                                    right = text.x + (start + needle.length) * charW,
                                    bottom = top + lineH,
                                ),
                            ),
                        ),
                    )
                    start = haystack.indexOf(needle, start + 1)
                }
            }
        }

    private fun pdfHits(index: PdfTextIndex, pageIndex: Int, needle: String): List<SearchHit> {
        val words = index.words(pageIndex)
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
                val boxes = words.indices
                    .filter { ranges[it].overlaps(span) }
                    .map { words[it].toBounds() }
                if (boxes.isNotEmpty()) add(SearchHit(pageIndex, boxes))
                start = haystack.indexOf(needle, start + 1)
            }
        }
    }

    private fun handwritingHits(index: HandwritingIndex, pageIndex: Int, rawNeedle: String): List<SearchHit> {
        val words = index.words(pageIndex)
        if (words.isEmpty()) return emptyList()

        val needleNorm = normalizeText(rawNeedle)
        val hits = mutableListOf<SearchHit>()
        val matchedWordIndices = mutableSetOf<Int>()

        // 1. Phrase / Substring matching across the page word sequence
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
                hits.add(SearchHit(pageIndex, boxes))
                matchedWordIndices.addAll(matchingIndices)
            }
            start = haystack.indexOf(needleNorm, start + 1)
        }

        // 2. Multi-candidate & fuzzy word matching for individual words
        for (i in words.indices) {
            if (i in matchedWordIndices) continue
            val word = words[i]
            val matchesCandidate = word.candidates.any { candidate ->
                val candNorm = normalizeText(candidate)
                if (candNorm.contains(needleNorm)) return@any true
                if (needleNorm.length >= 3) {
                    val maxDist = if (needleNorm.length >= 7) 2 else 1
                    levenshteinDistance(candNorm, needleNorm, maxDist) <= maxDist
                } else {
                    false
                }
            }
            if (matchesCandidate) {
                hits.add(SearchHit(pageIndex, listOf(word.bounds)))
                matchedWordIndices.add(i)
            }
        }

        return hits
    }

    private fun normalizeText(input: String): String =
        java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase(Locale.ROOT)

    private fun levenshteinDistance(s1: String, s2: String, maxDistance: Int = 2): Int {
        if (s1 == s2) return 0
        if (kotlin.math.abs(s1.length - s2.length) > maxDistance) return maxDistance + 1
        val len1 = s1.length
        val len2 = s2.length
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)
        for (i in 1..len1) {
            curr[0] = i
            var minInRow = curr[0]
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost,
                )
                minInRow = minOf(minInRow, curr[j])
            }
            if (minInRow > maxDistance) return maxDistance + 1
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[len2]
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && last >= other.first

    private fun PdfWord.toBounds(): Bounds = Bounds(left, top, right, bottom)
}
