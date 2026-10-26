package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import com.mobixournal.design.ShortcutKeys

/**
 * One row of the window's key table: a command, and the key it ships with.
 *
 * [tool] and [command] are the two kinds of thing a key can reach — a slot of the strips, or a shell
 * command — and exactly one of them is set, so a row can never be both and the resolution below has no
 * ambiguity to resolve.
 */
data class ShortcutEntry(
    val id: String,
    val label: String,
    val defaultKey: String,
    val tool: DesktopTool? = null,
    val command: ShellCommand? = null,
)

/**
 * **The window's key table**, as data: what ships, what the file changed, and which key reaches what.
 *
 * It exists because the table used to be code — `shellCommand`'s `when` and `DesktopTool.shortcut`'s
 * strings — and a table written as code cannot be remapped. The defaults are the same bindings as before
 * (`docs/ui-modernization.md` §3), so a window that has never touched the section behaves identically;
 * what changes is that the lookup now reads a map, and the map is what the Shortcuts section writes.
 *
 * The format, the conflict rule and the reset live in [ShortcutKeys] (in `mobix-core`), which the phone
 * reads too, so a stored table means the same thing in both shells.
 */
internal object ShortcutTable {

    /** The commands a key can run, in the order the Settings section lists them. */
    val entries: List<ShortcutEntry> = tools() + commands()

    private fun tools(): List<ShortcutEntry> = DesktopTool.entries.map { tool ->
        ShortcutEntry(id = tool.name, label = tool.label, defaultKey = tool.shortcut, tool = tool)
    }

    private fun commands(): List<ShortcutEntry> = listOf(
        entry(ShellCommand.OPEN, "Open a document", "Ctrl+T"),
        entry(ShellCommand.SAVE, "Save", "Ctrl+S"),
        entry(ShellCommand.SAVE_AS, "Save as", "Ctrl+Shift+S"),
        entry(ShellCommand.EXPORT_PDF, "Export PDF", "Ctrl+E"),
        entry(ShellCommand.CLOSE_TAB, "Close the tab", "Ctrl+W"),
        entry(ShellCommand.NEXT_TAB, "Next tab", "Ctrl+Tab"),
        entry(ShellCommand.PREVIOUS_TAB, "Previous tab", "Ctrl+Shift+Tab"),
        entry(ShellCommand.UNDO, "Undo", "Ctrl+Z"),
        entry(ShellCommand.REDO, "Redo", "Ctrl+Shift+Z"),
        entry(ShellCommand.CUT_SELECTION, "Cut", "Ctrl+X"),
        entry(ShellCommand.COPY_SELECTION, "Copy", "Ctrl+C"),
        entry(ShellCommand.PASTE_CLIPBOARD, "Paste", "Ctrl+V"),
        entry(ShellCommand.DUPLICATE_SELECTION, "Duplicate", "Ctrl+D"),
        entry(ShellCommand.DELETE_SELECTION, "Delete the selection", "Delete"),
        entry(ShellCommand.TOGGLE_SIDEBAR, "Show the pages", "Ctrl+B"),
        entry(ShellCommand.OPEN_SETTINGS, "Settings", "Ctrl+,"),
        entry(ShellCommand.TOGGLE_FULLSCREEN, "Full screen", "F11"),
        entry(ShellCommand.START_PAN, "Pan with the pointer", "Space"),
        entry(ShellCommand.NEXT_PAGE, "Next page", "PageDown"),
        entry(ShellCommand.PREVIOUS_PAGE, "Previous page", "PageUp"),
        entry(ShellCommand.ZOOM_IN, "Zoom in", "Ctrl+="),
        entry(ShellCommand.ZOOM_OUT, "Zoom out", "Ctrl+-"),
        entry(ShellCommand.FIT_PAGE, "Fit the page", "Ctrl+0"),
        entry(ShellCommand.FIT_WIDTH, "Fit the width", "Ctrl+1"),
        entry(ShellCommand.DISMISS_FAILURE, "Dismiss the notice", "Esc"),
    )

    private fun entry(command: ShellCommand, label: String, key: String) =
        ShortcutEntry(id = command.name, label = label, defaultKey = key, command = command)

    /** The factory table, as the file stores it. */
    fun defaults(): Map<String, String> = entries.associate { it.id to it.defaultKey }

    /** The ids this build knows, for the decoder's filter. */
    fun ids(): List<String> = entries.map { it.id }

    /** [stored] laid over the defaults — the table the window actually reads. */
    fun merged(stored: Map<String, String>): Map<String, String> =
        ShortcutKeys.merge(defaults(), stored)

    /** The key [id] answers to in [table], or "" when it has none. */
    fun key(table: Map<String, String>, id: String): String = table[id] ?: ""

    /** The tool [name] reaches in [table], or null when no tool is bound to it. */
    fun tool(table: Map<String, String>, name: String): DesktopTool? =
        entries.firstOrNull { it.tool != null && key(table, it.id).equals(name, ignoreCase = true) }?.tool

    /** The command [name] reaches in [table], or null when no command is bound to it. */
    fun command(table: Map<String, String>, name: String): ShellCommand? =
        entries.firstOrNull { it.command != null && key(table, it.id).equals(name, ignoreCase = true) }?.command

    /** The row an id names, or null for an id a newer build wrote. */
    fun entry(id: String): ShortcutEntry? = entries.firstOrNull { it.id == id }

    /**
     * The **canonical name** of a key press — `"Ctrl+Shift+S"`, `"PageDown"`, `"Space"` — or null for a key
     * this table does not name.
     *
     * This is the one place a Compose key becomes text, which is what makes the table a map of strings: the
     * pills print these names, the file stores them, and a press is compared as the same string.
     */
    fun name(key: Key, ctrl: Boolean = false, shift: Boolean = false, alt: Boolean = false): String? {
        val base = names[key] ?: return null
        val modifiers = buildString {
            if (ctrl) append("Ctrl+")
            if (alt) append("Alt+")
            if (shift) append("Shift+")
        }
        return modifiers + base
    }

    /**
     * The key a name stands for — the inverse of [name] for a bare key (`"B"`, `"PageDown"`).
     *
     * What a caller holding a printed shortcut needs in order to press it, which is how a strip's pill and
     * the keyboard are checked against one another.
     */
    fun keyFor(name: String): Key? = names.entries.firstOrNull { it.value == name }?.key

    /** The keys the table (and the window's leftovers) name, by the text a row shows. */
    private val names: Map<Key, String> = buildMap {
        val letters = mapOf(
            Key.A to "A", Key.B to "B", Key.C to "C", Key.D to "D", Key.E to "E", Key.F to "F",
            Key.G to "G", Key.H to "H", Key.I to "I", Key.J to "J", Key.K to "K", Key.L to "L",
            Key.M to "M", Key.N to "N", Key.O to "O", Key.P to "P", Key.Q to "Q", Key.R to "R",
            Key.S to "S", Key.T to "T", Key.U to "U", Key.V to "V", Key.W to "W", Key.X to "X",
            Key.Y to "Y", Key.Z to "Z",
        )
        putAll(letters)
        put(Key.Comma, ",")
        put(Key.Tab, "Tab")
        put(Key.F11, "F11")
        put(Key.Delete, "Delete")
        put(Key.Backspace, "Backspace")
        put(Key.Spacebar, "Space")
        put(Key.Escape, "Esc")
        put(Key.PageUp, "PageUp")
        put(Key.PageDown, "PageDown")
        put(Key.DirectionUp, "Up")
        put(Key.DirectionDown, "Down")
        put(Key.DirectionLeft, "Left")
        put(Key.DirectionRight, "Right")
        put(Key.Zero, "0")
        put(Key.One, "1")
        put(Key.Two, "2")
        put(Key.Three, "3")
        put(Key.Equals, "=")
        put(Key.Minus, "-")
        put(Key.Plus, "+")
        put(Key.RightBracket, "]")
        put(Key.LeftBracket, "[")
        put(Key.NumPadAdd, "Num+")
        put(Key.NumPadSubtract, "Num-")
        put(Key.NumPad0, "Num0")
    }
}
