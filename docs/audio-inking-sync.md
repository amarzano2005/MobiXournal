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
- **The container is the one the bytes are in**, and the extension says which — the app's own recorder
  writes 16-bit mono RIFF/WAVE, so its recordings are `aud_<sha256>.wav`; an `.ogg`/Opus stream from
  another writer keeps `.ogg` rather than being renamed. Opus at $32\text{--}64\text{ kbps}$ mono is the
  *intended* recorder codec ($\approx 15\text{ MB}$ an hour against $> 300\text{ MB}$ of WAV) and
  `audio/OpusAudioRecorder.kt` exists for it, but nothing calls it yet: there is no transcode on the
  save path, and pretending one had happened would put Opus bytes under a `.wav` name.
- On open, the recording is adopted into the app's audio store when **the page that names it is read**
  (`io/BundledPageAssets.kt`), under the sidecar name the stroke carries — so playback needs nothing
  beside the file, and the `.xopp` export still names that sidecar and pushes a copy out for it.
- The audio is bound to the document and cannot be detached when sharing or syncing; a `.xopp`
  exported from it is the one shape that still needs a sidecar file on disk.

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

**What runs today.** The clock and the loop are live: `AudioSession.playheadMs`/
`seek`/`nowPlaying` publish where a clip is, and `render/KaraokeFrameDriver.kt` posts a
`Choreographer` frame per vsync while one sounds, putting the state the canvas paints from onto the
surface (`KaraokeStrokeAnimator.State`, read as `DrawingSurfaceView.karaokeState`). It owns its frames
rather than asking for a repaint per tick, because the surface's own `render()` no-ops while a fling is
running; it publishes `null` on completion, on a stop and when the editor goes away, so a page never
freezes at its last dimmed frame. The *drawing* of `resolve`'s directive is the paint loop's own half
(`draw-the-karaoke-directive-on-the-canvas`), so today the canvas repaints at the playhead and paints
the page as it always did.

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

- **To `.xopp`**: The export compiler synthesizes a companion `.wav` file into the nominated audio directory
  and sets the stroke-level `<stroke ts="...">` tag to the start timestamp, ensuring compatibility with
  desktop Xournal++'s legacy playback.
- **To PDF**: The document exporter generates standard PDF annotations with embedded hyperlink actions or
  multimedia stream clips where supported.
