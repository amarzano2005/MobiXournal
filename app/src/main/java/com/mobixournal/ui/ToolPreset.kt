package com.mobixournal.ui

import com.mobixournal.format.model.LineStyle
import com.mobixournal.render.DrawingSurfaceView

/**
 * A named snapshot of the whole tool configuration — the tool itself plus every knob the rail can
 * turn on it (colour, width, line style).
 *
 * A preset is *only* a value: capturing one reads the live editor state, applying one writes the
 * same fields back through the same paths the rail's own tool switch uses. Nothing here talks to storage
 * (that's [AppSettings]) or to the UI, which is what keeps it unit-testable off-device.
 */
data class ToolPreset(
    val id: String,
    val name: String,
    val tool: EditorTool,
    val colorArgb: Int,
    val widthPt: Float,
    val lineStyle: LineStyle = LineStyle.PLAIN,
) {
    companion object {
        /**
         * Snapshot the live pen: everything the preset needs comes from [ui], since fill is gone and
         * the line style is held there too.
         */
        fun capture(
            ui: EditorUiState,
            name: String,
            id: String = slugId(name),
        ): ToolPreset = ToolPreset(
            id = id,
            name = name,
            tool = ui.tool,
            colorArgb = ui.color,
            widthPt = ui.width,
            lineStyle = ui.lineStyle,
        )

        /** A stable kebab-case handle for a preset named [name], falling back to `preset`. */
        fun slugId(name: String): String =
            name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
                .joinToString("").trim('-').replace(Regex("-+"), "-")
                .ifEmpty { "preset" }
    }
}

/** Restore the session-only half of the preset onto [ui]. */
fun ToolPreset.applyToState(ui: EditorUiState) {
    ui.tool = tool
    ui.color = colorArgb
    ui.width = widthPt
    // The highlighter keeps its own colour/width pair in sync, so leaving it and coming back
    // restores what the preset set rather than the value it had before.
    if (tool == EditorTool.HIGHLIGHTER) {
        ui.highlighterColor = colorArgb
        ui.highlighterWidth = widthPt
    }
    ui.lineStyle = lineStyle
}

/**
 * The persisted half: the settings that [settings] becomes once this preset is active. The width
 * goes into the slot the preset's tool owns — the pen's [AppSettings.lastWidth], the highlighter's
 * [AppSettings.highlighterWidth] or a figure's [AppSettings.shapeWidth] — so applying a fat
 * highlighter preset can't move the pen's own width.
 */
fun ToolPreset.applyToSettings(settings: AppSettings): AppSettings {
    val withWidth = settings.withWidthFor(tool, widthPt)
    return withWidth.withColorUsed(colorArgb)
}

/**
 * Activate [preset] — the one entry point the rail and a radial-palette slot both call.
 *
 * Every write below is the same one the toolbar makes for that knob (see `EditorRegions`), so a
 * preset can't mean anything the user couldn't have set by hand.
 */
fun applyToolPreset(
    preset: ToolPreset,
    ui: EditorUiState,
    surface: DrawingSurfaceView?,
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
) {
    preset.applyToState(ui)
    surface?.let {
        it.applyTool(preset.tool)
        it.colorArgb = preset.colorArgb
        it.baseWidthPt = preset.widthPt
        it.currentLineStyle = preset.lineStyle
    }
    onSettingsChange(preset.applyToSettings(settings))
}
