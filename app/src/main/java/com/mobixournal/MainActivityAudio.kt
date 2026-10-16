package com.mobixournal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Choreographer
import androidx.core.content.ContextCompat
import com.mobixournal.audio.AudioSession
import com.mobixournal.format.SaveFormat
import com.mobixournal.render.DrawingSurfaceView
import com.mobixournal.render.KaraokeFrameDriver
import com.mobixournal.render.KaraokeFrames
import com.mobixournal.render.karaokeState
import com.mobixournal.ui.AudioUiState

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

/** Wire the canvas to the audio session: stamp new strokes while recording, replay on a tap. */
internal fun MainActivity.attachAudio(view: DrawingSurfaceView) {
    view.audioStamp = { audio.stamp() }
    view.onAudioTap = { ref ->
        when {
            ref == null -> toast("That stroke has no recording")
            !audio.play(ref) -> toast("Recording not found: ${ref.filename}")
            else -> followRecording(view)
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
internal fun MainActivity.scrubTo(ms: Int) {
    audio.seek(ms)
    karaokeDriver?.publishOnce()
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
    val driver = KaraokeFrameDriver(
        choreographer = Choreographer.getInstance(),
        clip = { KaraokeFrames.Clip(audio.nowPlaying, audio.playheadMs, audio.isPlaying) },
        publish = { state -> view.karaokeState = state },
    )
    karaokeDriver = driver
    driver.follow()
}

/** Stop following any recording and let every page paint itself again (the host's teardown). */
internal fun MainActivity.detachAudio() {
    karaokeDriver?.stop()
    karaokeDriver = null
}

/** The audio slot's current state, read fresh on every recomposition [MainActivity.audioTick] triggers. */
internal fun MainActivity.audioUiState(): AudioUiState {
    audioTick.value // read so Compose re-invokes this when the session changes
    return AudioUiState(
        recording = audio.isRecording,
        playing = audio.isPlaying,
        clipOn = audio.nowPlaying != null,
        playheadMs = audio.playheadMs,
        durationMs = audio.clipDurationMs,
        folderChosen = audioFolder != null,
        onToggleRecord = ::toggleRecording,
        onResume = {
            audio.resume()
            karaokeDriver?.publishOnce()
            audioTick.value++
        },
        onPause = {
            audio.pausePlayback()
            audioTick.value++
        },
        onStopPlayback = {
            audio.stopPlayback()
            karaokeDriver?.stop()
            audioTick.value++
        },
        onScrub = ::scrubTo,
        onChooseFolder = { audioFolderLauncher.launch(null) },
    )
}

/** Stop a running recording, or ask for the microphone and start one. */
internal fun MainActivity.toggleRecording() {
    if (audio.isRecording) {
        val file = audio.stopRecording()
        toast(if (file != null) "Recording saved: ${file.name}" else "Nothing was recording")
        pushAudioSidecars()
        return
    }
    val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) beginRecording() else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
}

/** Start recording, reporting either the file we opened or the microphone we could not. */
internal fun MainActivity.beginRecording() {
    // The document's own format decides the codec: a `.mobix` bundles its audio, so it records the
    // compressed stream, while a `.xopp` records the WAV sidecar the desktop plays
    // (`docs/audio-inking-sync.md`). A `.mobix` whose device cannot encode Opus records WAV too.
    val name = audio.startRecording(compressed = saveFormat == SaveFormat.MOBIX)
    toast(if (name != null) "Recording — strokes will replay from here" else "Could not open the microphone")
}

/**
 * Adopt a newly picked audio folder, holding onto the grant across restarts so sidecars keep
 * resolving, and immediately pull in whatever the open document references.
 */
internal fun MainActivity.adoptAudioFolder(uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
    audioFolder = uri
    settingsStore.save(settingsStore.load().copy(audioFolderUri = uri.toString()))
    audioTick.value++
    pullAudioSidecars()
}

/** Copy the sidecars the open document references out of the nominated folder, so they replay. */
internal fun MainActivity.pullAudioSidecars() {
    val folder = audioFolder ?: return
    val doc = surface?.toDocument() ?: return
    val pulled = audio.importSidecars(folder, doc)
    val missing = audio.missingFor(doc).size
    if (pulled > 0) toast("Loaded $pulled recording(s)")
    else if (missing > 0) toast("$missing recording(s) referenced but not in the audio folder")
}

/** Copy the sidecars the open document references into the nominated folder, beside the .xopp. */
internal fun MainActivity.pushAudioSidecars() {
    val folder = audioFolder ?: return
    val doc = surface?.toDocument() ?: return
    audio.exportSidecars(folder, doc)
    // The contract an export cannot keep on its own: a `.xopp` names a sidecar the desktop plays, and
    // nothing here transcodes, so a compressed recording travels as itself and is reported rather than
    // renamed to a `.wav` that would hold Opus bytes.
    val compressed = audio.compressedFor(doc)
    if (compressed.isNotEmpty()) {
        toast("${compressed.size} recording(s) here are compressed — desktop Xournal++ plays WAV only")
    }
}
