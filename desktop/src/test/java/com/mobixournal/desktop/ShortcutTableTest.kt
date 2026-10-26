package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **key table**: what ships, what a remap does to the command behind a key, and what survives a
 * restart.
 *
 * The tests read the same seam the keyboard does — a key press becomes a name ([ShortcutTable.name]) and the
 * name is resolved against the table — so a binding proved here is a binding the window honours, without a
 * window: Compose's `KeyEvent` cannot be built outside one, which is why the resolution is a pure function in
 * the first place.
 */
class ShortcutTableTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun state(name: String = "desktop.properties") =
        DesktopSettingsState(DesktopSettings(File(temp.root, name)))

    @Test
    fun `the factory table is the table the doc lists`() {
        val table = ShortcutTable.defaults()
        assertEquals("B", table["PEN"])
        assertEquals("Shift+E", table["ERASER_WHOLE"])
        assertEquals("Alt+K", table["CIRCUIT_LOGIC"])
        assertEquals("Ctrl+S", table[ShellCommand.SAVE.name])
        assertEquals("Ctrl+Shift+S", table[ShellCommand.SAVE_AS.name])
        assertEquals("F11", table[ShellCommand.TOGGLE_FULLSCREEN.name])
        assertEquals("Ctrl+,", table[ShellCommand.OPEN_SETTINGS.name])
        // Every row the page draws is a row the window resolves: the two lists are the same list.
        assertTrue(ShortcutTable.entries.all { it.id in table })
    }

    @Test
    fun `a press is named the way the file stores it`() {
        assertEquals("Ctrl+S", ShortcutTable.name(Key.S, ctrl = true))
        assertEquals("Ctrl+Shift+Z", ShortcutTable.name(Key.Z, ctrl = true, shift = true))
        assertEquals("Alt+K", ShortcutTable.name(Key.K, alt = true))
        assertEquals("B", ShortcutTable.name(Key.B))
        assertEquals("PageDown", ShortcutTable.name(Key.PageDown))
        assertEquals("Space", ShortcutTable.name(Key.Spacebar))
        assertEquals("Esc", ShortcutTable.name(Key.Escape))
        assertNull("a key the table does not name has no row to bind", ShortcutTable.name(Key.F1))
    }

    @Test
    fun `the factory bindings resolve exactly as before the table was data`() {
        val table = ShortcutTable.defaults()
        assertEquals(ShellCommand.SAVE, shellCommand(table, Key.S, ctrl = true, shift = false))
        assertEquals(ShellCommand.SAVE_AS, shellCommand(table, Key.S, ctrl = true, shift = true))
        assertEquals(ShellCommand.EXPORT_PDF, shellCommand(table, Key.E, ctrl = true, shift = false))
        assertEquals(ShellCommand.COPY_SELECTION, shellCommand(table, Key.C, ctrl = true, shift = false))
        assertEquals(ShellCommand.ZOOM_IN, shellCommand(table, Key.Equals, ctrl = true, shift = false))
        assertEquals(
            "the layout's own variant is not a table row",
            ShellCommand.ZOOM_IN,
            shellCommand(table, Key.Zero, ctrl = true, shift = true),
        )
        assertEquals(ShellCommand.DELETE_SELECTION, shellCommand(table, Key.Backspace, ctrl = false, shift = false))
        assertEquals(ShellCommand.NEXT_PAGE, shellCommand(table, Key.DirectionDown, ctrl = false, shift = false))
        assertNull("a bare S is a tool's letter, not a command", shellCommand(table, Key.S, ctrl = false, shift = false))
        assertEquals(DesktopTool.MARQUEE, toolForKey(table, Key.S, shift = false, ctrl = false))
    }

    @Test
    fun `a remapped command is reached by its new key and not by the old one`() {
        val policy = state()
        policy.rebind(ShellCommand.SAVE.name, "Ctrl+Shift+K")

        val table = policy.shortcutTable
        assertEquals(ShellCommand.SAVE, shellCommand(table, Key.K, ctrl = true, shift = true))
        assertNull("the key it left reaches nothing", shellCommand(table, Key.S, ctrl = true, shift = false))
        assertEquals("and Save As keeps its own key", ShellCommand.SAVE_AS, shellCommand(table, Key.S, ctrl = true, shift = true))
    }

    @Test
    fun `a remapped tool is reached by its new key and the letter it left is free`() {
        val policy = state()
        policy.rebind(DesktopTool.PEN.name, "Q")
        val table = policy.shortcutTable
        assertEquals(DesktopTool.PEN, toolForKey(table, Key.Q, shift = false, ctrl = false))
        assertNull(toolForKey(table, Key.B, shift = false, ctrl = false))
        assertEquals("another tool's letter is untouched", DesktopTool.HIGHLIGHTER, toolForKey(table, Key.H, shift = false, ctrl = false))
    }

    @Test
    fun `a taken key is moved, and the row says which command lost it`() {
        val policy = state()
        policy.rebind(ShellCommand.SAVE.name, "Ctrl+W")
        assertEquals("the tab command lost it", ShellCommand.CLOSE_TAB.name, policy.shortcutConflict?.id)
        val table = policy.shortcutTable
        assertEquals(ShellCommand.SAVE, shellCommand(table, Key.W, ctrl = true, shift = false))
        assertNull("and the changed row has no key at all", table[ShellCommand.CLOSE_TAB.name])
    }

    @Test
    fun `a tool cannot take the shell's Ctrl column`() {
        val policy = state()
        policy.rebind(DesktopTool.PEN.name, "Ctrl+Q")
        assertNull("the binding is refused", ShortcutTable.key(policy.shortcutTable, DesktopTool.PEN.name).takeIf { it == "Ctrl+Q" })
        assertEquals("and the row that refused it is named", DesktopTool.PEN.name, policy.shortcutConflict?.id)
    }

    @Test
    fun `only the changed rows are stored, and a restart reads them back`() {
        val file = File(temp.root, "keys.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        policy.rebind(ShellCommand.SAVE.name, "Ctrl+Shift+K")
        assertEquals(setOf(ShellCommand.SAVE.name), policy.shortcuts.keys)

        val reopened = DesktopSettingsState(DesktopSettings(file))
        assertEquals(ShellCommand.SAVE, shellCommand(reopened.shortcutTable, Key.K, ctrl = true, shift = true))
        // Everything else is the factory's, because the file never named it.
        assertEquals("B", ShortcutTable.key(reopened.shortcutTable, DesktopTool.PEN.name))
    }

    @Test
    fun `restore defaults empties the file and the table`() {
        val file = File(temp.root, "restore.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        policy.rebind(ShellCommand.SAVE.name, "Ctrl+Shift+K")
        policy.restoreShortcuts()
        assertTrue(policy.shortcuts.isEmpty())
        assertNull(policy.shortcutConflict)
        val reopened = DesktopSettings(file)
        assertTrue("no key at all means the factory table", reopened.shortcuts.isEmpty())
        assertEquals(ShellCommand.SAVE, shellCommand(ShortcutTable.merged(reopened.shortcuts), Key.S, ctrl = true, shift = false))
    }
}
