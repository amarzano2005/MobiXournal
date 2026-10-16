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
 * **Where in the recording playback is** — the clock `AudioSession.playheadMs` publishes and the replay
 * path reads (`docs/audio-inking-sync.md`).
 *
 * It lives here rather than inside the player because every rule it enforces is ours and none of them is
 * Android's:
 *
 * - **Nothing sounding answers nothing.** A stale zero would draw the writing at the head of a recording
 *   nobody is playing, so an idle playhead is `null` rather than `0`.
 * - **A seek is clamped to the clip's own length**, the same guard the player applies to `ts` on the way
 *   in: a scrubber dragged past the end must land on the end, not past it.
 * - **A seek reads as the position it asked for until the stack gets there.** `MediaPlayer.seekTo` is
 *   asynchronous — for a moment after it, `currentPosition` still reports where playback *was* — and a
 *   replay that read that would draw the writing at the wrong word for a frame or two.
 *
 * The whole of that is testable with no device, which is the point of the seam: the player's own
 * behaviour is the platform's business, the arithmetic around it is this class's.
 */
class Playhead {

    /** The clip that is sounding, or null while nothing is. */
    private var clock: ClipClock? = null

    /** A seek the clip has not visibly carried out yet: what to report until it has. */
    private var pending: PendingSeek? = null

    /** Begin reading [clock] — a clip has just started. */
    fun attach(clock: ClipClock) {
        this.clock = clock
        pending = null
    }

    /** Nothing is sounding any more: the playhead answers nothing until the next [attach]. */
    fun detach() {
        clock = null
        pending = null
    }

    /** Where playback is, in ms — null when nothing is sounding. */
    fun positionMs(): Int? {
        val clip = clock ?: return null
        val now = clip.positionMs()
        val seek = pending
        if (seek != null && !seek.carriedOutBy(now)) return seek.targetMs
        pending = null
        return now.coerceIn(0, clip.durationMs().coerceAtLeast(0))
    }

    /**
     * Move playback to [ms] and answer the position it will be at — clamped to the clip's own length, or
     * null when nothing is sounding.
     */
    fun seekTo(ms: Int): Int? {
        val clip = clock ?: return null
        val target = ms.coerceIn(0, clip.durationMs().coerceAtLeast(0))
        pending = PendingSeek(target, clip.positionMs())
        clip.seekTo(target)
        return target
    }

    /** A seek in flight: what it asked for, and where playback was when it asked. */
    private data class PendingSeek(val targetMs: Int, val wasMs: Int) {

        /**
         * Whether the clip's reading [now] shows the seek has landed — one comparison per direction,
         * because until the seek takes, the raw reading sits on the *other* side of the target.
         */
        fun carriedOutBy(now: Int): Boolean = if (targetMs >= wasMs) now >= targetMs else now <= targetMs
    }
}
