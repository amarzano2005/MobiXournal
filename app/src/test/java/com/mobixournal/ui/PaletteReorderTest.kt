package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The palette's drag-to-reorder arithmetic, on its own: where a carried swatch lands, and what the
 * list is after the drop.
 *
 * Both are pure, so the gesture that feeds them can be rewritten without losing the two decisions that
 * make a reorder read as one: the pitch is one measured row, and the ends do not wrap.
 */
class PaletteReorderTest {

    @Test
    fun `a swatch lands on the row it has travelled past`() {
        assertEquals(3, paletteDropTarget(from = 1, offsetPx = 112f, rowHeightPx = 56, count = 6))
        assertEquals(1, paletteDropTarget(from = 1, offsetPx = 27f, rowHeightPx = 56, count = 6))
    }

    @Test
    fun `the palette ends stop the drag instead of wrapping it`() {
        assertEquals(0, paletteDropTarget(from = 0, offsetPx = -400f, rowHeightPx = 56, count = 8))
        assertEquals(7, paletteDropTarget(from = 7, offsetPx = 400f, rowHeightPx = 56, count = 8))
    }

    @Test
    fun `an unmeasured row or a stale index answers the row picked up`() {
        assertEquals(4, paletteDropTarget(from = 4, offsetPx = 100f, rowHeightPx = 0, count = 8))
        assertEquals(4, paletteDropTarget(from = 4, offsetPx = 100f, rowHeightPx = 56, count = 0))
    }

    @Test
    fun `a move keeps every other swatch in its relative order`() {
        val colors = listOf(10, 20, 30, 40, 50)
        assertEquals(listOf(20, 30, 10, 40, 50), movePaletteColor(colors, from = 0, to = 2))
        assertEquals(listOf(10, 20, 40, 50, 30), movePaletteColor(colors, from = 2, to = 4))
        assertEquals(listOf(10, 50, 20, 30, 40), movePaletteColor(colors, from = 4, to = 1))
    }

    @Test
    fun `a drop where it started writes nothing`() {
        val colors = listOf(10, 20, 30)
        assertSame(colors, movePaletteColor(colors, from = 1, to = 1))
        assertSame(colors, movePaletteColor(colors, from = 1, to = 9))
        assertSame(colors, movePaletteColor(colors, from = -1, to = 2))
    }
}
