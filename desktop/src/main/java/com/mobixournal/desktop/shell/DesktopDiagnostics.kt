package com.mobixournal.desktop.shell
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.sheet.number

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
 *    must not report that minute as a stall, so gaps longer than [IDLE_GAP_NANOS] are not counted;
 *  - **`sheet=` and `page=`** are the sheet picture's own full-record count and the identity of the page
 *    the painter was handed: a frame that repaints while the sheet stops recording, and keeps the same
 *    page object, is the shape of "the ink did not follow the drag" — which is how the tail that never
 *    noticed a changed element was found, live, without a debugger attached.
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

    /**
     * What the **hand** did, which is the one input the window cannot check by looking at the page: the
     * numbers say whether a press was even offered to the pan, which path moved the sheet, and how much.
     *
     * A hand that does nothing has three shapes and they look identical on screen: no press reached the
     * pan (`press=0`), the press arrived but the packets' ownership stood every event down (`skip=` climbing
     * with `compose=0`), or the writes happened and the offset never moved — which is a clamp, not an input.
     */
    private var panPresses = 0
    private var panCompose = 0
    private var panPackets = 0
    private var panSkipped = 0
    private var panDy = 0f
    private var panSource: Boolean? = null

    /** A press the canvas offered to the hand's pan — the gesture was recognised at all. */
    fun panPress() {
        if (enabled) panPresses++
    }

    /** One applied Compose-drag event, in dp. */
    fun panCompose(dy: Float) {
        if (!enabled) return
        panCompose++
        panDy = dy
    }

    /** One applied batch of the tablet's own packets, in dp. */
    fun panPackets(dy: Float) {
        if (!enabled) return
        panPackets++
        panDy = dy
    }

    /** An event the packets' ownership stood down, so one contact is never panned twice. */
    fun panSkipped() {
        if (enabled) panSkipped++
    }

    /** Whether this host offers a packet stream at all, and so whether the hand is the packets' or Compose's. */
    fun panSource(packets: Boolean) {
        if (enabled) panSource = packets
    }

    /** Say out loud that the record is on, so a run with no paints is not read as "the tool is off". */
    fun start() {
        if (enabled) write("watching: one line per second of painting")
    }

    /**
     * A draft whose painted geometry disagrees with a **full rebuild** of the same samples.
     *
     * Written the moment it happens rather than folded into the per-second line, because the defect it
     * watches for lasts one frame: "the stroke warps by itself for an instant and comes back" is not a
     * stutter and not a missing paint, and its numbers — how far, at how many points — are what says
     * whether the geometry or the transform was wrong.
     */
    fun draftMismatch(detail: String) {
        if (enabled) write("draft-mismatch: $detail")
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
    fun painted(paintMs: Double, sheetRecords: Int = -1, pageId: Int = 0) {
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
            append(" sheet=").append(sheetRecords)
            append(" page=").append(Integer.toHexString(pageId))
            append(" paint=").append(frameThread ?: "-")
            if (panPresses + panCompose + panPackets + panSkipped > 0) {
                append(" pan=press:").append(panPresses)
                append("/compose:").append(panCompose)
                append("/packets:").append(panPackets)
                append("/skip:").append(panSkipped)
                append(" dy=").append(String.format(java.util.Locale.ROOT, "%.1f", panDy))
            }
            panSource?.let { append(" hand=").append(if (it) "packets" else "compose") }
        }
        write(line)
        frames = 0
        worst = 0L
        paintTotalMs = 0.0
        paintWorstMs = 0.0
        panPresses = 0
        panCompose = 0
        panPackets = 0
        panSkipped = 0
        panDy = 0f
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
