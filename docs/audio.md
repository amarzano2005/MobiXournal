# Audio annotation: record, replay, sidecars

> Read when you touch audio recording, playback, or the sidecar files a `.xopp` refers to.

_One spoke of the architecture docs; the index is [architecture.md](architecture.md)._

## Audio-annotated strokes (`audio/`)

**Audio-annotated strokes (`audio/`).** Xournal++ can record while you write and then replay from
any stroke: the stroke carries `fn` (a `.wav` file name) and `ts` (how far into that recording it was
started). We implement both ends of that.

*Recording.* The **Audio** rail slot toggles capture. `AudioSession.startRecording` names the file
with the desktop's local-time `yyyy-MM-ddTHH-mm-ss.wav` convention and hands it to `AudioRecorder`,
which pumps `AudioRecord` (44.1 kHz mono 16-bit PCM) into a `WavWriter` on its own thread — Android
has no WAV encoder, so `WavWriter` frames the RIFF header itself and patches its two length fields on
close. `RECORD_AUDIO` is requested the first time Record is pressed; everything else in the app works
without it.

The surface's `audioStamp` hook is read **inside `appendStroke`**, which is the single funnel every
committed stroke passes through — so freehand, shapes and splines are all stamped, and the audio
machinery stays out of the drawing hot path. The `ts` it stamps comes from the *bytes written so far*
(`WavWriter.durationMs`), not wall time, so a stroke's offset points at the sample it was really
drawn over even if the capture thread stalls.

*Replay.* The **Play object** tool sets `audioPlayMode`, which short-circuits `beginPointer` before
the gesture classifier — it is a pure query that never edits the document, so it earns no
`GestureIntent`. A tap picks the topmost stroke (`SelectionTester.pickTopmost`), reads its `AudioRef`,
and `AudioPlayer` seeks a `MediaPlayer` to that offset. A tap that misses, or lands on a stroke with
no recording, says so rather than failing silently.

*The playhead.* Playback publishes **where it is**: `AudioSession.playheadMs` (null while nothing
sounds) and `AudioSession.seek(ms)`, both reading the one `Playhead` beside the player. It is a class
of its own because every rule it enforces is ours, not the platform's — an idle clip answers nothing
rather than a stale zero, a seek is clamped to the clip's own length so a scrubber dragged past the
end lands on it, and a seek reads as the position it asked for until the media stack gets there,
since `MediaPlayer.seekTo` is asynchronous and `currentPosition` reports where playback *was* for a
moment after it. None of that needs a device to exercise, which is the point of the seam
(`audio/Playhead.kt`, JVM-tested). What draws from it — the karaoke replay and the scrubber — is
`docs/audio-inking-sync.md`'s subject.

*Sidecars.* A `.xopp` never carries its audio, and SAF grants access to the single document the user
picked — not to its folder — so we can't write a sibling from a `CreateDocument` URI alone. Instead
recordings are captured into an app-private directory (always available, no permission needed), and
the user nominates an **audio folder** once from the Audio pop-up; that persisted `OpenDocumentTree`
grant is the sidecars' home on disk. Opening a document pulls the files it references in; saving (and
stopping a recording) pushes them back out. Without a nominated folder audio still records and plays
for the session — only the hand-off to and from the desktop is missing, and the app says so. `fn` is
reduced to a bare file name before use, so a hand-edited path in a document can't escape that folder.
