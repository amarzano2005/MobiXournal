package com.mobixournal.render

/**
 * One word a **recogniser** read out of a page's ink, with its box in page-local points.
 *
 * The type lives here, not beside the recogniser, because the *search* needs it and search is shared:
 * the tablet reads ink with an Android model, the PC window reads it with the pure fallback, and a word
 * either way is still a string and a rectangle.
 */
data class HandwrittenWord(
    val text: String,
    val bounds: Bounds,
    val confidence: Double = 1.0,
    val candidates: List<String> = listOf(text),
)

/**
 * What [DocumentSearch] asks a shell for when searching handwriting: the recognised words of a page.
 *
 * The seam exists because recognition is the one half of search that cannot be shared all the way down —
 * a model needs a runtime, so only the *reading* is platform-specific ([InkWordEngine]). Segmentation, the
 * per-page index and the fallback engine are shared, which is why both shells can pass one of these; a
 * shell that keeps no index at all passes null, and search answers for the text the document actually
 * carries: text boxes, math blocks and an imported PDF's words.
 */
interface SearchableHandwriting {

    /** The recognised words on 0-based page [page], in reading order; empty when nothing was read. */
    fun words(page: Int): List<HandwrittenWord>

    /** True when at least one page carries a recognised word — what a shell asks before showing a hit count. */
    val hasAnyText: Boolean
}
