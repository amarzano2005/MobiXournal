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
 * (`fn`/`ts`, see `docs/audio.md`): a stroke started at `startMs` of a named recording.
 * That is enough to follow a playing clip across the page — the mini-player asks which strokes were
 * being written at the playhead and scrolls to them ([findStrokesAtTime]) — and it is all this file
 * answers. Speech recognition, transcripts and the alignment of spoken words to ink are not part of
 * the app.
 */
object AudioInkAligner {

    internal const val DEFAULT_TIMESTAMP_TOLERANCE_MS = 3000

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
     * Every stroke of [audioFilename] in [doc], as a timeline to answer a scrub from — page by page,
     * keeping only that clip's own strokes ([AudioSeekIndex]).
     */
    internal fun timeline(
        doc: Document,
        audioFilename: String,
        checkCancelled: () -> Unit = {},
    ): List<AudioSeekIndex.TimedStroke> {
        val timed = ArrayList<AudioSeekIndex.TimedStroke>(16)
        doc.pages.forEachIndexed { pageIndex, page ->
            checkCancelled()
            for (layer in page.layers) {
                for (element in layer.elements) {
                    if (element !is Stroke) continue
                    val ref = element.audioRef() ?: continue
                    if (!isMatchingAudio(ref.filename, audioFilename)) continue
                    val end = ref.startMs + element.points.sumOf { (it.dtMs ?: 0).coerceAtLeast(0) }
                    timed += AudioSeekIndex.TimedStroke(
                        pageIndex, ref.startMs, end, HandwritingRecognizer.strokeBounds(element),
                    )
                }
            }
        }
        return timed
    }

    /**
     * Resolves the handwritten stroke bounds closest in time to [playheadMs] for [audioFilename].
     *
     * A one-shot scan: the app itself answers a drag from the timeline it built once
     * ([AudioSeekIndex.at]), and this is what a caller that has no index to keep can ask for.
     */
    fun findStrokesAtTime(
        doc: Document,
        audioFilename: String,
        playheadMs: Int,
        toleranceMs: Int = DEFAULT_TIMESTAMP_TOLERANCE_MS,
        checkCancelled: () -> Unit = {},
    ): List<Pair<Int, Bounds>> =
        timeline(doc, audioFilename, checkCancelled)
            .filter { playheadMs in (it.startMs - 500)..(it.endMs + toleranceMs) }
            .map { it.page to it.bounds }

    /** Whether two references name the same recording, whatever folder each was written with. */
    fun isMatchingAudio(refA: String, refB: String): Boolean {
        val a = refA.substringAfterLast('/').substringAfterLast('\\')
        val b = refB.substringAfterLast('/').substringAfterLast('\\')
        return a.equals(b, ignoreCase = true)
    }
}
