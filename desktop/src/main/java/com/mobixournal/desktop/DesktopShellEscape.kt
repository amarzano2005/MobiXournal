package com.mobixournal.desktop

/**
 * `Esc`, in the order the window's surfaces stack: the drag in flight, the placement question, the
 * component palette, the failure card. A surface that is not up answers false, so the key is not
 * swallowed by nothing.
 *
 * It is a policy object rather than a member of [DesktopShellState] because the shell file holds the
 * state and the commands and is at its structural limit (`SourceStructureTest`): the *order* is the whole
 * of what this answers, and it reads nothing private — each surface is a public value the window's own
 * chrome draws from.
 *
 * Two surfaces are decided elsewhere because they are the surface's own keys: the Settings page reads
 * `Esc` itself (clearing its search before it closes, `DesktopSettingsState.onKey`), and the page-setup
 * panel closes through its own state.
 */
internal object WindowEscape {

    /** Close the newest surface on screen; false when there is nothing to put away. */
    fun dismiss(state: DesktopShellState): Boolean {
        // A drag that has not finished is first: cancelling the figure is the only way back from it.
        val cancel = state.liveDrag
        if (cancel != null) {
            state.liveDrag = null
            cancel()
            return true
        }
        // The placement question is newer than the palette and older than the failure card.
        if (state.pendingPlacement != null) {
            state.pendingPlacement = null
            return true
        }
        if (state.circuitPickerOpen != null) {
            state.circuitPickerOpen = null
            return true
        }
        if (state.failure == null) return false
        state.failure = null
        return true
    }
}
