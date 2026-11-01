/**
 * The **pen's pressure**, as the canvas applies it: whether width follows pressure at all, how hard the
 * raw reading is scaled, and the floor a soft touch still draws at.
 *
 * The three numbers were three `var`s on a 1 300-line `View`, read from four places — the router that
 * feeds samples, the width of every committed point, the shapes' nominal-width rule and the diagnostics
 * panel that reports what the filter makes of a half press. They are one subject: the pen-width model
 * desktop Xournal++ exposes as *Pressure sensitivity*, *Pressure multiplier* and *Minimum pressure*, and
 * the arithmetic they feed is [PressureCurve]'s.
 *
 * This object exists to own the state, not to re-implement the maths: [widthPt] is a call to
 * [PressureCurve.widthPt] with the three settings in force, so a change here can only be about *which*
 * numbers are used — never about what a pressure means. That separation is what lets a slider moved in
 * Settings take effect on the next sample (the values are read per point, not captured once).
 */
package com.mobixournal.render

internal class PenPressure(
    /**
     * Whether the pen's width follows pressure at all. Off draws every stroke at the tool's width, so a
     * pen stroke and a geometric shape match exactly.
     */
    enabled: Boolean = true,
    /** Raw pressure is scaled by this before the floor — desktop's "pressure multiplier". */
    multiplier: Float = 1f,
    /** The floor under the filtered pressure: a zero reading still draws at this fraction of the width. */
    minimum: Float = PressureCurve.MINIMUM_PRESSURE_DEFAULT,
) {

    var enabled = enabled

    var multiplier = multiplier

    var minimum = minimum

    /**
     * The width one sample draws at, in pt: [baseWidthPt] (the tool's size setting) times the filtered
     * [pressure]. A highlighter bypasses this — it has its own width rule — which is the caller's
     * decision and stays there.
     */
    fun widthPt(baseWidthPt: Float, pressure: Float): Double =
        PressureCurve.widthPt(baseWidthPt, pressure, enabled, multiplier, minimum)
}
