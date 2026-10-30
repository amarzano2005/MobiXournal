# Audio annotation: record, replay, sidecars

> Read when you touch audio recording, playback, or the sidecar files a `.xopp` refers to.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Audio-annotated strokes (`audio/`)

**Audio-annotated strokes (`audio/`).** Xournal++ can record while you write and then replay from
any stroke: the stroke carries `fn` (a `.wav` file name) and `ts` (how far into that recording it was
started). We implement both ends of that.

*Recording.* The **Audio** rail slot toggles capture. `AudioSession.startRecording(compressed)` names
the file with the desktop's local-time `yyyy-MM-ddTHH-mm-ss` convention plus the extension of the
container the recorder it picked writes, and **the document's own format picks the recorder**: a
`.mobix` records Opus/Ogg (`OpusAudioRecorder`: `AudioRecord` at 48 kHz mono, `MediaCodec`
`audio/opus` at 32 kbps, an Ogg muxer — ~15 MB an hour), while a `.xopp` records the WAV sidecar the
desktop plays (`AudioRecorder`: 44.1 kHz mono 16-bit PCM into `WavWriter`, which frames the RIFF
header itself because Android has no WAV encoder and patches its two length fields on close). A
`.mobix` on a device with no Ogg muxer or no Opus encoder falls back to WAV rather than failing, and
the file's name always says which container it holds. `RECORD_AUDIO` is requested the first time
Record is pressed; everything else in the app works without it.

The surface's `audioStamp` hook is read **inside `appendStroke`**, which is the single funnel every
committed stroke passes through — so freehand and shapes are both stamped, and the audio
machinery stays out of the drawing hot path. The `ts` it stamps comes from the *bytes written so far*
(`WavWriter.durationMs`), not wall time, so a stroke's offset points at the sample it was really
drawn over even if the capture thread stalls.

*Replay.* The **Play object** tool sets `audioPlayMode`, which short-circuits `beginPointer` before
the gesture classifier — it is a pure query that never edits the document, so it earns no
`GestureIntent`. A tap picks the topmost stroke (`SelectionTester.pickTopmost`), reads its `AudioRef`,
and `AudioPlayer` seeks a `MediaPlayer` to that offset. A tap that misses, or lands on a stroke with
no recording, says so rather than failing silently. **With `fingerDraws` off the query belongs to the
pen alone** (`InputClassifier.playModePans`): a finger keeps navigating — one finger pans, two pinch —
and only a stylus or eraser tip picks a stroke, so the page stays scrollable under a resting hand while
the pen decides what replays. With finger drawing on, a finger tap plays, as it always has.

*Which side the recording has to suit.* A `.xopp` and a `.mobix` want opposite things from a codec, so
the format decides: **size** for the container, which bundles the audio and can afford Opus, and
**compatibility** for the interchange file, whose `fn` has to name a file desktop Xournal++ plays.
Nothing transcodes, so a compressed recording that does reach a `.xopp` export — a `.mobix` saved as
one — is written out beside the file **as itself** and reported to the user, rather than renamed to a
`.wav` holding Opus bytes (`docs/audio-inking-sync.md`).

*The playhead, and holding it.* Playback publishes **where it is**: `AudioSession.playheadMs` (null
while no clip is on), `clipDurationMs`, `nowPlaying` and `seek(ms)`, all reading the one `Playhead`
beside the player. A clip is on in one of two ways, and the difference is the reason the playhead is a
class of its own — every rule it enforces is ours, not the platform's. **Sounding:** the position is
the media stack's, a seek is clamped to the clip's own length so a drag past the end lands on it, and a
seek reads as the position it asked for until the stack gets there, since `MediaPlayer.seekTo` is
asynchronous and `currentPosition` reports where playback *was* for a moment after it. **Held:**
nothing is sounding — a pause, or a position scrubbed with the sound off — so the position is ours and
moves only when we seek it, over the same one player (a scrub never opens a second). Pausing therefore
keeps the clip *on* and shows the writing where it stopped, while **Stop** lets it go and lets the page
paint itself again. None of that needs a device to exercise, which is the point of the seam
(`audio/Playhead.kt`, JVM-tested); what draws from it — the karaoke replay, the scrubber — is
`docs/audio-inking-sync.md`'s subject.

## The window's audio (`desktop/`)

**The window's audio (`desktop/`).** The three facts both shells must agree on live in
`mobix-core/audio/`: the `fn`/`ts` link (`AudioFiles.kt` — `withAudio`, `documentAudioFiles`,
`audioFileName`), the RIFF writer a `.xopp` sidecar needs (`WavWriter`), and the playhead's rules
(`Playhead`). The window is a client of them, so a stroke stamped on the PC replays on the tablet and a
position means the same thing on both screens.

*Where a recording lives.* `%APPDATA%\MobiXournal\audio`, one bare file name each
(`DesktopAudioStore`), because a stroke names its recording by name alone and a document that moves
must not take the audio out of reach. **Every open adopts what the document carries**: a `.mobix`
bundles its recordings as `assets/aud_*` entries and they are copied out under the name the strokes call
them by, a `.xopp`'s sidecars are pulled out of the document's folder, and a save writes them back —
bundled into the archive (`DocumentEncoder`'s `audioFiles` seam) or pushed beside the `.xopp`. That adoption is what makes editing on the PC safe: a
`.mobix` rewrite names `assets/` entries, so without a local copy the entry is dropped and the
document loses its audio. A recording a document names and this machine does not have is reported once,
in one sentence, naming the files.

*Recording.* The header's pill records through `javax.sound.sampled` into the WAV the `.xopp`
convention wants (44.1 kHz, mono, 16-bit signed little-endian), pausing by **muting** the open line so
`elapsedMs` — the offset a stroke is stamped with — stays the writer's own timeline. A line that cannot
open answers one sentence and leaves no file behind.

*Playing back.* `DesktopAudioSources` reads a `.wav` as seekable PCM and an `.ogg` as Ogg pages
(`OggOpus`, the container layer, pure Kotlin and tested without a device) around Opus packets decoded by
**Concentus**, a pure-Java decoder, so the packaged window ships no native codec. An `.m4a`/AAC
recording is refused in words — a plain JVM has no AAC decoder, and renaming it would be a lie. The
player streams a few kilobytes at a time (a lecture is never a `ShortArray`), writes them to a
`SourceDataLine`, and reports its position through the shared `Playhead`, so a seek reads as where it
was asked for until the sound arrives.

*The pill.* The audio controls are the tablet's capsule, in the header immediately left of Save:
record/stop, pause, play, the take's `mm:ss` and a level bar, in the same three-button order and the
same state colours `AudioMiniCapsule` uses — and with its **meanings**, which is what the window's first
cut got wrong: pause holds the **take** and does nothing when no take is running, because a clip's pause
is the play button's (two pause marks side by side were the same act twice), and Play also **picks the
audio tap**, since the tool strip carries no slot for it. The status bar carries the take indicator (a
record dot, the clock and the level) and the one sentence a failure has to say.

*One document's audio, not the window's.* A machine has one microphone and one output, but a take belongs
to the **tab it was started in** — `ownsRecording` in `mobix-core/audio/` is that rule, asked by both shells
at the one place a stroke is committed. Its `fn`/`ts` land only on its own tab's ink; it is paused and
stopped only from that tab, whose capsule shows the red stop while every other tab shows a disabled
microphone naming the recording tab (`DesktopTabAudio.takeElsewhere`, `AudioUiState.recordingElsewhere`);
and closing that tab finishes the take. A **clip** belongs to its tab too: switching documents lets the
sound go, so the pill, the transport and the karaoke always describe the document on screen
(`DesktopAudioSession.of` answers per tab).

*The devices.* **Settings → Audio** picks the microphone and the output by the platform's own names, with
**System default** first; the choice is stored as `audio.inputDevice` / `audio.outputDevice` and reaches the
engines through one route object, so a take or a clip opens the picked device without either engine reading
the settings (`docs/desktop-settings.md` owns the page). A device that has gone is dropped to the default
with the row saying so, and the open call falls back as well — a headset unplugged mid-session costs a
default take, not a failure.

*The transport.* A clip on screen turns the same capsule into the transport, because the window has no
second surface for it: the readout becomes `position / length`, a **scrubber** appears between the
buttons and the time (a hairline whose drag calls `seek`, landing on the clip's ends rather than past
them — `scrubMs`), and a **speed** button walks `1× · 1.25× · 1.5× · 2×`. The speed is the feeder's
own resample (`resample`, linear, applied per chunk), while the clock keeps counting the
*recording's* frames — a clock counting written samples would run at half speed at 2× and put the
karaoke a word behind. The transport's controls are the pill's, not a floating panel: the owner asked
for the tablet's capsule in the window's header, and a second transport over the page would be a second
place where "where am I" can be wrong.

*Karaoke.* While a clip sounds, the page is painted **directly** from its elements instead of from
`PagePicture`, one directive per stroke (`KaraokeStrokeAnimator`, shared with the tablet): strokes the
recording has not reached are dimmed, the stroke being written is cut at the playhead and marked with
a glowing nib, and a finished page comes back from its recorded picture the moment the clip is let go.
`DesktopKaraoke` publishes the position **once per frame** while sound is coming out and once when it
is held, so the writing keeps up with the voice without asking for frames while nothing is playing.

*Sidecars.* A `.xopp` never carries its audio, and SAF grants access to the single document the user
picked — not to its folder — so we can't write a sibling from a `CreateDocument` URI alone. Instead
recordings are captured into an app-private directory (always available, no permission needed), and
the user nominates an **audio folder** once from the Audio pop-up; that persisted `OpenDocumentTree`
grant is the sidecars' home on disk. Opening a document pulls the files it references in; saving (and
stopping a recording) pushes them back out. Without a nominated folder audio still records and plays
for the session — only the hand-off to and from the desktop is missing, and the app says so. `fn` is
reduced to a bare file name before use, so a hand-edited path in a document can't escape that folder.
