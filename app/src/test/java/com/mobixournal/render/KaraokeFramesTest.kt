package com.mobixournal.render

import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What one frame of playback publishes, and what the page does with it.
 *
 * The mapping is the only part of the karaoke loop that can be exercised without a device, and it is the
 * part with a decision in it: a sounding clip publishes its playhead, and **nothing else publishes
 * anything** — a stopped or finished clip and a session that cannot say what it is playing all mean
 * "paint normally", not "paint at position zero".
 */
class KaraokeFramesTest {

    private fun strokeAt(startMs: Int, vararg dtMs: Int): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = dtMs.mapIndexed { index, dt -> StrokePoint(10.0 + index, 10.0, 2.0, dtMs = dt) },
        uniformWidth = true,
    ).withAudio(AudioRef("lesson.wav", startMs))

    @Test
    fun `a sounding clip publishes the playhead it is at`() {
        val state = KaraokeFrames.stateOf(KaraokeFrames.Clip("lesson.wav", 12_345, isPlaying = true))

        assertEquals(
            KaraokeStrokeAnimator.State(audioFile = "lesson.wav", playheadMs = 12_345, isPlaying = true),
            state,
        )
    }

    @Test
    fun `a held clip publishes the position it is held at`() {
        val state = KaraokeFrames.stateOf(KaraokeFrames.Clip("lesson.wav", 8_000, isPlaying = false))

        assertEquals(
            "paused is a position, and the page keeps showing the writing there",
            KaraokeStrokeAnimator.State(audioFile = "lesson.wav", playheadMs = 8_000, isPlaying = false),
            state,
        )
    }

    @Test
    fun `a clip that stopped or finished publishes nothing`() {
        assertNull(
            "the session holds no clip",
            KaraokeFrames.stateOf(KaraokeFrames.Clip(null, null, isPlaying = false)),
        )
    }

    @Test
    fun `a session that cannot name the clip or place it publishes nothing`() {
        assertNull(KaraokeFrames.stateOf(KaraokeFrames.Clip(null, 4_000, isPlaying = true)))
        assertNull(KaraokeFrames.stateOf(KaraokeFrames.Clip("lesson.wav", null, isPlaying = true)))
    }

    @Test
    fun `the published state is what dims unwritten strokes and animates the one being written`() {
        val stroke = strokeAt(startMs = 1_000, 0, 200, 400)

        val before = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeFrames.stateOf(KaraokeFrames.Clip("lesson.wav", 500, isPlaying = true)),
        )
        assertTrue("a stroke the recording has not reached is dimmed", before is KaraokeStrokeAnimator.RenderDirective.Dimmed)

        val during = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeFrames.stateOf(KaraokeFrames.Clip("lesson.wav", 1_250, isPlaying = true)),
        )
        val animating = during as KaraokeStrokeAnimator.RenderDirective.Animating
        assertEquals("two of the three points are written by then", 2, animating.visiblePoints.size)
        assertEquals(1, animating.futurePoints?.size)

        val after = KaraokeStrokeAnimator.resolve(
            stroke,
            KaraokeFrames.stateOf(KaraokeFrames.Clip("lesson.wav", 9_000, isPlaying = true)),
        )
        assertEquals("and once the recording is past it, it is simply a stroke", KaraokeStrokeAnimator.RenderDirective.Normal, after)
    }

    @Test
    fun `a page with nothing on resolves every stroke normally`() {
        val stroke = strokeAt(startMs = 1_000, 0, 200)

        assertEquals(
            KaraokeStrokeAnimator.RenderDirective.Normal,
            KaraokeStrokeAnimator.resolve(
                stroke,
                KaraokeFrames.stateOf(KaraokeFrames.Clip(null, null, isPlaying = false)),
            ),
        )
    }
}
