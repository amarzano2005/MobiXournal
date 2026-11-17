package com.mobixournal.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A Contents view that does not forget**: the filter, the folds and the reader's place are one value keyed by
 * row id, so renaming a bookmark, adding one before a closed chapter, deleting the row the reader is standing
 * on or undoing the edit cannot reset the list. Every claim here was a way the old per-row `remember` lost state.
 */
class ContentsNavigationTest {

    @Test fun newBranchesStartClosedWithoutReclosingTheReadersExpandedChapters() {
        val roots = listOf(
            OutlineRow("bkm_1", "Chapter", 0, 0), OutlineRow("bkm_2", "Child", 1, 1),
            OutlineRow("bkm_3", "Next", 0, 2), OutlineRow("bkm_4", "Child", 1, 3),
        )
        val initial = ContentsNavigation().against(roots, closeNewBranches = true)
        assertEquals(setOf("bkm_1", "bkm_3"), initial.collapsed)
        val opened = initial.toggled("bkm_1")
        assertEquals(setOf("bkm_3"), opened.against(roots, closeNewBranches = true).collapsed)
        assertEquals(opened.collapsed, opened.withQuery("Child").against(roots, true).collapsed)
        val later = roots + listOf(OutlineRow("bkm_5", "Late", 0, 4), OutlineRow("bkm_6", "Child", 1, 5))
        assertEquals(setOf("bkm_3", "bkm_5"), opened.against(later, true).collapsed)
    }

    private fun row(title: String, page: Int?, depth: Int, index: Int) =
        OutlineRow(id = "bkm_$index", title = title, depth = depth, page = page, row = index)

    private val rows = listOf(
        row("Introduction", 0, 0, 0),
        row("Getting started", 0, 1, 1),
        row("Advanced", 1, 0, 2),
        row("Appendix", 2, 0, 3),
    )

    @Test
    fun `a query is kept without touching the folds, so clearing it restores the tree`() {
        val folded = ContentsNavigation().toggled(rows[0].id)
        val filtered = folded.withQuery("advanced")
        assertEquals("advanced", filtered.query)
        assertEquals(folded.collapsed, filtered.collapsed)
        assertEquals(folded, filtered.withQuery(""))
    }

    @Test
    fun `a fold is one row id, so an edit above it cannot close another chapter`() {
        val folded = ContentsNavigation().toggled(rows[2].id)
        val afterInsert = listOf(row("Preface", null, 0, 9)) + rows
        val kept = folded.against(afterInsert)
        assertTrue("the folded row is still the one that was picked", rows[2].id in kept.collapsed)
        assertEquals("and nothing else was folded", 1, kept.collapsed.size)
    }

    @Test
    fun `a folded row that was deleted is forgotten rather than shadowing a later one`() {
        val folded = ContentsNavigation().toggled(rows[0].id)
        val afterDelete = rows.drop(1).mapIndexed { index, entry -> entry.copy(row = index) }
        assertTrue(folded.against(afterDelete).collapsed.isEmpty())
    }

    @Test
    fun `the reader's place survives an edit elsewhere in the list`() {
        val left = ContentsNavigation().scrolledTo(rows[2].id, 2)
        val after = left.against(rows)
        assertEquals("the same row still anchors the list", rows[2].id, after.anchorId)
        assertEquals(2, after.anchorIndex)
    }

    @Test
    fun `a vanished anchor falls back to the nearest surviving row, not the top`() {
        val left = ContentsNavigation().scrolledTo(rows[2].id, 2)
        val afterDelete = rows.drop(2).mapIndexed { index, entry -> entry.copy(row = index) }
        val after = left.against(afterDelete)
        assertEquals("the row that took its place", afterDelete[0].id, after.anchorId)
        assertEquals(0, after.anchorIndex)
    }

    @Test
    fun `an empty list keeps no anchor`() {
        assertNull(ContentsNavigation().scrolledTo(rows[1].id, 1).against(emptyList()).anchorId)
    }

    @Test
    fun `an untouched navigation is still untouched after the same rows arrive again`() {
        val start = ContentsNavigation()
        assertEquals(start, start.against(rows))
    }
}
