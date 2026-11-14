package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A Contents tree's expansion and its filter**, pinned on the pure view — the rules both shells draw.
 *
 * Three things are claimed here, and each of them was a bug waiting to happen: a row is addressed by its
 * **position**, so two chapters called "Introduction" collapse separately; a filter keeps the ancestors of
 * its matches as context and never touches the collapse state, so clearing the box restores the tree; and
 * the row the reader is standing on is found by **destination**, with a destination-less header naming
 * nothing at all.
 */
class PdfOutlineViewTest {

    /**
     * Chapters "Introduction" and "Introduction" again — the duplicate title — each with a section, plus a
     * header that names no page.
     */
    private val entries = listOf(
        PdfOutlineEntry("Introduction", 0, 0),
        PdfOutlineEntry("Getting started", 0, 1),
        PdfOutlineEntry("Install", 1, 2),
        PdfOutlineEntry("Introduction", 3, 0),
        PdfOutlineEntry("Advanced", 4, 1),
        PdfOutlineEntry("Header only", PdfOutlineEntry.NO_DESTINATION, 1),
    )

    @Test
    fun `a branch is a row whose next row is deeper, and the duplicate titles are two branches`() {
        val branches = PdfOutlineView.branchRows(entries)
        assertTrue("the first Introduction owns its section", 0 in branches)
        assertTrue("which in turn owns a subsection", 1 in branches)
        assertTrue("the second Introduction owns Advanced", 3 in branches)
        assertFalse("a leaf draws no control", 2 in branches)
        assertEquals("and the branch ends where the depth does not grow", 3, PdfOutlineView.endOfBranch(entries, 0))
        assertEquals("a section's branch is only its own rows", 3, PdfOutlineView.endOfBranch(entries, 1))
    }

    @Test
    fun `collapsing by position closes one duplicate chapter and leaves the other open`() {
        val collapsed = PdfOutlineView.toggled(emptySet(), row = 0)
        val rows = PdfOutlineView.visible(entries, collapsed)
        assertEquals(
            listOf(0, 3, 4, 5),
            rows.map { it.row },
        )
        // The other "Introduction" is still there, with the section under it.
        assertTrue("the same title elsewhere is untouched", 4 in rows.map { it.row })
    }

    @Test
    fun `a filter keeps the path to its matches as context and marks the matches`() {
        val rows = PdfOutlineView.visible(entries, query = "install")
        assertEquals(listOf(0, 1, 2), rows.map { it.row })
        assertTrue("the chapter is only the path", rows[0].context)
        assertFalse(rows[0].matched)
        assertTrue("so is the section above the hit", rows[1].context)
        assertTrue("and the deepest row is the hit", rows[2].matched)
        assertFalse(rows[2].context)
    }

    @Test
    fun `a filter matches every duplicate, and both branches come with it`() {
        val rows = PdfOutlineView.visible(entries, query = "INTRODUCTION")
        assertEquals(listOf(0, 3), rows.map { it.row })
        assertTrue(rows.all { it.matched && !it.context })
    }

    @Test
    fun `a filter overrides a collapse but does not change it, so clearing restores the tree`() {
        val collapsed = setOf(0)
        val before = PdfOutlineView.visible(entries, collapsed)
        val filtered = PdfOutlineView.visible(entries, collapsed, query = "install")
        assertEquals("the hidden section is found anyway", listOf(0, 1, 2), filtered.map { it.row })
        assertEquals("and the collapse state never moved", collapsed, setOf(0))
        assertEquals("clearing the box gives back exactly what it showed", before, PdfOutlineView.visible(entries, collapsed))
    }

    @Test
    fun `a search nothing answers is empty, not the whole tree`() {
        assertTrue(PdfOutlineView.visible(entries, query = "bibliography").isEmpty())
        assertTrue(PdfOutlineView.visible(entries, query = "   ").size == entries.size)
    }

    @Test
    fun `the current row is the last one naming the page the reader stands on`() {
        assertEquals("two bookmarks can share a page, and the later one wins", 1, PdfOutlineView.currentRow(entries, 0))
        assertEquals(3, PdfOutlineView.currentRow(entries, 3))
        assertEquals("two bookmarks can share a page, and the later one wins", 3, PdfOutlineView.currentRow(entries, 3))
        assertNull("a sheet no bookmark names is no row at all", PdfOutlineView.currentRow(entries, 9))
        assertNull(PdfOutlineView.currentRow(entries, null))
    }

    @Test
    fun `a missing destination does not make a header the current row`() {
        val headersOnly = listOf(PdfOutlineEntry("Header only", PdfOutlineEntry.NO_DESTINATION, 0))
        assertNull(PdfOutlineView.currentRow(headersOnly, 0))
        assertFalse(PdfOutlineView.isMatch(headersOnly.single(), "  "))
        assertTrue(PdfOutlineView.isMatch(headersOnly.single(), "only"))
    }
}
