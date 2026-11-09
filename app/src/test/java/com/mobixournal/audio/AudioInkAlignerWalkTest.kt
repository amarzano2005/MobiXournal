package com.mobixournal.audio

import com.mobixournal.format.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scrubber's question — *which stroke was being written at this moment* — is answered page by page
 * and only for the clip that is on. Two things matter here: the answer is the same one a document-wide
 * scan gave, and the walk is interruptible, because a drag asks it on every step and the previous step
 * has to be retireable.
 */
class AudioInkAlignerWalkTest {

    private fun stroke(name: String, startMs: Int, x: Double = 10.0): Stroke =
        Stroke(Tool.PEN, 0, "round", listOf(StrokePoint(x, x, 1.0)), uniformWidth = true)
            .withAudio(AudioRef(name, startMs))

    private fun page(vararg strokes: Stroke) =
        Page(100.0, 100.0, Background.Solid(-1, "plain"), listOf(Layer(strokes.toList())))

    private val document = Document(pages = listOf(
        page(stroke("lecture.ogg", 0)),
        page(),
        page(stroke("lecture.ogg", 5_000, 20.0), stroke("other.ogg", 5_000, 30.0)),
    ))

    @Test fun onlyTheStrokesOfThatClipInsideTheWindowAreAnswered() {
        val hits = AudioInkAligner.findStrokesAtTime(document, "lecture.ogg", 5_000)
        assertEquals("one stroke of this clip at that moment, on page 2 only", listOf(2), hits.map { it.first })
        val start = AudioInkAligner.findStrokesAtTime(document, "folder/LECTURE.OGG", 0)
        assertEquals(listOf(0), start.map { it.first })
        assertTrue("no stroke of another recording", AudioInkAligner
            .findStrokesAtTime(document, "lecture.ogg", 60_000).isEmpty())
    }

    @Test fun theWalkIsInterruptibleBecauseEveryDragStepAsksItAgain() {
        var checks = 0
        try {
            AudioInkAligner.findStrokesAtTime(document, "lecture.ogg", 5_000,
                checkCancelled = { checks++; if (checks == 2) throw java.util.concurrent.CancellationException() })
            throw AssertionError("the walk must stop when the step that asked it is cancelled")
        } catch (_: java.util.concurrent.CancellationException) {
            // Per page, so the cancelled step never inflated the rest of the notebook.
            assertEquals(2, checks)
        }
    }
}
