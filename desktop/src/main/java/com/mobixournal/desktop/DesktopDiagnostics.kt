package com.mobixournal.desktop

import java.io.File

/**
 * The window's own watch: how fast frames are coming, how long the worst one took, and what the packet
 * path was doing meanwhile.
 *
 * A freeze cannot be argued with from the outside — "it stutters" and "the thread is blocked" look the
 * same on screen — so the window keeps a one-line-per-second record when it is asked to
 * (`-Dmobix.diagnose=true`, or `--diagnose`, writing `mobix-diagnose.log` beside the working directory).
 * The line carries exactly the three numbers that tell the cases apart:
 *
 *  - **paints/s and the worst interval** — a paint that got slow shows up as a long interval, and a
 *    window that repaints when *nothing is happening* shows up as a line with no input at all (which is
 *    how the perpetual `withFrameNanos` loop was found: ~120 paints a second on an idle sheet);
 *  - **the worst interval** is only a *paint* gap: a window that was idle for a minute and then painted
 *    must not report that minute as a stall, so gaps longer than [IDLE_GAP_NANOS] are not counted.
 *
 * Off by default: a file per second is not something an editor should leave behind.
 */
internal object DesktopDiagnostics {

    /** Longer than any frame, short enough that a real stall is still reported. */
    private const val IDLE_GAP_NANOS = 2_000_000_000L

    /** Whether the window was asked to keep the record. */
    val enabled: Boolean = System.getProperty("mobix.diagnose") == "true"

    private var out: java.io.BufferedWriter? = null
    private var frames = 0
    private var lastFrame = 0L
    private var worst = 0L
    private var windowStart = System.nanoTime()

    /** The paint's own cost inside the frame — the number the 6.9 ms budget is about. */
    private var paintTotalMs = 0.0
    private var paintWorstMs = 0.0

    /** True once, so a run that writes nothing is distinguishable from a run that never started. */
    private var opened = false

    /** The thread each side runs on, recorded once: the hand-off is the thing being checked. */
    private var packetThread: String? = null
    private var frameThread: String? = null

    /** Say out loud that the record is on, so a run with no paints is not read as "the tool is off". */
    fun start() {
        if (enabled) write("watching: one line per second of painting")
    }

    fun packet() {
        if (!enabled) return
        if (packetThread == null) packetThread = Thread.currentThread().name
    }

    /**
     * One frame, with what the packet path had queued when it began.
     *
     * Called from the canvas's own ticker — the same loop that empties the queue — so the numbers belong
     * to the frame the reader actually sees.
     */
    fun painted(paintMs: Double) {
        if (!enabled) return
        if (frameThread == null) frameThread = Thread.currentThread().name
        val now = System.nanoTime()
        if (lastFrame != 0L) {
            val interval = now - lastFrame
            // An idle window is not a stall: the pen resting, or the reader looking away, leaves gaps that
            // say nothing about how fast a frame is. Only gaps a *running* loop could have produced count.
            if (interval in 1..IDLE_GAP_NANOS && interval > worst) worst = interval
        }
        lastFrame = now
        frames++
        paintTotalMs += paintMs
        if (paintMs > paintWorstMs) paintWorstMs = paintMs
        val span = now - windowStart
        if (span < 1_000_000_000L) return
        val line = buildString {
            append("paints/s=").append(frames * 1_000_000_000L / span)
            append(" worst=").append(worst / 1_000_000L).append("ms")
            append(" paint=").append(String.format(java.util.Locale.ROOT, "%.1f", paintTotalMs / frames))
            append("/").append(String.format(java.util.Locale.ROOT, "%.1f", paintWorstMs)).append("ms")
            append(" packet=").append(packetThread ?: "-")
            append(" paint=").append(frameThread ?: "-")
        }
        write(line)
        frames = 0
        worst = 0L
        paintTotalMs = 0.0
        paintWorstMs = 0.0
        windowStart = now
    }

    /** The raster's counters, so a rebuild per frame is visible in the same record. */
    private fun write(line: String) {
        val sink = out ?: runCatching {
            File(System.getProperty("user.dir"), "mobix-diagnose.log").bufferedWriter().also { out = it }
        }.getOrNull() ?: return
        runCatching {
            if (!opened) {
                sink.write("# MobiXournal desktop diagnostics: one line per second\n")
                opened = true
            }
            sink.write(line + "\n")
            sink.flush()
        }
    }
}
