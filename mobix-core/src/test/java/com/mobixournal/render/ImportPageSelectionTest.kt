package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The grid's arithmetic**, as the one rule both shells share: a tap toggles a page, *Select all* names
 * every one, and the answer compresses to the grammar the field is typed in.
 *
 * The round trip is the part worth guarding: whatever the grid writes, the field reads back as the same
 * pages — otherwise tapping and typing would disagree about what is going to be imported.
 */
class ImportPageSelectionTest {

    @Test
    fun `a tap adds the page in the file's order`() {
        assertEquals(listOf(1, 3, 6), ImportPageSelection.toggle(listOf(3, 1), 6))
        assertEquals(listOf(0, 2), ImportPageSelection.toggle(listOf(2), 0))
    }

    @Test
    fun `a tap on a picked page takes it away`() {
        assertEquals(listOf(1, 6), ImportPageSelection.toggle(listOf(1, 3, 6), 3))
        assertEquals("the last page leaves an empty answer", emptyList<Int>(), ImportPageSelection.toggle(listOf(4), 4))
    }

    @Test
    fun `select all names every page and nothing else`() {
        assertEquals(listOf(0, 1, 2), ImportPageSelection.all(3))
        assertEquals(emptyList<Int>(), ImportPageSelection.all(0))
    }

    @Test
    fun `a run of three collapses and anything shorter is named page by page`() {
        assertEquals("2,4-6,9", ImportPageSelection.text(listOf(1, 3, 4, 5, 8)))
        assertEquals("1,2", ImportPageSelection.text(listOf(0, 1)))
        assertEquals("1-10", ImportPageSelection.text((0 until 10).toList()))
        assertEquals("", ImportPageSelection.text(emptyList()))
    }

    @Test
    fun `the grid's text is what the field reads back`() {
        val picked = listOf(1, 3, 4, 5, 8)
        val text = ImportPageSelection.text(picked)
        assertEquals("the field and the grid agree", picked, PageRanges.selection(text, 20).indices())
    }

    @Test
    fun `a selection typed into the field is the same answer`() {
        val typed = PageRanges.selection("2,4-6,9", 20).indices()!!
        assertEquals("2,4-6,9", ImportPageSelection.text(typed))
    }
}
