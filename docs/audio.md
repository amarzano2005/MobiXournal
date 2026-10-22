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
committed stroke passes through — so freehand, shapes and splines are all stamped, and the audio
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

*Sidecars.* A `.xopp` never carries its audio, and SAF grants access to the single document the user
picked — not to its folder — so we can't write a sibling from a `CreateDocument` URI alone. Instead
recordings are captured into an app-private directory (always available, no permission needed), and
the user nominates an **audio folder** once from the Audio pop-up; that persisted `OpenDocumentTree`
grant is the sidecars' home on disk. Opening a document pulls the files it references in; saving (and
stopping a recording) pushes them back out. Without a nominated folder audio still records and plays
for the session — only the hand-off to and from the desktop is missing, and the app says so. `fn` is
reduced to a bare file name before use, so a hand-edited path in a document can't escape that folder.
