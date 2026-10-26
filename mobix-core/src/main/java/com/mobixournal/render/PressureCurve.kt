package com.mobixournal.render

import kotlin.math.pow

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

    /** The lowest [minimumPressure] the desktop accepts (its `setMinimumPressure` assertion), and the
     * low end of its "Minimum pressure" slider (`adjustmentMinimumPressure`). */
    const val MINIMUM_PRESSURE_MIN = 0.01f

    /**
     * The highest [minimumPressure] the desktop's slider offers. At 1 the floor alone is full width, so
     * pressure no longer changes the line at all — the desktop's own top of the range, offered rather
     * than clamped short of it.
     */
    const val MINIMUM_PRESSURE_MAX = 1f

    /**
     * The desktop's "Pressure multiplier" slider bounds (`adjustmentPressureMultiplier`): 0.5 … 4, so a
     * light writer can thicken a stroke well past its size setting — the headroom is what makes the
     * control worth having, and desktop Xournal++ offers all of it.
     */
    const val MULTIPLIER_MIN = 0.5f
    const val MULTIPLIER_MAX = 4f

    /** Full width — what an unmagnified pressure of 1.0 gives, and what pressure-off returns. */
    const val FULL = 1f

    /** The softest response the shape offers: a light press reaches a given width sooner. */
    const val EXPONENT_MIN = 0.5f

    /** The hardest: a light press draws very thin and the width only arrives near a full press. */
    const val EXPONENT_MAX = 2.0f

    /** No shaping at all — the default, and the only value that leaves the model's widths untouched. */
    const val EXPONENT_IDENTITY = 1f

    /**
     * The **reading response**: [pressure] raised to [exponent], clamped at both ends.
     *
     * This is the one curve in the model, and it is **off by default** for the reason the class note
     * gives: at [EXPONENT_IDENTITY] it returns its input unchanged, so a document drawn without touching
     * the setting holds exactly the widths desktop Xournal++ would have stored. It shapes the *reading*, not
     * the width — the shaped value still goes through [penFactor], so the multiplier and the floor keep
     * their meaning and a document's own stored pressure is never rewritten.
     *
     * `exponent < 1` is a soft pen (a light press gets thicker sooner), `exponent > 1` a hard one. The
     * function is monotonic over 0..1 for every exponent in [EXPONENT_MIN]..[EXPONENT_MAX], pins both ends
     * (0 → 0, 1 → 1), and clamps an out-of-range reading or exponent rather than throwing — a driver
     * reporting 1.2 must not turn into a wider-than-full stroke.
     */
    fun shape(pressure: Float, exponent: Float): Float {
        val reading = pressure.coerceIn(0f, 1f)
        val curve = exponent.coerceIn(EXPONENT_MIN, EXPONENT_MAX)
        if (curve == EXPONENT_IDENTITY) return reading
        return reading.toDouble().pow(curve.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /**
     * The width multiplier for a [pressure]. [enabled] is desktop Xournal++'s "Pressure sensitivity":
     * switched off it returns [FULL], so every sample draws at the size setting. Otherwise the raw
     * pressure is scaled by [multiplier] and floored at [minimum]. There is **no upper clamp**, exactly
     * as the desktop allows a multiplier above 1 to push a stroke past its nominal width.
     */
    fun penFactor(pressure: Float, enabled: Boolean, multiplier: Float, minimum: Float): Float =
        if (!enabled) FULL else maxOf(minimum, pressure * multiplier)

    /**
     * The width one sample of a pressure-sensitive pen draws at, in pt: the tool's width setting times
     * [penFactor].
     *
     * The whole pressure model is this one expression, so there is exactly one place where a sample
     * becomes a width — the drawing surface calls it rather than repeating the arithmetic, which is
     * what lets the settings' effect be unit-tested without a device (`PressureCurveTest`).
     */
    fun widthPt(
        baseWidthPt: Float,
        pressure: Float,
        enabled: Boolean,
        multiplier: Float,
        minimum: Float,
    ): Double = (baseWidthPt * penFactor(pressure, enabled, multiplier, minimum)).toDouble()
}
