package com.mobixournal.render

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Document

/**
 * Per-page index of recognized handwritten words extracted from stroke ink.
 *
 * The index is the shape search wants — "what does page *n* say?" — and both shells can build one:
 * the tablet reads ink with its model, a plain JVM with [FallbackInkEngine]. What the engine reads
 * through is [InkWordEngine] ([InkEngines.platform]); what a call site passes is nothing at all, unless
 * it wants to answer as a specific engine (a test does).
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
            engine: InkWordEngine = InkEngines.platform,
        ): HandwritingIndex =
            HandwritingRecognizer.index(doc, onProgress, isCancelled, engine)

        /**
         * Builds a [HandwritingIndex] by reading [source] **one page at a time** — the door a paged
         * `.mobix` needs, where the whole document must never be materialised to search it. A cancelled
         * walk answers an empty index.
         */
        fun build(
            source: PageSource,
            onProgress: ((Int, Int) -> Unit)? = null,
            isCancelled: () -> Boolean = { false },
            engine: InkWordEngine = InkEngines.platform,
        ): HandwritingIndex =
            HandwritingRecognizer.index(source, onProgress, isCancelled, engine)
    }
}
