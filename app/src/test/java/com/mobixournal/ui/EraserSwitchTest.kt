package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The eraser switch is one button with two jobs: into the eraser from whatever is live, and back to
 * **that** tool. The second half used to be hard-coded to the pen, so a reader who was drawing with a
 * highlighter (or a line, or a rectangle) got a pen back and had to re-pick the tool every time.
 *
 * What is pinned here is the memory itself: it is kept as the tool is left, it survives reaching the
 * eraser any way (the switch, the rail's own slot, a shortcut), and it only ever answers for the two
 * erasers.
 */
class EraserSwitchTest {

    private fun state(tool: EditorTool) = EditorUiState(
        tool = tool,
        color = 0xFF112233.toInt(),
        width = 1.5f,
        highlighterColorInit = 0xFFFFFF00.toInt(),
        highlighterWidthInit = 2.6f,
    )

    /** The switch, as the barrel button, a gesture and the shortcut all run it. */
    private fun EditorUiState.switchEraser(settings: AppSettings = AppSettings()) {
        val previous = tool
        toggleTool(eraserSwitchTarget(settings))
        if (previous != tool) switchToolTo(tool, settings, previous)
    }

    @Test
    fun `the eraser gives the highlighter back, not the pen`() {
        val ui = state(EditorTool.HIGHLIGHTER)

        ui.switchEraser()
        assertEquals(EditorTool.ERASER_WHOLE, ui.tool)

        ui.switchEraser()
        assertEquals(EditorTool.HIGHLIGHTER, ui.tool)
    }

    @Test
    fun `a figure comes back as the figure it was`() {
        val ui = state(EditorTool.RECTANGLE)

        ui.switchEraser()
        ui.switchEraser()

        assertEquals(EditorTool.RECTANGLE, ui.tool)
    }

    @Test
    fun `the eraser reached from the rail still gives that tool back`() {
        val ui = state(EditorTool.LINE)
        // The rail's eraser slot — a plain tool switch, not the toggle.
        ui.switchToolTo(EditorTool.ERASER_WHOLE, AppSettings())

        // The switch now has to leave the eraser for the line, because that is what was live.
        ui.toggleTool(ui.eraserSwitchTarget(AppSettings()))
        assertEquals(EditorTool.LINE, ui.tool)
    }

    @Test
    fun `either eraser answers the switch`() {
        for (eraser in ERASER_TOOLS) {
            val ui = state(EditorTool.HIGHLIGHTER)
            ui.switchToolTo(eraser, AppSettings())

            assertEquals("$eraser is an eraser too", EditorTool.HIGHLIGHTER, ui.eraserSwitchTarget(AppSettings()))
        }
    }

    @Test
    fun `an eraser nobody arrived from falls back to the pen`() {
        // The app started on the eraser (a saved default tool): there is no previous tool to give back.
        val ui = state(EditorTool.ERASER)

        assertEquals(EditorTool.PEN, ui.eraserSwitchTarget(AppSettings()))
    }

    @Test
    fun `from any other tool the switch lands on the eraser the rail shows`() {
        val settings = AppSettings(toolGroupSelections = mapOf("eraser" to EditorTool.ERASER))

        assertEquals(EditorTool.ERASER, state(EditorTool.PEN).eraserSwitchTarget(settings))
        assertEquals(EditorTool.ERASER, state(EditorTool.HIGHLIGHTER).eraserSwitchTarget(settings))
    }

    @Test
    fun `the tool left for a selection is not remembered as the eraser's return`() {
        val ui = state(EditorTool.SELECT)
        ui.switchToolTo(EditorTool.PEN, AppSettings())
        ui.switchToolTo(EditorTool.ERASER_WHOLE, AppSettings())

        assertEquals(EditorTool.PEN, ui.eraserSwitchTarget(AppSettings()))
    }
}
