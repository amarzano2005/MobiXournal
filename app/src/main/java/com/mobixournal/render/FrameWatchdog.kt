package com.mobixournal.render

import java.util.Locale

/**
 * The canvas's own watch: how fast frames are coming, how long the worst one took, and how much of it was
 * the paint itself.
 *
 * The window has had this since the perpetual-ticker bug (`DesktopDiagnostics`), and the tablet needed the
 * same three numbers for the same reason: "it stutters" and "the surface is doing twice the work" look
 * identical from the outside. One line per second of *painting*, appended to the canvas's pen-diagnostics
 * panel when that panel is on — no new UI, and the record appears exactly where a stylus problem is already
 * being read.
 *
 * What the line separates:
 *
 *  - **frames/s** — a canvas that paints when nothing is happening shows a line with no input at all, which
 *    is what `DrawingSurfaceView`'s `paintPosted` guard and the Choreographer pacing exist to prevent;
 *  - **worst** — the longest *frame* gap, ignoring a gap longer than [IDLE_GAP_NANOS]: a reader who looked
 *    away for a minute did not drop a minute's worth of frames. Without that rule the number is meaningless
 *    the moment the device is left alone;
 *  - **paint** — the average and worst time spent inside one paint, which is the part the 16.7 ms budget at
 *    60 Hz (or 8.3 ms at 120 Hz) is actually about, as opposed to the wait for the next vsync;
 *  - **page=** — how many full page rasters the ink cache produced in the window: a stroke that rebuilds the
 *    page every frame is visible as a number that climbs with the frames instead of staying still.
 *
 * Android-free on purpose — plain longs and doubles — so the arithmetic is unit-testable on the JVM with a
 * fake clock (`FrameWatchdogTest`) instead of on a device frame by frame.
 */
internal class FrameWatchdog(
    /** A gap longer than this is idleness, not a stall. */
    private val idleGapNanos: Long = IDLE_GAP_NANOS,
    /** How long a window of frames is before a line is produced. */
    private val windowNanos: Long = WINDOW_NANOS,
) {

    private var frames = 0
    private var lastFrame = 0L
    private var worstInterval = 0L
    private var paintTotalMs = 0.0
    private var paintWorstMs = 0.0
    private var pagesRastered = 0
    private var windowStart = 0L

    /**
     * Whether the window has a start — a flag, not `windowStart != 0L`, because zero is a legitimate reading
     * of an injected clock and a sentinel that agrees with it silently loses the window's first frame.
     */
    private var started = false

    /** Frames counted since the last line — what a test asserts on. */
    val framesInWindow: Int get() = frames

    /** The longest frame gap of the window so far, in nanoseconds. */
    val worstInWindowNanos: Long get() = worstInterval

    /**
     * Record one painted frame and, when the window closes, return the line describing it.
     *
     * [paintMs] is the paint's own cost (the work between locking and posting the buffer), [pagesRastered]
     * is the ink cache's full-raster count at the time of this frame — read as a running total, so the line
     * reports the difference. [nowNanos] is injectable so a test can drive a second of frames without
     * sleeping.
     */
    fun painted(paintMs: Double, pagesRastered: Int = 0, nowNanos: Long = System.nanoTime()): String? {
        if (!started) {
            started = true
            windowStart = nowNanos
            // The window's baseline is the first frame it sees, so `page=` counts this window's rasters and
            // not every raster since the file was opened.
            this.pagesRastered = pagesRastered
        }
        if (lastFrame != 0L) {
            val interval = nowNanos - lastFrame
            // An idle canvas is not a stall: only gaps a running loop could have produced count.
            if (interval in 1..idleGapNanos && interval > worstInterval) worstInterval = interval
        }
        lastFrame = nowNanos
        frames++
        paintTotalMs += paintMs
        if (paintMs > paintWorstMs) paintWorstMs = paintMs
        val span = nowNanos - windowStart
        if (span < windowNanos) return null
        val line = String.format(
            Locale.ROOT,
            "frames/s=%d worst=%dms paint=%.1f/%.1fms page=%d",
            frames * 1_000_000_000L / span,
            worstInterval / 1_000_000L,
            paintTotalMs / frames,
            paintWorstMs,
            (pagesRastered - this.pagesRastered).coerceAtLeast(0),
        )
        frames = 0
        worstInterval = 0L
        paintTotalMs = 0.0
        paintWorstMs = 0.0
        this.pagesRastered = pagesRastered
        windowStart = nowNanos
        return line
    }

    /** Forget the window — the canvas detaching, or the panel being cleared. */
    fun reset() {
        frames = 0
        lastFrame = 0L
        worstInterval = 0L
        paintTotalMs = 0.0
        paintWorstMs = 0.0
        pagesRastered = 0
        windowStart = 0L
        started = false
    }

    companion object {
        /** Longer than any frame, short enough that a real stall is still reported. */
        const val IDLE_GAP_NANOS = 2_000_000_000L

        /** One line per second of painting. */
        const val WINDOW_NANOS = 1_000_000_000L
    }
}
