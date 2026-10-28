package com.mobixournal.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page list's drag arithmetic: which row a pointer is over, and how the list scrolls while a drag holds
 * the pointer against an edge.
 *
 * Pure numbers, so they are pinned without a window — the gesture that feeds them is checked in
 * `PageSidebarDragTest`, and the edit they end in by `DesktopPagesTest`.
 */
class PageReorderTest {

    private val rows = listOf(
        PageReorder.Row(index = 0, top = 0f, height = 80f),
        PageReorder.Row(index = 1, top = 88f, height = 80f),
        PageReorder.Row(index = 2, top = 176f, height = 80f),
    )

    @Test
    fun `the pointer lands on the row it is over`() {
        assertEquals(0, PageReorder.target(rows, 0f, fallback = 0))
        assertEquals(0, PageReorder.target(rows, 79f, fallback = 0))
        assertEquals(1, PageReorder.target(rows, 120f, fallback = 0))
        assertEquals(2, PageReorder.target(rows, 250f, fallback = 0))
    }

    @Test
    fun `a pointer past either end answers that end`() {
        assertEquals("above the list", 0, PageReorder.target(rows, -40f, fallback = 2))
        assertEquals("below the list", 2, PageReorder.target(rows, 400f, fallback = 0))
    }

    @Test
    fun `with nothing on screen the drag keeps its own page`() {
        assertEquals(3, PageReorder.target(emptyList(), 50f, fallback = 3))
    }

    @Test
    fun `the middle of the list does not scroll`() {
        assertEquals(0f, PageReorder.scrollStep(y = 300f, viewportHeight = 600f, edge = 56f, maxStep = 10f), 0.001f)
    }

    @Test
    fun `the edges scroll, fastest against the edge`() {
        val gentle = PageReorder.scrollStep(y = 50f, viewportHeight = 600f, edge = 56f, maxStep = 10f)
        val pinned = PageReorder.scrollStep(y = 0f, viewportHeight = 600f, edge = 56f, maxStep = 10f)
        assertTrue("just inside the top edge creeps up ($gentle)", gentle < 0f && gentle > -10f)
        assertEquals("pinned against the top goes at full speed", -10f, pinned, 0.001f)
        assertTrue("the bottom edge goes down", PageReorder.scrollStep(580f, 600f, 56f, 10f) > 0f)
        assertEquals("and past it no faster", 10f, PageReorder.scrollStep(700f, 600f, 56f, 10f), 0.001f)
    }

    @Test
    fun `a list with no height does not scroll`() {
        assertEquals(0f, PageReorder.scrollStep(10f, viewportHeight = 0f, edge = 56f, maxStep = 10f), 0.001f)
    }
}
