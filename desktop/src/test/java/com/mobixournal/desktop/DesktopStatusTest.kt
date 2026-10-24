package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The status bar's refresh readout.
 *
 * "Is the refresh unlocked" used to be answerable only by inferring it from the watchdog's `paints/s`,
 * which exists only under `--diagnose`. The display's rate is now on screen, so the formatting is pinned
 * here: a 120 Hz panel must read as 120 Hz with its own 8.3 ms budget, a 144 Hz one as 6.9 ms, and a
 * host that cannot say must say so in one word rather than divide by zero.
 */
class DesktopStatusTest {

    @Test
    fun `a refresh rate reads with the frame budget it allows`() {
        assertEquals("120 Hz · 8.3 ms frame", refreshLabel(120))
        assertEquals("144 Hz · 6.9 ms frame", refreshLabel(144))
        assertEquals("60 Hz · 16.7 ms frame", refreshLabel(60))
    }

    @Test
    fun `a host that cannot report a rate says so instead of showing a zero`() {
        assertEquals("refresh unknown", refreshLabel(null))
        assertEquals("refresh unknown", refreshLabel(0))
        assertEquals("refresh unknown", refreshLabel(-1))
    }

    /**
     * The reader's own machine is asked for real. A headless JVM (which is how the suite runs) has no
     * device, so the reading is either absent or a positive rate — never an exception and never `0`,
     * because both are what the guarded path is for.
     */
    @Test
    fun `the display's rate is absent or positive on this host, never an error`() {
        val hz = displayRefreshHz()
        assertTrue("a reported rate is a real one", hz == null || hz > 0)
    }
}
