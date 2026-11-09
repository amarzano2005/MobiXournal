package com.mobixournal.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind a long take's waveform: blocks that tile the recording, samples that land in
 * one block, and windows that stay inside theirs (`WaveformScan`, used by `CompressedWaveform`).
 */
class WaveformScanTest {

    @Test
    fun `blocks tile the whole recording, first instant to last`() {
        val twoHours = 7_200_000_000L
        assertEquals(0L, WaveformScan.blockStart(0, 80, twoHours))
        assertEquals(twoHours, WaveformScan.blockEnd(79, 80, twoHours))
        for (block in 0 until 79) {
            assertEquals(
                "block $block ends where the next begins",
                WaveformScan.blockEnd(block, 80, twoHours),
                WaveformScan.blockStart(block + 1, 80, twoHours),
            )
        }
    }

    @Test
    fun `a sample belongs to the block its instant falls in`() {
        val minute = 60_000_000L
        assertEquals(0, WaveformScan.blockOf(0, 80, minute))
        assertEquals(0, WaveformScan.blockOf(749_999, 80, minute))
        assertEquals(1, WaveformScan.blockOf(750_000, 80, minute))
        assertEquals(79, WaveformScan.blockOf(minute, 80, minute))
        assertEquals("past the end is the last block", 79, WaveformScan.blockOf(minute * 2, 80, minute))
        assertEquals("before the start is the first", 0, WaveformScan.blockOf(-5, 80, minute))
    }

    @Test
    fun `a long take is sampled and a short one decoded whole`() {
        assertFalse(WaveformScan.shouldSample(30_000_000L))
        assertFalse(WaveformScan.shouldSample(WaveformScan.EXACT_LIMIT_US))
        assertTrue(WaveformScan.shouldSample(WaveformScan.EXACT_LIMIT_US + 1))
        assertTrue("half an hour", WaveformScan.shouldSample(1_800_000_000L))
    }

    @Test
    fun `windows stay inside their block and end where it does`() {
        val halfHour = 1_800_000_000L
        assertEquals(
            WaveformScan.blockStart(3, 80, halfHour),
            WaveformScan.windowStart(3, 80, 0, 3, halfHour),
        )
        assertEquals(
            WaveformScan.blockEnd(3, 80, halfHour),
            WaveformScan.windowEnd(3, 80, 2, 3, halfHour),
        )
        var previous = WaveformScan.windowStart(3, 80, 0, 3, halfHour)
        for (window in 0 until 3) {
            val start = WaveformScan.windowStart(3, 80, window, 3, halfHour)
            val end = WaveformScan.windowEnd(3, 80, window, 3, halfHour)
            assertTrue("window $window starts at or after the last", start >= previous)
            assertTrue("window $window has a length to read", end > start)
            previous = end
        }
    }

    @Test
    fun `a recording with no length answers its first block instead of dividing by nothing`() {
        assertEquals(0L, WaveformScan.blockStart(5, 80, 0))
        assertEquals(0L, WaveformScan.blockEnd(5, 80, 0))
        assertEquals(0, WaveformScan.blockOf(1_000, 80, 0))
        assertEquals(0L, WaveformScan.windowStart(5, 80, 1, 3, 0))
    }
}
