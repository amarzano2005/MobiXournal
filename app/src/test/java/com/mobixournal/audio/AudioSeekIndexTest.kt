package com.mobixournal.audio

import com.mobixournal.render.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scrubber's cost is the walk, not the question: fifty drag steps over one clip must read the
 * notebook once. Keyed by identity, so an edit — which replaces the document — invalidates the timeline
 * without anything having to remember to.
 */
class AudioSeekIndexTest {

    @Before
    fun clearTheCache() = AudioSeekIndex.clear()

    private fun stroke(page: Int, startMs: Int, endMs: Int, x: Double = 0.0) =
        AudioSeekIndex.TimedStroke(page, startMs, endMs, Bounds(x, 0.0, x + 1.0, 1.0))

    @Test fun theNotebookIsWalkedOncePerDocumentAndClip() {
        val document = Any()
        var builds = 0
        val timeline = listOf(stroke(0, 0, 900), stroke(3, 5_000, 6_000))
        repeat(50) {
            assertEquals(timeline, AudioSeekIndex.timelineOf(document, "lecture.ogg") { builds++; timeline })
        }
        assertEquals("fifty drag steps, one walk of the notebook", 1, builds)

        AudioSeekIndex.timelineOf(document, "other.ogg") { builds++; emptyList() }
        assertEquals("another clip is another timeline", 2, builds)

        AudioSeekIndex.timelineOf(Any(), "lecture.ogg") { builds++; timeline }
        assertEquals("an edited document has a new identity, and therefore a new walk", 3, builds)
    }

    @Test fun aStepAnswersTheStrokesInsideItsWindow() {
        AudioSeekIndex.timelineOf(Any(), "clip.ogg") {
            listOf(stroke(0, 1_000, 2_000), stroke(7, 5_000, 5_500))
        }
        assertEquals(listOf(0), AudioSeekIndex.at(1_500, AudioInkAligner.DEFAULT_TIMESTAMP_TOLERANCE_MS).map { it.first })
        assertEquals(listOf(7), AudioSeekIndex.at(5_200, AudioInkAligner.DEFAULT_TIMESTAMP_TOLERANCE_MS).map { it.first })
        assertTrue("far from every stroke there is nothing to scroll to",
            AudioSeekIndex.at(60_000, AudioInkAligner.DEFAULT_TIMESTAMP_TOLERANCE_MS).isEmpty())
        AudioSeekIndex.clear()
        assertTrue("a torn-down session answers nothing",
            AudioSeekIndex.at(1_500, AudioInkAligner.DEFAULT_TIMESTAMP_TOLERANCE_MS).isEmpty())
    }
}
