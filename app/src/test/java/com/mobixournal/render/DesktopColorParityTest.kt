package com.mobixournal.render

import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the appearance values the app takes from desktop Xournal++ so either side drifting fails a
 * test rather than silently diverging:
 *
 *  - the highlighter snaps onto the desktop's own tip sizes (1 mm / 3 mm / 7 mm), and
 *  - the page ruling uses the desktop backgrounds' colours (`xopp_dodgerblue`, `xopp_silver`).
 *
 * The expected hexes are transcribed from upstream `src/util/include/util/Color.h` and
 * `src/core/control/ToolHandler.cpp`.
 */
class DesktopColorParityTest {

    @Test
    fun `highlighter width snaps to the desktop tip sizes`() {
        assertEquals(2.83, DrawingSurfaceDefaults.highlighterWidthFor(0.85f), 1e-6)
        assertEquals(8.50, DrawingSurfaceDefaults.highlighterWidthFor(1.5f), 1e-6)
        assertEquals(19.84, DrawingSurfaceDefaults.highlighterWidthFor(2.6f), 1e-6)
    }

    @Test
    fun `every highlighter width is one of the desktop tip sizes`() {
        for (w in listOf(0.1f, 0.5f, 0.85f, 1.5f, 2.6f, 4f, 8f)) {
            val out = DrawingSurfaceDefaults.highlighterWidthFor(w)
            assertTrue(
                "$w pt -> $out pt is not a desktop highlighter size",
                out in DrawingSurfaceDefaults.HIGHLIGHTER_SIZES_PT,
            )
        }
    }

    @Test
    fun `highlighter alpha matches the desktop values`() {
        // SaveHandler.cpp writes alpha 0x7f for a highlighter stroke's stored colour…
        assertEquals(0x7f, com.mobixournal.format.XoppColor.HIGHLIGHTER_ALPHA)
        // …and StrokeView.h paints it at OPACITY_HIGHLIGHTER = 0.47 (0x78).
        assertEquals(0x78, StrokePainter.HIGHLIGHTER_RENDER_ALPHA)
        assertEquals(0.47, StrokePainter.HIGHLIGHTER_RENDER_ALPHA / 255.0, 1e-2)
    }

    @Test
    fun `every palette swatch is the desktop palette's own hex`() {
        // Transcribed from upstream `palettes/xournal.gpl` — the file Xournal++ loads as its
        // DEFAULT_PALETTE_FILE. The app ships a subset (Black, Red, Green, Blue, Orange, Magenta,
        // Yellow, White), but each of those must be the desktop value for that colour, not a
        // lookalike: the point of matching is that the same swatch writes the same ARGB.
        val desktop = mapOf(
            0xFF000000.toInt() to "Black",       // 0 0 0
            0xFF008000.toInt() to "Green",       // 0 128 0
            0xFF3333CC.toInt() to "Blue",        // 51 51 204
            0xFFFF0000.toInt() to "Red",         // 255 0 0
            0xFFFF00FF.toInt() to "Magenta",     // 255 0 255
            0xFFFF8000.toInt() to "Orange",      // 255 128 0
            0xFFFFFF00.toInt() to "Yellow",      // 255 255 0
            0xFFFFFFFF.toInt() to "White",       // 255 255 255
        )
        // Every shipped swatch must be a desktop colour: nothing invented, nothing approximated.
        assertTrue(
            "the palette must be the desktop palette's own hexes: ${com.mobixournal.ui.PEN_COLORS}",
            com.mobixournal.ui.PEN_COLORS.toSet().all { it in desktop },
        )
        for ((argb, name) in desktop) {
            assertEquals(name, com.mobixournal.ui.predefinedColorName(argb))
        }
    }

    @Test
    fun `ruling colours match the desktop backgrounds`() {
        assertEquals(0x40A0FF, BackgroundGrid.LINED_RGB) // Colors::xopp_dodgerblue
        assertEquals(0xBDBDBD, BackgroundGrid.GRAPH_RGB) // Colors::xopp_silver
        assertEquals(0xBDBDBD, BackgroundGrid.DOT_RGB)   // Colors::xopp_silver
    }

    @Test
    fun `clipboard bounds union covers every element`() {
        val a = Stroke(Tool.PEN, 0xFF000000.toInt(), "round", listOf(StrokePoint(10.0, 10.0, 0.0)), true)
        val b = Stroke(Tool.PEN, 0xFF000000.toInt(), "round", listOf(StrokePoint(30.0, 40.0, 0.0)), true)
        val bounds = SelectionOps.boundsOf(listOf(a, b))!!
        assertEquals(10.0, bounds.left, 1e-9)
        assertEquals(10.0, bounds.top, 1e-9)
        assertEquals(30.0, bounds.right, 1e-9)
        assertEquals(40.0, bounds.bottom, 1e-9)
        assertNull(SelectionOps.boundsOf(emptyList()))
    }
}
