package com.mobixournal.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A settings **choice** is a select, not a column of radio rows.
 *
 * One answer per row said the same thing four times: a dot and a line for every answer nobody picked, a
 * page to scan to find the answer already set, and no line of its own for the answer in hand. The choice
 * is now one row — its name, the answer, a chevron — with the rest of the list in the app's own pop-up
 * panel, which is also the grammar the window's Settings keeps (`docs/desktop-settings.md`).
 *
 * Read off the sources, like the full-bleed guard beside it: the mistake this prevents is the component
 * quietly growing a radio row back, which compiles and looks deliberate.
 */
class SettingsChoiceTest {

    @Test
    fun `a choice shows the answer in hand and opens the rest as a menu`() {
        val body = optionGroupBody()
        assertTrue("OptionGroup is where the test expects it", body.isNotEmpty())
        assertTrue("the answer in hand must be on the row itself", body.contains("label(selected)"))
        assertTrue(
            "the rest of the list opens in the app's own pop-up panel, not a Material menu",
            body.contains("ToolbarMenu("),
        )
        assertTrue("the row is the trigger, so the whole line is the hit area", body.contains("clickable("))
    }

    @Test
    fun `no radio row is left in the choices`() {
        assertFalse(
            "one dot per answer is the control this replaced",
            optionGroupBody().contains("RadioButton("),
        )
        val widgets = File(repoRoot(), "app/src/main/java/com/mobixournal/ui/SettingsWidgets.kt").readText()
        assertFalse(
            "and nothing else in the settings widgets builds one either",
            widgets.contains("RadioButton("),
        )
    }

    /** `OptionGroup`'s body: from its signature to the next `}` in the first column. */
    private fun optionGroupBody(): String {
        val source = File(repoRoot(), "app/src/main/java/com/mobixournal/ui/SettingsWidgets.kt").readText()
        val signature = "fun <T> OptionGroup("
        val start = source.indexOf(signature)
        if (start < 0) return ""
        val end = source.indexOf("\n}", start)
        return if (end < 0) source.substring(start) else source.substring(start, end)
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) return dir
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }
}
