package com.mobixournal.desktop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window's floating surfaces must **never take the keyboard**.
 *
 * Compose Desktop's `Popup` is focusable by default, and a focusable popup takes the window's focus while
 * it is up — including a tool's hover pill, which comes and goes as the pointer crosses the strip. The
 * shortcuts are dispatched from the root of the composition, so each of those surfaces turned the keyboard
 * off until the reader clicked back into the page: the pen stayed put and `Ctrl+Z`, `G`, `Esc` did nothing.
 * The reader described it as the shortcuts "stopping after some buttons", which is exactly what a popup
 * holding the focus looks like from the page.
 *
 * Every surface is therefore a menu ([FloatingSurface]): the ones that edit text are overlays inside the
 * window rather than popups, so they keep their own focus. This test fails the build if a new `Popup` is
 * added without it, which is the only way that rule stays true as the strips grow.
 */
class DesktopPopupFocusTest {

    @Test
    fun `every popup opens as a menu that does not take the keyboard`() {
        val folder = File("src/main/java/com/mobixournal/desktop")
        val sources = folder.listFiles { file -> file.extension == "kt" }.orEmpty()
        assertTrue("the desktop sources are where the test expects them", sources.isNotEmpty())

        val offenders = mutableListOf<String>()
        for (file in sources) {
            val lines = file.readLines()
            for ((index, line) in lines.withIndex()) {
                if (!line.contains("Popup(")) continue
                // The call's own arguments: from `Popup(` to the line that closes it.
                val call = lines.drop(index).takeWhile { it.trim() != "{" }.joinToString("\n")
                if (!call.contains("properties = FloatingSurface")) {
                    offenders += "${file.name}:${index + 1}"
                }
            }
        }
        assertTrue(
            "a focusable popup turns the shortcuts off while it is up:\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    /** The properties themselves: a menu, dismissed by a click outside, and never the keyboard's owner. */
    @Test
    fun `the shared surface is a non-focusable menu`() {
        assertFalse("it must not take the keyboard", FloatingSurface.focusable)
        assertTrue("a click outside closes it", FloatingSurface.dismissOnClickOutside)
    }
}
