package com.mobixournal.audio

import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Plays a stroke's recording back from the moment that stroke was drawn.
 *
 * Only one clip plays at a time — tapping a second stroke replaces the first, which matches how
 * "play object" behaves on the desktop. The player owns a single [MediaPlayer] it recycles, so the
 * caller only has to remember to [release] it when the editor goes away.
 *
 * It also owns the **playhead** ([Playhead]): where in the clip playback is, which nothing else can
 * know — the platform's reading is the only clock there is, and a second timer beside it would drift
 * away from the sound (`docs/audio.md`). A clip finishing reports through the same
 * [onPlayingChanged] hook a stop does, so nothing downstream needs a completion path of its own.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private var preparedPath: String? = null
    private var preparing: kotlinx.coroutines.CancellableContinuation<Boolean>? = null

    /** Android UI callers prepare asynchronously; a late callback can never replace a newer player. */
    suspend fun prepare(file: File, atMs: Int): Boolean {
        stop()
        if (!file.isFile) return false
        return suspendCancellableCoroutine { continuation ->
            val mp = MediaPlayer()
            player = mp
            preparing = continuation
            continuation.invokeOnCancellation {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (player === mp) stop() else runCatching { mp.release() }
                }
            }
            mp.setOnPreparedListener {
                if (player !== mp || !continuation.isActive) return@setOnPreparedListener
                preparing = null
                preparedPath = file.absolutePath
                mp.setOnPreparedListener(null)
                mp.setOnCompletionListener { if (player === mp) stop() }
                mp.setOnErrorListener { _, _, _ -> if (player === mp) stop(); true }
                playhead.hold(mp.duration, atMs)
                continuation.resume(true)
            }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) stop()
                if (continuation.isActive) continuation.resume(false)
                true
            }
            try { mp.setDataSource(file.absolutePath); mp.prepareAsync() }
            catch (_: Exception) {
                if (player === mp) stop()
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private val playhead = Playhead()

    /** Notified when playback starts or stops, so the chrome can show a "playing" state. */
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    /** True while a clip is sounding. */
    val isPlaying: Boolean get() = preparedPath != null && player?.isPlaying == true

    /**
     * True while the clip that is on is **sounding** — false when none is on, and false for one held at
     * a position. This is what the app's own state reads rather than [isPlaying]: `MediaPlayer.isPlaying`
     * also reports false for the moment a seek is in flight, which would read as a pause mid-scrub.
     */
    val isSounding: Boolean get() = sounding

    private var sounding = false

    /** Keep the decoder prepared while paused; resuming must not reopen and decode the same file. */
    fun pause(): Boolean {
        val mp = player ?: return false
        val at = currentPositionMs ?: return false
        return runCatching {
            mp.pause()
            sounding = false
            playhead.hold(mp.duration, at)
            true
        }.getOrDefault(false)
    }

    fun resume(): Boolean {
        val mp = player ?: return false
        return runCatching {
            mp.start()
            sounding = true
            playhead.attach(MediaPlayerClock(mp))
            onPlayingChanged?.invoke(true)
            true
        }.getOrDefault(false)
    }

    /** Where playback is, in ms — null while no clip is on. */
    val currentPositionMs: Int? get() = playhead.positionMs()

    /** How long the clip that is on runs, in ms — null while none is (what a scrubber's range needs). */
    val durationMs: Int? get() = if (preparedPath == null) null else player?.let { runCatching { it.duration }.getOrNull() }

    /**
     * Jump to [ms] within the clip that is on — sounding or held — and answer the position it will be
     * at (clamped to the clip's own length). Null while no clip is on.
     */
    fun seekTo(ms: Int): Int? {
        val at = playhead.seekTo(ms) ?: return null
        // A held clip is moved here as well, so playing it later starts where the scrub left it rather
        // than where it was paused: the one player is reused, so there is never a second clip open.
        player?.let { runCatching { it.seekTo(at) } }
        return at
    }

    /**
     * Load [file] and hold it at [atMs] **without sounding it**, so its length is known and a position
     * can be walked back and forth with nothing playing — the paused frame a scrubber needs. False when
     * the file can't be opened, which leaves nothing on.
     */
    fun hold(file: File, atMs: Int): Boolean {
        stop()
        if (!file.isFile) return false
        val mp = MediaPlayer()
        val ok = runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.seekTo(atMs.coerceIn(0, mp.duration.coerceAtLeast(0)))
            mp.setOnCompletionListener { stop() }
        }.isSuccess
        if (!ok) {
            runCatching { mp.release() }
            return false
        }
        player = mp
        preparedPath = file.absolutePath
        sounding = false
        playhead.hold(mp.duration, atMs)
        return true
    }

    /** Playback speed multiplier (e.g. 0.8f, 1.0f, 1.25f, 1.5f, 2.0f). */
    var playbackSpeed: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.25f, 4.0f)
            applyPlaybackSpeed()
        }

    private fun applyPlaybackSpeed() {
        val mp = player ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            runCatching {
                val params = mp.playbackParams
                params.speed = playbackSpeed
                mp.playbackParams = params
                if (!sounding) mp.pause()
            }
        }
    }

    /**
     * Play [file] from [startMs]. Returns false when the file can't be opened or decoded (a missing
     * sidecar is the common case), leaving any previous clip stopped.
     */
    fun play(file: File, startMs: Int): Boolean {
        if (preparedPath == file.absolutePath) {
            val mp = player ?: return false
            return runCatching {
                playhead.attach(MediaPlayerClock(mp))
                playhead.seekTo(startMs)
                mp.start()
                sounding = true
                applyPlaybackSpeed()
                onPlayingChanged?.invoke(true)
                true
            }.getOrDefault(false)
        }
        stop()
        if (!file.isFile) return false
        val mp = MediaPlayer()
        val ok = runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            // Clamp: a `ts` past the end of a truncated sidecar would otherwise seek into nothing.
            mp.seekTo(startMs.coerceIn(0, mp.duration.coerceAtLeast(0)))
            mp.setOnCompletionListener { stop() }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && playbackSpeed != 1.0f) {
                val params = mp.playbackParams
                params.speed = playbackSpeed
                mp.playbackParams = params
            }
            mp.start()
        }.isSuccess
        if (!ok) {
            runCatching { mp.release() }
            return false
        }
        player = mp
        preparedPath = file.absolutePath
        sounding = true
        playhead.attach(MediaPlayerClock(mp))
        onPlayingChanged?.invoke(true)
        return true
    }

    /** UI entry point: never fall back to synchronous preparation, even after a cancelled request. */
    fun playPrepared(file: File, startMs: Int): Boolean =
        preparedPath == file.absolutePath && play(file, startMs)

    /** Stop and free whatever is on — sounding or held; a no-op when none is. */
    fun stop() {
        preparedPath = null
        val pending = preparing
        preparing = null
        val mp = player ?: return
        player = null
        sounding = false
        playhead.detach()
        runCatching { mp.stop() }
        runCatching { mp.release() }
        if (pending?.isActive == true) pending.resume(false)
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
