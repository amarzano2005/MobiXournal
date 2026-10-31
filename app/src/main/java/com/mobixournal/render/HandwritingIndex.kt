package com.mobixournal.render

import com.mobixournal.format.model.Document

/**
 * Per-page index of recognized handwritten words extracted from stroke ink.
 *
 * The **word itself** ([HandwrittenWord]) and the interface the shared search asks for
 * ([SearchableHandwriting]) live in mobix-core: recognition is the tablet's own business, but what a
 * recognised word *is* has to mean the same thing to the window, which searches the same engine
 * without ever recognising anything. This class is the index plus the recogniser's door.
 */
class HandwritingIndex(private val pages: List<List<HandwrittenWord>>) : SearchableHandwriting {

    /** Returns recognized handwritten words on the given 0-based page index. */
    override fun words(page: Int): List<HandwrittenWord> = pages.getOrElse(page) { emptyList() }

    /** True when at least one page contains recognized handwritten words. */
    override val hasAnyText: Boolean = pages.any { it.isNotEmpty() }


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
