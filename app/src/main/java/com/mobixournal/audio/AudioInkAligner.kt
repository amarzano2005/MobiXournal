package com.mobixournal.audio

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.render.Bounds
import com.mobixournal.render.HandwritingRecognizer
import kotlin.math.abs

/**
 * An aligned link between a recognized handwritten word/stroke and a spoken transcription segment.
 *
 * @property pageIndex 0-based index of the document page containing the ink stroke.
 * @property wordText The recognized text or spoken word.
 * @property bounds Bounding box on the page in point coordinates.
 * @property timeMs Exact millisecond timestamp into the audio recording.
 * @property stroke The source stroke if mapped from inking.
 * @property segment The corresponding transcription segment if mapped from audio.
 */
data class AlignedWordHit(
    val pageIndex: Int,
    val wordText: String,
    val bounds: Bounds,
    val timeMs: Int,
    val stroke: Stroke? = null,
    val segment: TranscriptionSegment? = null,
)

/**
 * Synchronizes temporal audio transcription with page handwriting ink telemetry.
 *
 * Provides bidirectional temporal lookup:
 * - Given an audio playhead time $t$, locates the active handwritten ink bounding boxes on the canvas.
 * - Given a handwritten stroke timestamp, locates the matching transcription words/sentence.
 */
object AudioInkAligner {

    private const val DEFAULT_TIMESTAMP_TOLERANCE_MS = 3000

    /**
     * Extracts all time-stamped strokes across all pages of [doc].
     * Returns a list of triples: (pageIndex, stroke, audioRef).
     */
    fun findTimestampedStrokes(doc: Document): List<Triple<Int, Stroke, AudioRef>> =
        buildList {
            doc.pages.forEachIndexed { pageIndex, page ->
                for (layer in page.layers) {
                    for (element in layer.elements) {
                        if (element is Stroke) {
                            val ref = element.audioRef()
                            if (ref != null) {
                                add(Triple(pageIndex, element, ref))
                            }
                        }
                    }
                }
            }
        }

    /**
     * Resolves the handwritten stroke bounds closest in time to [playheadMs] for [audioFilename].
     */
    fun findStrokesAtTime(
        doc: Document,
        audioFilename: String,
        playheadMs: Int,
        toleranceMs: Int = DEFAULT_TIMESTAMP_TOLERANCE_MS,
    ): List<Pair<Int, Bounds>> {
        val strokes = findTimestampedStrokes(doc)
            .filter { (_, _, ref) -> isMatchingAudio(ref.filename, audioFilename) }

        return strokes.filter { (_, stroke, ref) ->
            val strokeStart = ref.startMs
            val strokeEnd = stroke.points.sumOf { (it.dtMs ?: 0).coerceAtLeast(0) } + strokeStart
            playheadMs in (strokeStart - 500)..(strokeEnd + toleranceMs)
        }.map { (pageIndex, stroke, _) ->
            pageIndex to HandwritingRecognizer.strokeBounds(stroke)
        }
    }

    /**
     * Aligns a list of [transcription] segments with strokes on [doc].
     * For each segment, finds any strokes committed during that segment's time span
     * and produces [AlignedWordHit] entries for unified search and seeking.
     */
    fun align(
        doc: Document,
        transcription: AudioTranscription,
    ): List<AlignedWordHit> {
        val timestampedStrokes = findTimestampedStrokes(doc)
            .filter { (_, _, ref) -> isMatchingAudio(ref.filename, transcription.audioFilename) }

        return buildList {
            for (segment in transcription.segments) {
                // Find strokes whose interval overlaps this transcription segment
                val matching = timestampedStrokes.filter { (_, stroke, ref) ->
                    val start = ref.startMs
                    val end = start + stroke.points.sumOf { (it.dtMs ?: 0).coerceAtLeast(0) }
                    // Overlap check between [segment.startTimeMs, segment.endTimeMs] and [start, end]
                    start <= segment.endTimeMs && end >= segment.startTimeMs
                }

                if (matching.isNotEmpty()) {
                    for ((pageIndex, stroke, ref) in matching) {
                        add(
                            AlignedWordHit(
                                pageIndex = pageIndex,
                                wordText = segment.text,
                                bounds = HandwritingRecognizer.strokeBounds(stroke),
                                timeMs = ref.startMs,
                                stroke = stroke,
                                segment = segment,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun isMatchingAudio(refA: String, refB: String): Boolean {
        val a = refA.substringAfterLast('/').substringAfterLast('\\')
        val b = refB.substringAfterLast('/').substringAfterLast('\\')
        return a.equals(b, ignoreCase = true)
    }
}
