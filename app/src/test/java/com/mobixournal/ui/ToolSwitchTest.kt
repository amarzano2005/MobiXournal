package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every tool switch has to carry *that tool's* colour and width across, on every path that makes
 * one. These pin down the bug behind "I switch from a fat figure/highlighter to the pen and the pen
 * comes out fat too": the outgoing style goes into its own slot and the incoming one comes back out
 * of it, so no path can leave the pen holding somebody else's width.
 */
class ToolSwitchTest {

    private fun state(tool: EditorTool, width: Float) = EditorUiState(
        tool = tool,
        color = 0xFF112233.toInt(),
        width = width,
        highlighterColorInit = 0xFFFFFF00.toInt(),
        highlighterWidthInit = 2.6f,
    )

    @Test
    fun `switching from a fat figure lands the pen on the pen's own width`() {
        val ui = state(EditorTool.ELLIPSE, 8.5f)

        ui.switchToolTo(
            EditorTool.PEN,
            AppSettings(lastColor = 0xFF112233.toInt(), lastWidth = 1.5f, shapeWidth = 8.5f),
        )

        assertEquals(EditorTool.PEN, ui.tool)
        assertEquals(1.5f, ui.width, 0f)
        assertEquals(0xFF112233.toInt(), ui.color)
    }

    @Test
    fun `switching from the highlighter lands the pen on the pen's own width`() {
        val ui = state(EditorTool.HIGHLIGHTER, 8.5f)

        ui.switchToolTo(EditorTool.PEN, AppSettings(lastWidth = 1.5f, highlighterWidth = 8.5f))

        assertEquals(1.5f, ui.width, 0f)
    }

    @Test
    fun `each tool gets its own width back after a round trip`() {
        val ui = state(EditorTool.PEN, 2.6f)
        // The live fields the slider would have written, and their persisted mirror.
        ui.highlighterWidth = 8.5f
        var settings = AppSettings(lastWidth = 2.6f, highlighterWidth = 8.5f)

        settings = ui.switchToolTo(EditorTool.HIGHLIGHTER, settings)
        assertEquals(8.5f, ui.width, 0f)

        // The pen restores the width it was left with, not the highlighter's.
        settings = ui.switchToolTo(EditorTool.PEN, settings)
        assertEquals(2.6f, ui.width, 0f)

        // ...and the highlighter still has the one it was left with.
        ui.switchToolTo(EditorTool.HIGHLIGHTER, settings)
        assertEquals(8.5f, ui.width, 0f)
    }

    @Test
    fun `figures keep the width dragged to when moving between figures`() {
        val ui = state(EditorTool.ELLIPSE, 6f)

        // Arriving from another figure must not reset the width to the slot default.
        ui.switchToolTo(EditorTool.RECTANGLE, AppSettings(shapeWidth = 6f))

        assertEquals(6f, ui.width, 0f)
    }

    @Test
    fun `arriving at a figure from elsewhere adopts the figure slot`() {
        val ui = state(EditorTool.PEN, 1.5f)

        ui.switchToolTo(EditorTool.RECTANGLE, AppSettings(shapeWidth = 2.6f))

        assertEquals(2.6f, ui.width, 0f)
    }

    @Test
    fun `a tool that doesn't ink keeps the live width and hands it back`() {
        val ui = state(EditorTool.PEN, 0.85f)

        var settings = ui.switchToolTo(EditorTool.ERASER_WHOLE, AppSettings(lastWidth = 0.85f))
        assertEquals(0.85f, ui.width, 0f)

        settings = ui.switchToolTo(EditorTool.PEN, settings)
        assertEquals(0.85f, ui.width, 0f)
        assertEquals(0xFF112233.toInt(), ui.color)
    }

    @Test
    fun `the highlighter keeps its colour across a switch away and back`() {
        val ui = state(EditorTool.PEN, 1.5f)
        var settings = AppSettings(lastColor = 0xFF112233.toInt())

        settings = ui.switchToolTo(EditorTool.HIGHLIGHTER, settings)
        ui.color = 0xFFFF0000.toInt()

        settings = ui.switchToolTo(EditorTool.PEN, settings)
        assertEquals(0xFF112233.toInt(), ui.color)

        ui.switchToolTo(EditorTool.HIGHLIGHTER, settings)
        assertEquals(0xFFFF0000.toInt(), ui.color)
    }

    @Test
    fun `width changes land in the live tool's own slot`() {
        var settings = AppSettings()

        settings = settings.withWidthFor(EditorTool.HIGHLIGHTER, 8.5f)
        assertEquals(8.5f, settings.highlighterWidth, 0f)
        assertEquals(1.5f, settings.lastWidth, 0f)

        settings = settings.withWidthFor(EditorTool.ELLIPSE, 6f)
        assertEquals(6f, settings.shapeWidth, 0f)
        assertEquals(1.5f, settings.lastWidth, 0f)

        settings = settings.withWidthFor(EditorTool.PEN, 0.85f)
        assertEquals(0.85f, settings.lastWidth, 0f)
    }

    @Test
    fun `figure width change matching a pen slot updates defaultShapeSlot`() {
        var settings = AppSettings(penWidths = listOf(0.85f, 1.5f, 2.6f), defaultShapeSlot = 1)

        // Setting a figure to 2.6f (slot index 2) should update both shapeWidth and defaultShapeSlot
        settings = settings.withWidthFor(EditorTool.RECTANGLE, 2.6f)
        assertEquals(2.6f, settings.shapeWidth, 0f)
        assertEquals(2, settings.defaultShapeSlot)

        // Setting a custom width not in slots leaves defaultShapeSlot unchanged
        settings = settings.withWidthFor(EditorTool.TRIANGLE, 4.0f)
        assertEquals(4.0f, settings.shapeWidth, 0f)
        assertEquals(2, settings.defaultShapeSlot)
    }
}
