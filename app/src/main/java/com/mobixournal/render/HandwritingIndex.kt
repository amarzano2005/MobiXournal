package com.mobixournal.render

import com.mobixournal.format.model.Document

/**
 * One recognized handwritten word on a document page with its pt bounding box and confidence score.
 */
data class HandwrittenWord(
    val text: String,
    val bounds: Bounds,
    val confidence: Double = 1.0,
    val candidates: List<String> = listOf(text),
)

/**
 * Per-page index of recognized handwritten words extracted from stroke ink.
 * Mirrors [PdfTextIndex] for search and navigation.
 */
class HandwritingIndex(private val pages: List<List<HandwrittenWord>>) {

    /** Returns recognized handwritten words on the given 0-based page index. */
    fun words(page: Int): List<HandwrittenWord> = pages.getOrElse(page) { emptyList() }

    /** True when at least one page contains recognized handwritten words. */
    val hasAnyText: Boolean = pages.any { it.isNotEmpty() }

    companion object {
        /** Builds a [HandwritingIndex] for all pages in [doc] using [HandwritingRecognizer]. */
        fun build(
            doc: Document,
            onProgress: ((Int, Int) -> Unit)? = null,
            isCancelled: () -> Boolean = { false },
        ): HandwritingIndex =
            HandwritingRecognizer.index(doc, onProgress, isCancelled)
    }
}
