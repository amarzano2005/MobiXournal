package com.mobixournal.desktop

/**
 * What the dial's two inputs do, as commands on the shell's state.
 *
 * They are extensions rather than members so the shell file stays the size of a shell
 * (`SourceStructureTest`), and they are **commands on the same state** the keyboard edits: a dial turn is
 * a zoom step and the dial's button is a fit, so neither needs a second notion of where the view is.
 */

/**
 * Turn the dial into a zoom, by the signed [degrees] Windows reports for one rotation event.
 *
 * The step is [dialZoomFactor]'s — exponential, so it feels the same at 30 % and at 300 % — and it zooms
 * around the page's centre, which is the anchor a rotation event has: the dial's own contact position is
 * a screen coordinate of a device that need not even be on the glass. A fitted tab drops to `free zoom`
 * on the first turn, exactly as a keyboard zoom does.
 */
internal fun DesktopShellState.dialZoom(degrees: Double) {
    val tab = tabs.active ?: return
    zoomBy(tab, dialZoomFactor(degrees))
}

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
