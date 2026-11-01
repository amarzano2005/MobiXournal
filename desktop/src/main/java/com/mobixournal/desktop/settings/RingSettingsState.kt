package com.mobixournal.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.desktop.sheet.DesktopHud
import com.mobixournal.desktop.sheet.RingContents
import com.mobixournal.design.PenPalette
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud

/**
 * **The Ring page's state**, live: the ring's faces right now, and the edits the page makes to them.
 *
 * It is the same shape [DesktopAudioSettings] and `PenPresetState` have — a subject of its own hung off
 * [DesktopSettingsState] — so a choice changes the window the moment the row is answered and is written to
 * the file in the same call. The ring reads [contents] through a lambda (`DesktopRing`), which is what makes
 * a change take effect on the **next** summon without the ring being reopened.
 *
 * Two states, one key: [RingContents.Auto] is "nobody edited this" and follows the palette, while
 * [RingContents.Fixed] is the reader's own list and stops following anything. Every edit pins the ring —
 * the first face a reader changes is the moment they meant *this ring*, not *the palette's ring* — and a ring
 * whose last face is removed goes back to automatic rather than to a stored emptiness.
 */
internal class RingSettingsState(private val settings: DesktopSettings) {

    /** What the ring holds, as the page's rows show it. */
    var contents by mutableStateOf(settings.ring.contents)
        private set

    /** The faces the ring draws right now, in order — the automatic ring computed from the palette. */
    val slots: List<HudSlot> get() = contents.slots(settings.paletteColours, settings.paletteCustom)

    /**
     * Every answer a position can hold, in the order the page offers them: the three tools, the three widths,
     * the palette's colours (the custom one last), and **null** — the row's own way of removing the position.
     */
    fun candidates(): List<HudSlot?> = toolFaces() + widthFaces() + colourFaces() + listOf(null)

    private fun toolFaces(): List<HudSlot> = RingContents.TOOL_FACES
        .mapNotNull { face -> DesktopHud.documentTool(face)?.let(HudSlot::ToolFace) }

    private fun widthFaces(): List<HudSlot> = PenPalette.DEFAULT_WIDTHS.map(HudSlot::Width)

    private fun colourFaces(): List<HudSlot> {
        val colours = (settings.paletteColours + listOfNotNull(settings.paletteCustom)).distinct()
        return colours.map(HudSlot::Colour)
    }

    /** Give position [index] the face [face], or remove it when [face] is null; the ring is pinned. */
    fun chooseFace(index: Int, face: HudSlot?) {
        val faces = slots
        if (index !in faces.indices) return
        val next = faces.toMutableList()
        if (face == null) next.removeAt(index) else next[index] = face
        writeFaces(next)
    }

    /** Append one position — the first answer the ring does not carry yet, so a press adds something new. */
    fun addPosition() {
        val faces = slots
        if (faces.size >= RadialHud.MAX_SLOTS) return
        val fresh = candidates().filterNotNull().firstOrNull { it !in faces }
            ?: HudSlot.Width(PenPalette.DEFAULT_WIDTHS.first())
        writeFaces(faces + fresh)
    }

    /** The automatic ring back — every position given up, the palette's own ring drawn again. */
    fun reset() = write(RingContents.Auto)

    private fun writeFaces(faces: List<HudSlot>) {
        // Nothing left to draw is not a ring a file should remember: it is the automatic one.
        write(if (faces.isEmpty()) RingContents.Auto else RingContents.Fixed(faces))
    }

    private fun write(next: RingContents) {
        contents = next
        settings.ring.contents = next
    }
}

