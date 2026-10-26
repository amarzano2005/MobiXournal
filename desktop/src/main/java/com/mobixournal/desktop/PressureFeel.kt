package com.mobixournal.desktop

import com.mobixournal.render.PressureCurve

/**
 * The window's one **reading** knob: the shape `tablet.pressureExponent` asks for, where the two adapters
 * that turn a device sample into ink can read it.
 *
 * It is a module-level value for the same reason `DesktopExportQueue` is: it belongs to the window rather
 * than to a document, a tab or a canvas, and the two places that need it — `PageInkGesture.inkPointer`
 * (Compose's pointer, with Windows Ink's contact folded in) and `PenPacketStroke.pointer` (the driver's
 * own packets) — sit under five composables each, so threading a lambda down to them would add a
 * parameter to every page, sheet and workspace in between for a number nothing else reads. **[DesktopSettingsState]
 * is its only writer**, so the value on screen and the value stored in `desktop.properties` cannot
 * disagree.
 *
 * Identity by default, which is what keeps this from changing anything: at `1.0` the samples reach the
 * model's own pressure filter untouched, so a document written without touching the slider holds exactly
 * the widths desktop Xournal++ would have stored ([PressureCurve.shape]).
 *
 * Volatile because the packet path fills its queue on the driver's thread while the frame thread drains
 * it: a stroke may begin on one and continue on the other.
 */
internal object PressureFeel {

    /** The exponent in force: 0.5 (soft) … 1.0 (untouched) … 2.0 (hard). */
    @Volatile
    var exponent: Float = PressureCurve.EXPONENT_IDENTITY

    /** [pressure] through the curve in force — the one call both adapters make. */
    fun shape(pressure: Float): Float = PressureCurve.shape(pressure, exponent)
}
