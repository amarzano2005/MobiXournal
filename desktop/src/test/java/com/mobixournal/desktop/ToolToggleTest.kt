package com.mobixournal.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The three switches of the Shortcuts page, and the two things they lean on: the pick that outlives the
 * window, and the page that always opens on the pen.
 *
 * Both halves are pressed on a real [DesktopShellState] with a settings file in a temporary folder, because
 * the questions are "what happens on the *next* press" and "what does a *restart* read back" — the two
 * things a test of a pure function cannot answer alone.
 */
class ToolToggleTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(name: String = "toggle.properties") = File(temp.root, name)

    private fun state(file: File) = DesktopShellState(DesktopSettings(file))

    @Test
    fun `a toggle picks its tool, and the next press puts back the tool that was in hand`() {
        val shell = state(file())
        shell.selectTool(DesktopTool.HIGHLIGHTER)

        shell.toggleTool(DesktopTool.ERASER)
        assertEquals("the eraser is picked", DesktopTool.ERASER, shell.tools.current)

        shell.toggleTool(DesktopTool.ERASER)
        assertEquals("the second press returns to the highlighter", DesktopTool.HIGHLIGHTER, shell.tools.current)
    }

    @Test
    fun `the eraser toggle counts both erasers as already there`() {
        val shell = state(file())
        shell.selectTool(DesktopTool.PEN)
        shell.selectTool(DesktopTool.ERASER_WHOLE)

        shell.toggleTool(DesktopTool.ERASER)

        assertEquals("a whole-stroke eraser is the eraser too", DesktopTool.PEN, shell.tools.current)
    }

    @Test
    fun `the selection toggle reads the two selection tools as one family`() {
        val shell = state(file())
        shell.selectTool(DesktopTool.PEN)
        shell.toggleTool(DesktopTool.LASSO)
        shell.toggleTool(DesktopTool.MARQUEE)

        assertEquals(DesktopTool.PEN, shell.tools.current)
    }

    @Test
    fun `the hand toggle comes back to the pen when there is nothing better`() {
        val shell = state(file())

        shell.toggleTool(DesktopTool.HAND)
        assertEquals(DesktopTool.HAND, shell.tools.current)

        shell.toggleTool(DesktopTool.HAND)
        assertEquals(DesktopTool.PEN, shell.tools.current)
    }

    @Test
    fun `the picked tool survives a restart, together with its own width`() {
        val file = file("restart.properties")
        val first = state(file)
        first.selectTool(DesktopTool.HIGHLIGHTER)
        first.pickWidth(9f)

        val second = state(file)
        assertEquals("the window comes back on the slot it was left on", DesktopTool.HIGHLIGHTER, second.tools.current)
        assertEquals("and draws at the width that slot keeps", 9f, second.storedWidth(), 0.001f)
    }

    /** The width the tool in hand is stored with — the slot value, before the highlighter's band scaling. */
    private fun DesktopShellState.storedWidth(): Float = ink.width

    @Test
    fun `a tool this build does not act on is not restored`() {
        val file = file("stale.properties")
        DesktopSettings(file).also { it.pickedTool = "SPLINES_THAT_NEVER_EXISTED" }

        assertEquals(DesktopTool.PEN, state(file).tools.current)
    }

    @Test
    fun `the toggle variants are remembered per family`() {
        val file = file("variants.properties")
        val policy = DesktopSettingsState(DesktopSettings(file))
        policy.chooseToggleEraser(DesktopTool.ERASER_WHOLE)
        policy.chooseToggleSelection(DesktopTool.LASSO)

        val reopened = DesktopSettingsState(DesktopSettings(file))
        assertEquals(DesktopTool.ERASER_WHOLE, reopened.toggleEraserTool)
        assertEquals(DesktopTool.LASSO, reopened.toggleSelectionTool)
    }

    @Test
    fun `settings open on the stylus, whatever section was showing when they closed`() {
        val policy = DesktopSettingsState(DesktopSettings(file("sections.properties")))
        policy.openAt(DesktopSettingSection.TOOLBAR)
        policy.close()

        policy.openAt()

        assertEquals(DesktopSettingSection.STYLUS, policy.section)
        assertTrue(policy.open)
    }
}
