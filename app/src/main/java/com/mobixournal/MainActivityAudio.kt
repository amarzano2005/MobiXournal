package com.mobixournal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Choreographer
import androidx.core.content.ContextCompat
import com.mobixournal.audio.AudioRecordingService
import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.AudioSession
import com.mobixournal.audio.AudioStandaloneExporter
import com.mobixournal.audio.AudioWaveformExtractor
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
                followRecording(view)
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
    val settings = settingsStore.load()
    val activeClip = audio.nowPlaying ?: lastAudioRef?.filename
    val playingFile = activeClip?.let { File(filesDir, "audio/$it") }
    val waveform = if (playingFile != null && playingFile.exists()) {
        AudioWaveformExtractor.extract(playingFile)
    } else {
        emptyList()
    }

    val transcription = activeTranscription.value ?: playingFile?.let { file ->
        val transcriptFile = File(filesDir, "audio/${file.name}.transcript.json")
        if (transcriptFile.isFile) {
            runCatching { com.mobixournal.audio.TranscriptionCodec.decode(transcriptFile.readText()) }.getOrNull()
        } else {
            surface?.toDocument()?.let { doc ->
                com.mobixournal.audio.AudioInkAligner.buildTranscriptFromStrokes(doc, file.name)
            }
        }
    }

    if (audio.clipDurationMs != null && audio.clipDurationMs != 0) {
        lastDurationMs = audio.clipDurationMs
    }
    val duration = audio.clipDurationMs ?: lastDurationMs

    return AudioUiState(
        recording = audio.isRecording,
        recordingPaused = audio.isRecordingPaused,
        playing = audio.isPlaying,
        clipOn = audio.nowPlaying != null,
        playheadMs = audio.playheadMs ?: if (!audio.isPlaying && lastAudioRef != null) (lastDurationMs ?: 0) else 0,
        durationMs = duration,
        folderChosen = audioFolder != null,
        saveStandalone = settings.saveStandaloneAudio,
        playbackSpeed = audio.playbackSpeed,
        waveform = waveform,
        transcription = transcription,
        miniPlayerVisible = miniPlayerVisible.value,
        onToggleRecord = ::toggleRecording,
        onTogglePauseRecord = ::togglePauseRecording,
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
    )
}

/** Pause or resume the active recording. */
internal fun MainActivity.togglePauseRecording() {
    if (!audio.isRecording) return
    if (audio.isRecordingPaused) {
        audio.resumeRecording()
        speechEngine?.resumeListening()
        toast("Registrazione ripresa")
    } else {
        audio.pauseRecording()
        speechEngine?.pauseListening()
        toast("Registrazione in pausa")
    }
    audioTick.value++
}

/** Stop a running recording, or ask for the microphone and start one. */
internal fun MainActivity.toggleRecording() {
    if (audio.isRecording) {
        AudioRecordingService.stop(this)
        speechEngine?.stopListening()
        speechEngine = null
        val file = audio.stopRecording()
        toast(if (file != null) "Recording saved: ${file.name}" else "Nothing was recording")
        if (file != null) {
            lastAudioRef = AudioRef(file.name, 0)
            val transcript = activeTranscription.value
            if (transcript != null && transcript.segments.isNotEmpty()) {
                val transcriptFile = File(filesDir, "audio/${file.name}.transcript.json")
                runCatching {
                    transcriptFile.writeText(com.mobixournal.audio.TranscriptionCodec.encode(transcript))
                }
            }
        }
        if (file != null && settingsStore.load().saveStandaloneAudio) {
            val exported = AudioStandaloneExporter.exportStandalone(this, file)
            if (exported) toast("Standalone audio exported to Music/Recordings")
        }
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
    if (name != null) {
        AudioRecordingService.onStopRequested = {
            runOnUiThread { if (audio.isRecording) toggleRecording() }
        }
        AudioRecordingService.start(this)
        startLiveSpeechTranscription(name)
        toast("Recording — strokes will replay from here")
    } else {
        toast("Could not open the microphone")
    }
}

internal fun MainActivity.startLiveSpeechTranscription(filename: String) {
    runCatching {
        val engine = com.mobixournal.audio.SpeechToTextEngine(this)
        if (engine.isAvailable()) {
            val finalizedSegments = mutableListOf<com.mobixournal.audio.TranscriptionSegment>()
            val selectedLang = settingsStore.load().transcriptionLanguage
            val resolvedLang = com.mobixournal.audio.resolveTranscriptionLanguageTag(selectedLang)
            com.mobixournal.audio.checkSpeechLanguagePack(this, resolvedLang) { status ->
                if (status == com.mobixournal.audio.LanguagePackStatus.DOWNLOAD_AVAILABLE) {
                    toast("Pacchetto offline [$resolvedLang] non scaricato: trascrizione online attiva")
                }
            }
            engine.startListening(
                language = resolvedLang,
                listener = object : com.mobixournal.audio.SpeechToTextEngine.TranscriptionListener {
                    override fun onSegmentRecognized(segment: com.mobixournal.audio.TranscriptionSegment) {
                        runOnUiThread {
                            finalizedSegments.add(segment)
                            activeTranscription.value = com.mobixournal.audio.AudioTranscription(
                                audioFilename = filename,
                                segments = finalizedSegments.toList(),
                            )
                            audioTick.value++
                        }
                    }

                    override fun onPartialSubtitle(text: String, startMs: Int, endMs: Int) {
                        runOnUiThread {
                            val liveSegment = com.mobixournal.audio.TranscriptionSegment(
                                startTimeMs = startMs,
                                endTimeMs = endMs,
                                text = text,
                                confidence = 0.8,
                            )
                            activeTranscription.value = com.mobixournal.audio.AudioTranscription(
                                audioFilename = filename,
                                segments = finalizedSegments + listOf(liveSegment),
                            )
                            audioTick.value++
                        }
                    }

                    override fun onError(error: String) {
                        android.util.Log.w("AudioTranscription", "Speech recognition error: $error")
                    }
                    override fun onFinished() {}
                },
            )
            speechEngine = engine
        }
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
    val last = lastAudioRef
    if (last != null && File(filesDir, "audio/${last.filename}").isFile) {
        return AudioRef(last.filename, 0)
    }

    val doc = surface?.toDocument()
    if (doc != null) {
        val docFiles = documentAudioFiles(doc)
        val available = docFiles.firstOrNull { File(filesDir, "audio/$it").isFile }
        if (available != null) return AudioRef(available, 0)
        if (docFiles.isNotEmpty()) return AudioRef(docFiles.first(), 0)
    }

    val audioDir = File(filesDir, "audio")
    if (audioDir.isDirectory) {
        val latest = audioDir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".wav", ignoreCase = true) || it.name.endsWith(".ogg", ignoreCase = true)) }
            ?.maxByOrNull { it.lastModified() }
        if (latest != null) return AudioRef(latest.name, 0)
    }

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
            if (surfaceView != null) followRecording(surfaceView)
            audioTick.value++
        } else {
            toast("Registrazione non trovata: ${candidate.filename}")
        }
    } else {
        toast("Nessuna registrazione audio trovata")
    }
}
