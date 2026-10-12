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
- Recordings are encoded using **Opus** at $32\text{--}64\text{ kbps}$ mono speech profiles. Opus is
  the *only* bundled codec, and the entry name carries it
  (`assets/aud_<sha256>.opus`, [mobix-schema-spec.md](mobix-schema-spec.md)): one codec means one
  decoder on Android and on desktop, so a document recorded on the tablet plays on the laptop without
  a codec negotiation nobody can debug. (`AAC`/`M4A` were considered and dropped for that reason.)
- An hour of audio takes $\approx 15\text{ MB}$ (compared to $> 300\text{ MB}$ uncompressed WAV).
- Stored directly inside `document.mobix/assets/`, named by content hash so the same recording
  referenced twice is stored once. The audio is inextricably bound to the document and cannot be
  detached when sharing or syncing.

---

## Point-level telemetry mapping

Every `StrokePoint` in a synchronized stroke records a relative time delta $\Delta t$ in milliseconds:
$$t_{\text{abs}}(P_i) = T_{\text{stroke\_start}} + \sum_{k=0}^{i} \Delta t_k$$

```json
{
  "type": "stroke",
  "audioRef": { "assetId": "aud_7a9f1c.opus", "startMs": 14250 },
  "points": [
    { "x": 120.4, "y": 80.2, "dtMs": 0 },
    { "x": 121.0, "y": 81.5, "dtMs": 12 },
    { "x": 122.3, "y": 83.0, "dtMs": 15 }
  ]
}
```

---

## Interactive replay modes

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
