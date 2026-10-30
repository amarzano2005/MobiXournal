package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.audio.documentAudioFiles
import com.mobixournal.format.model.AudioRef
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.audioRef
import com.mobixournal.render.KaraokeStrokeAnimator
import com.mobixournal.render.SelectionTester

/**
 * Where a drag at [fraction] of a bar lands, in a clip of [durationMs]: both ends of the bar are the
 * clip's ends, and a bar for a clip nobody has measured (0 ms) is 0 rather than a division by nothing.
 */
internal fun scrubMs(fraction: Float, durationMs: Int): Int {
    if (durationMs <= 0) return 0
    return (fraction.coerceIn(0f, 1f) * durationMs).toInt().coerceIn(0, durationMs)
}

/**
 * The window's audio, as **one object** the header pill, the recorder and a page commit all read.
 *
 * The parts are separate ([DesktopAudioStore], [DesktopAudioRecorder], [DesktopAudioPlayer]) so each is
 * testable with a fake device; this is the seam that makes them one feature — the pill's three buttons,
 * the sentence a failure shows, and the `fn`/`ts` a stroke is stamped with all come from here, so a
 * recording cannot be in progress in one place and invisible in another.
 *
 * The observable properties are Compose snapshot state because the pill is drawn from them; the
 * recorder's and player's own fields stay their business, and [refresh] copies what they have to say
 * once per pill tick rather than making every sample a recomposition.
 */
internal object DesktopAudioSession {

    val store: DesktopAudioStore = DesktopAudioStore.shared

    val recorder: DesktopAudioRecorder by lazy { DesktopAudioRecorder(store) }

    val player: DesktopAudioPlayer by lazy { DesktopAudioPlayer(store) }

    /** True while a take is running (paused or not). */
    var recording by mutableStateOf(false)
        private set

    /** True while that take is muted. */
    var recordingPaused by mutableStateOf(false)
        private set

    /** The take's peak level, 0..1, for the pill's level bar. */
    var level by mutableFloatStateOf(0f)
        private set

    /** How far into the take the microphone has written, in ms — what a stroke's `ts` carries. */
    var recordedMs by mutableIntStateOf(0)
        private set

    /** True while sound is coming out of the player. */
    var playing by mutableStateOf(false)
        private set

    /** Where the clip is, in ms. */
    var positionMs by mutableIntStateOf(0)
        private set

    /** The clip's length, in ms. */
    var durationMs by mutableIntStateOf(0)
        private set

    /** How fast the clip plays, 1×..2× — the pill's speed button. */
    var speed by mutableFloatStateOf(1f)
        private set

    /** The name of the clip the player has, or null when nothing is on. */
    var clipName by mutableStateOf<String?>(null)
        private set

    /** One English sentence about something that could not be recorded or played, or null. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Something worth hearing exists in the open document — what enables the pill's Play. */
    var hasAudio by mutableStateOf(false)
        private set

    /**
     * Where a standalone copy of each finished take goes, when [saveExternally] — the Audio page
     * nominates both (`DesktopAudioSettings`), so this object never reads the settings store.
     */
    var exportFolder: java.io.File? = null

    /** Whether a finished take is copied out of the window's store at all (the tablet's toggle). */
    var saveExternally: Boolean = false

    /**
     * Adopt what [document] carries: the recordings a `.mobix` bundles, and the sidecars a `.xopp` keeps
     * beside it.
     *
     * This runs on **every open** and is the reason an edit on the PC no longer deletes the audio: the
     * bytes are copied into the window's store first, so the archive's rewrite can find them by name
     * (`DesktopAudioStore.adoptBundled`). A recording the document names and this machine still cannot
     * find is reported once, naming it — silence with no explanation is the one answer a person cannot
     * act on.
     */
    fun adopt(document: DesktopDocument) {
        val archive = document.pageSource ?: document.file
        if (document.container == DesktopContainer.MOBIX) {
            store.adoptBundled(archive, document.model)
        } else {
            store.importSidecars(document.file.parentFile, document.model)
        }
        noteAudioPresence(document.model)
        val missing = store.missing(document.model)
        if (missing.isNotEmpty()) {
            message = if (missing.size == 1) {
                "This document's recording is not on this machine: ${missing.first()}."
            } else {
                "This document names ${missing.size} recordings this machine does not have: " +
                    missing.joinToString(", ") + "."
            }
        }
    }

    /** Read the open document again to know whether its Play button has anything to play. */
    fun noteAudioPresence(document: Document?) {
        hasAudio = document != null && documentAudioFiles(document).isNotEmpty()
    }

    /** Start or finish a take. A click is the whole gesture, exactly as on the tablet. */
    fun toggleRecord(document: Document?) {
        if (recording) {
            finishRecording(document)
            return
        }
        if (document == null) {
            message = "Open a document first: a recording is a stroke's, and there is no page to draw on."
            return
        }
        val name = recorder.start()
        val failure = recorder.message
        if (name == null) {
            message = failure ?: "The microphone could not be started."
            refresh()
            return
        }
        message = null
        recording = true
        recordingPaused = false
        refresh()
    }

    /** Mute or unmute the take; nothing is closed, so the same file keeps growing. */
    fun toggleRecordPause() {
        if (!recording) return
        if (recordingPaused) recorder.resume() else recorder.pause()
        recordingPaused = recorder.paused
    }

    /**
     * Stop and finalise the take, keeping the file the strokes name — and, when the Audio page asks for
     * a standalone copy, copying the document's recordings into the nominated folder.
     *
     * The copy is of **every** recording the document names, not only this take: the folder is meant to
     * hold the notebook's audio, and a second take must not be the only file in it. A copy that cannot be
     * written is reported in words rather than swallowed.
     */
    fun finishRecording(document: Document? = null) {
        val name = recorder.stop()
        recording = false
        recordingPaused = false
        level = 0f
        recordedMs = 0
        if (name == null && recorder.message != null) message = recorder.message
        hasAudio = hasAudio || name != null
        if (name != null) exportStandalone(document)
    }

    /** The tablet's *Save separate audio*: put the document's recordings in [exportFolder]. */
    private fun exportStandalone(document: Document?) {
        val folder = exportFolder ?: return
        if (!saveExternally || document == null) return
        val copied = store.exportToFolder(folder, document)
        if (copied > 0) {
            message = if (copied == 1) {
                "The recording was also saved to ${folder.absolutePath}."
            } else {
                "$copied recordings were also saved to ${folder.absolutePath}."
            }
        } else {
            message = "The recordings could not be copied to ${folder.absolutePath}."
        }
    }

    /**
     * The link a stroke committed **now** carries, or null when no take is running.
     *
     * The offset is the **bytes written so far**, never wall time: a paused take writes nothing, so the
     * stroke drawn over a pause belongs to the same moment the next sound does — and a seek to that
     * stroke lands on the word it was written over.
     */
    fun stamp(): AudioRef? {
        if (!recording || recordingPaused) return null
        val name = recorder.fileName ?: return null
        return AudioRef(name, recorder.elapsedMs())
    }

    /** Play or hold the document's recording, starting where its first stamped stroke begins. */
    fun togglePlay(tab: DesktopTab?) {
        if (player.playing) {
            player.pause()
            refresh()
            return
        }
        if (player.clipName != null) {
            player.resume()
            refresh()
            return
        }
        val ref = firstRef(tab) ?: run {
            message = "This page has no recording to play."
            return
        }
        play(ref)
    }

    /**
     * The tablet's **Play object** tap: the stroke under the page point ([x], [y]) replays from the point
     * that was touched, not from the stroke's head.
     *
     * The pick is the shared `SelectionTester.pickTopmost`, so the topmost element under the tap is the one
     * a person sees, and the time is `KaraokeStrokeAnimator.seekTimeMs` — the stroke's stamp plus the
     * deltas up to the nearest point. A stroke with no deltas (a `.xopp` import) falls back to its own
     * stamp, which is all it can promise. A tap on the desk, or on an element that carries no recording,
     * answers in one sentence instead of doing nothing.
     */
    fun tapAt(tab: DesktopTab, x: Double, y: Double) {
        val page = runCatching { tab.document.page(tab.page) }.getOrNull() ?: return
        val stroke = SelectionTester.pickTopmost(page, x, y)
            ?.let { page.layers.getOrNull(it.layerIndex)?.elements?.getOrNull(it.elementIndex) }
            ?.let { it as? Stroke }
        val ref = stroke?.audioRef()
        if (ref == null) {
            message = if (stroke == null) {
                "Tap a stroke that was written while recording to hear it."
            } else {
                "That stroke was not drawn over a recording, so there is nothing to play."
            }
            return
        }
        val at = KaraokeStrokeAnimator.seekTimeMs(ref.startMs, stroke.points, x, y)
        play(ref.copy(startMs = at ?: ref.startMs))
    }

    /** Walk the transport's speeds: 1×, 1.25×, 1.5×, 2×, and back to 1×. */
    fun cycleSpeed() {
        player.nextSpeed()
        refresh()
    }

    /** Play [ref] from its stamp, replacing whatever was on. */
    fun play(ref: AudioRef) {
        val failure = player.play(ref)
        message = failure
        refresh()
    }

    /** Everything the pill draws, copied from the two engines once per tick. */
    fun refresh() {
        recording = recorder.recording
        recordingPaused = recorder.paused
        level = recorder.level
        recordedMs = recorder.elapsedMs()
        playing = player.playing
        positionMs = player.positionMs() ?: 0
        durationMs = player.durationMs
        speed = player.speed
        clipName = player.clipName
        player.message?.let { message = it }
        recorder.message?.let { message = it }
    }

    /**
     * Move the clip to [ms] — the pill's scrubber, which walks the recording with the sound on or off.
     *
     * A held clip (a pause) moves at once, a sounding one is asked to seek; [Playhead] owns the
     * difference, so this is the same one player either way and a drag never opens a second.
     */
    fun seek(ms: Int) {
        player.seek(ms)
        refresh()
    }

    /** A fraction of the clip, 0..1, as the millisecond a scrub lands on — clamped to the clip's length. */
    fun scrubTargetMs(fraction: Float): Int = scrubMs(fraction, durationMs)

    /** Stop playback and forget the clip (the pill's stop). */
    fun stopPlayback() {
        player.stop()
        refresh()
    }

    /** The user read the sentence: drop it. */
    fun dismissMessage() {
        message = null
        player.dismissMessage()
        recorder.dismissMessage()
    }

    /** The recording the **active page** plays from: its topmost stamped stroke, or the document's first. */
    fun firstRef(tab: DesktopTab?): AudioRef? {
        val document = tab?.document?.model ?: return null
        val page = runCatching { tab.document.page(tab.page) }.getOrNull()
        val onPage = page?.layers
            ?.flatMap { it.elements }
            ?.filterIsInstance<Stroke>()
            ?.mapNotNull { it.audioRef() }
            ?.lastOrNull()
        if (onPage != null) return onPage
        val name = documentAudioFiles(document).firstOrNull() ?: return null
        return AudioRef(name, 0)
    }
}
