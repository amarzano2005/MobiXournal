package com.mobixournal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import androidx.core.content.ContextCompat
import com.mobixournal.audio.AudioRecordingService
import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.AudioSession
import com.mobixournal.audio.AudioWaveformExtractor
import com.mobixournal.audio.WavWriter
import com.mobixournal.format.model.audioRef
import com.mobixournal.audio.documentAudioFiles
import com.mobixournal.audio.ownsRecording
import com.mobixournal.format.SaveFormat
import com.mobixournal.panes.EditorPane
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.KaraokeFrameDriver
import com.mobixournal.render.KaraokeFrames
import com.mobixournal.render.karaokeState
import com.mobixournal.render.scrollToContentBounds
import com.mobixournal.ui.AudioUiState
import java.io.File

// --- audio: record onto strokes, replay from them, keep sidecars beside the .xopp -----------
//
// [MainActivity]'s audio half, kept out of the activity file itself.
//
// A stroke drawn while recording is stamped with the current file and offset ([AudioSession.stamp]),
// and tapping it later replays from that offset. The recordings themselves are plain `.wav` sidecar
// files that live *beside* the `.xopp` rather than inside it, because the format only stores the
// filename and timestamp — so keeping them in step with the document is a copy in
// ([pullAudioSidecars]) and a copy out ([pushAudioSidecars]) against the folder the user nominated.
//
// Without a nominated folder audio still records and plays, but never leaves the app's own storage.

/**
 * Wire the canvas to the audio session: stamp new strokes while recording, replay on a tap.
 *
 * [p] is the pane the surface belongs to, because a take belongs to the **tab** that started it: the stamp
 * is asked only while that pane's showing tab is the owner ([ownsRecording]), so ink drawn in another tab
 * never claims this microphone (`docs/audio.md`).
 */
internal fun MainActivity.attachAudio(view: DrawingSurfaceView, p: EditorPane = pane) {
    view.audioStamp = {
        if (ownsRecording(recordingTabId, p.tabs.active?.id)) audio.stamp() else null
    }
    view.onAudioTap = { ref ->
        when {
            ref == null -> Unit
            else -> {
                withPreparedAudio(ref) {
                    if (!audio.playPrepared(ref)) toast("Recording not found: ${ref.filename}") else {
                        lastAudioRef = ref
                        miniPlayerVisible.value = true
                        scrollToAudioTimestamp(ref.startMs)
                        followRecording(view)
                        audioTick.value++
                    }
                }
            }
        }
    }
}

/**
 * Move the playhead to [ms] and repaint where it landed — the pop-up's scrubber, one step of a drag.
 *
 * A drag on a sounding clip seeks it; on a held one it moves the held position, which is the same call
 * ([AudioSession.seek]). Either way the state is published once rather than by a frame loop: nothing is
 * moving between the drag's own events, so a frame per vsync would repaint a page that has not changed
 * ([KaraokeFrameDriver.publishOnce]).
 */
internal fun MainActivity.scrollToAudioTimestamp(ms: Int) {
    requestAudioScroll(ms)
}

internal fun MainActivity.scrubTo(ms: Int) {
    if (audio.nowPlaying == null) {
        withAudioCandidate(preparePlayer = true) { candidate ->
            if (candidate != null) {
                audio.playPrepared(candidate.copy(startMs = ms))
                audio.pausePlayback()
                karaokeDriver?.publishOnce()
                scrollToAudioTimestamp(ms)
                audioTick.value++
            }
        }
        return
    } else {
        audio.seek(ms)
    }
    karaokeDriver?.publishOnce()
    scrollToAudioTimestamp(ms)
    audioTick.value++
}

/**
 * Follow the clip that just started with the page it was tapped on, so the writing animates to the
 * playhead until it stops ([KaraokeFrameDriver]).
 *
 * One driver at a time — one clip plays at a time, so a second tap replaces the first — and it targets
 * the pane the tap was made in, which is also the pane the recording belongs to in split view. The
 * driver ends itself on completion or a stop; [MainActivity.detachAudio] ends it when the editor goes
 * away, so no frame is posted at a surface that is gone.
 */
internal fun MainActivity.followRecording(view: DrawingSurfaceView) {
    karaokeDriver?.stop()
    val cadence = com.mobixournal.audio.AudioUiCadence()
    val driver = KaraokeFrameDriver(
        choreographer = Choreographer.getInstance(),
        clip = { KaraokeFrames.Clip(audio.nowPlaying, audio.playheadMs, audio.isPlaying) },
        publish = { state ->
            view.karaokeState = state
            if (cadence.publish(System.nanoTime(), state?.isPlaying == true)) audioTick.value++
        },
    )
    karaokeDriver = driver
    driver.follow()
}

/** Stop following any recording and let every page paint itself again (the host's teardown). */
internal fun MainActivity.detachAudio() {
    audioWork.cancel()
    karaokeDriver?.stop()
    karaokeDriver = null
    // A recording torn down with the activity must not leave the `.m4a` encoder running: the tap is
    // finalised first, so whatever was captured is still a playable file.
    endStandaloneExport()
}

/** The audio slot's current state, read fresh on every recomposition [MainActivity.audioTick] triggers. */
@androidx.compose.runtime.Composable
internal fun MainActivity.audioUiState(): AudioUiState {
    audioTick.value // read so Compose re-invokes this when the session changes
    val settings = settingsState.value
    val preview = rememberAudioPreview(miniPlayerVisible.value || audio.nowPlaying != null)
    val activeClip = audio.nowPlaying ?: preview.clip
    // Whichever clip is on, its own bars are the ones to show — `rememberAudioPreview` keys the decode on
    // that same clip, so the two cannot disagree about which recording the waveform belongs to.
    val waveform = preview.waveform

    if (audio.clipDurationMs != null && audio.clipDurationMs != 0) {
        lastDurationMs = audio.clipDurationMs
    }
    val duration = if (activeClip != null) (audio.clipDurationMs ?: lastDurationMs) else null

    val title = pane.tabs.active?.title?.takeIf(String::isNotBlank) ?: "Document"
    val clipTitle = when {
        audio.isRecording -> "Live recording"
        activeClip == null -> null
        preview.page != null -> "$title · Page ${preview.page + 1}"
        else -> title
    }

    // A take belongs to its tab: this capsule shows a recording only when **this** tab owns it, and says
    // whose it is when another document is the one being recorded.
    val ownerId = recordingTabId ?: pane.tabs.active?.id
    val recordingHere = audio.isRecording && ownerId == pane.tabs.active?.id
    val recordingElsewhere = if (audio.isRecording && !recordingHere) titleOfTab(ownerId) else null

    return AudioUiState(
        recording = recordingHere,
        recordingPaused = recordingHere && audio.isRecordingPaused,
        recordingElsewhere = recordingElsewhere,
        playing = audio.isPlaying,
        clipOn = audio.nowPlaying != null,
        clipTitle = clipTitle,
        playheadMs = if (activeClip != null) (audio.playheadMs ?: 0) else 0,
        durationMs = duration,
        folderChosen = audioFolder != null,
        saveStandalone = settings.saveStandaloneAudio,
        playbackSpeed = audio.playbackSpeed,
        waveform = waveform,
        miniPlayerVisible = miniPlayerVisible.value && (activeClip != null || audio.isRecording),
        onToggleRecord = ::toggleRecording,
        onTogglePauseRecord = ::togglePauseRecording,
        onResume = {
            audio.resume()
            surface?.let { followRecording(it) }
            audioTick.value++
        },
        onPause = {
            audio.pausePlayback()
            audioTick.value++
        },
        onStopPlayback = {
            audioWork.cancelPlayback()
            audio.stopPlayback()
            karaokeDriver?.stop()
            miniPlayerVisible.value = false
            audioTick.value++
        },
        onScrub = ::scrubTo,
        onSkip = { deltaMs ->
            val cur = audio.playheadMs ?: 0
            val dur = duration ?: 0
            val target = (cur + deltaMs).coerceIn(0, dur.coerceAtLeast(0))
            scrubTo(target)
        },
        onSpeedChange = { speed ->
            audio.playbackSpeed = speed
            audioTick.value++
        },
        onChooseFolder = { audioFolderLauncher.launch(null) },
        // Written through the one settings writer, so the toggle and the Settings page read the same
        // value: saving the store alone left the window holding the old one and the next unrelated
        // change put it back.
        onToggleSaveStandalone = { enabled ->
            updateSettings { it.copy(saveStandaloneAudio = enabled) }
            audioTick.value++
        },
        onToggleMiniPlayer = {
            miniPlayerVisible.value = !miniPlayerVisible.value
            audioTick.value++
        },
        onPlayAutonomous = ::playAutonomousAudio,
    )
}

/**
 * Pause or resume the active recording, from whichever surface asked — the mini capsule, the audio
 * pop-up, or the notification's own Pause/Resume button.
 *
 * The notification is told the new state here rather than by the caller, so a pause made in the app
 * and a pause made in the shade cannot leave the shade saying the opposite of what is happening.
 */
internal fun MainActivity.togglePauseRecording() {
    if (!audio.isRecording) return
    if (audio.isRecordingPaused) {
        audio.resumeRecording()
        toast("Recording resumed")
    } else {
        audio.pauseRecording()
        toast("Recording paused")
    }
    AudioRecordingService.notifyPaused(this, audio.isRecordingPaused)
    audioTick.value++
}

/**
 * Stop a running recording, or ask for the microphone and start one — **in this tab**.
 *
 * The capsule is per tab: a take belongs to the tab that started it, so while one runs elsewhere this is a
 * click that says whose recording it is rather than one that opens a second microphone or stops another
 * document's take (`AudioUiState.recordingElsewhere` is the same fact drawn on the button).
 */
internal fun MainActivity.toggleRecording() {
    if (audio.isRecording) {
        if (recordingTabId != null && recordingTabId != pane.tabs.active?.id) {
            toast("A recording is running in another tab. Switch back to it to stop it")
            return
        }
        stopRecordingTake()
        return
    }
    // Nothing is recording, so no tab owns a take any more: a stale id must not freeze the button.
    recordingTabId = null

    withAudioCandidate { existing ->
        if (existing != null) {
            pendingAudioOverwriteClip.value = existing.filename
            pendingAudioOverwriteConsent.value = { checkPermissionAndBeginRecording() }
        } else checkPermissionAndBeginRecording()
    }
}

/**
 * Stop the take this window is capturing and record the finished file under **its own** tab.
 *
 * The owner is read before the session is stopped, because stopping clears it — and a file filed under the
 * tab on screen instead of the one it was recorded in is exactly the confusion this rule removes.
 */
internal fun MainActivity.stopRecordingTake() {
    AudioRecordingService.stop(this)
    val owner = recordingTabId
    val file = audio.stopRecording()
    toast(if (file != null) "Recording saved: ${file.name}" else "No active recording")
    if (file != null) {
        val ref = AudioRef(file.name, 0)
        lastAudioRef = ref
        if (owner != null) tabAudioRefs[owner] = ref
    }
    recordingTabId = null
    // The parallel encoder is unhooked before anything else can feed it, whether or not a
    // standalone copy is still wanted.
    val standaloneTap = detachStandaloneTap()
    if (file != null && settingsState.value.saveStandaloneAudio) {
        // Nothing is converted here any more: the `.m4a` was encoded while the microphone was
        // open (`StandaloneAudioTap`), so the export is a copy. The job is queued first, so a
        // copy the closing app interrupts is picked up on the next launch instead of lost.
        exportStandaloneInBackground(file, standaloneTap)
    } else {
        discardStandaloneExport(standaloneTap)
    }
    pushAudioSidecars()
    audioTick.value++
}

/**
 * The title of the tab with [id], for the one sentence the capsule says about a take running elsewhere —
 * "another tab" when this pane does not hold it (a take can be started in the other half of a split).
 */
internal fun MainActivity.titleOfTab(id: String?): String {
    val open = id?.let { wanted ->
        panes.asSequence().flatMap { it.tabs.tabs.asSequence() }.firstOrNull { it.id == wanted }
    }
    return open?.title?.takeIf { it.isNotBlank() } ?: "another tab"
}

internal fun MainActivity.checkPermissionAndBeginRecording() {
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) beginRecording() else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
}

/** Start recording, reporting either the file we opened or the microphone we could not. */
internal fun MainActivity.beginRecording() {
    // The document's own format decides the codec: a `.mobix` bundles its audio, so it records the
    // compressed stream, while a `.xopp` records the WAV sidecar the desktop plays
    // (`docs/audio.md`). A `.mobix` whose device cannot encode Opus records WAV too.
    requestNotificationPermissionIfNecessary()
    val name = audio.startRecording(compressed = saveFormat == SaveFormat.MOBIX)
    if (name != null) {
        // The take is this tab's from its first sample: the id is what every later stamp and the stop
        // are keyed on.
        recordingTabId = pane.tabs.active?.id
        // The standalone `.m4a` is produced while the lecture is still going, so the end of it costs
        // a copy rather than a conversion.
        if (settingsState.value.saveStandaloneAudio) beginStandaloneExport(name)
        // The shade stops the take wherever it was started, so it calls the stop itself rather than the
        // capsule's tab-gated toggle (`toggleRecording` answers "another tab" on purpose).
        AudioRecordingService.onStopRequested = {
            runOnUiThread { if (audio.isRecording) stopRecordingTake() }
        }
        // The shade's Pause/Resume runs the same toggle the capsule does, so the recording it pauses
        // (and the notification that describes it) is the one the user is watching.
        AudioRecordingService.onPauseRequested = {
            runOnUiThread { if (audio.isRecording) togglePauseRecording() }
        }
        AudioRecordingService.start(this)
        toast("Recording in progress")
    } else {
        toast("Unable to open microphone")
    }
}

/**
 * Adopt a newly picked audio folder, holding onto the grant across restarts so sidecars keep
 * resolving, and immediately pull in whatever the open document references.
 */
internal fun MainActivity.adoptAudioFolder(uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
    audioFolder = uri
    updateSettings { it.copy(audioFolderUri = uri.toString()) }
    audioTick.value++
    pullAudioSidecars()
}

/** Copy the sidecars the open document references out of the nominated folder, so they replay. */
internal fun MainActivity.pullAudioSidecars() = syncAudioSidecars(export = false)

/** Copy the sidecars the open document references into the nominated folder, beside the .xopp. */
internal fun MainActivity.pushAudioSidecars() = syncAudioSidecars(export = true)

/**
 * Play audio autonomously without requiring the user to tap a specific stroke on the page,
 * or toggle pause/resume if playback is already active.
 */
internal fun MainActivity.playAutonomousAudio() {
    if (audio.isPlaying) {
        audio.pausePlayback()
        audioTick.value++
        return
    }
    val surfaceView = surface
    if (audio.nowPlaying != null) {
        audio.resume()
        miniPlayerVisible.value = true
        if (surfaceView != null) followRecording(surfaceView)
        audioTick.value++
        return
    }
    // Playing the document's audio is the one gesture that may look past the page in front: the user
    // asked for *the* recording, so that walk is this gesture's own answer rather than a cost the
    // chrome pays on every tick and every page turn.
    withAudioCandidate(preparePlayer = true, deep = true) { candidate ->
        when {
            candidate == null -> toast("No audio recording in this tab")
            !audio.playPrepared(candidate) -> toast("Recording not found: ${candidate.filename}")
            else -> {
                lastAudioRef = candidate
                miniPlayerVisible.value = true
                scrollToAudioTimestamp(candidate.startMs)
                if (surfaceView != null) followRecording(surfaceView)
                audioTick.value++
            }
        }
    }
}
