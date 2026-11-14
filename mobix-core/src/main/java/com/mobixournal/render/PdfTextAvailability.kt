package com.mobixournal.render

/**
 * **Whether Select text (PDF) can work on the page in view**, in the four states a reader can be in — and the
 * one sentence each of them owes.
 *
 * The slot used to answer with a single line for everything that was not text: a page still being read, a
 * scanned page and a file whose text could not be read all said "No text on this page", so a reader who
 * pressed too early was told their document had no text, and a reader whose PDF failed was told the same. The
 * four states are different facts and deserve different words:
 *
 * - **idle** — the tool is not the one in hand, or the page is not a PDF page: the slot says nothing at all.
 * - **indexing** — the extraction is running: "Reading this PDF's text…" and no retry, because it is working.
 * - **ready** — the words for this sheet are there; with none on it the page is a **picture of text** and the
 *   sentence says so, which is the scanned-page case the app has no OCR for.
 * - **failed** — the read broke: a sentence that says so and a **Try again** next to it, since the same file
 *   may well be readable on the second try (a mount that was not ready, a name that changed under the tab).
 *
 * Pure, so the copy and the transitions are JVM-tested once and both shells say the same thing.
 */
enum class PdfTextPhase {
    /** Nothing is being read, and the slot is not in hand. */
    IDLE,

    /** The text layer is being extracted right now. */
    INDEXING,

    /** The extraction finished; [PdfTextAvailability.wordsOnPage] says what this sheet holds. */
    READY,

    /** The extraction broke, for the reason the availability carries. */
    FAILED,
}

/** What the Select-text slot can say about the page in view, and what the reader can do about it. */
data class PdfTextAvailability(
    val phase: PdfTextPhase = PdfTextPhase.IDLE,
    /** Words the extracted layer holds for **this** sheet — 0 on a scan, and on a page with no PDF. */
    val wordsOnPage: Int = 0,
    /** Why the read failed, in the reader's words; null unless [phase] is [PdfTextPhase.FAILED]. */
    val failure: String? = null,
) {

    /** True when a drag on this page would actually select words. */
    val canSelect: Boolean get() = phase == PdfTextPhase.READY && wordsOnPage > 0

    /** True when the only useful thing on offer is another attempt. */
    val canRetry: Boolean get() = phase == PdfTextPhase.FAILED

    /**
     * The one sentence the slot owes a reader looking at [pageShowsPdf], or null when it owes none.
     *
     * A page that is not a PDF page is not this tool's business: the sentence would be about a file the
     * reader is not looking at, so the slot stays silent rather than explaining itself everywhere.
     */
    fun notice(pageShowsPdf: Boolean): String? = when {
        !pageShowsPdf -> null
        phase == PdfTextPhase.INDEXING -> INDEXING_SENTENCE
        phase == PdfTextPhase.FAILED -> failedSentence()
        phase == PdfTextPhase.READY && wordsOnPage == 0 -> NO_TEXT_SENTENCE
        else -> null
    }

    private fun failedSentence(): String {
        val reason = failure?.trim()?.takeIf { it.isNotEmpty() }
        return if (reason == null) "$FAILED_SENTENCE." else "$FAILED_SENTENCE ($reason)."
    }

    companion object {
        /** Shown while the walk runs. No retry: it is working. */
        const val INDEXING_SENTENCE = "Reading this PDF's text…"

        /** A page that is a picture of text: the scanned-page case, which this app has no OCR for. */
        const val NO_TEXT_SENTENCE = "No text on this page: it is a picture of text, not text."

        /** The read broke. Both shells follow it with a **Try again**. */
        const val FAILED_SENTENCE = "Could not read this PDF's text"
    }
}
