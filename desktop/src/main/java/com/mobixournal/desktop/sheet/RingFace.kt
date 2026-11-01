package com.mobixournal.desktop.sheet

import com.mobixournal.desktop.tools.DesktopTool

/**
 * **One face of the pen-tip ring**: a colour, a width or any tool the window has.
 *
 * The tablet's `HudSlot` carries the document's three writing tools, because that is all its ring offers.
 * The window's ring is the reader's (Settings → Ring), and what it can hold is everything the window can put
 * in hand — every tool of the rail, the ribbon and the circuit family — so the desktop has a face of its own
 * and the shared `RadialHud` keeps the geometry only.
 */
internal sealed interface RingFace {

    /** A palette colour, as opaque ARGB. */
    data class Colour(val argb: Int) : RingFace

    /** One of the pen's widths, in points. */
    data class Width(val pt: Float) : RingFace

    /** A tool of the window — `DesktopTool`, not the document's `Tool`, so nothing is filtered out. */
    data class Tool(val tool: DesktopTool) : RingFace
}
