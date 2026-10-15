package com.mobixournal.io

/**
 * Where a `.mobix`'s **bundled recording** goes, so the player finds it under the name the stroke
 * already carries.
 *
 * A `.xopp` keeps its audio in sidecar files beside the document, and the app keeps its copies in its
 * own audio store (`audio/AudioStore.kt`); a `.mobix` carries the recording inside itself. Adopting one
 * on open is therefore "put these bytes where the sidecar would have been" — the same store, under the
 * same name — and that is all this seam asks for. It is an interface rather than a directory path
 * because the answer belongs to whoever owns the store, and because a format-only test can supply its
 * own: the format layer has no business knowing how recordings are named on disk.
 */
fun interface BundledAudio {

    /**
     * Keep [bytes] as the recording a stroke names [reference] — the sidecar name it replayed from, so
     * playback of that stroke needs nothing else.
     */
    fun adopt(reference: String, bytes: ByteArray)
}
