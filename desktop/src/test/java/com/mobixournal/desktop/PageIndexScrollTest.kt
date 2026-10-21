package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five cases the page-offset arithmetic has to get right — and the two of them (first page, last
 * page) that are only visible as a page that "sticks" a few pixels short of where it should stop.
 */
class PageIndexScrollTest {

    @Test
    fun `an empty document has no range and no offset`() {
        assertEquals(0f..0f, PageIndexScroll.range(0f, 700f))
        assertEquals(0f, PageIndexScroll.clamp(120f, 0f, 700f), 0f)
        assertEquals(0, PageIndexScroll.pageAt(0f, emptyList(), 10f, 700f))
    }

    @Test
    fun `a page shorter than the viewport has exactly one legal offset`() {
        val range = PageIndexScroll.range(400f, 700f)
        assertEquals("centred", 150f, range.start, 0f)
        assertEquals(150f, range.endInclusive, 0f)
        assertEquals("any drag snaps back to the centre", 150f, PageIndexScroll.clamp(-999f, 400f, 700f), 0f)
        assertFalse("nothing to scroll", PageIndexScroll.isAtStart(150f, 400f, 700f))
        assertFalse(PageIndexScroll.isAtEnd(150f, 400f, 700f))
    }

    @Test
    fun `a page taller than the viewport scrolls from its top to its end`() {
        val range = PageIndexScroll.range(2000f, 700f)
        assertEquals("the farthest offset shows the page's end", -1300f, range.start, 0f)
        assertEquals("the nearest puts the page's top at the window's top", 0f, range.endInclusive, 0f)

        val middle = PageIndexScroll.clamp(-500f, 2000f, 700f)
        assertEquals(-500f, middle, 0f)
        assertEquals("clamped at the top", 0f, PageIndexScroll.clamp(50f, 2000f, 700f), 0f)
        assertEquals("clamped at the end", -1300f, PageIndexScroll.clamp(-5000f, 2000f, 700f), 0f)
        assertTrue(PageIndexScroll.isAtStart(0f, 2000f, 700f))
        assertTrue(PageIndexScroll.isAtEnd(-1300f, 2000f, 700f))
        assertFalse(PageIndexScroll.isAtEnd(-500f, 2000f, 700f))
    }

    @Test
    fun `the first page is reached without scrolling past it`() {
        val heights = listOf(800f, 800f, 800f)
        assertEquals("the first page sits at the top", 0f, PageIndexScroll.offsetOfPage(0, heights, 10f, 700f), 0f)
        assertEquals("and asking for it from anywhere returns to the top", 0f, PageIndexScroll.offsetOfPage(0, heights, 10f, 700f), 0f)
    }

    @Test
    fun `the last page is reached without scrolling past it`() {
        val heights = listOf(800f, 800f, 800f)
        // Three 800pt pages with 10pt gaps: page 2 starts at 1620pt, so its top reaches the window's top
        // at -1620 — which is inside the scroll range (the document's end is -1720).
        val last = PageIndexScroll.offsetOfPage(2, heights, 10f, 700f)
        assertEquals(-1620f, last, 0f)
        assertEquals("asking again does not scroll further", last, PageIndexScroll.offsetOfPage(2, heights, 10f, 700f), 0f)
    }

    @Test
    fun `the page at the viewport centre is reported at every page`() {
        val heights = listOf(400f, 400f, 400f)
        for (index in heights.indices) {
            val offset = PageIndexScroll.offsetOfPage(index, heights, 0f, 400f)
            assertEquals("scrolling to page $index reports it", index, PageIndexScroll.pageAt(offset, heights, 0f, 400f))
        }
    }
}
