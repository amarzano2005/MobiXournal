package com.mobixournal.audio

import android.media.MediaPlayer
import java.io.File

/**
 * Plays a stroke's recording back from the moment that stroke was drawn.
 *
 * Only one clip plays at a time — tapping a second stroke replaces the first, which matches how
 * "play object" behaves on the desktop. The player owns a single [MediaPlayer] it recycles, so the
 * caller only has to remember to [release] it when the editor goes away.
 *
 * It also owns the **playhead** ([Playhead]): where in the clip playback is, which nothing else can
 * know — the platform's reading is the only clock there is, and a second timer beside it would drift
 * away from the sound (`docs/audio-inking-sync.md`). A clip finishing reports through the same
 * [onPlayingChanged] hook a stop does, so nothing downstream needs a completion path of its own.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null

    private val playhead = Playhead()

    /** Notified when playback starts or stops, so the chrome can show a "playing" state. */
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    /** True while a clip is sounding. */
    val isPlaying: Boolean get() = player?.isPlaying == true

    /** Where playback is, in ms — null while nothing is sounding. */
    val currentPositionMs: Int? get() = playhead.positionMs()

    /**
     * Jump to [ms] within the clip that is playing, and answer the position it will be at (clamped to
     * the clip's own length) — null while nothing is sounding.
     */
    fun seekTo(ms: Int): Int? = playhead.seekTo(ms)

    /**
     * Play [file] from [startMs]. Returns false when the file can't be opened or decoded (a missing
     * sidecar is the common case), leaving any previous clip stopped.
     */
    fun play(file: File, startMs: Int): Boolean {
        stop()
        if (!file.isFile) return false
        val mp = MediaPlayer()
        val ok = runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            // Clamp: a `ts` past the end of a truncated sidecar would otherwise seek into nothing.
            mp.seekTo(startMs.coerceIn(0, mp.duration.coerceAtLeast(0)))
            mp.setOnCompletionListener { stop() }
            mp.start()
        }.isSuccess
        if (!ok) {
            runCatching { mp.release() }
            return false
        }
        player = mp
        playhead.attach(MediaPlayerClock(mp))
        onPlayingChanged?.invoke(true)
        return true
    }

    /** Stop and free whatever is playing; a no-op when idle. */
    fun stop() {
        val mp = player ?: return
        player = null
        playhead.detach()
        runCatching { mp.stop() }
        runCatching { mp.release() }
        onPlayingChanged?.invoke(false)
    }

    /** Release the underlying player for good (call from the host's teardown). */
    fun release() = stop()
}

/**
 * One [MediaPlayer] as the [ClipClock] the playhead reads.
 *
 * Both readings are wrapped: the platform throws for a player that has been released, and a playhead
 * asking where a clip is at the wrong moment must not take the app down with it. Zero is the honest
 * answer there — a length of nothing clamps every seek to the beginning rather than inventing one.
 */
private class MediaPlayerClock(private val player: MediaPlayer) : ClipClock {

    override fun positionMs(): Int = runCatching { player.currentPosition }.getOrDefault(0)

    override fun durationMs(): Int = runCatching { player.duration }.getOrDefault(0)

    override fun seekTo(ms: Int) {
        runCatching { player.seekTo(ms) }
    }
}
