/**
 * The canvas's **diagnostics**: the raw stylus stream the on-canvas panel shows, and the frame record the
 * same panel carries. One object that owns their state, so the canvas no longer does.
 *
 * A pen's side button reaches an app down paths that cannot be told apart from the outside — a motion button
 * bit, a barrel-button key code, or a vendor pen the tablet only presents as a mouse — and a pen whose button
 * arrives down none of them is indistinguishable from one whose button the app misreads. Recording what
 * actually lands on the view settles that question from the device itself: switch the panel on, press the
 * button, and read the stream.
 *
 * Everything here is a **read-only observer**: it formats an event into one line, appends it to the
 * [PenInputLog] and (coalesced to one UI pass) pushes the snapshot to [onSnapshot]. Nothing in the input
 * pipeline changes when it is on, so what the panel reports is what the app really receives. The formatting
 * itself lives in [PenEventText] and the arithmetic in [FrameWatchdog], both Android-free and unit-tested;
 * what is left here is the Android plumbing plus the state.
 *
 * State ownership is the point of the extraction. The canvas used to carry eight members for this — the flag,
 * the callback, the log, the watchdog, the posted-flag and the Runnable — and every one of the twenty
 * `DrawingSurface*.kt` files could reach them. Now the canvas owns one reference, three tiny entry points
 * (`setPenDebug`, `clearPenDebug`, `refreshPenParameters`) and nothing else.
 *
 * [post] is injected rather than the view being held: the push has to land on the UI pass that draws the
 * panel, which is the view's `post`, and taking a lambda keeps this class constructible — and testable — with
 * no `View` at all (`CanvasDiagnosticsTest` drives the coalescing with a fake poster).
 */
package com.mobixournal.render

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

internal class CanvasDiagnostics(
    /** Schedule a push for the next UI pass — the view's `post`. False when nothing can be scheduled. */
    private val post: (Runnable) -> Boolean,
) {

    /** Whether the pen-event log is recording. */
    var enabled = false
        private set

    /** Receives the log's snapshots while the panel is on — the editor's overlay renders them. */
    var onSnapshot: ((List<String>) -> Unit)? = null

    /** The buffered lines. */
    val log = PenInputLog()

    /** Frames, their worst gap, the paint's cost and the page rasters — one line per second of painting. */
    val watchdog = FrameWatchdog()

    private var pushPosted = false

    /**
     * Push the log once per UI pass, coalescing a burst of events into one snapshot: a pen moving across the
     * glass emits far faster than anything can re-render.
     */
    private val push = Runnable {
        pushPosted = false
        onSnapshot?.invoke(log.snapshot())
    }

    /** Schedule the coalesced push; [push] is delivered straight away when nothing can be posted. */
    fun schedule() {
        if (pushPosted) return
        pushPosted = true
        // Before the first layout (or while detached) `post` never runs; deliver straight away instead of
        // leaving the panel stuck empty.
        if (!post(push)) {
            pushPosted = false
            onSnapshot?.invoke(log.snapshot())
        }
    }

    /**
     * Record one pointer event — touch, hover or generic motion — as the view received it, pressure included:
     * the reading the pen's width is computed from belongs next to the event that carried it.
     */
    fun motion(event: MotionEvent) {
        if (!enabled) return
        val index = event.actionIndex
        val toolType = event.getToolType(index)
        val deviceName = event.device?.name
        val pressure = event.getPressure(index)
        // The two stylus axes the platform has, under the same rule the inking path stores them by: a zero
        // from a declared axis is a reading, an undeclared axis is `-` (`StrokeTelemetry`). Shown so the
        // question "does this pen report tilt at all?" is answered by the pen that has it, not by a guess.
        val telemetry = PenTelemetry(
            tiltRadians = event.telemetryAxis(MotionEvent.AXIS_TILT, index),
            azimuthRadians = event.telemetryAxis(MotionEvent.AXIS_ORIENTATION, index),
        )
        record(
            PenEventText.motionLine(
                event.actionMasked, toolType, event.buttonState, event.pointerCount, deviceName, pressure,
                telemetry,
            ),
            PenEventText.motionKey(
                event.actionMasked, toolType, event.buttonState, event.pointerCount, deviceName, pressure,
                telemetry,
            ),
        )
    }

    /**
     * Record one key event. Every key is logged, not only the barrel's: when a pen's button does something
     * unexpected, the line that names what it *did* arrive as is the whole point. Repeats of a held key are
     * dropped — they would bury everything else.
     */
    fun key(event: KeyEvent) {
        if (!enabled) return
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return
        }
        if (event.repeatCount > 0) return
        record(PenEventText.keyLine(down, event.keyCode, penEventSource(event), event.device?.name), null)
    }

    /**
     * Record the live pressure filter and what it makes of a half press — the pen parameters as the **drawing
     * surface** holds them right now.
     *
     * The one thing the panel could not otherwise answer is whether a value typed into Settings reached the
     * pen at all. A slider moved with the panel open prints the new multiplier (or does not, which is the
     * answer). The canvas hands its numbers in (`refreshPenParameters`), so this class needs no document and
     * no settings store.
     */
    fun parameters(
        pressureEnabled: Boolean,
        pressureMultiplier: Float,
        minimumPressure: Float,
        baseWidthPt: Float,
    ) {
        if (!enabled) return
        record(
            PenEventText.penParametersLine(
                enabled = pressureEnabled,
                multiplier = pressureMultiplier,
                minimum = minimumPressure,
                baseWidthPt = baseWidthPt,
                samplePressure = SAMPLE_PRESSURE,
                sampleWidthPt = PressureCurve.widthPt(
                    baseWidthPt, SAMPLE_PRESSURE, pressureEnabled, pressureMultiplier, minimumPressure,
                ),
            ),
            key = null,
        )
    }

    /**
     * One line per input device the tablet exposes, with the source bits it advertises: which sources the
     * tablet gives the pen is often the answer on its own.
     */
    fun devices() {
        if (!enabled) return
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            record(PenEventText.deviceLine(id, device.name, device.sources), null)
        }
    }

    /**
     * Feed one painted frame: the watchdog turns a second of them into a line, which lands in the log only
     * while the panel is open.
     */
    fun frame(paintMs: Double, pagesRastered: Int) {
        val line = watchdog.painted(paintMs, pagesRastered) ?: return
        if (!enabled) return
        record(line, key = null)
    }

    /**
     * Record a line about what the app *did* with the pen's input — which action a button click ran, or why
     * one was swallowed. The event lines say what arrived; these say what the app made of it, and the two
     * together are what turns "my pen's button does nothing" into an answer.
     */
    fun note(line: String) {
        if (!enabled) return
        record(line, key = null)
    }

    /** Forget the recorded lines, leaving the panel open (and recording) — the user's Clear button. */
    fun clear() {
        log.clear()
        schedule()
    }

    /**
     * Switch the panel on or off. Switching it on [describe]s the pen first — the devices and the parameters
     * are what make the first lines readable — and switching it off empties the panel, so the next run never
     * reads as a continuation of the last.
     */
    fun setEnabled(value: Boolean, describe: CanvasDiagnostics.() -> Unit = {}) {
        if (value == enabled) return
        enabled = value
        log.clear()
        if (value) {
            describe()
            schedule()
        } else {
            onSnapshot?.invoke(emptyList())
        }
    }

    /** Append one formatted line and schedule a snapshot push; [key] dedupes an unchanged state. */
    private fun record(line: String, key: String?) {
        if (!log.record(line, key)) return
        schedule()
    }

    private companion object {
        /** The pressure the parameter line reports a width for: a mid press, so both ends stay comparable. */
        const val SAMPLE_PRESSURE = 0.5f
    }
}

/**
 * The source mask of a key event: the event's own, plus the whole device's. A vendor pen's button can arrive
 * with either one carrying the stylus flag, and both describe the same hardware.
 */
private fun penEventSource(event: KeyEvent): Int = event.source or (event.device?.sources ?: 0)
