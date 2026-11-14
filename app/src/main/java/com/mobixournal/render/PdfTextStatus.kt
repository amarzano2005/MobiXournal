package com.mobixournal.render

import androidx.compose.runtime.mutableStateMapOf
import java.io.File

/**
 * **Where the text layer of each imported PDF stands**, keyed by the file it was read from.
 *
 * The canvas holds the *result* (`pdfTextIndex`); what it cannot hold is the *process* — a walk that is
 * running, or one that broke — because a null index means both "not yet" and "nothing to be had". This is that
 * second half, and it is keyed by file rather than by canvas for the case that used to lose the news: a tab
 * switch during extraction. The answer is filed under the bytes it read, so a result for a document the reader
 * has left behind marks *that* document, and the canvas that moved on shows nothing at all.
 *
 * Compose snapshot state, so the notice that reads it redraws when the phase moves; the extraction runs off
 * the UI thread and writes here from it, which snapshot state allows.
 */
internal object PdfTextStatus {

    private val entries = mutableStateMapOf<String, PdfTextAvailability>()

    /** The phase [file] is in; idle when nothing has been read from it or it is not a PDF. */
    fun of(file: File?): PdfTextAvailability = file?.let { entries[it.absolutePath] } ?: PdfTextAvailability()

    /** A walk over [file] has just started. */
    fun indexing(file: File) {
        entries[file.absolutePath] = PdfTextAvailability(PdfTextPhase.INDEXING)
    }

    /**
     * [file]'s text layer is available. The words **on a page** are not this object's business: they come from
     * the index the canvas holds (`DrawingSurfaceView.pdfWordsOnPageInView`), because the same file shows
     * different sheets in two panes and one of them may be a scan.
     */
    fun ready(file: File) {
        entries[file.absolutePath] = PdfTextAvailability(PdfTextPhase.READY)
    }

    /** The walk over [file] broke, for [reason]; the slot then offers another attempt. */
    fun failed(file: File, reason: String?) {
        entries[file.absolutePath] = PdfTextAvailability(PdfTextPhase.FAILED, failure = reason)
    }

    /** Forget [file]: it was closed, or its bytes were replaced. */
    fun clear(file: File) {
        entries.remove(file.absolutePath)
    }
}
