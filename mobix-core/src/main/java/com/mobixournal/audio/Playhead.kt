package com.mobixournal.audio

/**
 * The two readings a playhead needs from whatever is producing sound, and the one instruction it sends
 * back: where the clip is, how long it is, and jump to [seekTo]. [AudioPlayer] hands it the
 * `MediaPlayer`; a test hands it a number.
 */
interface ClipClock {

    /** The clip's own current position, in ms. */
    fun positionMs(): Int

    /** The clip's own length, in ms (0 when it reports none). */
    fun durationMs(): Int

    /** Ask the clip to jump to [ms] — the platform's own seek, asynchronous as it is. */
    fun seekTo(ms: Int)
}

/**
 * **Where in the recording we are** — the clock `AudioSession.playheadMs` publishes and the replay path
 * draws the writing with (`docs/audio-inking-sync.md`).
 *
 * A clip is **on** in one of two ways, and the difference is the whole reason this is a class rather
 * than a getter on the player:
 *
 * - **Sounding.** The position is the media stack's ([ClipClock]), and a seek reads as the position it
 *   asked for until the stack gets there: `MediaPlayer.seekTo` is asynchronous, and `currentPosition`
 *   still reports where playback *was* for a moment after it, which would draw the writing at the wrong
 *   word. A seek is clamped to the clip's own length.
 * - **Held.** Nothing is sounding — a pause, or a position scrubbed before play — so the position is
 *   ours: it moves only when we seek it, and it is clamped to the same length. This is what lets a
 *   scrubber walk the writing back and forth with the sound stopped.
 *
 * Nothing on at all is `null` rather than a stale zero: a stale zero would draw the writing at the head
 * of a recording nobody is on. The whole of that is testable with no device, which is the point of the
 * seam: the media stack's behaviour is the platform's business, the arithmetic around it is this class's.
 */
class Playhead {

    /** The clip that is on, or null while none is. */
    private var on: OnClip? = null

    /** A seek the sounding clip has not visibly carried out yet: what to report until it has. */
    private var pending: PendingSeek? = null

    /** Begin reading [clock]: a clip has just started sounding. */
    fun attach(clock: ClipClock) {
        on = OnClip(durationMs = clock.durationMs().coerceAtLeast(0), clock = clock, heldMs = 0)
        pending = null
    }

    /**
     * Hold a clip of [durationMs] at [atMs] with nothing sounding — a pause, or the position a scrub
     * left it at. The playhead then answers that position until it is sought again.
     */
    fun hold(durationMs: Int, atMs: Int) {
        val duration = durationMs.coerceAtLeast(0)
        on = OnClip(durationMs = duration, clock = null, heldMs = atMs.coerceIn(0, duration))
        pending = null
    }

    /** No clip is on any more: the playhead answers nothing until the next [attach] or [hold]. */
    fun detach() {
        on = null
        pending = null
    }

    /** Where playback is, in ms — null while no clip is on. */
    fun positionMs(): Int? {
        val clip = on ?: return null
        val clock = clip.clock ?: return clip.heldMs
        val now = clock.positionMs()
        val seek = pending
        if (seek != null && !seek.carriedOutBy(now)) return seek.targetMs
        pending = null
        return now.coerceIn(0, clip.durationMs)
    }

    /**
     * Move playback to [ms] and answer the position it will be at — clamped to the clip's own length, or
     * null while no clip is on. A held clip moves there at once; a sounding one is asked to seek.
     */
    fun seekTo(ms: Int): Int? {
        val clip = on ?: return null
        val target = ms.coerceIn(0, clip.durationMs)
        val clock = clip.clock
        if (clock == null) {
            clip.heldMs = target
            return target
        }
        pending = PendingSeek(target, clock.positionMs())
        clock.seekTo(target)
        return target
    }

    /** The clip that is on: its length, the stack behind it when it sounds, and where it is held. */
    private class OnClip(val durationMs: Int, val clock: ClipClock?, var heldMs: Int)

    /** A seek in flight: what it asked for, and where playback was when it asked. */
    private data class PendingSeek(val targetMs: Int, val wasMs: Int) {

        /**
         * Whether the clip's reading [now] shows the seek has landed — one comparison per direction,
         * because until the seek takes, the raw reading sits on the *other* side of the target.
         */
        fun carriedOutBy(now: Int): Boolean = if (targetMs >= wasMs) now >= targetMs else now <= targetMs
    }
}
