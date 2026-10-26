package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.awt.Window

/**
 * Which stream the window's ink is coming from. The status bar says it in as many words, because "the
 * pen draws at one width" and "the pen is a mouse as far as this window can tell" are different facts
 * and a reader is entitled to know which one they have (`desktop-tablet-absent-fallback`).
 */
internal enum class PenSource(val label: String) {
    /** AWT's pointer only: pressure is `1.0`, which draws at the tool's width. */
    MOUSE("mouse"),

    /** Windows Ink: the pen's own pressure, tilt and twist, folded into the pointer events. */
    INK("Windows Ink"),

    /** Wintab: the driver's own packet stream — pressure on this machine (`TabletWintab`). */
    WINTAB("Wintab"),
}

/**
 * The window's device readers, opened once and closed with it.
 *
 * It is **one seam, not a second input path**: the canvas keeps taking Compose's pointer events, and what
 * this adds is
 *  - the pen's reading for the event being handled ([pen], a [PenContactStream]), and
 *  - the dial, from whichever of the two routes Windows offers it on: the radial controller's
 *    `RotationChanged` ([onRotation]) or the raw wheel report of the tablet's own HID device
 *    ([TabletWheel], which is the route this machine actually uses).
 *
 * The pen stream is **whichever one this host has**. Wintab is asked first, because it is the stream that
 * carries pressure here at the tablet's own packet rate; Windows Ink is the fallback for a driver that
 * reports the pointer stream instead; and with neither the canvas keeps the Compose pointer, which the
 * status bar calls `pen: mouse`.
 *
 * A sample is therefore never delivered twice: both readers decorate the pointer event the same physical
 * contact produced instead of starting a second stroke, and a dial notch stays the wheel event Compose
 * already received — [dialWheelRecently] only says *whose* wheel it was.
 */
internal class DesktopTablet {

    /** Windows Ink's reader — also the window procedure Wintab's packets arrive through. */
    var ink: TabletInk? = null
        private set

    /** Wintab's reader, when the driver offers one. */
    var wintab: TabletWintab? = null
        private set

    /** The tablet's wheel, or null when raw input is unavailable. */
    var wheel: TabletWheel? = null
        private set

    private var dialReader: TabletDial? = null

    /** True when a radial controller is bound to this window (a dial itself may or may not be present). */
    var dial by mutableStateOf(false)
        private set

    /** What the ink is coming from — the status bar's one word for it. */
    var source by mutableStateOf(PenSource.MOUSE)
        private set

    /** The pen's reading, from whichever stream this host has. */
    val pen: PenContactStream? get() = wintab ?: ink

    /** `WT_PACKET` messages seen, for the diagnostics log. */
    @Volatile
    private var packets = 0L

    /** True when the notch Compose just received was the tablet's dial, not the mouse's wheel. */
    fun dialWheelRecently(): Boolean = wheel?.recentDial() == true

    /**
     * Open the readers on [window].
     *
     * [onRotation] is a radial controller's delta in degrees, [onClick] the dial's own button. Both are
     * called from whichever thread Windows raises them on — a caller with state to touch marshals to its
     * UI thread (`DesktopApp` posts them). [onSample] and [onEvent] are the diagnostics probe's observers
     * and are null in the editor.
     *
     * [prefer] is Settings' pen-stream answer (`PenStream`). `AUTOMATIC` is the order this class has always
     * tried — Wintab, whose packets carry the pressure at the tablet's own rate, then Windows Ink. `INK`
     * skips the driver library entirely, for a pen whose Wintab reports a contact Windows Ink reports
     * better; the window procedure is still opened either way, because it is where the wheel arrives.
     */
    fun open(
        window: Window,
        onRotation: (Double) -> Unit,
        onClick: () -> Unit,
        log: (String) -> Unit,
        onSample: ((PenContact) -> Unit)? = null,
        onEvent: ((String) -> Unit)? = null,
        prefer: PenStream = PenStream.DEFAULT,
    ) {
        // The wheel first: the ink reader's window procedure is what feeds it a raw report.
        wheel = TabletWheel.open(window, log, onNotches = {}, onEvent = onEvent)
        // Then the window procedure, which carries both the Ink samples and Wintab's packets.
        ink = TabletInk.open(
            window = window,
            log = log,
            onSample = onSample,
            onEvent = onEvent,
            onRawInput = { lParam -> wheel?.handle(lParam) },
            onWindowMessage = { message, wParam, lParam ->
                // The message is the driver's own "packets are waiting": read the queue rather than
                // trusting either argument (the probe measured 400 of these a second and no usable
                // serial in them). Logged once in a while, so a probe's CSV stays readable.
                if (message == WintabApi.WT_PACKET) {
                    packets++
                    if (packets == 1L || packets % 500L == 0L) {
                        onEvent?.invoke("wintab message #$packets w=$wParam l=$lParam")
                    }
                    wintab?.drain()
                }
            },
        )
        // And the packet stream, which is the one that has pressure on this machine — unless Settings
        // asked for Windows Ink, which is then the only stream the pen is read from.
        wintab = if (prefer == PenStream.INK) {
            null
        } else {
            TabletWintab.open(window, log, onSample = onSample, onEvent = onEvent)
        }
        source = when {
            wintab != null -> PenSource.WINTAB
            ink != null -> PenSource.INK
            else -> PenSource.MOUSE
        }
        dialReader = TabletDial.open(window, onRotation, onClick, log)
        dial = dialReader?.attached == true
    }

    fun close() {
        dialReader?.close()
        dialReader = null
        wintab?.close()
        wintab = null
        wheel?.close()
        wheel = null
        ink?.close()
        ink = null
        source = PenSource.MOUSE
        dial = false
    }
}
