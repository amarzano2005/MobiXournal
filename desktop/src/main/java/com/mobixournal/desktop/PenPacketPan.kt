package com.mobixournal.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.channels.Channel

/**
 * The **hand**, driven by the tablet's own packets.
 *
 * Windows hands this pen to the window as a *coalesced mouse copy* (`docs/desktop-workstation.md`: this
 * tablet sends no `WM_POINTER` at all), so a pan built on Compose's pointer moved the sheet at the rate
 * the mouse emulation bothered to report — the hand crawled while the stylus moved normally, and the
 * gap was visible as a page that arrived a beat late. The driver's packets carry the position 400 times
 * a second, so the pan is taken from those instead.
 *
 * It is **not a second input path**: the packets are the same contact the mouse message describes, and
 * the Compose pan therefore stands aside while they own the drag (`PageCanvasInput.panByDrag`). The
 * mapping is the canvas's own — a pixel delta divided by the density is the dp the viewport moves by —
 * so a HiDPI screen pans at the same speed as a 1× one.
 *
 * Threading is [PenPacketStroke]'s, for the same reason: [packet] runs on the message thread and does
 * nothing but append to a lock-free queue; everything that writes state happens in [flush], on the frame
 * thread, woken once per batch by a conflated channel. No monitor is taken on either side, so the
 * driver's thread never waits on a frame and a frame never waits on the driver.
 */
internal class PenPacketPan(
    private val viewport: () -> Viewport,
    private val pageW: Float,
    private val pageH: Float,
    private val viewW: Float,
    private val viewH: Float,
    private val density: Float,
    private val onViewport: (Viewport) -> Unit,
    /** Called when this path takes the contact and when it lets it go. */
    private val onOwnership: (Boolean) -> Unit = {},
) {

    private val inbox = java.util.concurrent.ConcurrentLinkedQueue<PenPacket>()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Where the contact was last seen, in the window's client pixels. Frame-thread only. */
    private var lastX = 0f
    private var lastY = 0f

    /** True between the tip's first packet and its lift. Frame-thread only. */
    var dragging: Boolean = false
        private set

    /** One packet, from the driver's thread: no state, no lock, no viewport. */
    fun packet(packet: PenPacket) {
        inbox.add(packet)
        wake.trySend(Unit)
        DesktopDiagnostics.packet()
    }

    /** Suspend until the driver has something newer — what asks for the next frame. */
    suspend fun awaitPacket() {
        wake.receive()
    }

    /**
     * Everything the driver sent since the last frame, applied in its own order.
     *
     * The batch is **one** viewport write, not one per packet: a packet is a reading, not a frame, and 400
     * writes a second would recompose the window far more often than it can paint. The movement is the sum
     * of the batch's deltas, so the sheet still travels exactly as far as the pen did.
     */
    fun flush() {
        var dx = 0f
        var dy = 0f
        while (true) {
            val packet = inbox.poll() ?: break
            val moved = apply(packet) ?: continue
            dx += moved.first
            dy += moved.second
        }
        // Applied even when the batch ended the drag: a lift is not a reason to throw away the travel
        // that arrived with it, and the last frame of a pan is the one the eye follows.
        if (dx == 0f && dy == 0f) return
        onViewport(viewport().pannedBy(dx, dy, pageW, pageH, viewW, viewH))
    }

    /**
     * One packet's effect on the pan: the dp the sheet moves by, or null when it moved nothing.
     *
     * A tip-down opens the pan and only records where it landed (a press with no movement is a click, not
     * a scroll); every later tip-down packet reports the distance the contact travelled; the lift ends the
     * drag and hands the contact back.
     */
    private fun apply(packet: PenPacket): Pair<Float, Float>? {
        if (!packet.tip) {
            // The lift carries a position too, and the reach travelled just before it belongs to this
            // pan: dropping it would end every pan a few points short of where the hand stopped.
            val moved = if (dragging) deltaTo(packet) else null
            if (dragging) release()
            lastX = packet.x
            lastY = packet.y
            return moved
        }
        if (!dragging) {
            dragging = true
            onOwnership(true)
            lastX = packet.x
            lastY = packet.y
            return null
        }
        val moved = deltaTo(packet)
        lastX = packet.x
        lastY = packet.y
        return moved
    }

    /** The dp the sheet moves by for one reading, or null when the contact did not travel. */
    private fun deltaTo(packet: PenPacket): Pair<Float, Float>? {
        val dx = packet.x - lastX
        val dy = packet.y - lastY
        if (dx == 0f && dy == 0f) return null
        // A stylus pans **further than it travels**: a pen is moved by the wrist across a tablet's own
        // working area, not by the arm across a desk, so a 1:1 pan reads as a page that crawls under a
        // hand which is plainly moving. [PAN_GAIN] is the same feel rule as the tablet's, not a
        // measurement, and it is applied to the pen's packets only — a mouse drag and a wheel keep the
        // distance their device reports (`panByDrag`).
        return (dx / density * PAN_GAIN) to (dy / density * PAN_GAIN)
    }

    /** End the pan where it is — the canvas's ticker also calls this when the packets go quiet. */
    fun release() {
        if (!dragging) return
        dragging = false
        onOwnership(false)
    }
}

/**
 * How much further the page travels than the pen does, on the pen's own packets.
 *
 * A stylus is held like a pen: the hand moves it a few centimetres while the page has to cross a window
 * that may be forty wide, so matched 1:1 the pan reads as broken rather than precise. 1.6 is the point
 * where a wrist-flick crosses a third of a 21:9 sheet; it is a feel constant — the only one in this file
 * — and it is applied to the pen only, never to a mouse drag or a wheel notch.
 */
internal const val PAN_GAIN = 1.6f

/**
 * The hand's packet pan, composed while the **Hand** slot is picked (or Space is held, which is the same
 * pan) on a host that has a packet stream.
 *
 * Nothing is drawn from it: the page itself moves, so a frame is asked for by a packet and the canvas
 * repaints the sheet at the new offset. A tool that inks is not composed here at all — its strokes are
 * [PenPacketStrokes]'s — so one contact is never claimed twice.
 */
@Composable
internal fun PenPacketPan(
    viewport: Viewport,
    pageW: Float,
    pageH: Float,
    viewW: Float,
    viewH: Float,
    density: Float,
    onViewport: (Viewport) -> Unit,
    /** The canvas's shared ownership flag: what stands the Compose pan aside. */
    owns: MutableState<Boolean>,
    packets: PenPacketSource?,
) {
    val liveViewport by rememberUpdatedState(viewport)
    val pan = remember(density, pageW, pageH, viewW, viewH) {
        PenPacketPan(
            viewport = { liveViewport },
            pageW = pageW,
            pageH = pageH,
            viewW = viewW,
            viewH = viewH,
            density = density,
            onViewport = onViewport,
            onOwnership = { owns.value = it },
        )
    }
    DisposableEffect(packets, pan) {
        val stop = packets?.listen(pan::packet)
        // Leaving the hand (the next tool, another page) must hand the contact back: a stuck flag would
        // make the Compose pan of the *next* gesture do nothing at all.
        onDispose {
            stop?.invoke()
            pan.release()
            owns.value = false
        }
    }
    LaunchedEffect(pan) {
        while (true) {
            pan.awaitPacket()
            pan.flush()
        }
    }
}
