package com.mobixournal.render

/**
 * Maps a raw pen pressure (0..1) onto a stroke-width multiplier, **exactly as desktop Xournal++ does**
 * (`PenInputHandler::filterPressure`): `max(minimumPressure, pressure × pressureMultiplier)`.
 *
 * There is deliberately no curve and no wide floor here. A gamma curve or an invented floor would make
 * the same physical press store a different width than the desktop app, so a stroke drawn on one and
 * opened on the other would look different — the mismatch this model exists to remove. Pressure
 * sensitivity switched off draws every sample at the full size instead, matching a shape exactly.
 *
 * Pure and JVM-testable (see `PressureCurveTest`).
 */
object PressureCurve {

    /**
     * Desktop Xournal++'s default `minimumPressure`: the floor under a filtered pressure, so a
     * zero-pressure sample still leaves a (very light) line. Desktop asserts it is at least 0.01.
     */
    const val MINIMUM_PRESSURE_DEFAULT = 0.05f

    /** The lowest [minimumPressure] the desktop accepts (its `setMinimumPressure` assertion). */
    const val MINIMUM_PRESSURE_MIN = 0.01f

    /** Full width — what an unmagnified pressure of 1.0 gives, and what pressure-off returns. */
    const val FULL = 1f

    /**
     * The width multiplier for a [pressure]. [enabled] is desktop Xournal++'s "Pressure sensitivity":
     * switched off it returns [FULL], so every sample draws at the size setting. Otherwise the raw
     * pressure is scaled by [multiplier] and floored at [minimum]. There is **no upper clamp**, exactly
     * as the desktop allows a multiplier above 1 to push a stroke past its nominal width.
     */
    fun penFactor(pressure: Float, enabled: Boolean, multiplier: Float, minimum: Float): Float =
        if (!enabled) FULL else maxOf(minimum, pressure * multiplier)
}
