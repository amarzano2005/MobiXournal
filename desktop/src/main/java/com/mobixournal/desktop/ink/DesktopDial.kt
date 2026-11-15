package com.mobixournal.desktop.ink

import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.zoomBy
import kotlin.math.exp

/**
 * What the dial's two inputs do, as commands on the shell's state.
 *
 * They are extensions rather than members so the shell file stays the size of a shell
 * (`SourceStructureTest`), and they are **commands on the same state** the keyboard edits: a dial turn is
 * a zoom step and the dial's button is a fit, so neither needs a second notion of where the view is.
 */

/**
 * The dial's own button: back to the fit the tab opened in.
 *
 * `AUTO` is the mode that answers what the page *is* — a sheet whole, a long strip to its width
 * (`FitMode.resolve`) — so the button means "show it the way this document wants to be shown", which is
 * the one view a dial cannot turn itself back to.
 */
internal fun DesktopShellState.dialFit() {
    val tab = tabs.active ?: return
    tab.fit = FitMode.AUTO
    tab.viewport = tab.viewport.copy(offsetX = 0f, offsetY = 0f)
}

/**
 * Zoom the active tab by [notches] using the exact same discrete step as `Ctrl + wheel`
 * (`prefs.navigation.zoomStepWheel`, default `0.12` per notch).
 *
 * This is the radial controller's one zoom route, fed by [DialNotchAccumulator]: Windows reports a turn
 * as uneven degree bursts, and a notch of the dial has to mean what a notch of the wheel means — one
 * step around the page's centre, dropping a fitted tab to free zoom on the first turn like any other
 * manual zoom. A rotation event has no contact position worth anchoring to (the dial need not be on the
 * glass at all), which is why the step zooms around the page rather than at a cursor.
 */
internal fun DesktopShellState.dialWheelZoom(notches: Int) {
    if (notches == 0) return
    val tab = tabs.active ?: return
    val factor = exp(notches * prefs.navigation.zoomStepWheel).coerceIn(0.5f, 2f)
    zoomBy(tab, factor)
}

/**
 * Accumulates Windows' uneven `RotationChanged` degree bursts (`1°`, `4°`, `9°`, …) into
 * fixed [DEGREES_PER_NOTCH] steps so every dial tick fires the exact same zoom step as `Ctrl + wheel`.
 */
internal class DialNotchAccumulator {
    private var accumulated = 0.0

    /** Add signed [degrees] and return the number of whole notches crossed. */
    fun add(degrees: Double): Int {
        if (degrees == 0.0) return 0
        if (degrees * accumulated < 0.0) accumulated = 0.0
        accumulated += degrees
        val notches = (accumulated / DEGREES_PER_NOTCH).toInt()
        accumulated -= notches * DEGREES_PER_NOTCH
        return notches
    }

    /** Discard any partial degree remainder (e.g. when the dial button resets to fit). */
    fun reset() {
        accumulated = 0.0
    }

    private companion object {
        const val DEGREES_PER_NOTCH = 10.0
    }
}
