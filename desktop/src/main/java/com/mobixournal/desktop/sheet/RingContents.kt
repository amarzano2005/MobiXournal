package com.mobixournal.desktop.sheet

import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.design.PenPalette
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud

/**
 * **What the pen-tip ring holds**, as the one value the Settings page writes and the ring reads.
 *
 * Two rings, and the file holds which one the window draws:
 *
 * - [Auto] — a ring nobody has edited: the palette's **first five** colours (the custom colour last), then
 *   the pen's three widths, then the six tools of `RadialHud.FACTORY_TOOLS`, in the tablet's order. It
 *   follows the palette, which is what a fresh install and **Reset to defaults** want.
 * - [Fixed] — the reader's own ring: faces chosen one position at a time, each a colour, a width or **any**
 *   tool of the window. The palette no longer moves it, which is the point of having chosen it.
 *
 * Both answer [slots], so the ring and its painter never branch on which one they have.
 */
internal sealed interface RingContents {

    /** The faces this ring draws, given the palette `Auto` builds its swatches from. */
    fun slots(palette: List<Int>, custom: Int?): List<RingFace>

    /**
     * The ring nobody edited: the palette's first five, the three widths and the six tools.
     *
     * The contents come from `RadialHud.factorySlots` — the same call the tablet makes — so this class only
     * reads the shared ring in its own vocabulary.
     */
    data object Auto : RingContents {
        override fun slots(palette: List<Int>, custom: Int?): List<RingFace> =
            RadialHud.factorySlots(palette, custom, PenPalette.DEFAULT_WIDTHS).mapNotNull { slot ->
                when (slot) {
                    is HudSlot.Colour -> RingFace.Colour(slot.argb)
                    is HudSlot.Width -> RingFace.Width(slot.pt)
                    is HudSlot.ToolFace -> DesktopTool.entries
                        .firstOrNull { it.name == slot.tool }
                        ?.let { tool -> RingFace.Tool(tool) }
                }
            }
    }

    /** The reader's own ring, in the order they set it. */
    data class Fixed(val faces: List<RingFace>) : RingContents {
        override fun slots(palette: List<Int>, custom: Int?): List<RingFace> = faces.take(RadialHud.MAX_SLOTS)
    }

    companion object {

        /**
         * The six tools an **automatic** ring offers, in the tablet's order: the factory list of
         * `RadialHud.FACTORY_TOOLS`, each name read as the window's own tool of that name.
         */
        val TOOLS: List<DesktopTool> = RadialHud.FACTORY_TOOLS.mapNotNull { name ->
            DesktopTool.entries.firstOrNull { it.name == name }
        }

        /**
         * [faces] as the one line `ring.slots` stores: `c:` a colour (ARGB, hex), `w:` a width in points,
         * `t:` a tool by its own name — joined by commas.
         */
        fun encode(faces: List<RingFace>): String = RadialHud.encodeSlots(faces.map(::slotOf))

        /** A face in the shared ring's vocabulary, which is what the stored line is made of. */
        private fun slotOf(face: RingFace): HudSlot = when (face) {
            is RingFace.Colour -> HudSlot.Colour(face.argb)
            is RingFace.Width -> HudSlot.Width(face.pt)
            is RingFace.Tool -> HudSlot.ToolFace(face.tool.name)
        }

        /**
         * The faces [encode] wrote, or **null** when the line names none this build can read.
         *
         * Null is the automatic ring: a file written by another version, or hand-edited into nonsense, leaves
         * the window with the ring it ships rather than with a dead one. A face this build does not know is
         * dropped and the rest are kept.
         */
        fun decode(raw: String?): List<RingFace>? = RadialHud.decodeSlots(raw)
            ?.mapNotNull { slot -> faceOf(slot) }
            ?.takeIf { it.isNotEmpty() }

        /**
         * A shared face as one the window can draw, or null when this build has no such tool — a name written
         * by another version is dropped rather than shown as a face that does nothing.
         */
        private fun faceOf(slot: HudSlot): RingFace? = when (slot) {
            is HudSlot.Colour -> RingFace.Colour(slot.argb)
            is HudSlot.Width -> RingFace.Width(slot.pt)
            is HudSlot.ToolFace -> DesktopTool.entries
                .firstOrNull { it.name.equals(slot.tool, ignoreCase = true) }
                ?.let { tool -> RingFace.Tool(tool) }
        }
    }
}
