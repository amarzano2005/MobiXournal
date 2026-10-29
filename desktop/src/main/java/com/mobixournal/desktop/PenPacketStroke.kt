package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.mobixournal.render.InkPointer
import com.mobixournal.render.InkPointerPhase
import com.mobixournal.render.PointerKind

/**
 * A stroke drawn from the **tablet's own packets**, the way Xournal++ draws one.
 *
 * The measurement in `docs/desktop-workstation.md` is why this exists: Windows sends this pen no
 * `WM_POINTER`, and its mouse emulation is a *copy* of the contact — so a stroke that waits for a mouse
 * press can be dropped when the touch is light, and its samples arrive at the pointer device's rate. The
 * driver's packets carry the tip, the position and the pressure 400 times a second, and a stroke that
 * starts from those cannot lose a contact the tablet itself registered.
 *
 * The split of responsibility is unchanged, which is the point: this only turns a packet into an
 * [InkPointer] and hands it to the shared [PageInkGesture], so `InkPointerRouter` still decides what a
 * press means, `PressureCurve` still sizes the width, the eraser end still rubs out, and the lift still
 * commits one edit through the page window.
 *
 * A packet is **client pixels**, relative to the window; [origin] is where this canvas starts, so the
 * same arithmetic the Compose path uses (`pagePointOf` inside the gesture) applies unchanged.
 *
 * **Both ends of the stroke are the packets'.** The tip-down opens it and the lift closes it, so a light
 * touch the mouse emulation never reported is still a stroke, and one the emulation reported twice is
 * still one. The Compose pointer keeps a draft of its own (`PageCanvas`), which is what makes that true:
 * a mouse gesture cancelled when the packets take over drops *its* samples, never this stroke's.
 *
 * **Two threads, and only one of them touches the gesture.** A packet is delivered on the thread that
 * dispatches the window's messages (the window procedure `TabletWintab` drains its queue from); [flush]
 * runs on the frame ticker. [packet] therefore does **one thing**: it appends to a
 * `ConcurrentLinkedQueue`, which needs no monitor of its own. Everything else — the smoothing, the
 * router, the draft, the commit — happens inside [flush], on the frame thread, where Compose's snapshot
 * state belongs.
 *
 * Both alternatives were tried on the running window and both were wrong. Handing every packet straight
 * to the gesture from the driver's thread is what produced the `null` samples (a `kotlin.collections.ArrayDeque`
 * drained on another thread returns `null`, and the null reached `PageInkGesture.move(pointer)` as an
 * error dialog). Wrapping the entry points in a monitor was worse: the driver's thread took the lock and
 * then wrote snapshot state, while the frame thread waited for the lock *inside* a frame that already held
 * the snapshot's — a lock inversion that froze the window the moment a stroke began. A queue has neither
 * problem: the driver's thread never waits, and the gesture still sees a whole packet or none of it.
 *
 * The cost is at most one frame of latency for the tip-down, which is the same frame on which the samples
 * after it are painted, so nothing is visible: a queued tip-down opens the stroke and its queued
 * followers extend it in the same pass.
 */
internal class PenPacketStroke(
    private val gesture: PageInkGesture,
    /** The canvas's top-left corner in the window's client pixels. */
    private val origin: () -> Offset,
    /** The canvas's size in pixels — a packet outside it is not a stroke on this page. */
    private val size: () -> Size,
    /** Called when this path takes the pen and when it lets it go, so the mouse path stands aside. */
    private val onOwnership: (Boolean) -> Unit = {},
    /**
     * Where the pen is, in this canvas's pixels, for the ring the window paints — once per frame, with
     * the newest reading, or null once the driver has been quiet long enough to hand the pointer back.
     *
     * A ring is ink like any other: painted by this window, it trails the nib by a frame unless it is
     * moved from the driver's 400 readings instead of from the mouse's copy of them.
     */
    private val onHover: (Offset?) -> Unit = {},
) {

    /** True between the tip's first packet and its lift. Written only by [flush]'s thread. */
    var drawing: Boolean = false
        private set

    private var lastPacketNanos = 0L

    /** The newest reading's position, tip or hover, and when it arrived — the ring's source. */
    private var newestAt: Offset? = null
    private var newestAtNanos = 0L
    private var publishedAt: Offset? = null

    /**
     * The packets that have arrived since the last frame, in the driver's own order.
     *
     * Written only by [packet] (the driver's thread) and drained only by [flush] (the frame thread), so
     * it must be a queue that tolerates exactly that: `ConcurrentLinkedQueue` is lock-free for the
     * producer, which is what keeps the driver's thread from ever waiting on the window.
     *
     * The driver sends 400 packets a second while the window repaints at the screen's rate; feeding every
     * packet straight to the gesture made 400 snapshot writes and 400 invalidations for one frame's worth
     * of ink. They are handed over once per frame — every filtered sample in order, with its own width —
     * so the drawn line is identical while the canvas repaints once.
     */
    private val inbox = java.util.concurrent.ConcurrentLinkedQueue<PenPacket>()

    /** One token per batch of packets — the frame request, coalesced. */
    private val wake = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)

    /**
     * The samples of the stroke in progress that the gesture has not been told about yet.
     *
     * Frame-thread only, like everything else the gesture touches: it is the hand-off between [flush]'s
     * filtering of one frame's packets and the single `move` per sample the gesture expects.
     */
    private val pending = ArrayDeque<InkPointer>()

    /**
     * The point the stroke is being drawn at, low-passed.
     *
     * The system context maps the pen onto **whole screen pixels**, so a slow stroke at 400 packets a
     * second repeats a position for two or three samples and then jumps one — a staircase the eye reads
     * as a trembling line. A one-pole filter over the packets turns those steps into a smooth path, and
     * because it lags by less than a packet it is invisible in use. Reset at the start of every stroke so
     * a new line never inherits the last one's position.
     */
    private var smoothedAt: Offset? = null

    /**
     * The pressure the stroke is being drawn at, low-passed.
     *
     * The driver reports 14 bits 400 times a second, and the raw reading wobbles by a few hundred counts
     * between neighbouring packets — which is a visible zigzag in the width, not detail. [SMOOTHING] is
     * the same one-pole filter `StrokeSmoother` applies on the tablet, so both shells store a width that
     * moves with the hand rather than with the sensor.
     */
    private var smoothedPressure: Float? = null

    /**
     * One packet.
     *
     * A tip-down on the sheet starts the stroke, every tip-down packet is a sample (the router itself
     * decides which ones cross the tap slop), and a tip-up ends it. A tip-down that follows a silence
     * longer than [STALE_MS] is a **new** stroke: the lift of the previous one never arrived, so that one
     * is finished where it stopped rather than continued by a pen that has been lifted and put back down.
     *
     * The driver reports *changes*, so a pen held still on the glass sends nothing at all: silence is not
     * evidence that the lift came, which is why [STALE_MS] is generous and why a silence **finishes** the
     * stroke instead of throwing the hand's ink away.
     */
    fun packet(packet: PenPacket) {
        // The driver's thread does nothing but this: no state, no lock, no gesture. Anything more here
        // runs on the message thread while a frame is being painted.
        inbox.add(packet)
        // Wake the frame once per batch: a conflated channel keeps a single token however many packets
        // arrive before the canvas gets to it, so 400 packets a second still cost at most one frame each.
        wake.trySend(Unit)
        DesktopDiagnostics.packet()
    }

    /**
     * Suspend until the driver has something newer to paint.
     *
     * This is what keeps the window from rendering at the display's full rate for ever: a frame is asked
     * for when a packet arrives, not on a loop (`DesktopDiagnostics` caught the loop at ~120 fps on an
     * idle window, which is what starved the message thread and read as the window seizing up).
     */
    suspend fun awaitPacket() {
        wake.receive()
    }

    /** Packets waiting for the next frame — the watchdog's "is the frame loop falling behind" number. */
    fun backlog(): Int = inbox.size

    /**
     * One packet, applied on the frame thread — the state machine that was [packet]'s body.
     *
     * Everything below is single-threaded by construction: [flush] is its only caller, and [flush] is
     * called by the canvas's ticker.
     */
    private fun ingest(packet: PenPacket) {
        val raw = Offset(packet.x - origin().x, packet.y - origin().y)
        val local = smoothedAt?.let { previous ->
            Offset(
                previous.x + (raw.x - previous.x) * POSITION_SMOOTHING,
                previous.y + (raw.y - previous.y) * POSITION_SMOOTHING,
            )
        } ?: raw
        val inside = local.x >= 0f && local.y >= 0f && local.x <= size().width && local.y <= size().height
        // Every reading moves the ring, tip or not: a hovering pen is exactly what a ring is for. The raw
        // position is used — a ring is tens of pixels wide, so the whole-pixel staircase is not visible
        // on it, and the stroke's own smoothing must not be disturbed by a pass of hovering.
        newestAtNanos = packet.atNanos
        newestAt = if (inside && packet.near) raw else null
        if (!packet.tip) {
            if (drawing) {
                // The lift ends the stroke, so the samples still waiting for a frame are part of it.
                drainPending()
                gesture.up(pointer(packet, local))
                release()
            }
            return
        }
        if (drawing && packet.atNanos - lastPacketNanos >= STALE_NANOS) {
            drainPending()
            finishStroke()
        }
        if (!drawing) {
            if (!inside) return
            drawing = true
            // The first sample of a stroke is the hand's own reading, not a filtered one.
            smoothedAt = raw
            onOwnership(true)
            gesture.down(pointer(packet, raw))
        } else {
            smoothedAt = local
            pending.addLast(pointer(packet, local))
        }
        lastPacketNanos = packet.atNanos
    }

    /**
     * Hand the samples buffered since the last frame to the gesture, in the order they arrived.
     *
     * Called once per frame by the canvas (`PageCanvas`'s ticker), and before a lift or a silence so a
     * stroke never loses its tail to the frame it ended on. A no-op between packets, and cheap enough
     * to call every frame.
     */
    fun flush() {
        // The only cross-thread read: whatever the driver appended since the last frame, in order.
        while (true) {
            val next = inbox.poll() ?: break
            ingest(next)
        }
        drainPending()
        // A packet that arrives after a pause is the first chance this path has to notice the pause.
        gesture.dwellCheck()
    }

    /** The stroke's samples buffered for this frame, in arrival order — one `move` each. */
    private fun drainPending() {
        while (pending.isNotEmpty()) gesture.move(pending.removeFirst())
    }

    /**
     * Move the ring to the newest reading — one call per frame, from the canvas's ticker.
     *
     * Publishing only when the position changed keeps an idle pen from repainting the window; publishing
     * `null` once the readings stop is what hands the pointer back to the mouse when the pen leaves the
     * tablet's proximity, instead of freezing the ring where it was last seen.
     */
    fun publishHover() {
        // No timeout: the driver reports *changes*, so a pen held still above the glass sends nothing and
        // a ring that vanished under a resting hand would be worse than one that outlives it. The driver
        // says when the pen has left the tablet's proximity ([PenPacket.near]); until then the reading
        // stands. Handing the pointer to the mouse is a **separate** deadline, kept by the canvas from
        // [newestAtNanos] — the ring staying put must not disable the mouse.
        if (newestAt == publishedAt) return
        publishedAt = newestAt
        onHover(newestAt)
    }

    /**
     * Finish a stroke whose packets stopped arriving, and report that the pen is free again.
     *
     * Called from the canvas's own ticker as well as on a tip-down that follows a silence: the absence of
     * a packet is the event, and only the canvas knows the difference between "the pen is resting" and "the
     * user lifted it while the driver said nothing". Either way the ink the hand drew is the user's, so the
     * stroke is **committed** where it stopped — a stroke that vanishes under a pause is the loss this path
     * exists to prevent.
     */
    fun finishIfStale(nowNanos: Long) {
        if (!drawing) return
        if (nowNanos - lastPacketNanos < STALE_NANOS) return
        drainPending()
        finishStroke()
    }

    private fun finishStroke() {
        pending.clear()
        gesture.finish()
        release()
    }

    private fun release() {
        drawing = false
        pending.clear()
        smoothedPressure = null
        smoothedAt = null
        onOwnership(false)
    }

    /** One step of the one-pole filter; the first sample of a stroke is taken as it is. */
    private fun smooth(raw: Float): Float {
        val previous = smoothedPressure
        val next = if (previous == null) raw else previous + (raw - previous) * PRESSURE_SMOOTHING
        smoothedPressure = next
        return next
    }

    /** The packet in the router's vocabulary, positioned by the canvas's own corner. */
    private fun pointer(packet: PenPacket, local: Offset): InkPointer = InkPointer(
        // One pen, one id: the router's palm rejection and its second-finger pan both key off this, and
        // the tablet's stream is a single contact by definition.
        id = PEN_ID,
        x = local.x,
        y = local.y,
        // No pressure axis means the tool's width, the same rule the mouse follows. Shaped after the
        // smoothing and before the model's pressure filter — the one place this path applies the curve.
        pressure = PressureFeel.factor(smooth(packet.pressure ?: 1f)),
        timeMs = packet.atNanos / 1_000_000L,
        tool = if (packet.eraser) PointerKind.ERASER_TIP else PointerKind.STYLUS,
        phase = if (packet.tip) InkPointerPhase.MOVE else InkPointerPhase.UP,
        tiltRad = packet.tiltRad,
        azimuthRad = packet.azimuthRad,
        buttons = InkPointer.PRIMARY,
    )

    companion object {
        /** The id the tablet's contact is reported under. */
        const val PEN_ID = 1

        /**
         * How long a stroke may go without a packet before it is treated as lifted.
         *
         * Generous on purpose: the driver reports *changes* (a still pen sends nothing) and the packets
         * are handled on the same thread that paints, so a heavy page can leave a gap of a second or
         * more. Two seconds is not a pause a hand can make while drawing; past it, the pen really is
         * gone and the stroke is finished — committed, not discarded.
         */
        const val STALE_MS = 2000L

        /** [STALE_MS] in nanoseconds, the unit a packet carries. */
        const val STALE_NANOS = STALE_MS * 1_000_000L

        /**
         * One-pole coefficient for the pressure: 400 packets a second smoothed to a hand's motion.
         *
         * The pressure is the noisier of the two readings, so it is filtered harder; the position is
         * filtered lightly, because a lag the eye can see is worse than the staircase it removes.
         */
        const val PRESSURE_SMOOTHING = 0.25f

        /** One-pole coefficient for the position: enough to erase the whole-pixel staircase. */
        const val POSITION_SMOOTHING = 0.6f

        /**
         * How long the driver's reading outranks the mouse after the last packet.
         *
         * A pen out of the tablet's proximity may send nothing at all, so the window has to fall back to
         * the mouse on silence; short enough that the hand-over is immediate, long enough that a gap
         * between two packets (a heavy frame) does not make the ring jump to the emulated position.
         */
        const val HOVER_STALE_NANOS = 150_000_000L

        /**
         * How often a stroke's silence is checked — a timer, not a frame.
         *
         * The check has to happen when nothing is being painted (that is exactly the case it looks for),
         * so it runs on `delay` and commits at most once; making it a frame would put the window back on
         * the perpetual-repaint loop this file exists to avoid.
         */
        const val STALE_CHECK_MS = 200L
    }
}
