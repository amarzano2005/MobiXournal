package com.mobixournal

import android.net.Uri
import com.mobixournal.format.model.Page
import com.mobixournal.ui.entry.EntryPreviews

// --- the entry screen's memory of a document ------------------------------------------------
//
// Two calls the document pipeline makes about a notebook it has just opened: it joins the recents
// list, and it is given the face the entry screen draws for it. They live here rather than in
// `MainActivityDocuments` because that file is at its structural ceiling, and because they are one
// subject — what the app remembers about a notebook once it has been read.

/**
 * Put [uri] at the head of the entry screen's cards.
 *
 * Called before the bytes are read, so a notebook that fails to open still shows up (a card that
 * cannot be opened is more useful than one that silently vanished); its face arrives later, in
 * [rememberFaceOf].
 */
internal fun MainActivity.rememberRecentNotebook(uri: Uri, name: String) {
    recentList.value = recentNotebooks.record(uri, name)
}

/**
 * Write [page] as [uri]'s face, on the preview worker — a page raster is milliseconds but a document
 * can be thousands of strokes, and the entry screen must never pay for it.
 */
internal fun MainActivity.rememberFaceOf(uri: Uri, page: Page) {
    val key = uri.toString()
    previewWorker.execute {
        runCatching { EntryPreviews.save(this@rememberFaceOf, key, page) }
    }
}
