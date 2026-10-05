/**
 * Pen diagnostics: the view-side half of the on-canvas panel that shows the raw stylus stream.
 *
 * A pen's side button reaches an app down paths that cannot be told apart from the outside — a motion
 * button bit, a barrel-button key code, or a vendor pen the tablet only presents as a mouse — and a
 * pen whose button arrives down none of them is indistinguishable from one whose button the app
 * misreads. Recording what actually lands on the view settles that question from the device itself,
 * without a guess: switch the panel on, press the button, and read the stream.
 *
 * Everything here is a thin, read-only observer: it formats an event into one line, appends it to the
 * [PenInputLog] and (coalesced to one UI pass) pushes the snapshot to [onPenDebug]. Nothing in the
 * input pipeline changes when it is on, so what the panel reports is what the app really receives.
 * The formatting itself lives in [PenEventText], Android-free and unit-tested.
 */
package com.mobixournal.render

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Record one pointer event — touch, hover or generic motion — as the view received it, pressure
 * included: the reading the pen's width is computed from belongs next to the event that carried it.
 */
internal fun DrawingSurfaceView.tracePenMotion(event: MotionEvent) {
    if (!penDebugEnabled) return
    val index = event.actionIndex
    val toolType = event.getToolType(index)
    val deviceName = event.device?.name
    val pressure = event.getPressure(index)
    recordPenLine(
        PenEventText.motionLine(
            event.actionMasked, toolType, event.buttonState, event.pointerCount, deviceName, pressure,
        ),
        PenEventText.motionKey(
            event.actionMasked, toolType, event.buttonState, event.pointerCount, deviceName, pressure,
        ),
    )
}

/**
 * Record the live pressure filter and what it makes of a half press — the pen parameters as the
 * **drawing surface** holds them right now.
 *
 * Called whenever settings are pushed onto the canvas (and when the panel is switched on), because the
 * one thing the panel could not previously answer is whether a value typed into Settings reached the
 * pen at all. A slider moved with the panel open now prints the new multiplier (or does not, which is
 * the answer).
 */
internal fun DrawingSurfaceView.tracePenParameters() {
    if (!penDebugEnabled) return
    recordPenLine(
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

/** The pressure the parameter line reports a width for: a mid press, so both ends stay comparable. */
private const val SAMPLE_PRESSURE = 0.5f

/**
 * Record one key event. Every key is logged, not only the barrel's: when a pen's button does
 * something unexpected, the line that names what it *did* arrive as is the whole point. Repeats of a
 * held key are dropped — they would bury everything else.
 */
internal fun DrawingSurfaceView.tracePenKey(event: KeyEvent) {
    if (!penDebugEnabled) return
    val down = when (event.action) {
        KeyEvent.ACTION_DOWN -> true
        KeyEvent.ACTION_UP -> false
        else -> return
    }
    if (event.repeatCount > 0) return
    recordPenLine(
        PenEventText.keyLine(down, event.keyCode, penEventSource(event), event.device?.name),
        null,
    )
}

/**
 * Switch the panel on or off. Switching it on records the input devices first — which sources the
 * tablet gives the pen is often the answer on its own — and switching it off empties the panel, so
 * the next run never reads as a continuation of the last.
 */
internal fun DrawingSurfaceView.setPenDebug(enabled: Boolean) {
    if (enabled == penDebugEnabled) return
    penDebugEnabled = enabled
    penLog.clear()
    if (enabled) {
        tracePenDevices()
        tracePenParameters()
        postPenLogFlush()
    } else {
        onPenDebug?.invoke(emptyList())
    }
}

/** Forget the recorded lines, leaving the panel open (and recording) — the user's Clear button. */
internal fun DrawingSurfaceView.clearPenDebug() {
    penLog.clear()
    postPenLogFlush()
}

/**
 * Record a line about what the app *did* with the pen's input — which action a button click ran, or
 * why one was swallowed. The event lines say what arrived; these say what the app made of it, and the
 * two together are what turns "my pen's button does nothing" into an answer.
 */
internal fun DrawingSurfaceView.notePenDebug(line: String) {
    if (!penDebugEnabled) return
    recordPenLine(line, key = null)
}

/** One line per input device the tablet exposes, with the source bits it advertises. */
private fun DrawingSurfaceView.tracePenDevices() {
    for (id in InputDevice.getDeviceIds()) {
        val device = InputDevice.getDevice(id) ?: continue
        recordPenLine(PenEventText.deviceLine(id, device.name, device.sources), null)
    }
}

/** Append one formatted line and schedule a snapshot push; [key] dedupes an unchanged state. */
private fun DrawingSurfaceView.recordPenLine(line: String, key: String?) {
    if (!penLog.record(line, key)) return
    postPenLogFlush()
}

/**
 * The source mask of a key event: the event's own, plus the whole device's. A vendor pen's button
 * can arrive with either one carrying the stylus flag, and both describe the same hardware.
 */
private fun penEventSource(event: KeyEvent): Int = event.source or (event.device?.sources ?: 0)
