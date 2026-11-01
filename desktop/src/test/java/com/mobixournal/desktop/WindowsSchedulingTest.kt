package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The window's ask for a share of the machine (`WindowsScheduling`).
 *
 * Two things are worth pinning. First the **constants**: every one of them is a number copied out of a Win32
 * header, and a wrong one does not fail — it silently sets a different priority or a different throttle bit,
 * which is the kind of bug that looks like "the fix did nothing". Second, that the calls **land**: the suite
 * drives `prepare()` for real and checks the report, so a misdeclared JNA signature (a wrong parameter type,
 * a missing `W` suffix) shows up as a failure here instead of as a silent `runCatching` on the owner's PC.
 */
class WindowsSchedulingTest {

    @Test
    fun `the win32 constants are the ones the header states`() {
        assertEquals("ABOVE_NORMAL_PRIORITY_CLASS", 0x0000_8000, WindowsScheduling.ABOVE_NORMAL_PRIORITY_CLASS)
        assertEquals("THREAD_PRIORITY_HIGHEST", 2, WindowsScheduling.THREAD_PRIORITY_HIGHEST)
        assertEquals("ProcessPowerThrottling", 4, WindowsScheduling.PROCESS_POWER_THROTTLING)
        assertEquals("PROCESS_POWER_THROTTLING_CURRENT_VERSION", 1, WindowsScheduling.POWER_THROTTLING_VERSION)
        assertEquals("PROCESS_POWER_THROTTLING_EXECUTION_SPEED", 0x1, WindowsScheduling.POWER_THROTTLING_EXECUTION_SPEED)
        assertEquals("TIMERR_NOERROR", 0, WindowsScheduling.TIMERR_NOERROR)
        assertEquals("the MMCSS task a paint thread joins", "Audio", WindowsScheduling.MMCSS_TASK)
    }

    @Test
    fun `every lever lands, on the machine that runs this`() {
        assumeTrue("the scheduling calls are Windows'", WindowsScheduling.isWindows)
        val report = WindowsScheduling.prepare()
        assertNotNull("the first ask answers with what it did", report)
        assertTrue("timeBeginPeriod(1)", report!!.timerResolution)
        assertTrue("SetPriorityClass(ABOVE_NORMAL)", report.priorityClass)
        assertTrue("EcoQoS cleared", report.powerThrottling)

        // The event thread raises itself once the toolkit dispatches: draining the queue proves it ran.
        java.awt.EventQueue.invokeAndWait { }
        assertEquals("the event thread's own boost", true, WindowsScheduling.eventThreadBoosted)
    }
}
