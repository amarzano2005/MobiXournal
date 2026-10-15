package com.mobixournal.render

import android.view.MotionEvent

/**
 * The stylus telemetry axes a `MotionEvent` reports, as the model stores them.
 *
 * Tilt (`AXIS_TILT`) and azimuth (`AXIS_ORIENTATION`) are the two the platform supplies; there is no
 * barrel-roll axis to read at all, which is why no such field exists in the schema
 * (`docs/mobix-elements.md`). Both are read for their own sake — they are stored, never drawn with
 * (`docs/stylus.md`) — so the only rule this file owns is **when a reading counts**.
 *
 * A digitiser that does not have an axis reports `0` for it, and so does a pen held perfectly upright,
 * which is the ordinary writing posture. Reading the axis value alone therefore cannot tell a real zero
 * from a missing axis, and `MotionEvent.getAxisValue` *throws* for an axis its device does not declare,
 * so the honest reading needs both facts: whether the device declares the axis
 * (`InputDevice.getMotionRange`) and the value itself. [recorded] is that decision, kept pure and free
 * of Android so it can be unit-tested — the value is taken only when the axis is declared, which is what
 * lets a stored `0` mean "upright" and a stored null mean "this tablet has no such axis".
 *
 * Its callers are the two places that read the axes: the inking path, which stores them on a
 * [com.mobixournal.format.model.StrokePoint], and the pen-diagnostics panel, which shows them so the
 * question "does this pen report tilt at all?" can be answered on the device that has the pen.
 */
internal object StrokeTelemetry {

    /**
     * The value to store for one axis: the reading itself when [declared] — **zero included**, because a
     * reading of zero from a declared axis is a fact — and null when the tablet declares no such axis.
     * [reading] is invoked only in the declared case, so a platform call that would throw for an
     * undeclared axis is never made.
     */
    fun recorded(declared: Boolean, reading: () -> Float): Float? = if (declared) reading() else null
}

/**
 * The telemetry axis [axis] of [pointerIndex], as the document stores it: the reading when the event's
 * device declares that axis, null when it does not.
 *
 * The declaration is asked of `InputDevice.getMotionRange` **before** the value, because
 * `MotionEvent.getAxisValue` throws `IllegalArgumentException` for an axis its device does not have —
 * so asking the value first is not an option, and a tablet without the axis would otherwise look like a
 * pen held perfectly still. Together with [StrokeTelemetry.recorded] this is the whole rule, in one
 * place: the inking path stores what this returns, the pen-diagnostics panel shows it.
 */
internal fun MotionEvent.telemetryAxis(axis: Int, pointerIndex: Int): Float? =
    StrokeTelemetry.recorded(device?.getMotionRange(axis) != null) { getAxisValue(axis, pointerIndex) }

