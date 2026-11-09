# Audio: recording, the playhead and the karaoke

> Read when you touch audio recording, playback, bundling or karaoke, or the sidecar files a `.xopp` refers to.
> For the task: [Annotating a PDF, and recording](guide-pdf-and-audio.md), [The words this guide uses](guide-glossary.md).

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

This page owns recording, storage and replay in both shells. Strokes carry Xournal++'s `fn`/`ts` link; `.mobix` also preserves each point's time for precise replay.

## Recording (`audio/`)

The **Audio** rail slot toggles capture. `AudioSession.startRecording(compressed)` names the file with the
desktop's local-time `yyyy-MM-ddTHH-mm-ss` convention plus the extension of the container the recorder it
picked writes, and **the document's own format picks the recorder**: a `.mobix` records compressed audio —
**Ogg/Opus** (`audio/OpusAudioRecorder.kt`: `AudioRecord` at 48 kHz mono, `MediaCodec`'s `audio/opus` at
32 kbps into the platform's Ogg muxer) with an **M4A/AAC** fallback (`audio/AacAudioRecorder.kt`: 44.1 kHz
mono, 32 kbps, `audio/mp4a-latm` into an MP4 muxer), $\approx 15\text{ MB}$ an hour against
$> 300\text{ MB}$ of WAV — while a `.xopp` records the WAV sidecar the desktop plays (`AudioRecorder`:
44.1 kHz mono 16-bit PCM into `WavWriter`, which frames the RIFF header itself because Android has no WAV
encoder and patches its two length fields on close).

**Nothing is transcoded or renamed.** An `.ogg` stream from another writer keeps `.ogg`, and the sniffed
container is what an entry is named after (`MobixAssetSink.suffixFor` — `RIFF`/`WAVE` -> `wav`, `OggS` ->
`ogg`, `ftyp` -> `m4a`). The codec follows the document's format at the moment recording starts, so a
recording made while the document was a `.xopp` is WAV, and a later **Save As `.mobix`** bundles those WAV
bytes untouched (~5.3 MB a minute, so ~160 MB for half an hour): because nothing is transcoded, that file
is as big as its WAV rather than as small as an Opus recording would have been. A `.mobix` on a device
with no compressed encoder falls back to the WAV path rather than failing, and the file's name always says
which container it holds. All three recorders ask for `MediaRecorder.AudioSource.VOICE_RECOGNITION` first
— the source Android tunes for speech (automatic gain, noise suppression) — and fall back to
`MIC`/`DEFAULT` when a device does not offer it. `RECORD_AUDIO` is requested the first time Record is
pressed; everything else in the app works without it.

### Bundled audio (`assets/`)

- A recording the app's stores hold is **bundled** into the archive by content hash
  (`assets/aud_<sha256>.<ext>`, [mobix-schema-spec.md](mobix-schema-spec.md)), so the same recording
  referenced twice is stored once: a save and an open go through
  `MobixAssetSink.add(bytes, AssetKind.AUDIO)` and `io/BundledPageAssets.kt` respectively.
- Page reads register bundled audio without copying it (`io/BundledPageAssets.kt`). Workers resolve independent payloads through `DeferredRecordings` before playback/export, retaining sidecar names, and **the archive they read is the session's**: `io/MobixOpenSessions.kt` keeps the staged copy until the document is released, because sweeping it with the open left every bundled recording unplayable and every repack save without its entries. Atomic publication preserves an old file if extraction fails and allows retry.
- **A recording is streamed, never held.** `format/mobix/MobixPayload.kt` describes how to open it;
  hashing, deflating and copying read 64 KiB chunks. Bundling a 30-minute WAV formerly needed three
  lecture-sized buffers and could exhaust memory. Adoption also streams (`io/BundledAudio.kt`),
  keeping memory independent of the recording's duration.
- The audio is bound to the document and cannot be detached when sharing or syncing; a `.xopp` exported
  from it is the one shape that still needs a sidecar file on disk.

### Android audio work and frame pacing

Composition reads published preview state (`MainActivityAudioPreview.kt`); it never scans pages or extracts waveforms. Metadata discovery runs even with hidden controls; visible waveforms load independently, keyed by tab and clip rather than document edits, and never delay recording detection. A **compressed** recording's envelope is decoded through the platform's own demuxer and decoder (`audio/CompressedWaveform.kt`) — averaging a codec's bytes answers with the shape of a bit rate, a flat band rather than a waveform — one buffer at a time, keeping only the block peaks. A take **past a minute** is *sampled*, not decoded whole: each block reads three short windows seeked to inside it (`audio/WaveformScan.kt`), a fixed cost per bar whatever the recording runs to — decoding a two-hour lecture buffer by buffer was minutes with no bar on screen, which reads as a waveform that does not work.
`AudioReferenceIndex` caches compact page references and incrementally updates edited overlays and undo, and its reads are **page-scoped** (`read(only = …)`), so a sidecar sweep answers from what earlier reads cached and never inflates the notebook. The chrome's own discovery does not go through the index at all: it reads the page in front through the document the canvas paints and asks the audio store for the clip's **name** — a page read registers the recordings it bundles, so that is a map lookup — and only a player that is actually showing may **resolve** one, because resolving adopts a bundled payload into the store and copying a lecture's audio behind a closed player is what made opening a file with audio take seconds. A scrubber drag answers from the clip's **stroke timeline**, built once per document and clip (`audio/AudioSeekIndex.kt`): the walk reads page by page, keeps only that clip's strokes and holds one page at a time, and every later step of the same drag is a filter over a few hundred records rather than another walk. An **unedited document opened as a `.mobix` is snapshotted as its own archive** (`MobixTabSnapshots` copies the staged file) instead of being re-encoded page by page, because re-writing a lecture's container in the background at open is the I/O that made an audio file take seconds to open. Only the Play gesture that asks for *the* recording walks past that page. Identity keys never compare lazy document contents.
Background indexing and seek navigation use independent ZIP readers (`AudioPreviewReader.kt`), avoiding the canvas reader lock and its five-page LRU. Tab-owned lifecycle jobs reject stale results and check cancellation between pages.
Sidecar discovery and SAF copies run off main (`MainActivityAudioSidecars.kt`); bundled recordings are not imported from a sidecar folder. `AudioWaveformExtractor` keeps 16 file versions keyed by path, length, timestamp and sample count; cache locks never cover cancellable chunked reads.
The canvas follows vsync; `AudioUiCadence` limits moving player-chrome updates to 10 Hz, with immediate pause/stop publication. UI playback awaits asynchronous decoder preparation and then uses a prepared-only entry point; pause/resume retains that player.

### A take, and its tab

**A take belongs to the tab it started in** (`com.mobixournal.audio.ownsRecording`). Switching tabs does
not hand the microphone to the new document: the running take keeps its owner, ink drawn elsewhere carries
no `fn`, and the other tab's capsule shows a disabled microphone naming the tab that is recording. The
shade's Stop ends it wherever it was started, and closing its tab finishes it.

### Background capture and a standalone copy

- **Background capture (`AudioRecordingService`)** — an Android foreground service
  (`FOREGROUND_SERVICE_MICROPHONE`) holding a partial wake lock and showing an ongoing notification with
  an elapsed chronometer and two actions, **Pause/Resume** and **Stop**. The shade is the recording's
  remote control for a lecture written elsewhere in the app: both buttons call the same session methods
  the mini capsule calls (`AudioSession.pauseRecording`/`resumeRecording`, and the editor's Stop), and the
  notification is rebuilt from that one state (`AudioRecordingService.notifyPaused`), so it never says
  "recording" for a paused session or the reverse. The capsule's resume button reads as a **record dot**,
  not the play triangle beside it. A paused recording keeps the microphone open — the capture is muted,
  not closed — so resuming costs no new file and no gap. **The service outlives nobody**: it answers
  `START_STICKY` so a recording survives the app leaving the screen, but a sticky restart after the
  process was killed arrives with a null intent and the take died with that process, so the service stops
  itself (`START_NOT_STICKY`) instead of lingering with no notification.
- **Standalone export (`AudioStandaloneExporter`)** — an option (`saveStandaloneAudio` in settings and the
  audio pop-up) that exports an independent copy of each completed recording into the device's public
  `Music/MobiXournal` or `Recordings/MobiXournal` collection via MediaStore. **What leaves is `.m4a`,
  never the recorded container**: outside the app Ogg/Opus is the format uploaders most often refuse, and
  a lecture-length WAV is hundreds of megabytes. `audio/AudioTranscoder.kt` decodes the recording
  (`MediaExtractor` + `MediaCodec`) and re-encodes it mono at 48 kbps into MP4/AAC — the one encoder
  every Android device must provide — writing `<same timestamp>.m4a` in the cache. Nothing is lost to the
  conversion: a recording already in M4A is copied as it is, and a transcode that fails (unsupported
  source stream, no encoder, a busy codec) exports the original bytes under their own name and media type.
- **The `.m4a` is encoded during the lecture (`audio/StandaloneAudioTap.kt`)** — the same PCM feeds a
  second sink, which encodes MP4/AAC (`MediaCodec` + `MediaMuxer`, 48 kbps mono) into
  `audio-standalone/<recording stem>.m4a` on its own thread, so Stop costs a copy instead of a conversion
  and the moment the user closes the app is not the moment a transcode has to run. The sink is bounded:
  an encoder that falls behind drops chunks rather than slowing the microphone, and one that cannot open
  at all leaves the work to `audio/AudioTranscoder.kt`.
- **What the closing process did not finish resumes next launch (`audio/AudioPendingWork.kt`)** — the
  export leaves an entry in `audio-pending.txt` when it starts and removes it when it finishes, and
  `resumePendingAudioWork` copies the pre-encoded `.m4a` out on the next launch. A job whose recording
  and pre-encoded copy are both gone is dropped rather than retried forever; a line an older build wrote
  for a job this one no longer runs is skipped.

## Stamping a stroke

The surface's `audioStamp` hook is read **inside `appendStroke`**, the single funnel every committed
stroke passes through — so freehand and shapes are both stamped, and the audio machinery stays out of the
drawing hot path. The `ts` it stamps comes from the *bytes written so far* (`WavWriter.durationMs`), not
wall time, so a stroke's offset points at the sample it was really drawn over even if the capture thread
stalls.

Every `StrokePoint` in a synchronized stroke also records a relative time delta $\Delta t$ in milliseconds:
$$t_{\text{abs}}(P_i) = T_{\text{stroke\_start}} + \sum_{k=0}^{i} \Delta t_k$$

```json
"audioRef": { "filename": "2026-08-02T09-14-33.wav", "assetId": "assets/aud_7a9f1c0e…wav", "startMs": 14250 },
"points": [ { "x": 120.4, "y": 80.2, "dtMs": 0 }, { "x": 121.0, "y": 81.5, "dtMs": 12 } ]
```

## The playhead

Playback publishes **where it is**: `AudioSession.playheadMs` (null while no clip is on),
`clipDurationMs`, `nowPlaying` and `seek(ms)`, all reading the one `Playhead` beside the player. A clip is
on in one of two ways, and the difference is why the playhead is a class of its own — every rule it
enforces is ours, not the platform's. **Sounding:** the position is the media stack's, a seek is clamped
to the clip's own length so a drag past the end lands on it, and a seek reads as the position it asked for
until the stack gets there, since `MediaPlayer.seekTo` is asynchronous and `currentPosition` reports where
playback *was* for a moment after it. **Held:** nothing is sounding — a pause, or a position scrubbed with
the sound off — so the position is ours and moves only when we seek it, over the same one player (a scrub
never opens a second). Pausing therefore keeps the clip *on* and shows the writing where it stopped, while
**Stop** lets it go and lets the page paint itself again. None of that needs a device to exercise, which
is the point of the seam (`audio/Playhead.kt`, JVM-tested).

*Which side the recording has to suit.* A `.xopp` and a `.mobix` want opposite things from a codec, so the
format decides: **size** for the container, which bundles the audio and can afford Opus, and
**compatibility** for the interchange file, whose `fn` has to name a file desktop Xournal++ plays. Nothing
transcodes, so a compressed recording that does reach a `.xopp` export — a `.mobix` saved as one — is
written out beside the file **as itself** and reported to the user, rather than renamed to a `.wav` holding
Opus bytes.

## Replay: the karaoke

**Mode 1 — playback.** The clock, the loop and the painting are live: `AudioSession.playheadMs`/`seek`/
`nowPlaying` publish where a clip is, `render/KaraokeFrameDriver.kt` posts a `Choreographer` frame per
vsync while one sounds and puts the state onto the surface (`KaraokeStrokeAnimator.State`, read as
`DrawingSurfaceView.karaokeState`), and the paint loop applies that state's directive per stroke
(`render/DrawingSurfaceKaraoke.kt`): a stroke the recording has not reached is drawn **dimmed**, the one
being written is drawn as its visible prefix with a glowing tip at the nib, and every other stroke is
drawn exactly as it is. A page painted at the playhead **cannot come out of the ink cache** — the cached
bitmap holds the recording's strokes as finished, and no blit can un-draw writing that has not happened —
so one page is drawn directly for as long as a clip sounds — and only a page that carries that recording: a page with none of it resolves every stroke to `Normal`, so it keeps the cache blit instead of being redrawn stroke by stroke every frame. The driver owns its frames rather than asking
for a repaint per tick, because the surface's own `render()` no-ops while a fling is running; it publishes
`null` on completion, on a stop and when the editor goes away, so a page never freezes at its last dimmed
frame. A **pause** is the one stop that clears nothing: the clip is *held*, the state stays published and
the loop ends.

**Mode 2 — the interactive half.** The Audio pop-up plays, pauses and stops the clip that is on, and
carries a **scrubber** over the clip's own length. Dragging it moves the playhead (`AudioSession.seek`,
which walks a held clip and seeks a sounding one, reusing the one player), and the canvas repaints where
it landed — one publish per drag step rather than a frame per vsync, since nothing moves between the
drag's events. The **ink-to-audio** direction is the Play object tap, and the clip it starts **always begins at 0:00**: a
stroke's offset records when it was drawn — telemetry about the ink — and is not a place the user asked
playback to begin, so neither a tap nor the Play control seeks into the recording. `KaraokeStrokeAnimator.seekTimeMs`
still lands a point-precise start for a caller that wants one, and is library code in both shells. **Which** stroke that is comes from `AudioTap.pickStroke`, and the stroke's **ink** decides
it: a box rule is the right tolerance for grabbing an element and the wrong one for asking where in a
recording a tap landed, because a diagonal stroke's rectangle covers everything around its ink — so the
tap takes the topmost stroke whose **path** passes within reach of the point, falls back to the recorded
stroke whose ink is nearest when the ink under the pointer carries no recording, and never lets a stroke
drawn over a recording make that recording unreachable. Reading a timeline is the same question on the desktop: a stroke with no
deltas — a `.xopp` import, a `.mobix` written before the telemetry landed — has no times to be precise
with, and a gap *inside* one has its earlier points trusted and its later ones not, because summing a
missing delta as zero would start a clip too early rather than say it does not know.

**The window runs both modes from the same objects.** `DesktopKaraoke` publishes the player's own position
— once per vsync while sound is coming out, once when it is held — and `PageCanvas` paints the page
**directly** for as long as a clip is on, because a `PagePicture` holds the recording as finished;
`PageView`'s element loop applies `KaraokeStrokeAnimator.resolve` per stroke, so the dimming, the cut
prefix and the glowing nib are the tablet's own rules. When the clip is let go, the recorded picture is
replayed again. The transport is the header's audio capsule: its scrubber walks a held or sounding clip
through the same `seek`, and its speed button resamples in the player's feeder while the clock keeps
counting the *recording's* frames, so the karaoke stays in the clip's own time at every speed.

**Finding the writing, and the pill that sits over it.** `AudioInkAligner` answers one question: which
strokes were being written at a given moment of a named recording (`findStrokesAtTime`, a `startMs` plus
the stroke's own $\Delta t$ sum, with a three-second tolerance), and the mini-player uses it to scroll the
canvas to the writing the playhead has reached. There is no speech recognition and no transcript: the
words spoken over a lecture are not turned into text, so search covers typed text, PDF text and handwriting
only. The **HUD pill (`AudioMiniPlayer`)** is a draggable frosted pill over the canvas with the waveform
(`AudioWaveformExtractor` decodes normalized amplitudes with in-memory caching), the inking density map,
±5 s skips and a speed selector (0.8× · 1.0× · 1.25× · 1.5× · 2.0×, pitch preserved). Its width is fixed
(300 dp), so the header is a weighted `Row`: the controls are measured first and the clip title — which
carries the document's name — gets the leftover space and ellipsizes there, where before an uncapped name
pushed the speed selector out of the pill.

## The window's audio (`desktop/`)

**The window's audio (`desktop/`).** The three facts both shells must agree on live in `mobix-core/audio/`:
the `fn`/`ts` link (`AudioFiles.kt` — `withAudio`, `documentAudioFiles`, `audioFileName`), the RIFF writer
a `.xopp` sidecar needs (`WavWriter`), and the playhead's rules (`Playhead`). The window is a client of
them, so a stroke stamped on the PC replays on the tablet and a position means the same thing on both
screens.

*Where a recording lives.* `DesktopAudioStore` uses a private folder per document under
`%APPDATA%\MobiXournal\audio`, keyed by its origin path. Stroke references keep their original bare
names; playback, recording, saving and recovery use that document's store. Rehoming keeps a running
take's store; per-document bindings resolve immutable hashed copies, so reopening the old path cannot
overwrite a moved tab's recording. Sidecar comparisons check content rather than lengths. **Every open adopts what the document carries**: a `.mobix` bundles its recordings as
`assets/aud_*` entries and they are copied out under the name the strokes call them by, a `.xopp`'s
sidecars are pulled out of the document's folder, and a save writes them back — bundled into the archive
(`DocumentEncoder`'s `audioFiles` seam) or pushed beside the `.xopp`. That adoption is what makes editing
on the PC safe: a `.mobix` rewrite names `assets/` entries, so without a local copy the entry is dropped
and the document loses its audio. A recording a document names and this machine does not have is reported
once, in one sentence, naming the files.

*Recording.* The header's pill records through `javax.sound.sampled` into the WAV the `.xopp` convention
wants (44.1 kHz, mono, 16-bit signed little-endian), pausing by **muting** the open line so `elapsedMs` —
the offset a stroke is stamped with — stays the writer's own timeline. A line that cannot open answers one
sentence and leaves no file behind.

*Playing back.* `DesktopAudioSources` reads a `.wav` as seekable PCM and an `.ogg` as Ogg pages
(`OggOpus`, the container layer, pure Kotlin and tested without a device) around Opus packets decoded by
**Concentus**, a pure-Java decoder, so the packaged window ships no native codec. An `.m4a`/AAC recording
is refused in words — a plain JVM has no AAC decoder, and renaming it would be a lie. The player streams a
few kilobytes at a time (a lecture is never a `ShortArray`), writes them to a `SourceDataLine`, and
reports its position through the shared `Playhead`, so a seek reads as where it was asked for until the
sound arrives.

*What a seek costs.* A tap or a scrub is a **seek**, never a second open: the window keeps the decoder and
the output line for the clip it already holds, and tapping that recording again repositions them, where
reopening cost a device handshake and a full re-read of the file before the first sample sounded — a
click that looks like a click that missed. On an `.ogg` the seek is a **jump**: `OpusSource` builds the
packet timeline at open, so the packet holding the millisecond asked for is a binary search away and a tap
near the end of a minute-long take decodes two packets (~0.2 ms) instead of every packet from the head
(measured 78–121 ms on that clip, and linear in its length) — which a 100 ms device buffer hears as a gap
and a stutter. On a `.wav` it is arithmetic.

*What the clock counts.* The position is the frames written **minus the line's own backlog**
(`PcmLine.queuedFrames`), so the readout and the karaoke follow the sound being heard rather than the
sound already handed over; and the feeder finishes a chunk even when `SourceDataLine.write` accepts only
part of it, because a dropped tail is a hole in the sound.

*The pill.* The audio controls are the tablet's capsule, in the header immediately left of Save:
record/stop, pause, play, the take's `mm:ss` and a level bar, in the same three-button order and the same
state colours `AudioMiniCapsule` uses — and with its **meanings**, which is what the window's first cut
got wrong: pause holds the **take** and does nothing when no take is running, because a clip's pause is
the play button's (two pause marks side by side were the same act twice), and Play also **picks the audio
tap**, since the tool strip carries no slot for it. The status bar carries the take indicator (a record
dot, the clock and the level) and the one sentence a failure has to say.

*One document's audio, not the window's.* A machine has one microphone and one output, but a take belongs
to the **tab it was started in** — `ownsRecording` in `mobix-core/audio/` is that rule, asked by both
shells at the one place a stroke is committed. Its `fn`/`ts` land only on its own tab's ink; it is paused
and stopped only from that tab, whose capsule shows the red stop while every other tab shows a disabled
microphone naming the recording tab (`DesktopTabAudio.takeElsewhere`, `AudioUiState.recordingElsewhere`);
and closing that tab finishes the take. A **clip** belongs to its tab too: switching documents lets the
sound go, so the pill, the transport and the karaoke always describe the document on screen
(`DesktopAudioSession.of` answers per tab).

*The devices.* **Settings → Audio** picks the microphone and the output by the platform's own names, with
**System default** first; the choice is stored as `audio.inputDevice` / `audio.outputDevice` and reaches
the engines through one route object, so a take or a clip opens the picked device without either engine
reading the settings ([desktop-settings.md](desktop-settings.md) owns the page). A device that has gone is
dropped to the default with the row saying so, and the open call falls back as well — a headset unplugged
mid-session costs a default take, not a failure.

*The transport.* A clip on screen turns the same capsule into the transport, because the window has no
second surface for it: the readout becomes `position / length`, a **scrubber** appears between the buttons
and the time (a hairline whose drag calls `seek`, landing on the clip's ends rather than past them —
`scrubMs`), a **speed** button walks `1× · 1.25× · 1.5× · 2×`, and a **close** (the × after the speed)
lets the clip go — nothing else in the window can, so without it a recording stayed on the header for
good. A pause **holds** the streamed feeder rather than reading on: the loop waits at the held clip and a
stopped line's empty write is never taken for the end of the take, which used to jump the playhead to its
last word and close the transport on the pause. The speed is the feeder's own resample (`resample`,
linear, applied per chunk), while the clock keeps counting the *recording's* frames — a clock counting
written samples would run at half speed at 2× and put the karaoke a word behind. The transport's controls
are the pill's, not a floating panel: the owner asked for the tablet's capsule in the window's header, and
a second transport over the page would be a second place where "where am I" can be wrong. **Play starts
the take, not a stroke**: the button plays the recording from its head, because a stroke's `ts` links that
stroke to the sound rather than marking where the take begins — starting at the last stroke's stamp, which
is what the page's last element answers, dropped the first seconds of any take written over more than one
stroke. A clip already on screen resumes where it stands instead, and one that has ended plays again from
its head.

*Sidecars.* A `.xopp` never carries its audio, and SAF grants access to the single document the user picked
— not to its folder — so we can't write a sibling from a `CreateDocument` URI alone. Instead recordings
are captured into an app-private directory (always available, no permission needed), and the user
nominates an **audio folder** once from the Audio pop-up; that persisted `OpenDocumentTree` grant is the
sidecars' home on disk. Opening a document pulls the files it references in; saving (and stopping a
recording) pushes them back out. Without a nominated folder audio still records and plays for the session
— only the hand-off to and from the desktop is missing, and the app says so. `fn` is reduced to a bare file
name before use, so a hand-edited path in a document can't escape that folder.

## Leaving the app

- **To `.xopp`**: the stroke-level `<stroke fn="..." ts="...">` pair is written exactly as the stroke
  carries it, so `fn` names the file really beside the document, and the sidecar is pushed out to the
  nominated audio directory on save. There is **no transcode**, so a recording captured for a `.mobix` and
  exported inside a `.xopp` travels as the Ogg/Opus file it is, and the editor **reports** the gap instead
  of naming a `.wav` that does not exist: "N recording(s) here are compressed — desktop Xournal++ plays WAV
  only". A `.xopp` document records WAV from the start, so a `.xopp` authored here never hits it.
- **To PDF**: audio does not travel. Neither shell writes PDF annotation dictionaries — the page is its ink
  and nothing else ([pdf.md](pdf.md)) — so a recording lives in the `.mobix` or `.xopp` beside the
  document, never in the exported PDF.
