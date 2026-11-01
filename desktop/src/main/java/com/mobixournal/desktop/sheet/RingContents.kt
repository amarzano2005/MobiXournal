package com.mobixournal.desktop.sheet

import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.format.model.Tool
import com.mobixournal.render.HudSlot
import com.mobixournal.render.RadialHud

/**
 * **What the pen-tip ring holds**, as the one value the Settings page writes and the ring reads.
 *
 * There are two rings, and the file holds which one the window draws:
 *
 * - [Auto] — a ring nobody has edited: the palette's swatches (the custom colour last), then the pen's three
 *   widths, then the three writing tools, in the tablet's order. It follows the palette, which is what a
 *   fresh install and **Reset to defaults** want.
 * - [Fixed] — the reader's own ring: an ordered list of faces, each a colour, a width or a tool, chosen one
 *   position at a time. The palette no longer moves it, which is the whole point of having chosen it.
 *
 * Both answer [slots], so the ring itself never branches on which one it has. An empty [Fixed] is a ring that
 * offers nothing — `DesktopRing.summon` answers false and the key does nothing, rather than drawing an empty
 * ring at the nib.
 */
internal sealed interface RingContents {

    /** The faces this ring draws, given the palette `Auto` builds its swatches from. */
    fun slots(palette: List<Int>, custom: Int?): List<HudSlot>

    /** The ring nobody edited: the palette, the three widths and the three tools. */
    data object Auto : RingContents {
        override fun slots(palette: List<Int>, custom: Int?): List<HudSlot> = DesktopHud.slots(palette, custom)
    }

    /** The reader's own ring, in the order they set it. */
    data class Fixed(val faces: List<HudSlot>) : RingContents {
        override fun slots(palette: List<Int>, custom: Int?): List<HudSlot> = faces.take(RadialHud.MAX_SLOTS)
    }

    companion object {

        /**
         * The three writing tools the page offers, in the tablet's order.
         *
         * The same three `DesktopHud.TOOLS` names, kept here because this is the list the page builds its
         * answers from: a face the ring cannot commit is a choice that would do nothing.
         */
        val TOOL_FACES: List<DesktopTool> = DesktopHud.TOOLS

        /**
         * [faces] as the one line `ring.slots` stores: `c:` for a colour (ARGB, hex), `w:` for a width in
         * points, `t:` for a tool by its document name — joined by commas.
         */
        fun encode(faces: List<HudSlot>): String = faces.joinToString(",") { face ->
            when (face) {
                is HudSlot.Colour -> "c:" + Integer.toHexString(face.argb)
                is HudSlot.Width -> "w:" + face.pt
                is HudSlot.ToolFace -> "t:" + face.tool.name
            }
        }

        /**
         * The faces [encode] wrote, or **null** when the line names none this build can read.
         *
         * Null is the automatic ring: a file written by another version, or edited by hand into nonsense,
         * leaves the window with the ring it ships rather than with a dead one. A face this build does not
         * know is dropped and the rest are kept.
         */
        fun decode(raw: String?): List<HudSlot>? {
            if (raw.isNullOrBlank()) return null
            val faces = raw.split(',')
                .mapNotNull { token -> faceOf(token.trim()) }
                .take(RadialHud.MAX_SLOTS)
            return faces.ifEmpty { null }
        }

        /** One stored token as a face, or null when it is not one. */
        private fun faceOf(token: String): HudSlot? = when {
            token.startsWith("c:") ->
                token.removePrefix("c:").toLongOrNull(16)?.toInt()?.let(HudSlot::Colour)
            token.startsWith("w:") ->
                token.removePrefix("w:").toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
                    ?.let(HudSlot::Width)
            token.startsWith("t:") -> {
                val name = token.removePrefix("t:")
                Tool.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?.let(HudSlot::ToolFace)
            }
            else -> null
        }
    }
}
