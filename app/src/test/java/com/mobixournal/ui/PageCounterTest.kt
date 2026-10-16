package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The two canvas badges are pure text mappers; pin their formatting down. */
class PageCounterTest {

    @Test
    fun `zoom label is a whole percentage`() {
        assertEquals("100%", zoomLabel(1f))
        assertEquals("125%", zoomLabel(1.25f))
        assertEquals("25%", zoomLabel(0.25f))
        // Rounds rather than truncates: 96.1% reads as 96%, not 96.1 -> 96.00000.
        assertEquals("96%", zoomLabel(0.961f))
        assertEquals("400%", zoomLabel(4f))
    }

    @Test
    fun `page label counts from one`() {
        assertEquals("1 / 12", pageLabel(0, 12))
        assertEquals("12 / 12", pageLabel(11, 12))
    }

    @Test
    fun `page label never throws on an empty document`() {
        assertEquals("0 / 0", pageLabel(0, 0))
    }

    @Test
    fun `a board's badge reads the tile's coordinate`() {
        assertEquals("0, 0", tileLabel(0 to 0))
        assertEquals("1, -1", tileLabel(1 to -1))
        assertEquals("-3, 2", tileLabel(-3 to 2))
        // Never a page label: a board has no page 1 of N to count up to.
        assertEquals(false, tileLabel(2 to 5).contains("/"))
    }
}
