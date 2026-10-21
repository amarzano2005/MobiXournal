package com.mobixournal.audio

import com.mobixournal.format.model.ATTR_AUDIO_FILENAME
import com.mobixournal.format.model.ATTR_AUDIO_TIMESTAMP
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.audioRef
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A stroke's link into an audio recording: the sidecar file it was drawn over and how far into that
 * file the stroke began.
 *
 * The type itself is owned by the format model
 * ([com.mobixournal.format.model.AudioRef][com.mobixournal.format.model.AudioRef]) because the
 * `fn` / `ts` pair is a **file-format** fact — `.xopp` writes it as two attributes and `.mobix` as
 * an `audioRef` object, so the codecs are its readers and writers. This alias keeps the recording and
 * playback code naming the same type where a sidecar link conceptually belongs; there is one class,
 * not two, so the two halves of the app cannot drift.
 */
typealias AudioRef = com.mobixournal.format.model.AudioRef

/**
 * This stroke with [ref] stamped onto its `fn`/`ts` attributes. Every other extra attribute the file
 * carried is preserved untouched, which is what keeps a round-trip lossless for documents that use
 * attributes we don't model.
 */
fun Stroke.withAudio(ref: AudioRef): Stroke = copy(
    extraAttrs = extraAttrs +
        mapOf(ATTR_AUDIO_FILENAME to ref.filename, ATTR_AUDIO_TIMESTAMP to ref.startMs.toString()),
)

/**
 * Every sidecar file name [doc] references, de-duplicated. This is the set that must travel with the
 * `.xopp` for it to stay playable — used both to pull sidecars in on open and to push them back out
 * on save.
 */
fun documentAudioFiles(doc: Document): Set<String> =
    doc.pages.asSequence()
        .flatMap { it.layers.asSequence() }
        .flatMap { it.elements.asSequence() }
        .filterIsInstance<Stroke>()
        .mapNotNull { it.audioRef()?.filename }
        .toCollection(LinkedHashSet())

/**
 * The name a new recording started at [epochMillis] gets: desktop Xournal++'s local-time
 * `yyyy-MM-ddTHH-mm-ss.wav` convention, so recordings made on either side sort together in a folder
 * and never collide within a second.
 *
 * [extension] is the container the recorder actually writes — `wav` for the uncompressed sidecar a
 * `.xopp` needs, `ogg` for the Opus stream a `.mobix` bundles ([OpusAudioRecorder]). The name says which
 * container the file is in, exactly as an asset entry name does, so nothing has to guess by content.
 */
fun audioFileName(
    epochMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    extension: String = "wav",
): String = FILENAME_FORMAT.withZone(zone).format(Instant.ofEpochMilli(epochMillis)) + "." + extension

private val FILENAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
