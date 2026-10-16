package com.mobixournal.render

import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /**
     * The point-precise seek: a tap starts the recording where the **point under it** was drawn, which is
     * the timing only a `.mobix` carries (`docs/audio-inking-sync.md`). The lookup is pure, so what a tap
     * would seek to is checked here rather than on a device.
     */
    @Test
    fun seekingFindsTheNearestPointAndTheTimeItNames() {
        // A sparse stroke: three points a word apart, 0 / 1000 / 2000 ms into a stroke that started at 5 s.
        val points = listOf(
            StrokePoint(0.0, 0.0, 1.0, dtMs = 0),
            StrokePoint(100.0, 0.0, 1.0, dtMs = 1_000),
            StrokePoint(200.0, 0.0, 1.0, dtMs = 1_000),
        )

        assertEquals(5_000, KaraokeStrokeAnimator.seekTimeMs(5_000, points, x = 4.0, y = 2.0))
        assertEquals(6_000, KaraokeStrokeAnimator.seekTimeMs(5_000, points, x = 92.0, y = 0.0))
        assertEquals("a tap on the fifth word starts there, not at the head", 7_000, KaraokeStrokeAnimator.seekTimeMs(5_000, points, x = 196.0, y = 0.0))
    }

    @Test
    fun seekingPastEitherEndLandsOnTheEndItIsPast() {
        val points = listOf(
            StrokePoint(0.0, 0.0, 1.0, dtMs = 0),
            StrokePoint(100.0, 0.0, 1.0, dtMs = 1_000),
        )

        assertEquals(5_000, KaraokeStrokeAnimator.seekTimeMs(5_000, points, x = -900.0, y = -900.0))
        assertEquals(6_000, KaraokeStrokeAnimator.seekTimeMs(5_000, points, x = 900.0, y = 900.0))
    }

    @Test
    fun seekingAnswersNothingWithoutATimeline() {
        val untimed = listOf(
            StrokePoint(0.0, 0.0, 1.0),
            StrokePoint(100.0, 0.0, 1.0),
        )
        assertNull(
            "a .xopp import has no deltas, so the caller falls back to the stroke's offset",
            KaraokeStrokeAnimator.seekTimeMs(5_000, untimed, x = 100.0, y = 0.0),
        )
        assertNull(KaraokeStrokeAnimator.seekTimeMs(5_000, emptyList(), x = 0.0, y = 0.0))
    }

    /**
     * A timeline with a hole in it: the points *before* the hole are as well timed as they ever were, and
     * the ones after it are short by the delta nobody recorded — so the hole is a fact about each point,
     * not about the stroke.
     */
    @Test
    fun seekingAcrossAMissingDeltaOnlyTrustsThePointsBeforeIt() {
        val mixed = listOf(
            StrokePoint(0.0, 0.0, 1.0, dtMs = 0),
            StrokePoint(100.0, 0.0, 1.0, dtMs = 500),
            StrokePoint(200.0, 0.0, 1.0, dtMs = null),
            StrokePoint(300.0, 0.0, 1.0, dtMs = 200),
        )

        assertEquals(5_500, KaraokeStrokeAnimator.seekTimeMs(5_000, mixed, x = 100.0, y = 0.0))
        assertNull("the gap makes its own time unknown", KaraokeStrokeAnimator.pointTimeMs(5_000, mixed, 2))
        assertNull("and every point after it", KaraokeStrokeAnimator.seekTimeMs(5_000, mixed, x = 300.0, y = 0.0))
    }

    @Test
    fun matchingRecordingToleratesPathsAndCase() {
        assertTrue(KaraokeStrokeAnimator.isMatchingRecording("assets/aud_abc.opus", "aud_abc.opus"))
        assertTrue(KaraokeStrokeAnimator.isMatchingRecording("C:\\path\\aud_ABC.opus", "assets/aud_abc.opus"))
    }
}
