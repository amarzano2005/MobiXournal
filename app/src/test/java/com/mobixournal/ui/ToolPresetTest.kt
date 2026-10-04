package com.mobixournal.ui

import com.mobixournal.format.model.LineStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A preset is a snapshot of the live pen: capturing then re-applying it has to put the editor back
 * exactly where it was, whatever was changed in between.
 */
class ToolPresetTest {

    private fun state(tool: EditorTool, color: Int, width: Float) =
        EditorUiState(
            tool = tool,
            color = color,
            width = width,
            highlighterColorInit = 0xFFF0D000.toInt(),
            highlighterWidthInit = 2.6f,
        )

    @Test
    fun `capture snapshots the live tool state`() {
        val ui = state(EditorTool.HIGHLIGHTER, 0xFF112233.toInt(), 4.5f)
        ui.lineStyle = LineStyle.DASHED

        val preset = ToolPreset.capture(ui, "Yellow marker")

        assertEquals(EditorTool.HIGHLIGHTER, preset.tool)
        assertEquals(0xFF112233.toInt(), preset.colorArgb)
        assertEquals(4.5f, preset.widthPt, 0f)
        assertEquals(LineStyle.DASHED, preset.lineStyle)
        assertEquals("yellow-marker", preset.id)
    }

    @Test
    fun `apply restores every captured field after the pen moves on`() {
        val ui = state(EditorTool.PEN, 0xFF000000.toInt(), 1.5f)
        ui.lineStyle = LineStyle.DOTTED
        val preset = ToolPreset.capture(ui, "Fine black")

        ui.tool = EditorTool.ERASER
        ui.color = 0xFFFF0000.toInt()
        ui.width = 9f
        ui.lineStyle = LineStyle.DASHED

        preset.applyToState(ui)

        assertEquals(EditorTool.PEN, ui.tool)
        assertEquals(0xFF000000.toInt(), ui.color)
        assertEquals(1.5f, ui.width, 0f)
        assertEquals(LineStyle.DOTTED, ui.lineStyle)
    }

    @Test
    fun `apply writes the persisted half back into settings`() {
        val ui = state(EditorTool.PEN, 0xFF00FF00.toInt(), 2.5f)
        val preset = ToolPreset.capture(ui, "Green pen")

        val next = preset.applyToSettings(AppSettings(lastWidth = 1.5f))

        assertEquals(2.5f, next.lastWidth, 0f)
        assertEquals(0xFF00FF00.toInt(), next.lastColor)
    }

    @Test
    fun `a highlighter preset writes the highlighter's slot, not the pen's`() {
        val preset = ToolPreset("m", "Fat marker", EditorTool.HIGHLIGHTER, 0xFFFFFF00.toInt(), 8.5f)

        val next = preset.applyToSettings(AppSettings(lastWidth = 1.5f))

        // The pen keeps its own medium width: a fat marker preset must not make the pen fat.
        assertEquals(1.5f, next.lastWidth, 0f)
        assertEquals(8.5f, next.highlighterWidth, 0f)
    }

    @Test
    fun `ids are kebab-case and never empty`() {
        assertEquals("bold-red-pen", ToolPreset.slugId("Bold  Red /Pen"))
        assertEquals("preset", ToolPreset.slugId("!!!"))
    }
}
