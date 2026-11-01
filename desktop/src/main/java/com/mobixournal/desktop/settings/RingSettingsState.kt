package com.mobixournal.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.desktop.sheet.RingContents
import com.mobixournal.desktop.tools.DesktopTool

/**
 * **The Ring page's state**, live: what the ring holds right now, and the four edits the page makes.
 *
 * It is the same shape [DesktopAudioSettings] and `PenPresetState` have — a subject of its own hung off
 * [DesktopSettingsState] — so a choice changes the window the moment the row is clicked and is written to
 * the file in the same call. The ring reads [contents] through a lambda (`DesktopRing`), which is what
 * makes a switch take effect on the **next** summon without the ring being reopened.
 *
 * The state is deliberately not the ring itself: `DesktopRing` owns where the ring is and what a press
 * commits, and this owns only which parts it carries.
 */
internal class RingSettingsState(private val store: RingSettingsStore) {

    /** What the ring holds, as the page's switches show it. */
    var contents by mutableStateOf(store.contents)
        private set

    /** Put the palette's swatches on the ring, or take them off. */
    fun chooseSwatches(on: Boolean) = write(contents.copy(swatches = on))

    /** Put the pen's three widths on the ring, or take them off. */
    fun chooseWidths(on: Boolean) = write(contents.copy(widths = on))

    /** Give [tool] a face on the ring, or take it away — the last one off leaves a ring that stays shut. */
    fun chooseTool(tool: DesktopTool, on: Boolean) = write(
        contents.copy(tools = if (on) contents.tools + tool else contents.tools - tool),
    )

    /** Every part back on — the page's *Reset to defaults*. */
    fun reset() = write(RingContents.FACTORY)

    private fun write(next: RingContents) {
        contents = next
        store.contents = next
    }
}
