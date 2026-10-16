package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The playhead's arithmetic, with no Android in it.
 *
 * What is worth pinning is what the media stack *cannot* say: that nothing sounding is `null` rather
 * than a stale zero, that a seek past the end lands on the end, and that a seek reads as the position
 * it asked for while the platform's asynchronous seek is still on its way — the three readings the
 * replay path draws the writing with.
 */
class PlayheadTest {

    /**
     * A clip of the test's own: it reports a position and a length, and a seek is **recorded rather than
     * applied** until the test lands it — which is how `MediaPlayer.seekTo` behaves for a frame or two.
     */
    private class FakeClip(var duration: Int = 60_000, var position: Int = 0) : ClipClock {

        val seeks = mutableListOf<Int>()

        override fun positionMs(): Int = position

        override fun durationMs(): Int = duration

        override fun seekTo(ms: Int) {
            seeks += ms
        }

        /** The platform catching up with the seek it was handed. */
        fun landed(at: Int = seeks.last()) {
            position = at
        }
    }

    private val playhead = Playhead()

    @Test
    fun `an idle player has no playhead at all`() {
        assertNull("nothing is sounding, so there is no position to report", playhead.positionMs())
        assertNull("and nothing to seek", playhead.seekTo(1_000))
    }

    @Test
    fun `a sounding clip reports where it is`() {
        playhead.attach(FakeClip(position = 12_345))

        assertEquals(12_345, playhead.positionMs())
    }

    @Test
    fun `a position outside the clip is clamped to it`() {
        val clip = FakeClip(duration = 30_000, position = 31_500)
        playhead.attach(clip)

        assertEquals("a stack that overran its own length still reports the end", 30_000, playhead.positionMs())

        clip.position = -1
        assertEquals("and never a negative time", 0, playhead.positionMs())
    }

    @Test
    fun `a seek past the end stops at the end`() {
        val clip = FakeClip(duration = 30_000)
        playhead.attach(clip)

        assertEquals(30_000, playhead.seekTo(45_000))
        assertEquals("and the clip was asked for the end, not the request", listOf(30_000), clip.seeks)
        assertEquals("a negative seek is the beginning", 0, playhead.seekTo(-500))
    }

    @Test
    fun `a seek reads as the position it asked for until the clip gets there`() {
        val clip = FakeClip(duration = 60_000, position = 1_000)
        playhead.attach(clip)

        assertEquals(20_000, playhead.seekTo(20_000))
        assertEquals("the stack is still reporting where playback was", 1_000, clip.positionMs())
        assertEquals(
            "so the playhead keeps saying where it is going",
            20_000,
            playhead.positionMs(),
        )

        clip.landed()
        assertEquals("once the seek lands, the clip's own reading is the answer", 20_000, playhead.positionMs())

        // …and the same for a seek backwards, which is the case the one comparison has to get right.
        assertEquals(5_000, playhead.seekTo(5_000))
        assertEquals(5_000, playhead.positionMs())
        clip.landed()
        assertEquals(5_000, playhead.positionMs())
    }

    @Test
    fun `a clip that finished reports nothing`() {
        val clip = FakeClip(position = 59_000)
        playhead.attach(clip)
        assertTrue("it was reporting", playhead.positionMs() != null)

        playhead.detach()

        assertNull("completion or a stop leaves no position behind", playhead.positionMs())
        assertNull("and no seek", playhead.seekTo(0))
    }
}
