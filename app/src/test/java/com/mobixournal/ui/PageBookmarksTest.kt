package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bookmark model's pure half: labels/colours, the persisted codec, and the page shifts. */
class PageBookmarksTest {

    @Test
    fun `bookmarks survive the codec`() {
        val list = listOf(
            PageBookmark(0, "Intro", 0xFFE57373.toInt()),
            PageBookmark(7, "Methods", 0xFF64B5F6.toInt()),
        )
        assertEquals(list, PageBookmarks.decode(PageBookmarks.encode(list)))
    }

    @Test
    fun `a label with separators in it still round-trips`() {
        val awkward = "a\tb\nc\\d"
        val list = listOf(PageBookmark(2, awkward, 1))
        assertEquals(list, PageBookmarks.decode(PageBookmarks.encode(list)))
    }

    @Test
    fun `unparsable lines are dropped, not fatal`() {
        val text = "0\t-1\tOne\nbroken\n2\t-2\tTwo"
        assertEquals(listOf(0, 2), PageBookmarks.decode(text).map { it.page })
        assertTrue(PageBookmarks.decode(null).isEmpty())
        assertTrue(PageBookmarks.decode("").isEmpty())
    }

    @Test
    fun `an empty label falls back to the page number`() {
        assertEquals("Page 4", PageBookmark(3, "", 0).displayLabel())
        assertEquals("Page 4", PageBookmark(3, "   ", 0).displayLabel())
        assertEquals("Chapter 1", PageBookmark(3, "Chapter 1", 0).displayLabel())
    }

    @Test
    fun `a new bookmark replaces whatever was on that page`() {
        val start = listOf(PageBookmark(1, "One", 1), PageBookmark(4, "Four", 2))
        val updated = PageBookmarks.with(start, 4, "  Methods  ", 3)
        assertEquals(2, updated.size)
        assertEquals(PageBookmark(4, "Methods", 3), updated.first { it.page == 4 })
        // …and stays in page order.
        assertEquals(listOf(1, 4), updated.map { it.page })
        // Removing takes only that page's bookmark with it.
        assertEquals(listOf(1), PageBookmarks.without(updated, 4).map { it.page })
    }

    @Test
    fun `a new bookmark takes the next colour`() {
        val one = listOf(PageBookmark(0, "", PageBookmarks.COLORS[0]))
        assertEquals(PageBookmarks.COLORS[1], PageBookmarks.nextColor(one))
        // The cycle wraps rather than running out.
        val all = PageBookmarks.COLORS.mapIndexed { i, c -> PageBookmark(i, "", c) }
        assertEquals(PageBookmarks.COLORS.first(), PageBookmarks.nextColor(all))
    }

    @Test
    fun `inserting a page moves the flags at and after it down`() {
        val start = listOf(PageBookmark(0, "A", 1), PageBookmark(2, "B", 2))
        val after = PageBookmarks.insertedAt(start, 1)
        assertEquals(listOf(0 to "A", 3 to "B"), after.map { it.page to it.label })
    }

    @Test
    fun `deleting a page takes its bookmark and pulls the later ones up`() {
        val start = listOf(PageBookmark(0, "A", 1), PageBookmark(2, "B", 2), PageBookmark(5, "C", 3))
        val after = PageBookmarks.removedAt(start, 2)
        assertEquals(listOf(0 to "A", 4 to "C"), after.map { it.page to it.label })
        // Deleting the page "A" sat on takes that flag with it and moves the rest up one.
        assertEquals(
            listOf(1 to "B", 4 to "C"),
            PageBookmarks.removedAt(start, 0).map { it.page to it.label },
        )
    }
}
