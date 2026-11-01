package com.mobixournal.desktop.sheet

import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.design.PenPalette
import com.mobixournal.render.RadialHud

/**
 * **What the pen-tip ring holds**, as the one value the Settings page writes and the ring reads.
 *
 * Two rings, and the file holds which one the window draws:
 *
 * - [Auto] — a ring nobody has edited: the palette's swatches (the custom colour last), then the pen's three
 *   widths, then pen / highlighter / eraser, in the tablet's order. It follows the palette, which is what a
 *   fresh install and **Reset to defaults** want.
 * - [Fixed] — the reader's own ring: faces chosen one position at a time, each a colour, a width or **any**
 *   tool of the window. The palette no longer moves it, which is the point of having chosen it.
 *
 * Both answer [slots], so the ring and its painter never branch on which one they have.
 */
internal sealed interface RingContents {

    /** The faces this ring draws, given the palette `Auto` builds its swatches from. */
    fun slots(palette: List<Int>, custom: Int?): List<RingFace>

    /** The ring nobody edited: the palette, the three widths and the three writing tools. */
    data object Auto : RingContents {
        override fun slots(palette: List<Int>, custom: Int?): List<RingFace> {
            val ring = ArrayList<RingFace>(RadialHud.MAX_SLOTS)
            val paletteCap = (RadialHud.MAX_SLOTS - PenPalette.DEFAULT_WIDTHS.size - RingContents.TOOLS.size)
                .coerceIn(0, RadialHud.MAX_PALETTE_SLOTS)
            val distinct = palette.distinct()
            val customIsNew = custom != null && custom !in distinct
            val room = if (customIsNew) (paletteCap - 1).coerceAtLeast(0) else paletteCap
            for (colour in distinct.take(room)) ring += RingFace.Colour(colour)
            if (customIsNew && custom != null) ring += RingFace.Colour(custom)
            for (width in PenPalette.DEFAULT_WIDTHS) ring += RingFace.Width(width)
            for (tool in RingContents.TOOLS) ring += RingFace.Tool(tool)
            return ring.take(RadialHud.MAX_SLOTS)
        }
    }

    /** The reader's own ring, in the order they set it. */
    data class Fixed(val faces: List<RingFace>) : RingContents {
        override fun slots(palette: List<Int>, custom: Int?): List<RingFace> = faces.take(RadialHud.MAX_SLOTS)
    }

    companion object {

        /** The three writing tools an **automatic** ring offers, in the tablet's order. */
        val TOOLS: List<DesktopTool> = listOf(
            DesktopTool.PEN,
            DesktopTool.HIGHLIGHTER,
            DesktopTool.ERASER,
        )

        /**
         * [faces] as the one line `ring.slots` stores: `c:` a colour (ARGB, hex), `w:` a width in points,
         * `t:` a tool by its own name — joined by commas.
         */
        fun encode(faces: List<RingFace>): String = faces.joinToString(",") { face ->
            when (face) {
                is RingFace.Colour -> "c:" + Integer.toHexString(face.argb)
                is RingFace.Width -> "w:" + face.pt
                is RingFace.Tool -> "t:" + face.tool.name
            }
        }

        /**
         * The faces [encode] wrote, or **null** when the line names none this build can read.
         *
         * Null is the automatic ring: a file written by another version, or hand-edited into nonsense, leaves
         * the window with the ring it ships rather than with a dead one. A face this build does not know is
         * dropped and the rest are kept.
         */
        fun decode(raw: String?): List<RingFace>? {
            if (raw.isNullOrBlank()) return null
            val faces = raw.split(',')
                .mapNotNull { token -> faceOf(token.trim()) }
                .take(RadialHud.MAX_SLOTS)
            return faces.ifEmpty { null }
        }

        /** One stored token as a face, or null when it is not one. */
        private fun faceOf(token: String): RingFace? = when {
            token.startsWith("c:") ->
                token.removePrefix("c:").toLongOrNull(16)?.toInt()?.let(RingFace::Colour)
            token.startsWith("w:") ->
                token.removePrefix("w:").toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
                    ?.let(RingFace::Width)
            token.startsWith("t:") -> {
                val name = token.removePrefix("t:")
                DesktopTool.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?.let(RingFace::Tool)
            }
            else -> null
        }
    }
}
