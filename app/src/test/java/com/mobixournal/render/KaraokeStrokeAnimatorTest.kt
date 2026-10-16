package com.mobixournal.render

import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeStrokeAnimatorTest {

    private fun testStroke(startMs: Int, pointDts: List<Int>): Stroke {
        val points = pointDts.mapIndexed { idx, dt ->
            StrokePoint(10.0 + idx * 5.0, 20.0 + idx * 5.0, 1.5, dtMs = dt)
        }
        return Stroke(
            tool = Tool.PEN,
            color = 0xFF123456.toInt(),
            capStyle = "round",
            points = points,
            uniformWidth = true,
            extraAttrs = mapOf("fn" to "assets/aud_lecture1.opus", "ts" to startMs.toString()),
        )
    }

    @Test
    fun returnsNormalWithNothingOnOrADifferentRecording() {
        val stroke = testStroke(startMs = 5000, pointDts = listOf(0, 100, 200))

        val nothingOn = KaraokeStrokeAnimator.resolve(stroke, state = null)
        assertTrue("no state at all is a page painting itself", nothingOn is KaraokeStrokeAnimator.RenderDirective.Normal)

        val differentAudio = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeStrokeAnimator.State("aud_other.opus", playheadMs = 5100, isPlaying = true),
        )
        assertTrue(differentAudio is KaraokeStrokeAnimator.RenderDirective.Normal)
    }

    /**
     * A **held** playhead — a pause, or a position dragged out with the scrubber — resolves the writing
     * exactly as a sounding one does. That is what lets the scrubber walk the page with the sound off;
     * `isPlaying` is only what the chrome labels its button with.
     */
    @Test
    fun aHeldPlayheadResolvesTheWritingAtItsPosition() {
        val stroke = testStroke(startMs = 5000, pointDts = listOf(0, 100, 250, 400))

        val beforeIt = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeStrokeAnimator.State("aud_lecture1.opus", playheadMs = 4500, isPlaying = false),
        )
        assertTrue("held before the stroke, it is not on the page yet", beforeIt is KaraokeStrokeAnimator.RenderDirective.Dimmed)

        val duringIt = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeStrokeAnimator.State("aud_lecture1.opus", playheadMs = 5150, isPlaying = false),
        )
        val animating = duringIt as KaraokeStrokeAnimator.RenderDirective.Animating
        assertEquals("held mid-stroke, the writing stands where the playhead is", 2, animating.visiblePoints.size)

        val afterIt = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeStrokeAnimator.State("aud_lecture1.opus", playheadMs = 5400, isPlaying = false),
        )
        assertTrue("held at its last point, the stroke is whole", afterIt is KaraokeStrokeAnimator.RenderDirective.Normal)
    }

    @Test
    fun dimsFutureStrokeWrittenAfterPlayhead() {
        val stroke = testStroke(startMs = 5000, pointDts = listOf(0, 100, 200))
        val directive = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeStrokeAnimator.State("aud_lecture1.opus", playheadMs = 4500, isPlaying = true),
        )

        assertTrue("stroke is dimmed", directive is KaraokeStrokeAnimator.RenderDirective.Dimmed)
        val dimmed = directive as KaraokeStrokeAnimator.RenderDirective.Dimmed
        val expectedAlpha = (0xFF * 0.25f).toInt()
        assertEquals(expectedAlpha, (dimmed.colorArgb ushr 24) and 0xFF)
    }

    @Test
    fun animatesStrokeInProgressAtCurrentTimestamp() {
        // Stroke starts at 5000ms. Points at +0ms (5000), +100ms (5100), +250ms (5250), +400ms (5400)
        val stroke = testStroke(startMs = 5000, pointDts = listOf(0, 100, 250, 400))
        val directive = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeStrokeAnimator.State("assets/aud_lecture1.opus", playheadMs = 5150, isPlaying = true),
        )

        assertTrue("stroke is animating", directive is KaraokeStrokeAnimator.RenderDirective.Animating)
        val animating = directive as KaraokeStrokeAnimator.RenderDirective.Animating
        assertEquals(2, animating.visiblePoints.size) // 5000 and 5100 are <= 5150
        assertEquals(2, animating.futurePoints?.size) // 5250 and 5400 are > 5150
        assertNotNull(animating.activeTip)
        assertEquals(100, animating.activeTip?.dtMs)
    }

    @Test
    fun returnsNormalOncePlayheadPassesStrokeCompletion() {
        val stroke = testStroke(startMs = 5000, pointDts = listOf(0, 100, 250))
        val directive = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeStrokeAnimator.State("aud_lecture1.opus", playheadMs = 5300, isPlaying = true),
        )

        assertTrue("completed stroke is rendered normally", directive is KaraokeStrokeAnimator.RenderDirective.Normal)
    }

    /**
     * A stroke whose points carry **no** `dtMs` is a stroke with no timeline: a `.xopp` import, or a
     * `.mobix` written before the telemetry landed. The honest answer is that it is simply *complete* —
     * the recording has reached it (or not) as a whole, and it never animates, because animating it
     * would mean inventing a timeline and drawing the writing at a place nothing recorded.
     */
    @Test
    fun aStrokeWithNoPointTimesIsCompleteRatherThanAnimated() {
        val untimed = testStroke(startMs = 5000, pointDts = listOf(0, 100, 250)).copy(
            points = testStroke(startMs = 5000, pointDts = listOf(0, 100, 250)).points.map { it.copy(dtMs = null) },
        )
        val state = { ms: Int -> KaraokeStrokeAnimator.State("aud_lecture1.opus", playheadMs = ms, isPlaying = true) }

        assertTrue(
            "the recording has not reached it yet, so it is not on the page",
            KaraokeStrokeAnimator.resolve(untimed, state(4_000)) is KaraokeStrokeAnimator.RenderDirective.Dimmed,
        )
        assertTrue(
            "once it has, the stroke is whole — never a half-drawn one",
            KaraokeStrokeAnimator.resolve(untimed, state(5_000)) is KaraokeStrokeAnimator.RenderDirective.Normal,
        )
        assertTrue(KaraokeStrokeAnimator.resolve(untimed, state(9_000)) is KaraokeStrokeAnimator.RenderDirective.Normal)
    }

    @Test
    fun dimmedColorMultipliesAlphaChannelByFactor() {
        val opaqueRed = 0xFFFF0000.toInt()
        val dimmedRed = KaraokeStrokeAnimator.dimmedColor(opaqueRed, 0.25f)
        assertEquals(63, (dimmedRed ushr 24) and 0xFF)
        assertEquals(0x00FF0000, dimmedRed and 0x00FFFFFF)
    }

    @Test
    fun matchingRecordingToleratesPathsAndCase() {
        assertTrue(KaraokeStrokeAnimator.isMatchingRecording("assets/aud_abc.opus", "aud_abc.opus"))
        assertTrue(KaraokeStrokeAnimator.isMatchingRecording("C:\\path\\aud_ABC.opus", "assets/aud_abc.opus"))
    }
}
