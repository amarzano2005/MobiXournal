package com.mobixournal.desktop

import com.mobixournal.render.PressureCurve

/**
 * The window's pressure settings — sensitivity, floor and multiplier — where the two adapters that turn a
 * device sample into ink can read them.
 *
 * It is a module-level value for the same reason `DesktopExportQueue` is: it belongs to the window rather
 * than to a document, a tab or a canvas, and the two places that need it — `PageInkGesture.inkPointer`
 * (Compose's pointer, with Windows Ink's contact folded in) and `PenPacketStroke.pointer` (the driver's
 * own packets) — sit under five composables each, so threading a lambda down to them would add a
 * parameter to every page, sheet and workspace in between for a number nothing else reads.
 * **[DesktopSettingsState] is its only writer**, so the value on screen and the value stored in
 * `desktop.properties` cannot disagree.
 *
 * The whole filter is [PressureCurve.penFactor] — `max(minimum, reading × multiplier)`, desktop
 * Xournal++'s own arithmetic — and **nothing else**: no curve on top of it and no second floor, so the same
 * reading with the same three numbers gives the same width here, on the phone and in the desktop app. (The
 * window used to apply a `tablet.pressureExponent` gamma before the filter; a stored exponent is now
 * ignored, because a knob no surface shows is a difference nobody can see.)
 * With sensitivity off the factor is full width, which is what makes a pen stroke and a figure match.
 *
 * Volatile because the packet path fills its queue on the driver's thread while the frame thread drains
 * it: a stroke may begin on one and continue on the other.
 *
 * The router in `PageInkGesture` is built with a **neutral** filter (`enabled`, ×1, floor 0) on purpose:
 * the three numbers are applied here, once, so a stroke cannot be floored or multiplied twice and a
 * change on the settings surface reaches the next sample rather than the next canvas.
 */
internal object PressureFeel {

    /** Desktop Xournal++'s *Pressure sensitivity*: off draws every sample at the tool's width. */
    @Volatile
    var enabled: Boolean = true

    /** The `pressureMultiplier` of the same filter. */
    @Volatile
    var multiplier: Float = 1f

    /** The floor under a filtered pressure (`minimumPressure`). */
    @Volatile
    var minimum: Float = PressureCurve.MINIMUM_PRESSURE_DEFAULT

    /** [pressure], already 0..1, through every setting in force — the one call both adapters make. */
    fun factor(pressure: Float): Float = PressureCurve.penFactor(
        pressure = PressureCurve.reading(pressure, 1f),
        enabled = enabled,
        multiplier = multiplier,
        minimum = minimum,
    )
}
