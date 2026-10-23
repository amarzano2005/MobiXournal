package com.mobixournal.render

import android.view.MotionEvent

/**
 * The Android half of `give-both-shells-one-pointer-router`: the **platform vocabulary** of one
 * pointer, read off a `MotionEvent`.
 *
 * The Android surface stays an adapter — this file is where a `MotionEvent`'s tool type becomes the
 * shared [PointerKind] and one pointer sample becomes an [InkPointer], and nothing here decides
 * anything: the decisions it feeds live in `mobix-core` ([InputClassifier] for what a contact begins,
 * [PressureCurve] for how hard it draws, [PageEraser]/[StrokeEraser] for what a partial erase cuts,
 * [InkPointerRouter] for what a stream of samples means).
 *
 * Reading by **index** is deliberate: palm rejection depends on never reading pointer 0 when the pen
 * is pointer 1 (`docs/stylus.md` → *Palm rejection*).
 */
internal fun MotionEvent.inkPointerKind(pointerIndex: Int): PointerKind =
    when (getToolType(pointerIndex)) {
        MotionEvent.TOOL_TYPE_STYLUS -> PointerKind.STYLUS
        MotionEvent.TOOL_TYPE_ERASER -> PointerKind.ERASER_TIP
        MotionEvent.TOOL_TYPE_FINGER -> PointerKind.FINGER
        else -> PointerKind.UNKNOWN
    }

/**
 * One sample of the pointer at [pointerIndex], in the shared vocabulary.
 *
 * [history] selects a **historical** sample of the batch (`0 until historySize`); the default `-1` is
 * the event's own newest sample. Historical samples are where a fast pen's whole pressure ramp lives
 * — the platform batches a 100–240 Hz digitiser into one `ACTION_MOVE` — so the adapter keeps them
 * rather than collapsing the batch to its last point.
 *
 * [barrelHeld] is the view's latch rather than a reading of this event: a pen's press edge often lands
 * on a hover event and is not repeated on the following `ACTION_DOWN` (`docs/stylus.md` → *Barrel
 * button*), so the event's own `buttonState` alone would classify a barrel-erase stroke as ink.
 *
 * Tilt and azimuth go through [telemetryAxis], so an axis the tablet does not declare stays null
 * instead of reading as a pen held perfectly upright.
 */
internal fun MotionEvent.inkPointer(
    pointerIndex: Int,
    phase: InkPointerPhase,
    barrelHeld: Boolean,
    history: Int = -1,
): InkPointer {
    val historical = history >= 0
    return InkPointer(
        id = getPointerId(pointerIndex),
        x = if (historical) getHistoricalX(pointerIndex, history) else getX(pointerIndex),
        y = if (historical) getHistoricalY(pointerIndex, history) else getY(pointerIndex),
        pressure = if (historical) getHistoricalPressure(pointerIndex, history) else getPressure(pointerIndex),
        timeMs = if (historical) getHistoricalEventTime(history) else eventTime,
        tool = inkPointerKind(pointerIndex),
        phase = phase,
        tiltRad = telemetryAxis(MotionEvent.AXIS_TILT, pointerIndex),
        azimuthRad = telemetryAxis(MotionEvent.AXIS_ORIENTATION, pointerIndex),
        buttons = buttons(barrelHeld),
    )
}

/**
 * The button bits [InkPointer] reads, for this event: the tip (the primary button, or a pen the
 * tablet presents as a mouse) and the barrel (the view's latch, or a secondary click the event itself
 * reports — `BUTTON_STYLUS_SECONDARY` on a pen, `BUTTON_SECONDARY` on a mouse).
 */
private fun MotionEvent.buttons(barrelHeld: Boolean): Int {
    val primary = buttonState and (MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_STYLUS_PRIMARY)
    val secondary = buttonState and (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_SECONDARY)
    return (if (primary != 0) InkPointer.PRIMARY else 0) or
        (if (barrelHeld || secondary != 0) InkPointer.SECONDARY else 0)
}
