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
- The audio is bound to the document and cannot be detached when sharing or syncing; a `.xopp`
  exported from it is the one shape that still needs a sidecar file on disk.
- **Why a `.mobix` can still be large.** The codec follows the *document's* format at the moment
  recording starts: a recording made while the document is a `.xopp` is WAV (the desktop sidecar
  contract), and a later **Save As `.mobix`** bundles those WAV bytes untouched (~5.3 MB a minute, so
  ~160 MB for half an hour). Because nothing is transcoded, that file is as big as its WAV, not as big
  as an Opus recording would have been.

### One take, one tab
- **A take belongs to the tab it started in** (`com.mobixournal.audio.ownsRecording`). Switching tabs does
  not hand the microphone to the new document: the running take keeps its owner, ink drawn elsewhere carries
  no `fn`, and the other tab's capsule shows a disabled microphone naming the tab that is recording. The
  shade's Stop ends it wherever it was started, and closing its tab finishes it.

### Background capture and standalone media export
- **Background capture (`AudioRecordingService`)**: Android foreground service (`FOREGROUND_SERVICE_MICROPHONE`)
  holding a partial wake lock and showing an ongoing notification with an elapsed chronometer and two actions —
  **Pause/Resume** and **Stop**. The shade is the recording's remote control for the whole of a lecture written
  elsewhere in the app: both buttons call the same session methods the mini capsule calls (`onPauseRequested` →
  `AudioSession.pauseRecording`/`resumeRecording`, `onStopRequested` → the editor's Stop), and the notification is
  rebuilt from that one state (`AudioRecordingService.notifyPaused`), so it never says "recording" for a paused
  session or the reverse. The mini capsule's own resume button reads as a **record dot**, not the play triangle of
  the playback button beside it: resuming a recording and starting playback are two different acts. A paused recording keeps the microphone open — the capture is muted, not closed — so
  resuming costs no new file and no gap.
  Prevents OS microphone muting or capture termination when switching apps or locking the screen. All three
  recorders ask for `MediaRecorder.AudioSource.VOICE_RECOGNITION` first — the source Android tunes for
  speech (automatic gain, noise suppression) — and fall back to `MIC`/`DEFAULT` when a device does not offer it.
- **Standalone export (`AudioStandaloneExporter`)**: Option (`saveStandaloneAudio` in settings and audio popup)
  that automatically exports an independent copy of each completed recording into the device's public
  `Music/MobiXournal` or `Recordings/MobiXournal` collection via MediaStore.
- **The `.m4a` is encoded during the lecture (`audio/StandaloneAudioTap.kt`).** The same PCM the
  recorder captures also feeds a second sink, which encodes MP4/AAC (`MediaCodec` + `MediaMuxer`, 48 kbps
  mono) into `audio-standalone/<recording stem>.m4a` on its own thread. A lecture therefore ends with the
  file the export needs already on disk, and Stop costs a copy instead of a conversion — the moment the
  user closes the app is the one moment a transcode cannot run. The sink is bounded: an encoder that
  falls behind drops chunks rather than slowing the microphone, and one that cannot open at all leaves
  the work to `audio/AudioTranscoder.kt`, which decodes and re-encodes the finished recording.
- **What leaves is `.m4a`, never the recorded container.** The app records Ogg/Opus (`.mobix`) or WAV
  (`.xopp`), and the player decodes both, but outside the app Ogg/Opus is the format uploaders most often
  refuse: an assistant that does not recognise it answers that the file "was not processed by the audio
  component" and injects its bytes into the conversation as text, while a lecture-length WAV is hundreds
  of megabytes. `audio/AudioTranscoder.kt` therefore decodes the recording (`MediaExtractor` +
  `MediaCodec`) and re-encodes it mono at 48 kbps into MP4/AAC — the one encoder every Android device is
  required to provide — writing `<same timestamp>.m4a` in the cache, which is the file the media store
  receives. Nothing is ever lost to the conversion: a recording already in M4A is copied as it is, and a
  transcode that fails (unsupported source stream, no encoder, a busy codec) exports the original bytes
  under their own name and media type instead. It runs on a background thread, and only for a recording
  the tap did not already encode.
- **What the closing process did not finish resumes next launch (`audio/AudioPendingWork.kt`).** The
  export leaves an entry in `audio-pending.txt` when it starts and removes it when it finishes. A lecture
  that ends with the app being closed therefore has its work written down before it begins:
  `resumePendingAudioWork` copies the pre-encoded `.m4a` out on the next launch. A job whose recording
  and pre-encoded copy are both gone is dropped instead of being retried forever; a line an older build
  wrote for a job this one no longer runs is skipped, not retried.

### Stroke-time lookup
- **`AudioInkAligner`** answers one question: which strokes were being written at a given moment of a
  named recording (`findStrokesAtTime`, a `startMs` plus the stroke's own \(\Delta t\) sum, with a
  three-second tolerance). The mini-player uses it to scroll the canvas to the writing the playhead
  has reached. There is no speech recognition and no transcript: the words spoken over a lecture are
  not turned into text by this app, so search covers typed text, PDF text and handwriting only.

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
the stroke's head. **Which** stroke that is comes from `AudioTap.pickStroke`, and the stroke's **ink**
decides it: a box rule is the right tolerance for grabbing an element and the wrong one for asking where
in a recording a tap landed, because a diagonal stroke's rectangle covers everything around its ink and
handwriting overlaps, so the last-drawn box used to answer with a stroke drawn somewhere else — another
recording's offset, or none at all. The tap takes the topmost stroke whose **path** passes within reach
of the point, falls back to the recorded stroke whose ink is nearest when the ink under the pointer
carries no recording, and so never lets a stroke drawn over a recording make that recording unreachable.
A stroke whose points carry no deltas — a `.xopp` import, a `.mobix` written before
the telemetry landed — has no timeline to be precise with, and the tap falls back to the stroke's own
offset. A gap *inside* a stroke's deltas is read the same way, point by point: the times before the gap
are trusted and the ones after it are not, because summing a missing delta as zero would start the
recording too early rather than say it does not know.

**The window runs both modes from the same objects.** `DesktopKaraoke` publishes the player's own
position — once per vsync while sound is coming out, once when it is held — and `PageCanvas` paints the
page **directly** for as long as a clip is on, because a `PagePicture` holds the recording as finished;
`PageView`'s element loop applies `KaraokeStrokeAnimator.resolve` per stroke, so the dimming, the cut
prefix and the glowing nib are the tablet's own rules. When the clip is let go of, the recorded picture
is replayed again. The transport is the header's audio capsule: its scrubber walks a held or sounding
clip through the same `seek` (one publish per step, no frame loop), and its speed button resamples in
the player's feeder while the clock keeps counting the *recording's* frames, so the karaoke stays in the
clip's own time at every speed.

### 1. Inking Karaoke (Stroke Playback Animation)
During playback of a lecture or meeting:
- As the audio playhead advances to time $t$, strokes written after $t$ are rendered with lowered opacity
  ($\alpha = 0.25$) or hidden.
- As the audio timestamp reaches $t_{\text{abs}}(P_i)$, the stroke path animates smoothly forward in real time.
- The active writing tip is decorated with a subtle pulsing glowing indicator.

### 2. Floating frosted-glass mini-player (`AudioMiniPlayer`)
- **HUD pill (`AudioMiniPlayer`)**: A modern, draggable frosted-glass pill floating over the canvas with real
  amplitude waveform visualization, inking density heatmap, +/- 5s skips, and speed selector
  (0.8x, 1.0x, 1.25x, 1.5x, 2.0x) with pitch preservation. Its width is fixed (300 dp), so the header is a
  weighted Row: the controls (speed, close) are measured first and the clip title — which carries
  the document's name — gets the leftover space and ellipsizes there. Before that the title was unweighted and
  an uncapped name pushed the speed selector out of the pill.
- **Waveform extractor (`AudioWaveformExtractor`)**: Decodes normalized amplitude vectors from local audio recordings
  with in-memory caching to avoid redundant file reads.

### 3. Touch-to-seek synchronization
- **Ink → Audio**: Tapping any handwritten stroke with audio references instantly seeks the audio playhead
  to the point-precise timestamp, summons the floating mini-player HUD, and drives karaoke stroke
  illumination and glowing nib tips.
- **Waveform Scrub → Viewport**: Dragging or tapping along the amplitude waveform scrubber repositions the
  audio playhead, repaints karaoke states immediately via single vsync publish, and centers the canvas on
  the handwriting written at that moment (`scrollToAudioTimestamp`).

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
- **To PDF**: audio does not travel. Neither shell writes PDF annotation dictionaries — the page is its
  ink and nothing else (`docs/pdf.md`) — so a recording lives in the `.mobix` or `.xopp` beside the
  document, never in the exported PDF.
