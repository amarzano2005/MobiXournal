package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The factory pen palette is the eight swatches the app ships with — **Black, Red, Green, Blue,
 * Orange, Yellow, Magenta, White**, in that order — so a fresh install (and **Restore default palette**)
 * always opens on the same short list.
 *
 * Each hex is desktop Xournal++'s own value for that colour (transcribed from upstream
 * `palettes/xournal.gpl`, the file Xournal++ loads as its `DEFAULT_PALETTE_FILE`), so a colour
 * picked here still writes the same ARGB the desktop app writes for the same swatch. The expected
 * list below pins both the *set* and the *order*; a change to either side fails here rather than
 * silently drifting.
 */
class PaletteColorsTest {

    @Test
    fun `the default palette is the eight shipping colours, in the shipping order`() {
        assertEquals(EIGHT_DEFAULTS, PEN_COLORS)
    }

    @Test
    fun `palette entries are opaque`() {
        assertTrue(
            "every swatch must be opaque ARGB",
            PEN_COLORS.all { (it ushr 24) and 0xFF == 0xFF },
        )
    }

    @Test
    fun `the palette holds no duplicates`() {
        assertEquals(PEN_COLORS.size, PEN_COLORS.toSet().size)
    }

    @Test
    fun `fresh documents and the highlighter start on palette colours`() {
        assertEquals(PEN_COLORS.first(), AppSettings.DEFAULT_LAST_COLOR)
        assertTrue(
            "the highlighter colour must be one of the swatches",
            AppSettings.DEFAULT_HIGHLIGHTER_COLOR in PEN_COLORS,
        )
        assertEquals(XOPP_YELLOW, AppSettings.DEFAULT_HIGHLIGHTER_COLOR)
    }

    @Test
    fun `shortcut colours are palette colours`() {
        assertTrue(XOPP_BLACK in PEN_COLORS)
        assertTrue(XOPP_RED in PEN_COLORS)
        assertTrue(XOPP_GREEN in PEN_COLORS)
        assertTrue(XOPP_BLUE in PEN_COLORS)
        assertTrue(XOPP_ORANGE in PEN_COLORS)
        assertTrue(XOPP_YELLOW in PEN_COLORS)
        assertTrue(XOPP_MAGENTA in PEN_COLORS)
        assertTrue(XOPP_WHITE in PEN_COLORS)
    }

    @Test
    fun `predefined colors have human-readable names in English and format with hex`() {
        val expected = mapOf(
            XOPP_BLACK to "Black (#000000)",
            XOPP_RED to "Red (#FF0000)",
            XOPP_GREEN to "Green (#008000)",
            XOPP_BLUE to "Blue (#3333CC)",
            XOPP_ORANGE to "Orange (#FF8000)",
            XOPP_YELLOW to "Yellow (#FFFF00)",
            XOPP_MAGENTA to "Magenta (#FF00FF)",
            XOPP_WHITE to "White (#FFFFFF)",
        )
        for ((color, expectedLabel) in expected) {
            assertEquals(expectedLabel, colorDisplayName(color))
        }
        // Custom color without predefined name formats as bare hex
        assertEquals("#123456", colorDisplayName(0xFF123456.toInt()))
    }

    @Test
    fun `a fresh install starts on the default palette and can round-trip it`() {
        assertEquals(PEN_COLORS, AppSettings().penColors)
        assertEquals(PEN_COLORS, decodePenColors(encodePenColors(PEN_COLORS), emptyList()))
    }

    @Test
    fun `a user's own colours survive the palette round-trip`() {
        // Nothing usable stored: fall back rather than leave the pickers empty.
        assertEquals(PEN_COLORS, decodePenColors(null, PEN_COLORS))
        assertEquals(PEN_COLORS, decodePenColors("", PEN_COLORS))
        assertEquals(PEN_COLORS, decodePenColors("not-a-colour,", PEN_COLORS))
        // 1122867 is #112233, a colour of the user's own: a swatch like any other, so it is kept.
        assertEquals(listOf(0xFF112233.toInt()), decodePenColors("1122867", PEN_COLORS))
        assertEquals(
            listOf(XOPP_BLACK, 0xFF112233.toInt(), XOPP_RED),
            decodePenColors("${XOPP_BLACK},1122867,${XOPP_RED}", PEN_COLORS),
        )
    }

    private companion object {
        /** The eight shipping colours: Black, Red, Green, Blue, Orange, Yellow, Magenta, White. */
        val EIGHT_DEFAULTS: List<Int> = listOf(
            0xFF000000.toInt(), // Black
            0xFFFF0000.toInt(), // Red
            0xFF008000.toInt(), // Green
            0xFF3333CC.toInt(), // Blue
            0xFFFF8000.toInt(), // Orange
            0xFFFFFF00.toInt(), // Yellow
            0xFFFF00FF.toInt(), // Magenta
            0xFFFFFFFF.toInt(), // White
        )
    }
}
