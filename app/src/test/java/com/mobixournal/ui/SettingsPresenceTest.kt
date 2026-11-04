package com.mobixournal.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Leaving Settings is a **motion**, not a snap.
 *
 * The settings screen is an overlay on the still-composed editor, so nothing tears its composition
 * down for it — but it used to be gated by a bare `if (ui.showSettings)`, and the overlay was taken
 * away on the frame the flag cleared while `liquidGlassSpringAnimation` was told only about the
 * arrival. The closing half never played, which the reader reported as "the animation is missing when
 * you leave Settings". [com.mobixournal.ui.theme.DialogPresence] is the missing half: it holds the
 * overlay composed for the exit and tells the spring inside that it is closing.
 *
 * The wiring is read out of the source tree, because the mistake compiles and looks right: nothing
 * fails when a presence gate is dropped for an `if`. Their order is the whole rule — the gate must
 * wrap the screen, and the spring must be inside the gate or it never hears the exit.
 */
class SettingsPresenceTest {

    @Test
    fun `the settings overlay is held composed for its exit`() {
        val source = File(uiRoot(), "EditorScreen.kt").readText()
        val hold = source.indexOf("DialogPresence(ui.showSettings)")
        val screen = source.indexOf("SettingsScreen(")
        assertTrue(
            "EditorScreen must compose SettingsScreen inside DialogPresence(ui.showSettings): the overlay " +
                "has to stay composed while it draws back out",
            hold >= 0 && screen > hold,
        )
        val spring = source.indexOf(".liquidGlassSpringAnimation(", startIndex = screen)
        assertTrue(
            "the settings screen must run the shared glass spring on its own modifier, inside the gate",
            spring > screen,
        )
        assertTrue(
            "a bare `if (ui.showSettings) {` gate is the overlay torn down mid-departure — gate it with " +
                "DialogPresence instead",
            !source.contains("if (ui.showSettings) {"),
        )
    }

    private fun uiRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "AGENTS.md").isFile && File(dir, "docs").isDirectory) {
                return File(dir, "app/src/main/java/com/mobixournal/ui")
            }
            dir = dir.parentFile
        }
        error("no repository root above ${File(".").absolutePath}: this test must run inside the repo")
    }
}
