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
 */
internal class PenPacketStroke(
    private val gesture: PageInkGesture,
    /** The canvas's top-left corner in the window's client pixels. */
    private val origin: () -> Offset,
    /** The canvas's size in pixels — a packet outside it is not a stroke on this page. */
    private val size: () -> Size,
    /** Called when this path takes the pen and when it lets it go, so the mouse path stands aside. */
    private val onOwnership: (Boolean) -> Unit = {},
) {

    /** True between the tip's first packet and its lift. */
    var drawing: Boolean = false
        private set

    private var lastPacketNanos = 0L

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
        val raw = Offset(packet.x - origin().x, packet.y - origin().y)
        val local = smoothedAt?.let { previous ->
            Offset(
                previous.x + (raw.x - previous.x) * POSITION_SMOOTHING,
                previous.y + (raw.y - previous.y) * POSITION_SMOOTHING,
            )
        } ?: raw
        val inside = local.x >= 0f && local.y >= 0f && local.x <= size().width && local.y <= size().height
        if (!packet.tip) {
            if (drawing) {
                gesture.up(pointer(packet, local))
                release()
            }
            return
        }
        if (drawing && packet.atNanos - lastPacketNanos >= STALE_NANOS) finishStroke()
        if (!drawing) {
            if (!inside) return
            drawing = true
            // The first sample of a stroke is the hand's own reading, not a filtered one.
            smoothedAt = raw
            onOwnership(true)
            gesture.down(pointer(packet, raw))
        } else {
            smoothedAt = local
            gesture.move(pointer(packet, local))
        }
        lastPacketNanos = packet.atNanos
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
        finishStroke()
    }

    private fun finishStroke() {
        gesture.finish()
        release()
    }

    private fun release() {
        drawing = false
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
        // No pressure axis means the tool's width, the same rule the mouse follows.
        pressure = smooth(packet.pressure ?: 1f),
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
    }
}
