package com.mobixournal.desktop.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mobixournal.desktop.sheet.RingContents
import com.mobixournal.desktop.sheet.RingFace
import com.mobixournal.desktop.tools.DESKTOP_TOOLS_THAT_ACT
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.design.PenPalette
import com.mobixournal.render.RadialHud

/**
 * **The Ring page's state**, live: the ring's faces right now, and the edits the page makes to them.
 *
 * It is the same shape [DesktopAudioSettings] and `PenPresetState` have — a subject of its own hung off
 * [DesktopSettingsState] — so a choice changes the window the moment a face is picked and is written to the
 * file in the same call. The ring reads [contents] through a lambda (`DesktopRing`), which is what makes a
 * change take effect on the **next** summon without the ring being reopened.
 *
 * Two states, one key: [RingContents.Auto] is "nobody edited this" and follows the palette, while
 * [RingContents.Fixed] is the reader's own list and stops following anything. Every edit pins the ring — the
 * first face a reader changes is the moment they meant *this ring* — and a ring whose last face is removed
 * goes back to automatic rather than to a stored emptiness.
 */
internal class RingSettingsState(private val settings: DesktopSettings) {

    /** What the ring holds, as the page's drawing shows it. */
    var contents by mutableStateOf(settings.ring.contents)
        private set

    /** The faces the ring draws right now, in order — the automatic ring computed from the palette. */
    val slots: List<RingFace> get() = contents.slots(settings.paletteColours, settings.paletteCustom)

    /**
     * The **colours** a face can hold: the palette as the strips carry it, with the editable custom colour
     * last when it is not already among them. That is "the colours I want" — the palette is theirs, and the
     * page does not keep a second list.
     */
    fun colourChoices(): List<RingFace.Colour> {
        val colours = (settings.paletteColours + listOfNotNull(settings.paletteCustom)).distinct()
        return colours.map(RingFace::Colour)
    }

    /** The **tools** a face can hold: every tool the window actually puts in hand. */
    fun toolChoices(): List<RingFace.Tool> =
        DesktopTool.entries.filter { it in DESKTOP_TOOLS_THAT_ACT }.map(RingFace::Tool)

    /** The **widths** a face can hold: the pen's three slots, S / M / L. */
    fun widthChoices(): List<RingFace.Width> = PenPalette.DEFAULT_WIDTHS.map(RingFace::Width)

    /** Give position [index] the face [face], or remove it when [face] is null; the ring is pinned. */
    fun chooseFace(index: Int, face: RingFace?) {
        val faces = slots
        if (index !in faces.indices) return
        val next = faces.toMutableList()
        if (face == null) next.removeAt(index) else next[index] = face
        writeFaces(next)
    }

    /** Append one position — the first width, so a new face is always something the ring can commit. */
    fun addPosition() {
        val faces = slots
        if (faces.size >= RadialHud.MAX_SLOTS) return
        writeFaces(faces + RingFace.Width(PenPalette.DEFAULT_WIDTHS.first()))
    }

    /** The automatic ring back — every position given up, the palette's own ring drawn again. */
    fun reset() = write(RingContents.Auto)

    private fun writeFaces(faces: List<RingFace>) {
        // Nothing left to draw is not a ring a file should remember: it is the automatic one.
        write(if (faces.isEmpty()) RingContents.Auto else RingContents.Fixed(faces))
    }

    private fun write(next: RingContents) {
        contents = next
        settings.ring.contents = next
    }
}
