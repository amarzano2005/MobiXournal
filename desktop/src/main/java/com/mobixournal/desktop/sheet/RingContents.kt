package com.mobixournal.desktop.sheet

import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.design.PenPalette
import com.mobixournal.render.HudSlot

/**
 * **What the pen-tip ring holds**, as one value the Settings page writes and the ring reads.
 *
 * The three parts are the ring's three families — the palette's swatches, the three pen widths and the
 * writing tools — and each one can be left off. The ring's geometry, its cap and its order stay
 * `RadialHud`'s: this only decides which families take part, so a ring the reader narrowed still draws in
 * the tablet's order (swatches, then widths, then tools).
 *
 * A ring with nothing in it is a ring that does not open: `DesktopRing.summon` answers false and the key
 * does nothing, which is the honest answer to "I turned every part off" rather than a ring drawn empty.
 *
 * @property swatches The palette's colours and the editable custom slot.
 * @property widths The pen's three S/M/L widths.
 * @property tools The writing tools the ring offers, in [TOOL_FACES]' order.
 */
internal data class RingContents(
    val swatches: Boolean = true,
    val widths: Boolean = true,
    val tools: Set<DesktopTool> = TOOL_FACES.toSet(),
) {

    /** The slots this ring carries, in the tablet's order (`RadialHud.slots`). */
    fun slots(palette: List<Int>, custom: Int?): List<HudSlot> = DesktopHud.slots(
        palette = if (swatches) palette else emptyList(),
        custom = if (swatches) custom else null,
        widths = if (widths) PenPalette.DEFAULT_WIDTHS else emptyList(),
        tools = TOOL_FACES.filter { it in tools },
    )

    companion object {

        /**
         * The three writing tools the ring offers, in the tablet's order.
         *
         * The same three `DesktopHud.TOOLS` names, kept here because this is the list the page draws its
         * switches from: a face the ring cannot commit is a switch that would do nothing.
         */
        val TOOL_FACES: List<DesktopTool> = DesktopHud.TOOLS

        /** A fresh install's ring: everything on. */
        val FACTORY = RingContents()

        /** The tools a stored `ring.tools` line names, in [TOOL_FACES]' order; unknown names are dropped. */
        fun toolsOf(raw: String?): Set<DesktopTool> {
            if (raw == null) return FACTORY.tools
            val named = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            return TOOL_FACES.filter { face -> named.any { it.equals(face.name, ignoreCase = true) } }.toSet()
        }

        /** [tools] as the one line the file stores, in the ring's own order. */
        fun encodeTools(tools: Set<DesktopTool>): String =
            TOOL_FACES.filter { it in tools }.joinToString(",") { it.name }
    }
}
