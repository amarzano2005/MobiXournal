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
 *
 * The rows arrive resolved (`OutlineResolution`) — the `page` here is a document page, which is exactly what
 * the view is handed in both shells.
 */
class PdfOutlineViewTest {
    @Test fun aFilterKeepsTheParentOfTwentyThousandSiblingMatches() {
        val rows = listOf(OutlineRow("bkm_root", "Chapter", 0, 0)) + (1..20_000).map {
            OutlineRow("bkm_$it", "Section $it", 1, it)
        }
        val shown = PdfOutlineView.visible(rows, collapsed = setOf("bkm_root"), query = "Section", currentPage = 20_000)
        assertEquals(rows.size, shown.size)
        assertTrue(shown.first().context)
        assertTrue(shown.last().current)
        assertEquals(setOf(0), PdfOutlineView.branchRows(rows))
    }

    private fun row(title: String, page: Int?, depth: Int, index: Int) =
        OutlineRow(id = "bkm_$index", title = title, depth = depth, page = page, row = index)

    /**
     * Chapters "Introduction" and "Introduction" again — the duplicate title — each with a section, plus a
     * header that names no page.
     */
    private val entries = listOf(
        row("Introduction", 0, 0, 0),
        row("Getting started", 0, 1, 1),
        row("Install", 1, 2, 2),
        row("Introduction", 3, 0, 3),
        row("Advanced", 4, 1, 4),
        row("Header only", null, 1, 5),
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
    fun `collapsing by id closes one duplicate chapter and leaves the other open`() {
        val collapsed = PdfOutlineView.toggled(emptySet(), entries[0].id)
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
        val collapsed = setOf(entries[0].id)
        val before = PdfOutlineView.visible(entries, collapsed)
        val filtered = PdfOutlineView.visible(entries, collapsed, query = "install")
        assertEquals("the hidden section is found anyway", listOf(0, 1, 2), filtered.map { it.row })
        assertEquals("and the collapse state never moved", collapsed, setOf(entries[0].id))
        assertEquals("clearing the box gives back exactly what it showed", before, PdfOutlineView.visible(entries, collapsed))
    }

    @Test
    fun `a search nothing answers is empty, not the whole tree`() {
        assertTrue(PdfOutlineView.visible(entries, query = "bibliography").isEmpty())
        assertTrue(PdfOutlineView.visible(entries, query = "   ").size == entries.size)
    }

    @Test
    fun `the current row is the last one naming the page the reader stands on`() {
        assertEquals("three rows name page 0, and the last of them wins", 1, PdfOutlineView.currentRow(entries, 0))
        assertEquals(3, PdfOutlineView.currentRow(entries, 3))
        assertNull("a page no row names is no row at all", PdfOutlineView.currentRow(entries, 9))
        assertNull(PdfOutlineView.currentRow(entries, null))
    }

    @Test
    fun `the row naming the page in view is the one the view marks`() {
        val rows = PdfOutlineView.visible(entries, currentPage = 4)

        assertEquals("only the last chapter names page 4", listOf(false, false, false, false, true, false), rows.map { it.current })
    }

    @Test
    fun `a branch folded by id stays folded when a row is inserted above it`() {
        val folded = PdfOutlineView.toggled(emptySet(), entries[3].id)
        val afterInsert = listOf(row("Preface", null, 0, 6)) + entries.mapIndexed { index, entry -> entry.copy(row = index + 1) }
        val rows = PdfOutlineView.visible(afterInsert, folded)
        assertTrue("the inserted row is not folded", rows.first { it.id == afterInsert[0].id }.expanded)
        assertTrue("the chapter that was closed still is", rows.none { it.title == "Advanced" })
    }

    @Test
    fun `folds of rows that no longer exist are dropped`() {
        val gone = "bkm_gone"
        assertEquals(emptySet<String>(), PdfOutlineView.pruned(setOf(gone), entries))
        assertEquals(setOf(entries[0].id), PdfOutlineView.pruned(setOf(entries[0].id, gone), entries))
    }

    @Test
    fun `a missing destination does not make a header the current row`() {
        val headersOnly = listOf(OutlineRow(id = "bkm_h", title = "Header only", depth = 0, page = null))
        assertNull(PdfOutlineView.currentRow(headersOnly, 0))
        assertFalse(PdfOutlineView.isMatch(headersOnly.single(), "  "))
        assertTrue(PdfOutlineView.isMatch(headersOnly.single(), "only"))
    }
}
