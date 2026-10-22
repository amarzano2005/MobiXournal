package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import java.io.File

/**
 * What a key press *means*, separated from the window that receives it.
 *
 * The shell's keys are the table in `docs/ui-modernization.md` written as a pure decision: a
 * [ShellCommand], an [InkCommand] or a [DesktopTool], all computed from a Compose [Key] plus the
 * modifiers. Compose's `KeyEvent` cannot be constructed outside a real window, which is why the
 * decision lives here and [DesktopShellState.onKey] does nothing but translate the event and hand the
 * answer to [DesktopShellState.apply] — the shortcuts are testable at all only because of that split,
 * and the split itself keeps `DesktopShell.kt` about the window instead of about key maps.
 *
 * The two other helpers here — [selectPage], [zoomBy] — are the page and zoom arithmetic the commands
 * end in; they live beside the commands so a keyboard zoom and a sidebar click cannot disagree about
 * what a step is.
 */

/** What one key press means, independent of the platform's event type (and therefore testable). */
enum class ShellCommand {
    OPEN,
    CLOSE_TAB,
    NEXT_TAB,
    PREVIOUS_TAB,
    TOGGLE_SIDEBAR,
    ZOOM_IN,
    ZOOM_OUT,
    FIT_PAGE,
    FIT_WIDTH,
    NEXT_PAGE,
    PREVIOUS_PAGE,
    START_PAN,

    /** Write the active document back to the file it came from, in its own container. */
    SAVE,

    /** Write it somewhere else, choosing the container by the name: `Ctrl+Shift+S`. */
    SAVE_AS,

    DISMISS_FAILURE,
}

/** What an ink key means, independent of the platform's event type (and therefore testable). */
sealed interface InkCommand {
    /** Pick favourite colour [index] (`1`, `2`, `3`). */
    data class PickFavourite(val index: Int) : InkCommand

    /** Make the colour in use favourite [index] (`Alt` + `1`…`3`). */
    data class SetFavourite(val index: Int) : InkCommand

    /** One width slot up (`+1`) or down (`-1`): `]` and `[`. */
    data class StepWidth(val direction: Int) : InkCommand
}

/**
 * The ink's keys: `1`/`2`/`3` pick a favourite, `Alt`+digit redefines it, `]`/`[` step the width.
 *
 * `Ctrl` is refused outright so the shell's own `Ctrl+1` (fit width) and `Ctrl+0` (fit page) keep their
 * meaning; the numpad digits count as the same keys, because they are the same number on another pad.
 */
internal fun inkCommand(key: Key, alt: Boolean, ctrl: Boolean): InkCommand? {
    if (ctrl) return null
    val favourite = when (key) {
        Key.One, Key.NumPad1 -> 0
        Key.Two, Key.NumPad2 -> 1
        Key.Three, Key.NumPad3 -> 2
        else -> null
    }
    if (favourite != null) {
        return if (alt) InkCommand.SetFavourite(favourite) else InkCommand.PickFavourite(favourite)
    }
    return when (key) {
        Key.RightBracket -> InkCommand.StepWidth(1)
        Key.LeftBracket -> InkCommand.StepWidth(-1)
        else -> null
    }
}

/**
 * The letters a tool is bound to, as the physical key each one arrives on.
 *
 * It is a letters→keys map, not a second shortcuts table: [toolForKey] looks the letter up in
 * [DesktopTool.shortcut], so the pill's text, the doc's list and the keyboard all read one string.
 */
internal val toolKeyLetters = mapOf(
    Key.B to "B",
    Key.H to "H",
    Key.E to "E",
    Key.V to "V",
    Key.S to "S",
    Key.X to "X",
    Key.L to "L",
    Key.A to "A",
    Key.R to "R",
    Key.C to "C",
    Key.P to "P",
    Key.M to "M",
    Key.T to "T",
    Key.K to "K",
)

/**
 * The tool a key press picks, or null when the key is not a tool's accelerator.
 *
 * `Shift+X` is the axes tool while a bare `X` is the text box, which is why the shift is part of the
 * lookup instead of being ignored; `Ctrl` never picks a tool, so the shell's own commands keep their
 * letters (`Ctrl+B` is the sidebar, not a highlighter).
 */
internal fun toolForKey(key: Key, shift: Boolean, ctrl: Boolean): DesktopTool? {
    if (ctrl) return null
    val letter = toolKeyLetters[key] ?: return null
    return toolForShortcut(if (shift) "Shift+$letter" else letter)
}

/**
 * The shortcut table of `docs/ui-modernization.md`, as a decision.
 *
 * Order matters in two places: `Ctrl + 0` is "fit page" **only** without Shift (with Shift it is the
 * zoom-in key on layouts where `=` shares the `0`), and the zoom keys are checked before it so the
 * more specific binding wins.
 */
internal fun shellCommand(key: Key, ctrl: Boolean, shift: Boolean): ShellCommand? = when {
    key == Key.Escape -> ShellCommand.DISMISS_FAILURE
    ctrl && key == Key.T -> ShellCommand.OPEN
    ctrl && key == Key.S -> if (shift) ShellCommand.SAVE_AS else ShellCommand.SAVE
    ctrl && key == Key.W -> ShellCommand.CLOSE_TAB
    ctrl && key == Key.Tab -> if (shift) ShellCommand.PREVIOUS_TAB else ShellCommand.NEXT_TAB
    ctrl && key == Key.B -> ShellCommand.TOGGLE_SIDEBAR
    ctrl && isZoomIn(key, shift) -> ShellCommand.ZOOM_IN
    ctrl && isZoomOut(key) -> ShellCommand.ZOOM_OUT
    ctrl && key == Key.Zero -> ShellCommand.FIT_PAGE
    ctrl && key == Key.One -> ShellCommand.FIT_WIDTH
    !ctrl && (key == Key.PageDown || key == Key.DirectionDown) -> ShellCommand.NEXT_PAGE
    !ctrl && (key == Key.PageUp || key == Key.DirectionUp) -> ShellCommand.PREVIOUS_PAGE
    key == Key.Spacebar -> ShellCommand.START_PAN
    else -> null
}

/** One keyboard zoom step. */
internal const val ZOOM_STEP = 1.25f

/**
 * The keys that mean "zoom in" on the keyboard, whatever the layout puts on them.
 *
 * `Ctrl + =` is the documented shortcut, but `=` is not a key everywhere: on an Italian keyboard it is
 * **Shift + 0**, so the physical shortcut arrives as `Ctrl + Shift + 0` and a plain `Key.Equals` test
 * would never fire — worse, it would fall through to `Ctrl + 0` (fit page) and silently undo the zoom.
 * The keypad `+` is the layout-independent one. `Key.Plus` covers layouts where `+` is a key of its own.
 */
internal fun isZoomIn(key: Key, shift: Boolean): Boolean =
    key == Key.Equals ||
        key == Key.Plus ||
        key == Key.NumPadAdd ||
        (shift && key == Key.Zero)

/** The mirror of [isZoomIn]: `Ctrl + -` and the keypad's `-`. */
internal fun isZoomOut(key: Key): Boolean = key == Key.Minus || key == Key.NumPadSubtract

/** Zooms a tab around its centre (the keyboard path; the wheel zooms around the cursor). */
internal fun zoomBy(tab: DesktopTab, factor: Float) {
    tab.fit = FitMode.NONE
    tab.viewport = tab.viewport.copy(zoom = (tab.viewport.zoom * factor).coerceIn(Viewport.MIN_ZOOM, Viewport.MAX_ZOOM))
}

/** Select a page, keeping the tab's fit mode and re-centring the free viewport on it. */
internal fun selectPage(tab: DesktopTab, index: Int) {
    tab.page = index.coerceIn(0, (tab.pageCount - 1).coerceAtLeast(0))
    tab.viewport = tab.viewport.copy(offsetX = 0f, offsetY = 0f)
}

/** The label a document's tab gets before a rename, kept here so the strip and tests agree. */
internal fun tabLabel(file: File): String = file.name
