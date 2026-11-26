package com.mobixournal.render

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewportReportThrottleTest {

    @Test
    fun `scroll and zoom updates are throttled during active gesture and flush immediately when idle`() {
        val throttle = ViewportReportThrottle(intervalMs = 48L)

        // First scroll emits immediately
        assertTrue(throttle.shouldEmitScroll(ScrollReport(10f, 1000f, 500f, 0f), nowMs = 100L, gesturing = true))
        // Identical snapshot never emits
        assertFalse(throttle.shouldEmitScroll(ScrollReport(10f, 1000f, 500f, 0f), nowMs = 200L, gesturing = false))
        // Within 48ms while gesturing is suppressed
        assertFalse(throttle.shouldEmitScroll(ScrollReport(20f, 1000f, 500f, 0f), nowMs = 130L, gesturing = true))
        // At/past 48ms while gesturing emits
        assertTrue(throttle.shouldEmitScroll(ScrollReport(30f, 1000f, 500f, 0f), nowMs = 148L, gesturing = true))
        // Settled update flushes immediately even inside interval
        assertTrue(throttle.shouldEmitScroll(ScrollReport(35f, 1000f, 500f, 0f), nowMs = 150L, gesturing = false))

        // Zoom throttling behaves symmetrically
        assertTrue(throttle.shouldEmitZoom(1.2f, nowMs = 100L, gesturing = true))
        assertFalse(throttle.shouldEmitZoom(1.2f, nowMs = 200L, gesturing = false))
        assertFalse(throttle.shouldEmitZoom(1.3f, nowMs = 135L, gesturing = true))
        assertTrue(throttle.shouldEmitZoom(1.4f, nowMs = 148L, gesturing = true))
        assertTrue(throttle.shouldEmitZoom(1.42f, nowMs = 150L, gesturing = false))
    }
}
