package com.mobixournal.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * **Which tool the window has in hand** — the picked slot, the slot before it, and the file that remembers
 * both.
 *
 * A piece of the shell's state rather than a second one: [DesktopShellState] owns it and forwards it as
 * `tools`, so the strips, the page and the keyboard read the same selection as before. What lives here is
 * the part that is *only* about picking:
 *
 * - the **restored** slot (`interface.pickedTool`), because a reopened window should come back to the tool it
 *   was left on, with the colour and width that slot keeps;
 * - the slot **before** the last pick, which is what a toggle key puts back ([toggledTool]).
 *
 * The text box's commit and the picker's closing are the shell's, not this class's: they are consequences of
 * *leaving* a tool, and a state object that quietly committed a box would be a second owner of the page.
 */
internal class DesktopToolPicks(settings: DesktopSettings) {

    private val settings = settings

    /** The picked tool, as a value the Compose snapshot watches. */
    var selection by mutableStateOf(ToolSelection(restored()))
        private set

    /** The tool in hand before the last switch — the answer a toggle's second press gives. */
    private var last by mutableStateOf(DesktopTool.PEN)

    /** Pick [tool], remembering it — and remembering what was in hand for the next toggle. */
    fun pick(tool: DesktopTool) {
        if (tool != selection.current) last = selection.current
        selection = selection.select(tool)
        // The pick outlives the window: a restart comes back on this slot, with this slot's ink.
        settings.pickedTool = tool.name
    }

    /** What a toggle press on [target] should pick now — the arithmetic is [toggledTool]'s. */
    fun toggleTarget(target: DesktopTool): DesktopTool = toggledTool(selection.current, last, target)

    /** The tool the settings file names, when this build still acts on it; the pen otherwise. */
    private fun restored(): DesktopTool =
        toolNamed(settings.pickedTool, DesktopTool.PEN).takeIf { it in DESKTOP_TOOLS_THAT_ACT }
            ?: DesktopTool.PEN
}
