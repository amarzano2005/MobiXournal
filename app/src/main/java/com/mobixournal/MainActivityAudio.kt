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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mobixournal.audio.AudioRecordingService
import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.AudioSession
import com.mobixournal.audio.AudioStandaloneExporter
import com.mobixournal.audio.AudioTranscoder
import com.mobixournal.audio.AudioWaveformExtractor
import com.mobixournal.audio.WavWriter
import com.mobixournal.format.model.audioRef
import com.mobixournal.audio.documentAudioFiles
import com.mobixournal.format.SaveFormat
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

/** Wire the canvas to the audio session: stamp new strokes while recording, replay on a tap. */
internal fun MainActivity.attachAudio(view: DrawingSurfaceView) {
    view.audioStamp = { audio.stamp() }
    view.onAudioTap = { ref ->
        when {
            ref == null -> Unit
            !audio.play(ref) -> toast("Recording not found: ${ref.filename}")
            else -> {
                lastAudioRef = ref
                miniPlayerVisible.value = true
                scrollToAudioTimestamp(ref.startMs)
                followRecording(view)
                audioTick.value++
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
    val surfaceView = surface ?: return
    val doc = surfaceView.toDocument() ?: return
    val playing = audio.nowPlaying ?: return
    val hits = com.mobixournal.audio.AudioInkAligner.findStrokesAtTime(doc, playing, ms)
    val firstHit = hits.firstOrNull() ?: return
    surfaceView.scrollToContentBounds(firstHit.first, firstHit.second)
}

internal fun MainActivity.scrubTo(ms: Int) {
    if (audio.nowPlaying == null) {
        val candidate = findCandidateAudioRef()
        if (candidate != null) {
            audio.play(candidate.copy(startMs = ms))
            audio.pausePlayback()
        }
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
    val driver = KaraokeFrameDriver(
        choreographer = Choreographer.getInstance(),
        clip = { KaraokeFrames.Clip(audio.nowPlaying, audio.playheadMs, audio.isPlaying) },
        publish = { state ->
            view.karaokeState = state
            audioTick.value++
        },
    )
    karaokeDriver = driver
    driver.follow()
}

/** Stop following any recording and let every page paint itself again (the host's teardown). */
internal fun MainActivity.detachAudio() {
    karaokeDriver?.stop()
    karaokeDriver = null
}

private val transcriptDiskCache = mutableMapOf<String, com.mobixournal.audio.AudioTranscription?>()

private fun MainActivity.resolveTranscriptionForClip(activeClip: String?): com.mobixournal.audio.AudioTranscription? {
    val live = activeTranscription.value
    if (audio.isRecording && live != null) return live
    if (activeClip == null) return live
    if (live != null && live.audioFilename == activeClip) return live

    val transcriptFile = File(filesDir, "audio/$activeClip.transcript.json")
    val cacheKey = "${transcriptFile.absolutePath}_${transcriptFile.lastModified()}"
    if (transcriptDiskCache.containsKey(cacheKey)) {
        return transcriptDiskCache[cacheKey]
    }
    val decoded = if (transcriptFile.isFile) {
        runCatching { com.mobixournal.audio.TranscriptionCodec.decode(transcriptFile.readText()) }.getOrNull()
    } else {
        null
    }
    transcriptDiskCache[cacheKey] = decoded
    return decoded
}

/** The audio slot's current state, read fresh on every recomposition [MainActivity.audioTick] triggers. */
internal fun MainActivity.audioUiState(): AudioUiState {
    audioTick.value // read so Compose re-invokes this when the session changes
    val settings = settingsStore.load()
    val candidate = findCandidateAudioRef()
    val activeClip = audio.nowPlaying ?: candidate?.filename
    val playingFile = activeClip?.let { File(filesDir, "audio/$it") }
    val waveform = if (playingFile != null && playingFile.exists()) {
        AudioWaveformExtractor.extract(playingFile)
    } else {
        emptyList()
    }

    val transcription = resolveTranscriptionForClip(activeClip)

    if (audio.clipDurationMs != null && audio.clipDurationMs != 0) {
        lastDurationMs = audio.clipDurationMs
    }
    val duration = if (activeClip != null) (audio.clipDurationMs ?: lastDurationMs) else null

    val clipTitle = resolveActiveClipTitle(activeClip)

    return AudioUiState(
        recording = audio.isRecording,
        recordingPaused = audio.isRecordingPaused,
        playing = audio.isPlaying,
        clipOn = audio.nowPlaying != null,
        clipTitle = clipTitle,
        playheadMs = if (activeClip != null) (audio.playheadMs ?: 0) else 0,
        durationMs = duration,
        folderChosen = audioFolder != null,
        saveStandalone = settings.saveStandaloneAudio,
        playbackSpeed = audio.playbackSpeed,
        waveform = waveform,
        transcription = transcription,
        isTranscribing = isTranscribing.value,
        transcriptionStatus = transcriptionStatus.value,
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
        transcriptionLanguage = settings.transcriptionLanguage,
        onLanguageChange = { lang ->
            settingsStore.save(settingsStore.load().copy(transcriptionLanguage = lang))
            audioTick.value++
        },
        onChooseFolder = { audioFolderLauncher.launch(null) },
        onToggleSaveStandalone = { enabled ->
            settingsStore.save(settingsStore.load().copy(saveStandaloneAudio = enabled))
            audioTick.value++
        },
        onToggleMiniPlayer = {
            miniPlayerVisible.value = !miniPlayerVisible.value
            audioTick.value++
        },
        onPlayAutonomous = ::playAutonomousAudio,
        onTranscribeActiveClip = {
            val clip = activeClip?.let { File(filesDir, "audio/$it") }
            if (clip != null && clip.isFile) {
                transcribeDeferredAudio(clip)
            } else {
                toast("No active audio recording")
            }
        },
    )
}

private fun MainActivity.resolveActiveClipTitle(activeClip: String?): String? = when {
    audio.isRecording -> "Live recording"
    activeClip != null -> {
        val currentTabTitle = pane.tabs.active?.title?.takeIf { it.isNotBlank() } ?: "Document"
        val doc = surface?.toDocument()
        val matchedPage = if (doc != null) {
            com.mobixournal.audio.AudioInkAligner.findTimestampedStrokes(doc)
                .firstOrNull { (_, _, ref) ->
                    val a = ref.filename.substringAfterLast('/').substringAfterLast('\\')
                    val b = activeClip.substringAfterLast('/').substringAfterLast('\\')
                    a.equals(b, ignoreCase = true)
                }?.first
        } else null
        if (matchedPage != null) "$currentTabTitle • Page ${matchedPage + 1}" else currentTabTitle
    }
    else -> null
}

/** Pause or resume the active recording. */
/** Pause or resume the active recording. */
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

/** Stop a running recording, or ask for the microphone and start one. */
internal fun MainActivity.toggleRecording() {
    if (audio.isRecording) {
        AudioRecordingService.stop(this)
        // The live job stops feeding first, so the file path resumes from what it really finished.
        endLiveTranscription()
        val file = audio.stopRecording()
        toast(if (file != null) "Recording saved: ${file.name}" else "No active recording")
        if (file != null) {
            val ref = AudioRef(file.name, 0)
            lastAudioRef = ref
            pane.tabs.active?.id?.let { tabId ->
                tabAudioRefs[tabId] = ref
            }
            transcribeDeferredAudio(file)
        }
        if (file != null && settingsStore.load().saveStandaloneAudio) {
            // Off the main thread: a long recording is re-encoded to `.m4a` first (Ogg/Opus and WAV
            // are what the app records, M4A is what a phone or an uploader accepts), which takes a
            // moment for a lecture and must not freeze the editor while it runs.
            val toExport = file
            lifecycleScope.launch(Dispatchers.IO) {
                val exported = AudioStandaloneExporter.exportStandalone(this@toggleRecording, toExport)
                withContext(Dispatchers.Main) {
                    if (exported) toast("Standalone audio exported as ${AudioTranscoder.CONTAINER_SUFFIX}")
                }
            }
        }
        pushAudioSidecars()
        audioTick.value++
        return
    }

    val existing = findCandidateAudioRef()
    if (existing != null) {
        pendingAudioOverwriteClip.value = existing.filename
        pendingAudioOverwriteConsent.value = {
            checkPermissionAndBeginRecording()
        }
        return
    }

    checkPermissionAndBeginRecording()
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
    // (`docs/audio-inking-sync.md`). A `.mobix` whose device cannot encode Opus records WAV too.
    requestNotificationPermissionIfNecessary()
    val name = audio.startRecording(compressed = saveFormat == SaveFormat.MOBIX)
    if (name != null) {
        beginLiveTranscription(name)
        // The standalone `.m4a` is produced while the lecture is still going, so the end of it costs
        // a copy rather than a conversion.
        AudioRecordingService.onStopRequested = {
            runOnUiThread { if (audio.isRecording) toggleRecording() }
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
 * Start transcribing **while the microphone is open**, when a Whisper model is installed.
 *
 * The capture chunks go straight into [com.mobixournal.audio.LiveTranscription], which cuts them into
 * spans at silences and transcribes them on its own worker, writing the sidecar's transcript file as
 * it goes. That file is what the post-stop job resumes from, so nothing is transcribed twice and a
 * recording that keeps up needs only its last few seconds at Stop. It runs in the app's process, which
 * the recording foreground service keeps alive and awake, so background and screen-off recording are
 * no different.
 *
 * Without a model nothing starts: the first-run download still happens at Stop, and live inference on
 * a machine whose encoder is missing would only compete with the writing.
 */
internal fun MainActivity.beginLiveTranscription(fileName: String) {
    val model = com.mobixournal.audio.WhisperModelManager.getAvailableModel(this) ?: return
    val live = com.mobixournal.audio.LiveTranscription(
        fileName = fileName,
        dir = filesDir,
        language = com.mobixournal.audio.resolveTranscriptionLanguageTag(settingsStore.load().transcriptionLanguage),
        modelDir = com.mobixournal.audio.WhisperModelManager.getModelDir(this, model),
        isTiny = model.isTiny,
    )
    if (!live.start()) return
    liveTranscription = live
    audio.onPcmData = { buffer, count, rate -> live.feed(buffer, count, rate) }
}

/** Stop the live job and hand the capture back (the post-stop transcription reads its transcript). */
internal fun MainActivity.endLiveTranscription() {
    audio.onPcmData = null
    liveTranscription?.stop()
    liveTranscription = null
}

internal fun MainActivity.transcribeDeferredAudio(file: File) {
    val selectedLang = settingsStore.load().transcriptionLanguage
    val resolvedLang = com.mobixournal.audio.resolveTranscriptionLanguageTag(selectedLang)

    isTranscribing.value = true
    transcriptionStatus.value = "Starting transcription..."
    audioTick.value++

    val availableModel = com.mobixournal.audio.WhisperModelManager.getAvailableModel(this)
    if (availableModel != null) {
        startTranscriptionService(file, resolvedLang)
    } else {
        downloadWhisperAndTranscribe(file, resolvedLang)
    }
}

private fun MainActivity.startTranscriptionService(file: File, resolvedLang: String) {
    com.mobixournal.audio.AudioTranscriptionService.onProgressUpdated = { status, _, _ ->
        runOnUiThread {
            transcriptionStatus.value = status
            audioTick.value++
        }
    }
    com.mobixournal.audio.AudioTranscriptionService.onTranscriptionCompleted = { completedFile, transcript, statusMsg ->
        runOnUiThread {
            isTranscribing.value = false
            if (transcript != null) {
                commitTranscriptionSegments(completedFile, transcript.segments, resolvedLang)
                transcriptionStatus.value = null
                toast("Transcription complete: ${transcript.segments.size} segments synchronized")
            } else {
                transcriptionStatus.value = statusMsg
                toast(statusMsg)
            }
            audioTick.value++
        }
    }
    com.mobixournal.audio.AudioTranscriptionService.start(this, file, resolvedLang)
}

private fun MainActivity.downloadWhisperAndTranscribe(file: File, resolvedLang: String) {
    transcriptionStatus.value = "Download Whisper Tiny (39 MB)... 0%"
    audioTick.value++

    lifecycleScope.launch(Dispatchers.IO) {
        val success = com.mobixournal.audio.WhisperModelManager.downloadModel(
            context = this@downloadWhisperAndTranscribe,
            modelType = com.mobixournal.audio.WhisperModelType.TINY,
            onProgress = { _, _, ratio ->
                val percent = (ratio * 100).toInt().coerceIn(0, 99)
                runOnUiThread {
                    transcriptionStatus.value = "Download Whisper Tiny ($percent%)..."
                    audioTick.value++
                }
            },
            isCancelled = { !isTranscribing.value },
        )

        withContext(Dispatchers.Main) {
            if (success) {
                toast("Whisper Tiny downloaded! Starting transcription.")
                startTranscriptionService(file, resolvedLang)
            } else {
                transcriptionStatus.value = "Using system recognition..."
                audioTick.value++
                startTranscriptionService(file, resolvedLang)
            }
        }
    }
}

private fun MainActivity.commitTranscriptionSegments(
    file: File,
    segments: List<com.mobixournal.audio.TranscriptionSegment>,
    resolvedLang: String,
) {
    val transcript = com.mobixournal.audio.AudioTranscription(
        audioFilename = file.name,
        segments = segments,
        language = resolvedLang,
    )
    activeTranscription.value = transcript
    val transcriptFile = File(filesDir, "audio/${file.name}.transcript.json")
    runCatching {
        transcriptFile.writeText(com.mobixournal.audio.TranscriptionCodec.encode(transcript))
    }
    transcriptDiskCache.clear()
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

/** Find an audio recording candidate to play when autonomous playback is triggered. */
internal fun MainActivity.findCandidateAudioRef(): AudioRef? {
    val surfaceView = surface
    val doc = surfaceView?.toDocument()

    // When a document is open in the active tab:
    if (doc != null) {
        // 1. Check strokes on the current page first
        val curPageIndex = surfaceView.currentPage.coerceIn(0, (doc.pages.size - 1).coerceAtLeast(0))
        val curPage = doc.pages.getOrNull(curPageIndex)
        if (curPage != null) {
            val pageFiles = curPage.layers.asSequence()
                .flatMap { it.elements.asSequence() }
                .filterIsInstance<com.mobixournal.format.model.Stroke>()
                .mapNotNull { it.audioRef() }
                .toList()
            val available = pageFiles.firstOrNull { File(filesDir, "audio/${it.filename}").isFile }
            if (available != null) return available
        }

        // 2. Otherwise check strokes anywhere across the other pages of THIS document
        val docFiles = documentAudioFiles(doc)
        val available = docFiles.firstOrNull { File(filesDir, "audio/$it").isFile }
        if (available != null) return AudioRef(available, 0)
    }

    // 3. Check if an audio was recorded in this active tab session
    val activeTabId = pane.tabs.active?.id
    if (activeTabId != null) {
        val tabRef = tabAudioRefs[activeTabId]
        if (tabRef != null && File(filesDir, "audio/${tabRef.filename}").isFile) {
            return tabRef
        }
    }

    // If the active tab has no audio strokes or referenced files at all, do NOT play anything!
    return null
}

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
    val candidate = findCandidateAudioRef()
    if (candidate != null) {
        if (audio.play(candidate)) {
            lastAudioRef = candidate
            miniPlayerVisible.value = true
            scrollToAudioTimestamp(candidate.startMs)
            if (surfaceView != null) followRecording(surfaceView)
            audioTick.value++
        } else {
            toast("Recording not found: ${candidate.filename}")
        }
    } else {
        toast("No audio recording in this tab")
    }
}
