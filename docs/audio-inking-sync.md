# Audio-inking synchronization: karaoke replay and audio-tap

> Read when implementing real-time audio-stroke synchronization, compressed audio bundling, inking karaoke animation, or sub-stroke audio scrubbing.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Beyond disconnected sidecars

Legacy Xournal++ records external `.wav` sidecars (documented in [audio.md](audio.md)) and timestamps
only the starting moment of an entire stroke (`ts` in milliseconds). If a user writes a long sentence in
one continuous stroke or pauses while speaking, the temporal link between sound and ink is coarse and lost.

The `.mobix` architecture (see [mobix-schema-spec.md](mobix-schema-spec.md)) introduces **continuous
audio-to-point synchronization**:

```text
Audio Recording (Opus 48kHz Stream)
├── [00:00.000] "Oggi spieghiamo..."
├── [00:03.200] "la matrice jacobiana" ────────────────────────┐
└── [00:06.500] "calcolata nel punto P"                        │
                                                               ▼ Exact Audio Offset
Inking Stroke: "Jacobiana"                                    Point 0: t = 3200 ms
Point Sequence: P0(3200ms) ──► P1(3210ms) ──► P2(3225ms) ... ──► Point N: t = 4150 ms
```

---

## Bundled compressed audio (`assets/`)

Rather than relying on vulnerable loose sidecars in user folders:
- A recording the app's stores hold is **bundled** into the archive by content hash
  (`assets/aud_<sha256>.<ext>`, [mobix-schema-spec.md](mobix-schema-spec.md)), so the same recording
  referenced twice is stored once: a save and an open go through
  `MobixAssetSink.add(bytes, AssetKind.AUDIO)` and `io/BundledPageAssets.kt` respectively.
- **The container is the one the bytes are in**, and the extension says which. A `.mobix` document
  records compressed audio: **Ogg/Opus** (`audio/OpusAudioRecorder.kt`: 48 kHz mono, 32 kbps, `MediaCodec`'s `audio/opus`
  into the platform's Ogg muxer) with universal **M4A/AAC** fallback (`audio/AacAudioRecorder.kt`: 44.1 kHz mono, 32 kbps,
  `audio/mp4a-latm` into MP4 muxer), so its recordings are `aud_<sha256>.ogg` or `aud_<sha256>.m4a` — $\approx 15\text{ MB}$ an
  hour against $> 300\text{ MB}$ of WAV. A `.xopp` document records 16-bit mono RIFF/WAVE, because its
  audio leaves as a sidecar desktop Xournal++ plays, and a `.mobix` on a device without any compressed encoder
  falls back to that same WAV path. Nothing is ever transcoded or renamed: an `.ogg`
  stream from another writer keeps `.ogg`, and the sniffed container is what an entry is named after
  (`MobixAssetSink.suffixFor` — `RIFF`/`WAVE` -> `wav`, `OggS` -> `ogg`, `ftyp` -> `m4a`).
- On open, the recording is adopted into the app's audio store when **the page that names it is read**
  (`io/BundledPageAssets.kt`), under the sidecar name the stroke carries — so playback needs nothing
  beside the file, and the `.xopp` export still names that sidecar and pushes a copy out for it.
- **A recording is streamed, never held.** The archive addresses an asset by the hash of its bytes, so a
  save needs the recording's hash and its bytes but never both in memory: `format/mobix/MobixPayload.kt`
  is *how to open* the content (a file, an archive entry, or a small array), and the hash, the deflate
  and the copy out of a source archive each read it in 64 KiB chunks. Bundling a 30-minute WAV used to
  cost three copies of it — read to hash, read to deflate, deflated buffer — which is the
  `OutOfMemoryError` a long recording produced on saving. Adoption streams the other way for the same
  reason (`io/BundledAudio.kt` takes an `InputStream`).
- **Transcription streams too** (`audio/AudioPcmExtractor.kt` → `audio/PcmResampler16k.kt`): the WAV or
  the decoded Opus/AAC is resampled to 16 kHz mono as it is read, keeping only the output the engine
  needs, instead of loading the recording and then resampling it into a second array.
- The audio is bound to the document and cannot be detached when sharing or syncing; a `.xopp`
  exported from it is the one shape that still needs a sidecar file on disk.
- **Why a `.mobix` can still be large.** The codec follows the *document's* format at the moment
  recording starts: a recording made while the document is a `.xopp` is WAV (the desktop sidecar
  contract), and a later **Save As `.mobix`** bundles those WAV bytes untouched (~5.3 MB a minute, so
  ~160 MB for half an hour). Because nothing is transcoded, that file is as big as its WAV, not as big
  as an Opus recording would have been.

### Background capture and standalone media export
- **Background capture (`AudioRecordingService`)**: Android foreground service (`FOREGROUND_SERVICE_MICROPHONE`)
  holding a partial wake lock and showing an ongoing notification with elapsed chronometer and Stop action.
  Prevents OS microphone muting or capture termination when switching apps or locking the screen.
- **Standalone export (`AudioStandaloneExporter`)**: Option (`saveStandaloneAudio` in settings and audio popup)
  that automatically exports an independent copy of each completed recording into the device's public
  `Music/MobiXournal` or `Recordings/MobiXournal` collection via MediaStore.
- **What leaves is `.m4a`, never the recorded container.** The app records Ogg/Opus (`.mobix`) or WAV
  (`.xopp`), and the player decodes both, but outside the app Ogg/Opus is the format uploaders most often
  refuse: an assistant that does not recognise it answers that the file "was not processed by the audio
  component" and injects its bytes into the conversation as text, while a lecture-length WAV is hundreds
  of megabytes. `audio/AudioTranscoder.kt` therefore decodes the recording (`MediaExtractor` +
  `MediaCodec`) and re-encodes it mono at 48 kbps into MP4/AAC — the one encoder every Android device is
  required to provide — writing `<same timestamp>.m4a` in the cache, which is the file the media store
  receives. Nothing is ever lost to the conversion: a recording already in M4A is copied as it is, and a
  transcode that fails (unsupported source stream, no encoder, a busy codec) exports the original bytes
  under their own name and media type instead. The export runs on a background thread, since re-encoding
  a lecture takes longer than a tap.

### On-device Speech-to-Text and stroke synchronization
- **Temporal transcript segments (`TranscriptionSegment`)**: Stores recognized speech with start and end timestamps
  in milliseconds (`startTimeMs`, `endTimeMs`, `text`, `confidence`), serialized in `.mobix` via
  `TranscriptionCodec` into `transcript.json`.
- **Where its progress is shown**: only inside the Audio pop-up (draft status line with a spinner) and
  the mini-player's transcript section. The recording capsule in the top bar keeps showing play/pause
  throughout — a background job must not turn a control into a status display that the user then cannot
  press.
- **On-device Whisper engine (`WhisperOfflineTranscriber` & `WhisperModelManager`)**: Offline automatic speech recognition
  powered by `sherpa-onnx` ONNX Runtime int8 models (`whisper-tiny` ~39 MB, `whisper-base` ~75 MB). Runs locally at
  without network requests or microphone timeouts. The first
  transcription downloads `whisper-tiny` automatically (with visible progress), so transcription needs no
  manual model setup; when no model is present the service falls back to the platform recognizer.
- **It decodes windows, not phrases (`TranscriptionWindows`)**: Whisper's encoder always works on a 30-second mel
  window and pads shorter audio up to it, so decoding one 2–6 s VAD phrase costs what decoding 30 s costs. The
  phrases are therefore grouped into consecutive windows of 28 s (`WINDOW_MS`, cut only between phrases at a
  silence) and each window is one decode: on ten minutes of speech the VAD produces ~230 phrases and the grouping
  ~24 windows, i.e. 9–10× fewer padded encoder passes for the same bytes and the same model (pinned by
  `TranscriptionWindowsTest`). The recognised text is distributed back over the phrases the window covers by their
  share of it (`distributeText`), so segments keep the VAD's real timestamps, stay chronological, none is empty and
  every word is kept — Whisper reports no per-phrase boundary here, so a word can land one phrase early or late when
  speech speed changes sharply inside a window. Windows carry their audio lazily (`pcm()`): grouping an hour of
  speech must not hold a second copy of the recording. Inference uses the device's cores, capped at six
  (`WhisperOfflineTranscriber.inferenceThreads`), where it used to be a literal 4.
- **It runs during the recording too (`LiveTranscription`)**: while the microphone is open the recorder's PCM goes into a live job (`AudioSession.onPcmData` → `LiveResampler16k` → `LiveSpanCutter` → one worker thread). The stream is resampled to 16 kHz as it arrives and cut into **spans** at silences (≥6 s, ≤28 s, never inside a word unless the maximum forces it); each span then runs the same pipeline the file path runs (VAD phrases → windows → Whisper) and the transcript file is rewritten after every span. The post-stop job **resumes from the last segment it finds there**, so nothing is transcribed twice and a session that keeps up leaves only its final seconds for Stop. The queue holds four spans and then the live job gives up on its own: inference that falls behind the microphone must not take the machine from the app being written in, and whatever it did not reach is exactly what the post-stop path already does. It runs in the app's process, kept alive and awake by the recording foreground service, so a screen that is off changes nothing. The honest limit: this moves the wait into the recording, it does not make the engine faster — if inference is slower than real time, the tail is simply longer and the file path finishes it.
- **Background execution (`AudioTranscriptionService`)**: Android foreground service (`FOREGROUND_SERVICE_DATA_SYNC`)
  holding a partial wake lock with ongoing notification progress bar. Allows hour-long lectures to finish processing
  even when the device screen is locked or another application is active. Progress reports windows, and the final
  status carries the measured wall time (`Transcription complete (N segments in Xs)`) — for a resumed transcription
  that is the tail's time, which is the whole point of the live job — so a windowing or thread-count change can be
  judged on a device instead of argued about.
- **Temporal ink alignment (`AudioInkAligner`)**: Correlates handwritten stroke telemetry (`startMs` + $\sum \Delta t$)
  with spoken audio segments. Tapping a stroke navigates directly to the exact spoken phrase; seeking in audio
  locates the bounding box of words written at that moment on the page canvas.
- **Unified multi-modal search (`DocumentSearch`)**: Search queries automatically scan across typed text boxes,
  PDF background layers, recognized handwriting, and spoken audio transcripts simultaneously, highlighting
  both the handwritten ink on canvas and the active playback point.

---

## Point-level telemetry mapping

Every `StrokePoint` in a synchronized stroke records a relative time delta $\Delta t$ in milliseconds:
$$t_{\text{abs}}(P_i) = T_{\text{stroke\_start}} + \sum_{k=0}^{i} \Delta t_k$$

```json
{
  "type": "stroke",
  "audioRef": {
    "filename": "2026-08-02T09-14-33.wav",
    "assetId": "assets/aud_7a9f1c0e…wav",
    "startMs": 14250
  },
  "points": [
    { "x": 120.4, "y": 80.2, "dtMs": 0 },
    { "x": 121.0, "y": 81.5, "dtMs": 12 },
    { "x": 122.3, "y": 83.0, "dtMs": 15 }
  ]
}
```

---

## Interactive replay modes

**What runs today — mode 1, playback.** The clock, the loop and the painting are live:
`AudioSession.playheadMs`/`seek`/`nowPlaying` publish where a clip is,
`render/KaraokeFrameDriver.kt` posts a `Choreographer` frame per vsync while one sounds and puts the
state onto the surface (`KaraokeStrokeAnimator.State`, read as `DrawingSurfaceView.karaokeState`), and
the paint loop applies that state's directive per stroke (`render/DrawingSurfaceKaraoke.kt`): a stroke
the recording has not reached yet is drawn **dimmed**, the one being written is drawn as its visible
prefix with a glowing tip at the nib, and every other stroke is drawn exactly as it is with nothing
playing. A page painted at the playhead **cannot come out of the ink cache** — the cached bitmap holds
the recording's strokes as finished, and no blit can un-draw writing that has not happened — so one page
is drawn directly for as long as a clip sounds.

The driver owns its frames rather than asking for a repaint per tick, because the surface's own
`render()` no-ops while a fling is running; it publishes `null` on completion, on a stop and when the
editor goes away, so a page never freezes at its last dimmed frame. A **pause** is the one stop that
clears nothing: the clip is *held* where it was, the state stays published and the loop ends, so the
page keeps showing the writing as it stood at that moment.

**What runs today — mode 2, the interactive half.** The Audio pop-up plays, pauses and stops the clip
that is on, and carries a **scrubber** over the clip's own length. Dragging it moves the playhead
(`AudioSession.seek`, which walks a held clip and seeks a sounding one, reusing the one player so a
scrub never opens a second), and the canvas repaints where it landed — one publish per drag step rather
than a frame per vsync, since nothing is moving between the drag's own events. So the page is filled and
unfilled by the drag. The **ink-to-audio** direction is the Play object tap, and it is **point-precise
where the data is**: the tap seeks to the time of the point nearest it (`KaraokeStrokeAnimator.seekTimeMs`
— `T_start` plus the deltas up to that point), so a tap on the fifth word starts there rather than at
the stroke's head. A stroke whose points carry no deltas — a `.xopp` import, a `.mobix` written before
the telemetry landed — has no timeline to be precise with, and the tap falls back to the stroke's own
offset. A gap *inside* a stroke's deltas is read the same way, point by point: the times before the gap
are trusted and the ones after it are not, because summing a missing delta as zero would start the
recording too early rather than say it does not know.

### 1. Inking Karaoke (Stroke Playback Animation)
During playback of a lecture or meeting:
- As the audio playhead advances to time $t$, strokes written after $t$ are rendered with lowered opacity
  ($\alpha = 0.25$) or hidden.
- As the audio timestamp reaches $t_{\text{abs}}(P_i)$, the stroke path animates smoothly forward in real time.
- The active writing tip is decorated with a subtle pulsing glowing indicator.

### 3. Floating frosted-glass mini-player (`AudioMiniPlayer`)
- **HUD pill (`AudioMiniPlayer`)**: A modern, draggable frosted-glass pill floating over the canvas with real
  amplitude waveform visualization, inking density heatmap, subtitle previews, +/- 5s skips, and speed selector
  (0.8x, 1.0x, 1.25x, 1.5x, 2.0x) with pitch preservation. Its width is fixed (300 dp), so the header is a
  weighted Row: the controls (transcript, speed, close) are measured first and the clip title — which carries
  the document's name — gets the leftover space and ellipsizes there. Before that the title was unweighted and
  an uncapped name pushed the speed selector out of the pill.
- **Waveform extractor (`AudioWaveformExtractor`)**: Decodes normalized amplitude vectors from local audio recordings
  with in-memory caching to avoid redundant file reads.

### 4. Tri-directional touch-to-seek synchronization
- **Direction 1 (Ink → Audio & Transcript)**: Tapping any handwritten stroke with audio references instantly
  seeks the audio playhead to the point-precise timestamp, summons the floating mini-player HUD, drives
  karaoke stroke illumination and glowing nib tips, and highlights the spoken transcript segment in real time.
- **Direction 2 (Transcript → Audio & Canvas Viewport)**: Tapping any recognized phrase in the interactive
  transcript panel seeks the audio playback to `startTimeMs`, updates the waveform scrubber, and auto-scrolls
  the canvas viewport directly to the handwritten strokes written at that exact moment (`scrollToAudioTimestamp`).
- **Direction 3 (Waveform Scrub → Viewport & Transcript)**: Dragging or tapping along the amplitude waveform
  scrubber repositions the audio playhead, repaints karaoke states immediately via single vsync publish,
  centers the canvas on corresponding handwriting, and continuously updates the active spoken subtitle.

---

## Exporting back to `.xopp` and PDF

- **To `.xopp`**: the stroke-level `<stroke fn="..." ts="...">` pair is written exactly as the stroke
  carries it, so `fn` names the file that is really beside the document, and the sidecar is pushed out
  to the nominated audio directory on save. This app has **no transcode** — the Opus decoder is not on
  the export path — so a recording captured for a `.mobix` and exported inside a `.xopp` (a board handed
  to a machine that only knows pages) travels as the Ogg/Opus file it is, and the editor **reports** the
  gap instead of naming a `.wav` that does not exist or writing silence under a `.wav` name: "N
  recording(s) here are compressed — desktop Xournal++ plays WAV only". A `.xopp` document records WAV
  from the start, so a `.xopp` authored here never hits it ([audio.md](audio.md)).
- **To PDF**: The document exporter generates standard PDF annotations with embedded hyperlink actions or
  multimedia stream clips where supported.
