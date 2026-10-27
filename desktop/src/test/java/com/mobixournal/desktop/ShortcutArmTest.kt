package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Renaming a shortcut is **click the row, press the key** — and the press has to reach the state, which is
 * what this pins down. The page used to keep the arm in its own `remember` and wait for a focusable column to
 * hand it a press; the window reads a press first, so the row was drawn waiting and nothing was ever bound.
 */
class ShortcutArmTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun policy() = DesktopSettingsState(DesktopSettings(File(temp.root, "arm.properties")))

    @Test
    fun `a click on a row and the next key rename that command`() {
        val policy = policy()

        policy.armShortcut(ShellCommand.SAVE.name)
        assertEquals(ShellCommand.SAVE.name, policy.armedShortcut)

        assertTrue("the press is taken by the waiting row", policy.bindArmed("Ctrl+Shift+K"))

        assertEquals("Ctrl+Shift+K", policy.shortcutTable[ShellCommand.SAVE.name])
        assertNull("the row stops waiting once it is bound", policy.armedShortcut)
    }

    @Test
    fun `while a row waits the surface does not capture the press`() {
        val policy = policy()
        policy.openAt(DesktopSettingSection.SHORTCUTS)

        assertEquals(SettingsKeyAction.CAPTURE, policy.keyAction(Key.K))
        policy.armShortcut(DesktopTool.PEN.name)
        assertEquals(
            "the press must reach the window's own handler, not be swallowed by the page",
            SettingsKeyAction.NONE,
            policy.keyAction(Key.K),
        )
    }

    @Test
    fun `escaping a waiting row leaves the table as it was`() {
        val policy = policy()
        val before = policy.shortcutTable

        policy.armShortcut(ShellCommand.SAVE.name)
        assertTrue("the waiting row is put away", policy.cancelArmed())
        assertFalse("nothing is left waiting", policy.cancelArmed())

        assertEquals("and no key moved", before, policy.shortcutTable)
    }

    @Test
    fun `a key that is already taken is reported, and the row that lost it is named`() {
        val policy = policy()

        policy.armShortcut(ShellCommand.SAVE.name)
        policy.bindArmed("Ctrl+W")

        assertEquals("Ctrl+W", policy.shortcutTable[ShellCommand.SAVE.name])
        assertEquals(
            "the row that held the key is named, not silently robbed",
            ShellCommand.CLOSE_TAB,
            policy.shortcutConflict?.command,
        )
    }

    @Test
    fun `the armed row is put away when the surface closes`() {
        val policy = policy()
        policy.openAt(DesktopSettingSection.SHORTCUTS)
        policy.armShortcut(ShellCommand.OPEN.name)

        policy.close()

        assertNull(policy.armedShortcut)
    }
}
