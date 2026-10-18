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
 * **Two recorders, and the document decides which.** A recording is captured Opus/Ogg for a `.mobix`
 * (what its container is for: ~15 MB an hour against ~300 MB of WAV) and 16-bit mono WAV for a `.xopp`,
 * whose audio leaves as a **sidecar the desktop plays** — a legacy contract that has no Opus in it
 * (`docs/audio-inking-sync.md`). An Opus recording that does reach a `.xopp` export is pushed out as the
 * file it really is and reported rather than renamed or faked, because this app has no transcode
 * ([compressedFor]). WAV is also the fallback whenever the Opus encoder or the Ogg muxer is not there.
 *
 * @param clock supplies "now" for naming a recording; injected so the naming is testable.
 */
class AudioSession(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : BundledAudio {

    private val wav = AudioRecorder()
    private val opus = OpusAudioRecorder()
    private val player = AudioPlayer()
    private val store = AudioStore(context)

    /** The bare file name of the recording in progress, or null when idle. */
    var currentFile: String? = null
        private set

    /** True while the microphone is being captured — whichever of the two encoders took it. */
    val isRecording: Boolean get() = wav.isRecording || opus.isRecording

    /** True while recording is active but paused. */
    val isRecordingPaused: Boolean get() = (opus.isRecording && opus.isPaused) || (wav.isRecording && wav.isPaused)

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

    /** Optional listener receiving raw 16-bit mono PCM chunks `(buffer, bytesRead, sampleRate)` while recording. */
    var onPcmData: ((ByteArray, Int, Int) -> Unit)?
        get() = opus.onPcmData ?: wav.onPcmData
        set(value) {
            opus.onPcmData = value
            wav.onPcmData = value
        }

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
     *
     * @param compressed whether the document this recording belongs to is written as a `.mobix`, whose
     *   container bundles the audio and can afford the Opus codec. A `.xopp` records WAV instead, and
     *   so does a `.mobix` whose device has no Ogg muxer or no Opus encoder ([OpusAudioRecorder.start]
     *   refusing is the fallback, not an error).
     */
    fun startRecording(compressed: Boolean = false): String? {
        if (isRecording) return currentFile
        if (compressed) {
            val name = audioFileName(clock(), extension = OpusAudioRecorder.CONTAINER_SUFFIX)
            if (opus.start(store.local(name))) {
                currentFile = name
                onStateChanged?.invoke()
                return name
            }
        }
        val name = audioFileName(clock())
        if (!wav.start(store.local(name))) return null
        currentFile = name
        onStateChanged?.invoke()
        return name
    }

    /** Pause recording if currently recording and not already paused. */
    fun pauseRecording(): Boolean {
        if (!isRecording || isRecordingPaused) return false
        if (opus.isRecording) opus.pause() else wav.pause()
        onStateChanged?.invoke()
        return true
    }

    /** Resume recording if currently paused. */
    fun resumeRecording(): Boolean {
        if (!isRecording || !isRecordingPaused) return false
        if (opus.isRecording) opus.resume() else wav.resume()
        onStateChanged?.invoke()
        return true
    }

    /** Finish the recording and return the file written, or null if none was running. */
    fun stopRecording(): File? {
        val file = (if (opus.isRecording) opus.stop() else wav.stop()) ?: return null
        currentFile = null
        onStateChanged?.invoke()
        return file
    }

    /**
     * The link to stamp on a stroke committed right now, or null when nothing is recording. This is
     * what [com.mobixournal.render.DrawingSurfaceView.audioStamp] is wired to.
     */
    fun stamp(): AudioRef? {
        val name = currentFile ?: return null
        val elapsed = when {
            opus.isRecording -> opus.elapsedMs()
            wav.isRecording -> wav.elapsedMs()
            else -> return null
        }
        return AudioRef(name, elapsed)
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

    /** Playback speed factor (e.g. 0.8f, 1.0f, 1.25f, 1.5f, 2.0f). */
    var playbackSpeed: Float
        get() = player.playbackSpeed
        set(value) {
            player.playbackSpeed = value
            onStateChanged?.invoke()
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
     * The sidecars [doc] references that are **compressed** (anything that is not a `.wav`) — the ones
     * this app can play and bundle but cannot hand to desktop Xournal++, whose legacy playback takes the
     * uncompressed sidecar. Transcoding them would need the Opus decoder on the export path, so the
     * export writes the file it really has and says so instead of naming a WAV that does not exist
     * ([exportSidecars]).
     */
    fun compressedFor(doc: Document): Set<String> =
        documentAudioFiles(doc).filterNotTo(LinkedHashSet()) { it.endsWith(".wav", ignoreCase = true) }

    /**
     * Pull the sidecars [doc] references out of the nominated [folder] into local storage, so a
     * document authored elsewhere replays here. Returns how many arrived.
     */
    fun importSidecars(folder: Uri, doc: Document): Int =
        store.importFrom(folder, documentAudioFiles(doc)).size

    /**
     * Push the sidecars [doc] references out to the nominated [folder], so the saved `.xopp` has its
     * audio beside it — under the names the strokes carry, so the `fn` in the file resolves to the file
     * that is actually there. Returns how many were written; a compressed one is written exactly as
     * stored (the caller reports what the desktop will not play, [compressedFor]).
     */
    fun exportSidecars(folder: Uri, doc: Document): Int =
        store.exportTo(folder, documentAudioFiles(doc)).size

    /** Tear everything down (the host's `onDestroy`); a running recording is finalised first. */
    fun release() {
        stopRecording()
        player.release()
    }
}
