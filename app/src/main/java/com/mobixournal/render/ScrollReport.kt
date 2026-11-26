package com.mobixournal.render

/**
 * The throttling snapshot `DrawingSurfaceView.reportScroll` compares against and republishes: four
 * numbers instead of a Triple so a horizontal-only pan still reports (the member order is scrollY,
 * totalPx, viewportPx, scrollX). Consumers of [DrawingSurfaceView.onScrollChanged] get the same
 * order as the named fields here.
 */
internal data class ScrollReport(
    val scrollY: Float,
    val totalPx: Float,
    val viewportPx: Float,
    val scrollX: Float,
)

/**
 * Throttles Compose viewport notifications (`onScrollChanged`, `onZoomChanged`) to ~20 Hz while
 * a scroll, fling, or pinch is active, while emitting exact settled updates immediately once idle.
 */
internal class ViewportReportThrottle(private val intervalMs: Long = GESTURE_REPORT_INTERVAL_MS) {

    private var lastScroll = ScrollReport(-1f, -1f, -1f, -1f)
    private var lastScrollMs = Long.MIN_VALUE
    private var lastZoom = Float.NaN
    private var lastZoomMs = Long.MIN_VALUE

    fun shouldEmitScroll(report: ScrollReport, nowMs: Long, gesturing: Boolean): Boolean {
        if (report == lastScroll) return false
        if (gesturing && lastScrollMs != Long.MIN_VALUE && nowMs - lastScrollMs < intervalMs) return false
        lastScroll = report
        lastScrollMs = nowMs
        return true
    }

    fun shouldEmitZoom(zoom: Float, nowMs: Long, gesturing: Boolean): Boolean {
        if (zoom == lastZoom) return false
        if (gesturing && lastZoomMs != Long.MIN_VALUE && nowMs - lastZoomMs < intervalMs) return false
        lastZoom = zoom
        lastZoomMs = nowMs
        return true
    }

    companion object {
        const val GESTURE_REPORT_INTERVAL_MS = 48L
    }
}
