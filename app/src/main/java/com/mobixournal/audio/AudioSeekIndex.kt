package com.mobixournal.audio

import com.mobixournal.render.Bounds

/**
 * **A recording's stroke timeline**: where in the clip each stroke of it was being written, and where on
 * which page that stroke is.
 *
 * The scrubber asks "which stroke was being written at this moment" on **every step of a drag**, and the
 * answer for a given document and clip never changes: the recording's strokes and their offsets are the
 * document's own data. Asking the notebook each time — reading two hundred pages to throw away all but a
 * handful, again for the next pixel — is what made a long drag slow enough to take the editor down. So
 * the walk happens **once**, the result is a list of small records, and a drag step is a filter over it
 * (`docs/audio.md`, mode 2).
 *
 * One cache, not one per editor: there is one editor at a time, and the key is the **identity** of the
 * document being asked about — a stroke commits a new `Document`, so an edit invalidates the timeline by
 * itself, and a document that was saved and reopened has its own. Nothing else has to remember to clear
 * it, and a stale entry can never be mistaken for the current document's.
 */
internal object AudioSeekIndex {

    /** One stroke's slice of the recording: when it started and stopped, and where it is. */
    class TimedStroke(val page: Int, val startMs: Int, val endMs: Int, val bounds: Bounds)

    private var document: Any? = null
    private var clip: String? = null
    private var timeline: List<TimedStroke> = emptyList()

    /**
     * The timeline of [clip] in [document], built by [build] the first time this pair is asked for.
     *
     * [build] runs under this object's lock on purpose: a drag asks for the same timeline from several
     * superseded steps, and one walk that everyone waits for is cheaper — and far safer — than a race of
     * walks over the same archive.
     */
    @Synchronized
    fun timelineOf(document: Any, clip: String, build: () -> List<TimedStroke>): List<TimedStroke> {
        if (this.document !== document || this.clip != clip) {
            timeline = build()
            this.document = document
            this.clip = clip
        }
        return timeline
    }

    /** The strokes being written at [ms] — the question a scrub step asks, answered from the timeline. */
    fun at(ms: Int, toleranceMs: Int): List<Pair<Int, Bounds>> =
        timeline.filter { ms in (it.startMs - 500)..(it.endMs + toleranceMs) }
            .map { it.page to it.bounds }

    /** Forget everything: a session torn down must not answer for a document it no longer holds. */
    @Synchronized
    fun clear() {
        document = null
        clip = null
        timeline = emptyList()
    }
}
