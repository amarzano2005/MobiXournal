package com.mobixournal.desktop.shell
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type

/**
 * The window's **shortcuts, as the window's**: the one hook that fires whatever holds the keyboard.
 *
 * The keys used to be dispatched from the root of the composition, which works only while the focus path
 * runs through that root — so every way of losing composition focus silenced the whole table: a text box's
 * field being removed, a drag on the page, a bar that came and went. It read as "the shortcuts stop after
 * some gesture", and clicking the page was the only way back, because a click was what restored focus.
 *
 * `Window(onPreviewKeyEvent = …)` has no such condition: it sees the key before the composition is asked,
 * whether a field is focused or nothing is. [DesktopApp] installs the shell's answer here, and the answer
 * itself is unchanged — [DesktopShellState.onKey] still refuses to act while a text box owns the typing,
 * while a Shortcuts row waits for a key, or while the settings surface is up.
 *
 * [menuOpen] is the one exception the popup decides itself: the hamburger menu is the only surface that
 * takes the keyboard (`MenuSurface`), so its own field types without the table reaching under it.
 */
internal object WindowKeys {

    /** The shell's answer, installed while the window is composed; null in a test with no window. */
    var onKey: ((KeyEvent) -> Boolean)? = null

    /** True while the hamburger menu is open — the one popup whose field needs the keys for itself. */
    var menuOpen: Boolean = false

    private val controls = mutableSetOf<Any>()
    internal fun focusControl(owner: Any, focused: Boolean) = synchronized(controls) {
        if (focused) controls.add(owner) else controls.remove(owner)
        Unit
    }
    internal fun defersControlKey(key: Key): Boolean = synchronized(controls) {
        controls.isNotEmpty() && key in setOf(Key.Enter, Key.NumPadEnter, Key.Spacebar,
            Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.Home, Key.MoveHome, Key.MoveEnd)
    }

    /** True when the shell took the key, which is what makes the window consume it. */
    fun handle(event: KeyEvent): Boolean {
        if (menuOpen) return false
        if (defersControlKey(event.key)) {
            // Release held shell modifiers too, while letting the focused control see its key-up.
            if (event.type == KeyEventType.KeyUp) onKey?.invoke(event)
            return false
        }
        return onKey?.invoke(event) == true
    }
}
