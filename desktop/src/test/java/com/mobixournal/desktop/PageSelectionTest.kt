package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sidebar's **multi-page selection**, as arithmetic: which pages a click picks, what a drag carries,
 * and what survives the list changing under the selection.
 *
 * The gestures themselves are `PageSelectionSceneTest`'s; this file is the rule behind them, pure and
 * millisecond-fast, so the "Ctrl adds, Shift takes a run" behaviour is stated once instead of being read
 * out of a pointer handler.
 */
class PageSelectionTest {

    private fun click(at: Int, ctrl: Boolean = false, shift: Boolean = false, from: PageSelection = PageSelection()) =
        PageSelectionRules.click(from, at, ctrl, shift, pageCount = 10)

    @Test
    fun `a plain click picks only that page`() {
        val before = PageSelection(setOf(1, 2, 3), anchor = 1)
        val after = PageSelectionRules.click(before, 7, ctrl = false, shift = false, pageCount = 10)

        assertEquals(setOf(7), after.pages)
        assertEquals("the anchor is the page just clicked", 7, after.anchor)
    }

    @Test
    fun `ctrl adds and then takes a page away`() {
        val first = click(2, ctrl = true)
        val second = PageSelectionRules.click(first, 5, ctrl = true, shift = false, pageCount = 10)
        assertEquals("two rows collected", setOf(2, 5), second.pages)

        val third = PageSelectionRules.click(second, 2, ctrl = true, shift = false, pageCount = 10)
        assertEquals("the second Ctrl-click on a picked row un-picks it", setOf(5), third.pages)
        assertEquals("and the anchor follows the row touched", 2, third.anchor)
    }

    @Test
    fun `shift takes the run from the anchor, in either direction`() {
        val start = PageSelectionRules.click(PageSelection(), 3, ctrl = false, shift = false, pageCount = 10)
        val down = PageSelectionRules.click(start, 6, ctrl = false, shift = true, pageCount = 10)
        assertEquals(setOf(3, 4, 5, 6), down.pages)
        assertEquals("the anchor stays where the run started", 3, down.anchor)

        val up = PageSelectionRules.click(down, 1, ctrl = false, shift = true, pageCount = 10)
        assertEquals("a run measured upward is still that run", setOf(1, 2, 3), up.pages)
    }

    @Test
    fun `shift without an anchor is a single pick`() {
        val after = click(4, shift = true)
        assertEquals(setOf(4), after.pages)
        assertEquals(4, after.anchor)
    }

    @Test
    fun `a click outside the list changes nothing`() {
        val before = PageSelection(setOf(2), anchor = 2)
        val after = PageSelectionRules.click(before, 12, ctrl = true, shift = false, pageCount = 10)
        assertEquals("out-of-range is not a page", before, after)
    }

    @Test
    fun `a drag on a picked row carries the whole pick`() {
        val picked = PageSelection(setOf(1, 4, 5), anchor = 4)
        assertEquals(setOf(1, 4, 5), PageSelectionRules.dragging(picked, 4))
        assertEquals("a row outside the pick travels alone", setOf(7), PageSelectionRules.dragging(picked, 7))
        assertEquals(setOf(0), PageSelectionRules.dragging(PageSelection(), 0))
    }

    @Test
    fun `shrinking the document drops what no longer exists`() {
        val wide = PageSelection(setOf(1, 4, 9), anchor = 9)
        val kept = wide.sanitized(pageCount = 5)

        assertEquals(setOf(1, 4), kept.pages)
        assertEquals("an anchor that vanished falls back to none", -1, kept.anchor)
        assertTrue("a selection that still fits is left alone", wide.sanitized(10).pages == wide.pages)
        assertFalse("and emptying the document empties the selection", PageSelection(setOf(0)).sanitized(0).pages.isNotEmpty())
    }

    @Test
    fun `the bar says how many, in English`() {
        assertEquals("1 page selected", PageSelectionRules.title(1))
        assertEquals("2 pages selected", PageSelectionRules.title(2))
    }
}
