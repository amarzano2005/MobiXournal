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
  records **Ogg/Opus** (`audio/OpusAudioRecorder.kt`: 48 kHz mono, 32 kbps, `MediaCodec`'s `audio/opus`
  into the platform's Ogg muxer), so its recordings are `aud_<sha256>.ogg` — $\approx 15\text{ MB}$ an
  hour against $> 300\text{ MB}$ of WAV. A `.xopp` document records 16-bit mono RIFF/WAVE, because its
  audio leaves as a sidecar desktop Xournal++ plays, and a `.mobix` on a device without the Ogg muxer or
  the Opus encoder falls back to that same WAV path. Nothing is ever transcoded or renamed: an `.ogg`
  stream from another writer keeps `.ogg`, and the sniffed container is what an entry is named after
  (`MobixAssetSink.suffixFor` — `RIFF`/`WAVE` -> `wav`, `OggS` -> `ogg`).
- On open, the recording is adopted into the app's audio store when **the page that names it is read**
  (`io/BundledPageAssets.kt`), under the sidecar name the stroke carries — so playback needs nothing
  beside the file, and the `.xopp` export still names that sidecar and pushes a copy out for it.
- The audio is bound to the document and cannot be detached when sharing or syncing; a `.xopp`
  exported from it is the one shape that still needs a sidecar file on disk.

### Background capture and standalone media export
- **Background capture (`AudioRecordingService`)**: Android foreground service (`FOREGROUND_SERVICE_MICROPHONE`)
  holding a partial wake lock and showing an ongoing notification with elapsed chronometer and Stop action.
  Prevents OS microphone muting or capture termination when switching apps or locking the screen.
- **Standalone export (`AudioStandaloneExporter`)**: Option (`saveStandaloneAudio` in settings and audio popup)
  that automatically exports an independent copy of each completed recording into the device's public
  `Music/MobiXournal` or `Recordings/MobiXournal` collection via MediaStore.

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

### 2. Bi-directional Audio-Tap (Interactive Scrubbing)
- **Ink-to-Audio**: Tapping any word or mathematical formula with the Audio tool calculates the nearest
  `StrokePoint`, queries its exact $t_{\text{abs}}$, and commands the audio player to seek immediately
  to that millisecond.
- **Audio-to-Ink**: Dragging the audio scrubber bar highlights the corresponding paragraph or drawing on the
  canvas, keeping visual and auditory attention aligned.

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
