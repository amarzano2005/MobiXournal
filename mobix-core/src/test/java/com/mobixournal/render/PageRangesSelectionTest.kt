package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A selection of pages** — `2,4-6,9` — as the one reader both import cards share.
 *
 * The two things worth guarding are that the grammar is the reader's (spaces free, runs and single pages
 * mixed, either end included) and that the answer is a **set in the source's order**: a PDF's pages land
 * in the document in the order the file has them, so typing `9,2` brings page 2 and then page 9, and a
 * page named twice is still one sheet. Everything else — a letter, a page past the end, a run typed
 * backwards — is a sentence, never a guess.
 */
class PageRangesSelectionTest {

    private fun chosen(text: String, pageCount: Int = 10): List<Int>? =
        PageRanges.selection(text, pageCount).indices()

    @Test
    fun `runs and single pages mix, either end included`() {
        assertEquals(listOf(1, 3, 4, 5, 8), chosen("2,4-6,9"))
        assertEquals("spaces are free", listOf(1, 3, 4, 5, 8), chosen(" 2 , 4 - 6 , 9 "))
    }

    @Test
    fun `one page and one run are the small cases`() {
        assertEquals(listOf(2), chosen("3"))
        assertEquals(listOf(2, 3, 4), chosen("3-5"))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), chosen("1-10"))
    }

    @Test
    fun `a page named twice is still one sheet`() {
        assertEquals("pages 2 and 3, once each", listOf(1, 2), chosen("2,2,2-3"))
        assertEquals("overlapping runs collapse", listOf(2, 3, 4, 5), chosen("3-5,4-6"))
    }

    @Test
    fun `typing out of order brings the source's order`() {
        assertEquals("9 then 2 is page 2 then page 9", listOf(1, 8), chosen("9,2"))
        assertEquals(listOf(0, 4), chosen("5,1"))
    }

    @Test
    fun `a letter, a blank and an empty field are refused`() {
        assertTrue(PageRanges.selection("", 10) is PageRangePlan.Invalid)
        assertTrue(PageRanges.selection("   ", 10) is PageRangePlan.Invalid)
        assertTrue(PageRanges.selection("a", 10) is PageRangePlan.Invalid)
        assertTrue("a hole between two runs", PageRanges.selection("2,,4", 10) is PageRangePlan.Invalid)
        assertTrue(PageRanges.selection("1-2-3", 10) is PageRangePlan.Invalid)
        assertNull(chosen("a"))
    }

    @Test
    fun `a page outside the file is refused, and the sentence says the numbering`() {
        val past = PageRanges.selection("11", 10) as PageRangePlan.Invalid
        assertEquals("Pages are numbered 1 to 10.", past.message)
        assertEquals("Pages are numbered 1 to 10.", (PageRanges.selection("0", 10) as PageRangePlan.Invalid).message)
        assertEquals("Pages are numbered 1 to 10.", (PageRanges.selection("2,12-14", 10) as PageRangePlan.Invalid).message)
    }

    @Test
    fun `a run typed backwards is refused rather than read backwards`() {
        val reversed = PageRanges.selection("6-4", 10) as PageRangePlan.Invalid
        assertEquals("The last page of 6-4 comes before the first.", reversed.message)
    }

    @Test
    fun `a file with no pages has nothing to select`() {
        assertTrue(PageRanges.selection("1", 0) is PageRangePlan.Invalid)
        assertNull(PageRanges.selection("1", 0).indices())
    }

    @Test
    fun `the contiguous API is unchanged and answers the same accessor`() {
        val run = PageRanges.pages(PageRangeRequest(first = "3", last = "5"), 10)
        assertTrue(run is PageRangePlan.Pages)
        assertEquals(listOf(2, 3, 4), run.indices())
        assertEquals("one page is a run of one", listOf(2), PageRanges.pages(PageRangeRequest("3", "3"), 10).indices())
    }

    @Test
    fun `a card opens on the whole file`() {
        assertEquals("1", PageRanges.whole(1))
        assertEquals("1-108", PageRanges.whole(108))
        assertEquals(listOf(0, 1, 2), PageRanges.selection(PageRanges.whole(3), 3).indices())
    }
}
