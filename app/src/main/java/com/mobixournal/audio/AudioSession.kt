package com.mobixournal.audio

import android.content.Context
import android.net.Uri
import com.mobixournal.format.model.Document
import com.mobixournal.io.BundledAudio
import java.io.File

/**
 * The editor's one audio façade: recording, playback, and getting sidecars on and off disk.
 *
 * It exists so nothing else has to know how the three parts fit together — the surface asks it for a
 * stamp, the chrome asks it to start/stop, and the host asks it to sync sidecars around open/save.
 *
 * @param clock supplies "now" for naming a recording; injected so the naming is testable.
 */
class AudioSession(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : BundledAudio {

    private val recorder = AudioRecorder()
    private val player = AudioPlayer()
    private val store = AudioStore(context)

    /** The bare file name of the recording in progress, or null when idle. */
    var currentFile: String? = null
        private set

    /** True while the microphone is being captured. */
    val isRecording: Boolean get() = recorder.isRecording

    /** True while a clip is sounding — false while one is merely held (a pause, or a scrub). */
    val isPlaying: Boolean get() = player.isSounding

    /**
     * Where in the clip playback is, in ms — null while nothing is sounding — so the activity and the
     * chrome read one place rather than each asking the player ([AudioPlayer.currentPositionMs]).
     */
    val playheadMs: Int? get() = player.currentPositionMs

    /** How long the clip that is on runs, in ms — null while none is (a scrubber's own range). */
    val clipDurationMs: Int? get() = player.durationMs

    /** Notified whenever recording starts or stops, so the chrome can re-render its state. */
    var onStateChanged: (() -> Unit)? = null

    /**
     * The sidecar name of the clip that is **on** — sounding, or paused at a position — or null when
     * none is. What tells the replay path *which* recording its playhead belongs to, since the player
     * itself is handed a file and not a document's `fn` ([AudioRef]).
     */
    var nowPlaying: String? = null
        private set

    init {
        // A clip that finished is a clip no longer on, exactly as a stop is: the player reports both
        // through this one hook, so the two cannot disagree. Everything else the hook does is re-render.
        player.onPlayingChanged = { playing ->
            if (!playing) nowPlaying = null
            onStateChanged?.invoke()
        }
    }

    /**
     * Start a new recording. Returns the sidecar's name, or null when the microphone can't be opened
     * (denied permission, no mic, another app holding it) — the caller reports that to the user.
     */
    fun startRecording(): String? {
        if (recorder.isRecording) return currentFile
        val name = audioFileName(clock())
        if (!recorder.start(store.local(name))) return null
        currentFile = name
        onStateChanged?.invoke()
        return name
    }

    /** Finish the recording and return the file written, or null if none was running. */
    fun stopRecording(): File? {
        val file = recorder.stop() ?: return null
        currentFile = null
        onStateChanged?.invoke()
        return file
    }

    /**
     * The link to stamp on a stroke committed right now, or null when nothing is recording. This is
     * what [com.mobixournal.render.DrawingSurfaceView.audioStamp] is wired to.
     */
    fun stamp(): AudioRef? {
        val name = currentFile?.takeIf { recorder.isRecording } ?: return null
        return AudioRef(name, recorder.elapsedMs())
    }

    /**
     * Keep a recording a `.mobix` bundles, under the name the stroke carries — what lets a document
     * moved to an empty folder still replay ([com.mobixournal.io.BundledAudio]).
     */
    override fun adopt(reference: String, bytes: ByteArray) = store.adopt(reference, bytes)

    /**
     * The bytes of the recording [name] names, or null when this session does not hold it — what a
     * `.mobix` save hands [com.mobixournal.io.DocumentIo.encode] so the archive carries its audio.
     */
    fun recording(name: String): ByteArray? = store.local(name).takeIf(File::isFile)?.readBytes()

    /**
     * Play [ref]'s recording **from the stroke's own offset** — what the Play object tool promises when
     * a stroke is tapped, and the one thing a tap must not be talked out of by where a scrub happened to
     * leave things. To carry on from a held position instead, [resume].
     */
    fun play(ref: AudioRef): Boolean {
        val playing = player.play(store.local(ref.filename), ref.startMs)
        nowPlaying = ref.filename.takeIf { playing }
        return playing
    }

    /**
     * Hold the clip that is on where it is, so nothing sounds and the canvas still shows the writing as
     * it stood at that moment — a pause, and the state a scrub starts from. False when none is on.
     */
    fun pausePlayback(): Boolean {
        val name = nowPlaying ?: return false
        val at = player.currentPositionMs ?: return false
        if (!player.hold(store.local(name), at)) return false
        // Still the same clip, just not sounding — the one-clip rule holds, and the position survives.
        nowPlaying = name
        onStateChanged?.invoke()
        return true
    }

    /** Sound the clip that is on again from where it is held; false when none is on. */
    fun resume(): Boolean {
        val name = nowPlaying ?: return false
        val at = player.currentPositionMs ?: return false
        val playing = player.play(store.local(name), at)
        nowPlaying = name.takeIf { playing }
        onStateChanged?.invoke()
        return playing
    }

    /** Let go of the clip that is on — nothing sounds and nothing is held; a no-op when none is. */
    fun stopPlayback() {
        player.stop()
        nowPlaying = null
    }

    /**
     * Move playback to [ms], clamped to the clip's length, and answer the position it will be at — null
     * while nothing is sounding. What an audio-tap and the scrubber both end up calling.
     */
    fun seek(ms: Int): Int? = player.seekTo(ms)

    /** The sidecar names [doc] references that are **not** available locally (so won't replay). */
    fun missingFor(doc: Document): Set<String> {
        val referenced = documentAudioFiles(doc)
        return referenced - store.present(referenced)
    }

    /**
     * Pull the sidecars [doc] references out of the nominated [folder] into local storage, so a
     * document authored elsewhere replays here. Returns how many arrived.
     */
    fun importSidecars(folder: Uri, doc: Document): Int =
        store.importFrom(folder, documentAudioFiles(doc)).size

    /**
     * Push the sidecars [doc] references out to the nominated [folder], so the saved `.xopp` has its
     * audio beside it. Returns how many were written.
     */
    fun exportSidecars(folder: Uri, doc: Document): Int =
        store.exportTo(folder, documentAudioFiles(doc)).size

    /** Tear everything down (the host's `onDestroy`); a running recording is finalised first. */
    fun release() {
        stopRecording()
        player.release()
    }
}
