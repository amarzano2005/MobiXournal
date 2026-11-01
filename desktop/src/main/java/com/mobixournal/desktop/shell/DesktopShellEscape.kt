package com.mobixournal.desktop.shell
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopSettingsState
import com.mobixournal.desktop.sheet.dismissSelection

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

    /**
     * The shell's own `Esc` order, for the surfaces it puts **over the page**: the pen-tip ring, the close
     * question, a living element's editor, a selection, a colour panel, a tool group's picker. Newest first,
     * because the newest thing on screen is the one the key was aimed at.
     *
     * It lives beside [dismiss] rather than in the shell file for the same reason: the *order* is the whole of
     * what it answers, and it reads only values the window's own chrome draws from.
     */
    fun keys(state: DesktopShellState): Boolean {
        // The ring lies over everything it floats over, and nothing exists until it is summoned: whatever it
        // covers, it is the newest surface on screen.
        if (state.ring.dismiss()) return true
        // A question owns `Esc` before anything else: Cancel is the answer that loses nothing.
        if (state.closeRequest != null) {
            state.cancelClose()
            return true
        }
        // A living element's editor owns `Esc` next: the lane closes before a selection or a message.
        if (state.editingLiving != null) {
            state.closeLivingEditor()
            return true
        }
        // A selection owns `Esc` while there is one: the outline goes before a message is dismissed.
        if (dismissSelection(state.tabs.active)) return true
        // A colour panel that is up owns `Esc`: it closes before anything else the key might mean.
        if (state.editing != null) {
            state.cancelColourEdit()
            return true
        }
        // So does a tool group's picker: a chevron that opened a list must not hang over the page.
        if (state.openGroup != null) {
            state.openGroup = null
            return true
        }
        return false
    }

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
        if (state.prefs.figures.editingFigure != null) {
            state.prefs.figures.closeDialog()
            return true
        }
        // A figure's variant menu is the same layer as a palette: it is up over the strip, not over the
        // page, and it goes away before the failure card.
        if (state.prefs.figures.menu != null) {
            state.prefs.figures.showMenu(null)
            return true
        }
        if (state.failure == null) return false
        state.failure = null
        return true
    }
}
