package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.audio.documentAudioFiles
import com.mobixournal.audio.ownsRecording
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
 * What the audio pill draws **for one tab**: its take, its clip, and whether that tab's document names a
 * recording at all.
 *
 * A recording is the tab's — not the window's (`docs/audio.md`): a take started in one document stamps
 * that document's strokes and nothing else, and a clip sounds while its own tab is the one on screen. So
 * the pill reads this instead of the session's engine fields, and the answer for a tab that owns nothing
 * is idle rather than somebody else's take ([takeElsewhere] is what the microphone button reports).
 */
internal data class DesktopTabAudio(
    val recording: Boolean,
    val recordingPaused: Boolean,
    val level: Float,
    val recordedMs: Int,
    val clipName: String?,
    val playing: Boolean,
    val positionMs: Int,
    val durationMs: Int,
    val speed: Float,
    val hasAudio: Boolean,
    val message: String?,
    /** True when **another** tab's take is running, so this tab must not open a second microphone. */
    val takeElsewhere: Boolean,
)

/**
 * The window's audio, as **one object** the header pill, the recorder and a page commit all read.
 *
 * The parts are separate ([DesktopAudioStore], [DesktopAudioRecorder], [DesktopAudioPlayer]) so each is
 * testable with a fake device; this is the seam that makes them one feature — the pill's three buttons,
 * the sentence a failure shows, and the `fn`/`ts` a stroke is stamped with all come from here, so a
 * recording cannot be in progress in one place and invisible in another.
 *
 * A machine has **one microphone and one output**, so there is one recorder and one player — but what they
 * are doing belongs to a **tab**: [takeOwner] names the document whose strokes a take stamps, [clipOwner]
 * the document whose page a clip sounds over, and [heard] remembers which open document names a recording.
 * [of] is the per-tab answer the pill draws ([DesktopTabAudio]), which is why switching tabs shows that
 * tab's audio rather than the one that was recorded next door.
 *
 * The observable properties are Compose snapshot state because the pill is drawn from them; the
 * recorder's and player's own fields stay their business, and [refresh] copies what they have to say
 * once per pill tick rather than making every sample a recomposition.
 */
internal object DesktopAudioSession {

    val store: DesktopAudioStore = DesktopAudioStore.shared

    val recorder: DesktopAudioRecorder by lazy { DesktopAudioRecorder(store) }

    val player: DesktopAudioPlayer by lazy { DesktopAudioPlayer(store) }

    /** True while a take is running (paused or not) — the microphone is open, whichever tab owns it. */
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

    /** The tab ([DesktopTab.key]) whose take the one recorder is capturing, or null when idle. */
    private var takeOwner by mutableStateOf<String?>(null)

    /** The tab whose clip the one player holds, or null when nothing is on. */
    private var clipOwner by mutableStateOf<String?>(null)

    /** Which open document names a recording at all, by tab key — what enables that tab's Play. */
    private val heard = mutableStateMapOf<String, Boolean>()

    /** What the pill draws for [tab]; see [DesktopTabAudio]. */
    fun of(tab: DesktopTab?): DesktopTabAudio {
        val key = tab?.key
        val ownsTake = key != null && takeOwner == key
        val holdsClip = key != null && clipOwner == key
        return DesktopTabAudio(
            recording = ownsTake && recording,
            recordingPaused = ownsTake && recordingPaused,
            level = if (ownsTake) level else 0f,
            recordedMs = if (ownsTake) recordedMs else 0,
            clipName = if (holdsClip) clipName else null,
            playing = holdsClip && playing,
            positionMs = if (holdsClip) positionMs else 0,
            durationMs = if (holdsClip) durationMs else 0,
            speed = if (holdsClip) speed else 1f,
            hasAudio = key != null && heard[key] == true,
            message = message,
            takeElsewhere = takeOwner != null && takeOwner != key,
        )
    }

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

    /** Read [tab]'s document again to know whether its Play button has anything to play. */
    fun noteAudioPresence(tab: DesktopTab?) {
        val key = tab?.key ?: return
        heard[key] = documentAudioFiles(tab.document.model).isNotEmpty()
    }

    /**
     * Start or finish **this tab's** take. A click is the whole gesture, exactly as on the tablet.
     *
     * A take belongs to the tab that started it, so a click in another tab while one runs reports that in
     * one sentence instead of opening a second microphone (there is one) or stopping somebody else's take.
     */
    fun toggleRecord(tab: DesktopTab?) {
        if (recording) {
            if (tab != null && takeOwner == tab.key) {
                finishRecording()
            } else {
                message = "A recording is running in another tab. Switch back to it to stop it."
            }
            return
        }
        if (tab == null) {
            message = "Open a document first: a recording is a stroke's, and there is no page to draw on."
            return
        }
        if (takeOwner != null && takeOwner != tab.key) {
            message = "A recording is running in another tab. Switch back to it to stop it."
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
        takeOwner = tab.key
        heard[tab.key] = true
        refresh()
    }

    /** Mute or unmute [tab]'s take; nothing is closed, so the same file keeps growing. */
    fun toggleRecordPause(tab: DesktopTab?) {
        if (!recording || tab == null || takeOwner != tab.key) return
        if (recordingPaused) recorder.resume() else recorder.pause()
        recordingPaused = recorder.paused
    }

    /** Stop and finalise the take, keeping the file the strokes name. */
    private fun finishRecording() {
        val name = recorder.stop()
        recording = false
        recordingPaused = false
        level = 0f
        recordedMs = 0
        if (name == null && recorder.message != null) message = recorder.message
        takeOwner?.let { heard[it] = heard[it] == true || name != null }
        takeOwner = null
    }

    /**
     * The link a stroke committed **now** on [tab] carries, or null when no take of that tab is running.
     *
     * The offset is the **bytes written so far**, never wall time: a paused take writes nothing, so the
     * stroke drawn over a pause belongs to the same moment the next sound does — and a seek to that
     * stroke lands on the word it was written over. The tab gate is [ownsRecording]'s: ink drawn in
     * another tab while this take runs is somebody else's stroke and carries no `fn`.
     */
    fun stamp(tab: DesktopTab?): AudioRef? {
        if (!recording || recordingPaused) return null
        if (!ownsRecording(takeOwner, tab?.key)) return null
        val name = recorder.fileName ?: return null
        return AudioRef(name, recorder.elapsedMs())
    }

    /** Play or hold [tab]'s recording, starting where its first stamped stroke begins. */
    fun togglePlay(tab: DesktopTab?) {
        val key = tab?.key
        if (player.playing && clipOwner == key) {
            player.pause()
            refresh()
            return
        }
        if (clipOwner == key && player.clipName != null) {
            player.resume()
            refresh()
            return
        }
        // One output, one tab: a clip held for another document is let go rather than doubled.
        if (clipOwner != null) stopPlayback()
        val ref = firstRef(tab) ?: run {
            message = "This tab has no recording to play."
            return
        }
        play(ref, tab)
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
        play(ref.copy(startMs = at ?: ref.startMs), tab)
    }

    /** Walk the transport's speeds: 1×, 1.25×, 1.5×, 2×, and back to 1×. */
    fun cycleSpeed(tab: DesktopTab?) {
        if (tab == null || clipOwner != tab.key) return
        player.nextSpeed()
        refresh()
    }

    /** Play [ref] from its stamp, replacing whatever was on — the clip becomes [tab]'s. */
    fun play(ref: AudioRef, tab: DesktopTab?) {
        clipOwner = tab?.key
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
     * Move [tab]'s clip to [ms] — the pill's scrubber, which walks the recording with the sound on or off.
     *
     * A held clip (a pause) moves at once, a sounding one is asked to seek; [Playhead] owns the
     * difference, so this is the same one player either way and a drag never opens a second.
     */
    fun seek(tab: DesktopTab?, ms: Int) {
        if (tab == null || clipOwner != tab.key) return
        player.seek(ms)
        refresh()
    }

    /** A fraction of [tab]'s clip, 0..1, as the millisecond a scrub lands on. */
    fun scrubTargetMs(tab: DesktopTab?, fraction: Float): Int {
        val sound = of(tab)
        return scrubMs(fraction, sound.durationMs)
    }

    /** Where [tab]'s clip is to the millisecond — the karaoke's per-frame read, or null when it is not on. */
    fun clipPositionMs(tab: DesktopTab?): Int? {
        if (tab == null || clipOwner != tab.key) return null
        return player.positionMs() ?: positionMs
    }

    /** Stop playback and forget the clip (the pill's stop). */
    fun stopPlayback() {
        player.stop()
        clipOwner = null
        refresh()
    }

    /**
     * Another tab is now the one on screen: its own audio is what the pill and the page must show.
     *
     * A clip belongs to its document — sound coming out of a tab nobody is looking at would leave the
     * window playing something with no transport in sight — so it is let go. A **take** keeps running:
     * stopping it because the reader looked at another page would lose a lecture, and its stamps still
     * land only in its own tab ([stamp]).
     */
    fun leaveTab() {
        if (clipOwner != null) stopPlayback()
    }

    /**
     * [tab] was closed. Its take is finished (the file is kept, as a stop would keep it) and its clip is
     * let go, so no recording is left with no document to belong to.
     */
    fun forget(tab: DesktopTab?) {
        val key = tab?.key ?: return
        if (recording && takeOwner == key) finishRecording()
        if (clipOwner == key) stopPlayback()
        heard.remove(key)
    }

    /** The user read the sentence: drop it. */
    fun dismissMessage() {
        message = null
        player.dismissMessage()
        recorder.dismissMessage()
    }

    /** The recording [tab]'s page plays from: its topmost stamped stroke, or the document's first. */
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
