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
}

/**
 * The window's three device readers, opened once and closed with it.
 *
 * It is **one seam, not a second input path**: the canvas keeps taking Compose's pointer events, and what
 * this adds is
 *  - the pen's reading for the event being handled ([ink], `TabletInk.contact`), and
 *  - the dial, from whichever of the two routes Windows offers it on: the radial controller's
 *    `RotationChanged` ([onRotation]) or the raw wheel report of the tablet's own HID device
 *    ([TabletWheel], which is the route this machine actually uses).
 *
 * A sample is therefore never delivered twice: the Ink stream decorates the pointer event the same
 * physical contact produced instead of starting a second stroke, and a dial notch stays the wheel event
 * Compose already received — [dialWheelRecently] only says *whose* wheel it was, so the canvas can zoom
 * instead of scroll.
 *
 * Every reader answers `null` on a host without the device, and each logs the step that refused it. The
 * window then behaves exactly as it did before any of them existed, which is the fallback the status bar
 * reports in words.
 */
internal class DesktopTablet {

    /** The pen's own stream, or null when this window has no Windows Ink. */
    var ink: TabletInk? = null
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

    /** True when the notch Compose just received was the tablet's dial, not the mouse's wheel. */
    fun dialWheelRecently(): Boolean = wheel?.recentDial() == true

    /**
     * Open the readers on [window].
     *
     * [onRotation] is a radial controller's delta in degrees, [onClick] the dial's own button. Both are
     * called from whichever thread Windows raises them on — a caller with state to touch marshals to its
     * UI thread (`DesktopApp` posts them). [onSample] and [onEvent] are the diagnostics probe's observers
     * and are null in the editor.
     */
    fun open(
        window: Window,
        onRotation: (Double) -> Unit,
        onClick: () -> Unit,
        log: (String) -> Unit,
        onSample: ((PenContact) -> Unit)? = null,
        onEvent: ((String) -> Unit)? = null,
    ) {
        // The wheel first: the ink reader's window procedure is what feeds it a raw report.
        wheel = TabletWheel.open(window, log, onNotches = {}, onEvent = onEvent)
        ink = TabletInk.open(
            window = window,
            log = log,
            onSample = onSample,
            onEvent = onEvent,
            onRawInput = { lParam -> wheel?.handle(lParam) },
        )
        source = if (ink != null) PenSource.INK else PenSource.MOUSE
        dialReader = TabletDial.open(window, onRotation, onClick, log)
        dial = dialReader?.attached == true
    }

    fun close() {
        dialReader?.close()
        dialReader = null
        wheel?.close()
        wheel = null
        ink?.close()
        ink = null
        source = PenSource.MOUSE
        dial = false
    }
}
