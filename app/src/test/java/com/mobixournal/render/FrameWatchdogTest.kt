package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet canvas's frame record, driven by a fake clock.
 *
 * The window's twin (`DesktopDiagnostics`) proved this arithmetic on the running app — it found a ticker
 * painting ~120 frames a second on an idle sheet, which no screenshot could have shown. The tablet now keeps
 * the same record, and these cases pin the three rules that make the numbers mean something:
 *
 *  - a line is produced **once a second of painting**, not once a frame, so the log stays readable;
 *  - an **idle gap is not a stall**: a reader who looked away for a minute must not see that minute reported
 *    as one dropped frame, or every line after a pause reads as a catastrophe;
 *  - the **paint** and the **raster** counts are reported separately from the frame rate, because they answer
 *    different questions: the paint is what the code costs, the rasters are whether a stroke rebuilt the page.
 */
class FrameWatchdogTest {

    private val ms = 1_000_000L

    @Test
    fun `a second of frames produces one line and no line comes early`() {
        val watch = FrameWatchdog()
        val lines = mutableListOf<String>()
        watch.painted(2.0, nowNanos = 0L)?.let { lines += it }
        for (frame in 1..63) {
            watch.painted(2.0, nowNanos = frame * 16 * ms)?.let { lines += it }
            if (frame < 63) assertEquals("no line before the window closes", 0, lines.size)
        }
        assertEquals("one line for the first second", 1, lines.size)
        assertTrue("it states frames a second (${lines[0]})", lines[0].startsWith("frames/s="))
        assertTrue("and the worst frame is ~16 ms (${lines[0]})", lines[0].contains("worst=16ms"))
    }

    @Test
    fun `an idle gap is not a stall`() {
        val watch = FrameWatchdog()
        // One frame, then the reader looks away for a minute, then one more.
        watch.painted(1.0, nowNanos = 0L)
        watch.painted(1.0, nowNanos = 60_000 * ms)
        assertEquals(
            "a minute of idleness is not a dropped frame",
            0L,
            watch.worstInWindowNanos,
        )
    }

    @Test
    fun `the worst paint and the rasters are reported separately from the frame rate`() {
        val watch = FrameWatchdog()
        var rasters = 7
        watch.painted(1.0, pagesRastered = rasters, nowNanos = 0L)
        // A second of frames, one of which took 12 ms.
        var line: String? = null
        for (frame in 1..100) {
            if (frame == 50) rasters = 9 // two page rasters land mid-window
            line = watch.painted(if (frame == 40) 12.0 else 3.0, pagesRastered = rasters, nowNanos = frame * 10 * ms)
                ?: line
        }
        val reported = requireNotNull(line) { "a second should have produced a line" }
        assertTrue("the worst paint is the 12 ms one ($reported)", reported.contains("/12.0ms"))
        assertTrue("the window's rasters are counted, not the total ($reported)", reported.contains("page=2"))
    }

    @Test
    fun `a reset starts the record again`() {
        val watch = FrameWatchdog()
        watch.painted(4.0, nowNanos = 0L)
        watch.painted(4.0, nowNanos = 10 * ms)
        watch.reset()
        assertNull("a fresh window says nothing yet", watch.painted(4.0, nowNanos = 10_000 * ms))
        assertEquals("and it counts frames from scratch", 1, watch.framesInWindow)
    }
}
