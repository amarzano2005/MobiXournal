package com.mobixournal.audio

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.audioRef
import com.mobixournal.render.Bounds
import com.mobixournal.render.HandwritingRecognizer

/**
 * Where on the page the recording was being written when the playhead passed a given moment.
 *
 * The only temporal link the app keeps between ink and sound is the stroke's own timestamp
 * (`fn`/`ts`, see `docs/audio-inking-sync.md`): a stroke started at `startMs` of a named recording.
 * That is enough to follow a playing clip across the page — the mini-player asks which strokes were
 * being written at the playhead and scrolls to them ([findStrokesAtTime]) — and it is all this file
 * answers. Speech recognition, transcripts and the alignment of spoken words to ink are not part of
 * the app.
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

    /** Whether two references name the same recording, whatever folder each was written with. */
    fun isMatchingAudio(refA: String, refB: String): Boolean {
        val a = refA.substringAfterLast('/').substringAfterLast('\\')
        val b = refB.substringAfterLast('/').substringAfterLast('\\')
        return a.equals(b, ignoreCase = true)
    }
}
