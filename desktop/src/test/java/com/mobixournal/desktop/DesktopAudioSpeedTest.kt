package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The transport's speed: the steps a person picks, and the resample the feeder writes.
 *
 * The numbers matter twice over. The label is what the pill says, and the sample count is what keeps a
 * 2× clip **half as long** on the device while the clock keeps counting the recording's own frames — a
 * clock that counted written samples would run at half speed and put the karaoke a word behind.
 */
class DesktopAudioSpeedTest {

    @Test
    fun `the pill names each step`() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("1.5×", speedLabel(1.5f))
        assertEquals("2×", speedLabel(2f))
    }

    @Test
    fun `the speed walks the four steps and comes back`() {
        val player = DesktopAudioPlayer()
        assertEquals("as recorded", 1f, player.speed)
        assertEquals(1.25f, player.nextSpeed())
        assertEquals(1.5f, player.nextSpeed())
        assertEquals(2f, player.nextSpeed())
        assertEquals("and round again", 1f, player.nextSpeed())
    }

    @Test
    fun `a clip at twice the speed writes half the samples`() {
        val stereo = ShortArray(2_000) { it.toShort() }
        val (out, count) = resample(stereo, stereo.size, channels = 2, speed = 2f)
        assertEquals("500 frames, two channels each", 1_000, count)
        assertEquals("the first frame is untouched", stereo[0], out[0])
        assertEquals("and it is a frame of the recording, not a sample", stereo[1], out[1])
    }

    @Test
    fun `playing as recorded copies the chunk`() {
        val stereo = ShortArray(400) { it.toShort() }
        val (out, count) = resample(stereo, stereo.size, channels = 2, speed = 1f)
        assertEquals(400, count)
        assertEquals("nothing is rebuilt", stereo, out)
    }
}
