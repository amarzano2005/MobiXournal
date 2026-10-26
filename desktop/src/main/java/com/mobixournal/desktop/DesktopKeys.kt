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

    /** Export the document as a PDF, asking where to put it: `Ctrl+E`. */
    EXPORT_PDF,

    /**
     * Take back the active tab's last command (`Ctrl+Z`). **Unavailable while its history is empty** —
     * the command is then not handled at all (`apply` answers false), which is the state a header
     * control greys the action out on.
     */
    UNDO,

    /** Re-apply the step an undo took back (`Ctrl+Shift+Z`); unavailable with nothing to redo. */
    REDO,

    /**
     * Remove what the marquee caught (`Delete` or `Backspace`). **Unavailable with nothing selected**, so the
     * key can never delete by accident — the state a header control would grey out.
     */
    DELETE_SELECTION,

    /** The selection's clipboard: `Ctrl+X`, `Ctrl+C` and `Ctrl+V`, as the tablet's bar offers them. */
    CUT_SELECTION,
    COPY_SELECTION,
    PASTE_CLIPBOARD,

    /** `Ctrl+D`: a copy of the selection lands beside it, and the copy is left selected. */
    DUPLICATE_SELECTION,

    DISMISS_FAILURE,

    /** Open the Settings surface (`Ctrl+,`), the window's own page for every choice it keeps. */
    OPEN_SETTINGS,

    /**
     * Take the whole screen, or give it back (`F11`) — the placement's own key, and the only way the
     * display's target rate is ever applied (`DesktopDisplay`).
     */
    TOGGLE_FULLSCREEN,
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
 * The tool a key press picks, or null when the key is not a tool's accelerator.
 *
 * `Shift+X` is the axes tool while a bare `X` is the text box, and `Alt+K` is the logic-gate palette
 * while `K` is the passive one — which is why both modifiers are part of the lookup instead of being
 * ignored. `Ctrl` never picks a tool, so the shell's own commands keep their letters (`Ctrl+B` is the
 * sidebar, not a highlighter).
 *
 * The binding is read from [table] — the window's key table, `ShortcutTable.entries` for a window that has
 * not remapped anything — so a key a reader has moved is the key this answers to, and the one it moved away
 * from is no longer a tool's.
 */
internal fun toolForKey(
    table: Map<String, String>,
    key: Key,
    shift: Boolean,
    ctrl: Boolean,
    alt: Boolean = false,
): DesktopTool? {
    if (ctrl) return null
    val name = ShortcutTable.name(key, ctrl, shift, alt) ?: return null
    return ShortcutTable.tool(table, name)
}

/** The same lookup against the factory table — what the window does before anything is remapped. */
internal fun toolForKey(key: Key, shift: Boolean, ctrl: Boolean, alt: Boolean = false): DesktopTool? =
    toolForKey(ShortcutTable.defaults(), key, shift, ctrl, alt)

/**
 * The shortcut table of `docs/ui-modernization.md`, as a decision.
 *
 * Order matters in two places: `Ctrl + 0` is "fit page" **only** without Shift (with Shift it is the
 * zoom-in key on layouts where `=` shares the `0`), and the zoom keys are checked before it so the
 * more specific binding wins.
 */
internal fun shellCommand(key: Key, ctrl: Boolean, shift: Boolean): ShellCommand? =
    shellCommand(ShortcutTable.defaults(), key, ctrl, shift, alt = false)

/**
 * The same table, read from [table] — the window's own, so a remapped key reaches the command it was
 * bound to and the key it was taken from reaches nothing.
 *
 * What is left after the table is in [unlistedCommand]: the layout variants (`=` arriving as `Shift+0` on
 * an Italian keyboard, the keypad's `+`/`-`), `Backspace` beside `Delete`, and the arrow keys beside
 * `Page Up`/`Page Down`. None of those is a row of the table, so none of them is remappable — and none of
 * them stops working when another row is.
 */
internal fun shellCommand(
    table: Map<String, String>,
    key: Key,
    ctrl: Boolean,
    shift: Boolean,
    alt: Boolean = false,
): ShellCommand? {
    ShortcutTable.name(key, ctrl, shift, alt)
        ?.let { name -> ShortcutTable.command(table, name)?.let { return it } }
    return unlistedCommand(key, ctrl, shift)
}

/** The bindings the table does not own: a second key for a command, and the layout's own variants. */
private fun unlistedCommand(key: Key, ctrl: Boolean, shift: Boolean): ShellCommand? = when {
    // On an Italian keyboard `=` is **Shift + 0**, so the zoom key arrives as `Ctrl + Shift + 0`.
    ctrl && shift && key == Key.Zero -> ShellCommand.ZOOM_IN
    ctrl && (key == Key.NumPadAdd || key == Key.Plus) -> ShellCommand.ZOOM_IN
    ctrl && key == Key.NumPadSubtract -> ShellCommand.ZOOM_OUT
    !ctrl && key == Key.Backspace -> ShellCommand.DELETE_SELECTION
    !ctrl && key == Key.DirectionDown -> ShellCommand.NEXT_PAGE
    !ctrl && key == Key.DirectionUp -> ShellCommand.PREVIOUS_PAGE
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

/**
 * `Alt + Down`, the key that opens the picked tool's **group picker** and closes it again.
 *
 * It is not in [shellCommand] because that table has no `Alt` column and a bare `Down` already means
 * "next page": the picker's key is only a picker's key while `Alt` is held, which this test says
 * once instead of the window's key handler saying it twice.
 */
internal fun toolPickerKey(key: Key, alt: Boolean): Boolean = alt && key == Key.DirectionDown

/** The mirror of [isZoomIn]: `Ctrl + -` and the keypad's `-`. */
internal fun isZoomOut(key: Key): Boolean = key == Key.Minus || key == Key.NumPadSubtract

/** Zooms a tab around its centre (the keyboard path; the wheel zooms around the cursor). */
internal fun zoomBy(tab: DesktopTab, factor: Float) {
    tab.fit = FitMode.NONE
    tab.viewport = tab.viewport.copy(zoom = (tab.viewport.zoom * factor).coerceIn(Viewport.MIN_ZOOM, Viewport.MAX_ZOOM))
}

/**
 * Select a page, keeping the tab's fit mode and re-centring the free viewport on it.
 *
 * The selection goes with the page: its refs name a layer and an index **of the page it was drawn on**, so
 * carrying it across a page turn would point at whatever happened to be at those indices there.
 */
internal fun selectPage(tab: DesktopTab, index: Int) {
    tab.page = index.coerceIn(0, (tab.pageCount - 1).coerceAtLeast(0))
    tab.viewport = tab.viewport.copy(offsetX = 0f, offsetY = 0f)
    tab.selection = null
}

/** The label a document's tab gets before a rename, kept here so the strip and tests agree. */
internal fun tabLabel(file: File): String = file.name
