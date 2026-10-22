package com.mobixournal.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared palette and width rules, pinned where both shells read them.
 *
 * These are the facts a colour picker cannot be allowed to re-decide locally: which eight hexes ship
 * (Xournal++'s own, in its order), that a swatch is opaque, that adding one twice makes one, that a
 * palette has a ceiling and a floor, and which of the three width slots a width belongs to. The tablet's
 * `PaletteColorsTest` and the desktop's flyout tests both lean on this object, so a change here is a
 * change in both windows.
 */
class PenPaletteTest {

    private val eight = listOf(
        0xFF000000.toInt(), // black
        0xFFFF0000.toInt(), // red
        0xFF008000.toInt(), // green
        0xFF3333CC.toInt(), // blue
        0xFFFF8000.toInt(), // orange
        0xFFFFFF00.toInt(), // yellow
        0xFFFF00FF.toInt(), // magenta
        0xFFFFFFFF.toInt(), // white
    )

    @Test
    fun `the factory palette is the eight shipping hexes in order`() {
        assertEquals(eight, PenPalette.COLORS)
        assertTrue("every swatch is opaque", PenPalette.COLORS.all { (it ushr 24) and 0xFF == 0xFF })
        assertEquals("no duplicate swatch", PenPalette.COLORS.size, PenPalette.COLORS.toSet().size)
    }

    @Test
    fun `the ceiling is 24 swatches`() {
        assertEquals(24, PenPalette.MAX_COLORS)
    }

    @Test
    fun `adding a colour makes it opaque and keeps one copy`() {
        val transparent = 0x0012AB34
        assertEquals(0xFF12AB34.toInt(), PenPalette.opaque(transparent))

        val added = PenPalette.add(listOf(PenPalette.BLACK), transparent)
        assertEquals(listOf(PenPalette.BLACK, 0xFF12AB34.toInt()), added)

        assertEquals("re-adding a colour is not a second swatch", added, PenPalette.add(added, transparent))
        assertEquals("and it keeps its place in the palette", 1, PenPalette.add(added, transparent).indexOf(0xFF12AB34.toInt()))
    }

    @Test
    fun `adding stops at the ceiling`() {
        val full = (0 until PenPalette.MAX_COLORS).map { 0xFF000000.toInt() or it }
        val capped = PenPalette.add(full, 0xFF123456.toInt())
        assertEquals(PenPalette.MAX_COLORS, capped.size)
        assertTrue("the new colour did not fit past the ceiling", 0xFF123456.toInt() !in capped)
    }

    @Test
    fun `a stored palette is repaired, and an empty one falls back to the factory list`() {
        assertEquals(
            listOf(PenPalette.BLACK, PenPalette.RED),
            PenPalette.sanitised(listOf(0x00000000, 0xFF000000.toInt(), 0xFFFF0000.toInt())),
        )
        assertEquals("an empty palette must not leave the pickers blank", PenPalette.COLORS, PenPalette.sanitised(emptyList()))
    }

    @Test
    fun `the three width slots are the shared ones`() {
        assertEquals(listOf(0.85f, 1.5f, 2.6f), PenPalette.DEFAULT_WIDTHS)
        assertEquals(listOf("S", "M", "L"), PenPalette.WIDTH_LABELS)
        assertEquals(0.5f, PenPalette.WIDTH_MIN, 0f)
        assertEquals(15f, PenPalette.WIDTH_MAX, 0f)
    }

    @Test
    fun `a width is clamped to the range a document may carry`() {
        assertEquals(PenPalette.WIDTH_MIN, PenPalette.clampWidth(0.01f), 0f)
        assertEquals(PenPalette.WIDTH_MAX, PenPalette.clampWidth(99f), 0f)
        assertEquals(1.5f, PenPalette.clampWidth(1.5f), 0f)
    }

    @Test
    fun `a width belongs to the nearest slot, and a tie to the lower one`() {
        assertEquals(0, PenPalette.widthSlot(0.85f))
        assertEquals(1, PenPalette.widthSlot(1.5f))
        assertEquals(2, PenPalette.widthSlot(2.6f))
        assertEquals("closer to S than to M", 0, PenPalette.widthSlot(1.1f))
        assertEquals("closer to M than to S", 1, PenPalette.widthSlot(1.3f))
        assertEquals(
            "a width exactly between two slots goes to the lower one",
            0,
            PenPalette.widthSlot(2f, listOf(1f, 3f)),
        )
        assertEquals("past the last slot stays on it", 2, PenPalette.widthSlot(9f))
    }
}
