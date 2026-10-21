package com.mobixournal.format.model

/**
 * A stroke's link into an audio recording: the sidecar file it was drawn over and how far into that
 * file the stroke began. This is exactly the `fn` / `ts` pair desktop Xournal++ writes on `<stroke>`
 * (see `docs/xopp-format.md` → stroke attributes), so anything we stamp here replays on the desktop
 * and anything the desktop wrote replays here.
 *
 * It lives in this package rather than in `com.mobixournal.audio` because it is a **format** fact:
 * `.xopp` carries it as two attributes and `.mobix` as an `audioRef` object, so the codecs are the
 * readers and writers of it. `com.mobixournal.audio` keeps a typealias for it, so recording and
 * playback code names the same type it always did.
 */
data class AudioRef(
    /** The sidecar's bare file name (no directory), e.g. `2026-08-02T09-14-33.wav`. */
    val filename: String,
    /** Milliseconds from the start of that recording to the moment the stroke was started. */
    val startMs: Int,
)

/** The `<stroke>` attribute naming the audio sidecar. */
const val ATTR_AUDIO_FILENAME: String = "fn"

/** The `<stroke>` attribute holding the offset into the sidecar, in milliseconds. */
const val ATTR_AUDIO_TIMESTAMP: String = "ts"

/**
 * The recording this stroke replays from, or null when it carries no usable link. Xournal++ writes
 * `ts="0" fn=""` on strokes that have no audio, so a blank `fn` is "unlinked", not "file named ''".
 */
fun Stroke.audioRef(): AudioRef? {
    val fn = extraAttrs[ATTR_AUDIO_FILENAME]?.takeIf { it.isNotBlank() } ?: return null
    val ts = extraAttrs[ATTR_AUDIO_TIMESTAMP]?.trim()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    return AudioRef(fn, ts)
}
