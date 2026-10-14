package com.mobixournal.render

import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.mobix.TrackedEntrySource
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Editing a page the page window has evicted (`docs/data-path.md` → *The page window*).
 *
 * The window is a bounded read cache over a source plus an overlay of edited pages that eviction never
 * touches: a command may act on a page that is not in hand (it is materialised first), the result is
 * kept, and undo and redo still find it after the cache has moved on.
 *
 * Everything runs on a synthetic 200-page `.mobix` read through [MobixPagedReader], so "the page left
 * the window" is a real eviction and "only this page was opened" is counted by [TrackedEntrySource].
 */
class PageWindowTest {

    private val pageCount = 200
    private val lastPage = 150

    private fun archive(): File =
        File.createTempFile("page_window_", ".mobix").apply {
            deleteOnExit()
            SyntheticNotebook.write(this, SyntheticNotebook.pages(pageCount))
        }

    private fun elements(page: Page): Int = page.layers.sumOf { it.elements.size }

    private fun tracked(): TrackedEntrySource = TrackedEntrySource(MobixEntrySource.fromFile(archive()))

    /** Every page entry [TrackedEntrySource] has opened, by index (the entries of this fixture). */
    private fun openedPages(tracked: TrackedEntrySource): Set<Int> =
        (0 until pageCount).filter { tracked.openCount(SyntheticNotebook.entryOf(it)) > 0 }.toSet()

    @Test
    fun editingAPageOutsideTheWindowMaterialisesOnlyThatPage() {
        val tracked = tracked()
        val window = PageWindow(PageSource.of(MobixPagedReader(tracked, cacheBudget = PageWindow.DEFAULT_BUDGET)))
        try {
            // Pages 0..4 are the window: the only pages read so far.
            for (i in 0..4) window.page(i)
            assertEquals((0..4).toSet(), window.cachedIndices())

            // An edit on page 151, which nothing has touched.
            val edited = window.edit(lastPage) { page -> SelectionOps.deleteOn(page, setOf(ElementRef(0, 0))) }
            assertEquals(0, elements(edited!!))
            assertEquals(setOf(0, 1, 2, 3, 4, lastPage), openedPages(tracked))
            assertEquals(setOf(lastPage), window.dirtyIndices)
        } finally {
            window.reset()
        }
    }

    @Test
    fun deleteInsertAndMoveOnPage150LeavePages0To4Untouched() {
        val tracked = tracked()
        val window = PageWindow(PageSource.of(MobixPagedReader(tracked, cacheBudget = PageWindow.DEFAULT_BUDGET)))
        try {
            val resident = (0..4).map { window.page(it) }

            // Insert: page 151 gains an element.
            window.edit(lastPage) { page -> SelectionOps.addToTopLayerOn(page, listOf(SyntheticNotebook.stroke(999.0))).first }
            assertEquals(2, elements(window.page(lastPage)))

            // Delete: the element just inserted is removed again, by its ref.
            window.edit(lastPage) { page -> SelectionOps.deleteOn(page, setOf(ElementRef(0, 1))) }
            assertEquals(1, elements(window.page(lastPage)))

            // Move: page 3's only element onto page 151 (both pages are written back).
            val (from, to, _) = SelectionOps.moveOnto(
                window.page(3), window.page(lastPage), setOf(ElementRef(0, 0)), 1.0, 0.0, 0.0,
            )
            window.set(3, from)
            window.set(lastPage, to)

            assertEquals(0, elements(window.page(3)))
            assertEquals(2, elements(window.page(lastPage)))
            assertEquals(setOf(3, lastPage), window.dirtyIndices)

            // The pages that shared the window are exactly as the source declared them, page 3 aside.
            for (i in 0..4) if (i != 3) assertEquals("page $i must be unchanged", resident[i], window.page(i))
        } finally {
            window.reset()
        }
    }

    @Test
    fun anEditSurvivesItsPageLeavingTheReadCache() {
        val tracked = tracked()
        val window = PageWindow(PageSource.of(MobixPagedReader(tracked, cacheBudget = PageWindow.DEFAULT_BUDGET)))
        try {
            for (i in 0..4) window.page(i)
            val edited = window.edit(lastPage) { page ->
                SelectionOps.addToTopLayerOn(page, listOf(SyntheticNotebook.stroke(7.0))).first
            }!!

            // Read the whole notebook: the read cache holds five pages, and page 151 is long gone.
            for (i in 0 until pageCount - 1) window.page(i)
            assertFalse("page 151 must have left the read cache", lastPage in window.cachedIndices())

            // The edit is still there, because it lives in the overlay, not in the cache.
            assertSame(edited, window.page(lastPage))
            assertEquals(setOf(lastPage), window.dirtyIndices)
            assertFalse(window.isClean)
            assertEquals(2, elements(window.page(lastPage)))
        } finally {
            window.reset()
        }
    }

    @Test
    fun undoAndRedoRestoreAndReapplyAnEditWhosePageLeftTheWindow() {
        val tracked = tracked()
        val window = PageWindow(PageSource.of(MobixPagedReader(tracked, cacheBudget = PageWindow.DEFAULT_BUDGET)))
        try {
            for (i in 0..4) window.page(i)
            val before = window.page(lastPage)
            val edited = window.edit(lastPage) { page -> SelectionOps.deleteOn(page, setOf(ElementRef(0, 0))) }!!
            for (i in 0 until pageCount - 1) window.page(i) // force the page out of the read cache

            // Undo: the window answers with the source's page again, and nothing is dirty.
            assertEquals(before, window.revert(lastPage))
            assertEquals(1, elements(window.page(lastPage)))
            assertTrue(window.isClean)

            // Redo: the page that had left the window is put back, edit and all.
            window.set(lastPage, edited)
            assertSame(edited, window.page(lastPage))
            assertEquals(setOf(lastPage), window.dirtyIndices)
        } finally {
            window.reset()
        }
    }

    @Test
    fun applyToCarriesEveryUneditedPageThroughAndRewritesOnlyTheEditedOne() {
        val tracked = tracked()
        val window = PageWindow(PageSource.of(MobixPagedReader(tracked, cacheBudget = PageWindow.DEFAULT_BUDGET)))
        try {
            val base = SyntheticNotebook.pages(pageCount)
            // Clean: the list comes back as it is, not as a copy.
            assertSame(base, window.applyTo(base))

            val edited = window.edit(lastPage) { page -> SelectionOps.deleteOn(page, setOf(ElementRef(0, 0))) }!!
            val out = window.applyTo(base)
            assertSame(edited, out[lastPage])
            assertEquals(0, elements(out[lastPage]))
            for (i in 0 until pageCount) {
                if (i != lastPage) assertSame("page $i must be carried through unchanged", base[i], out[i])
            }
        } finally {
            window.reset()
        }
    }

    @Test
    fun anElementEditOnAnEvictedPageStaysOnThatPage() {
        val tracked = tracked()
        val window = PageWindow(PageSource.of(MobixPagedReader(tracked, cacheBudget = PageWindow.DEFAULT_BUDGET)))
        try {
            val resident = (0..4).map { window.page(it) }
            val base = SyntheticNotebook.pages(pageCount)

            // An element edit on page 151: the page is materialised by the window, edited on its own
            // (no document walk), and kept. `replaceElementOn` matches by identity, so the element
            // comes from the materialised page, exactly as the canvas finds it.
            val replacement = SyntheticNotebook.stroke(4242.0, id = "stk_replaced")
            val old = window.page(lastPage).layers[0].elements[0]
            val edited = window.edit(lastPage) { page ->
                ElementEdits.replaceElementOn(page, old, replacement) ?: page
            }
            assertEquals(1, elements(edited!!))
            assertEquals("stk_replaced", (edited.layers[0].elements[0] as com.mobixournal.format.model.Stroke).id)
            assertEquals(setOf(lastPage), window.dirtyIndices)

            // A page that does not hold the element answers null, so nothing is marked dirty.
            assertNull(ElementEdits.replaceElementOn(base[0], replacement, null))
            for (i in 0..4) assertEquals("page $i must be unchanged", resident[i], window.page(i))
        } finally {
            window.reset()
        }
    }

    @Test
    fun aPinnedPageStaysInTheReadCacheUntilTheGestureSettles() {
        val tracked = tracked()
        val window = PageWindow(PageSource.of(MobixPagedReader(tracked, cacheBudget = PageWindow.DEFAULT_BUDGET)))
        try {
            window.pin(0)
            for (i in 0..20) window.page(i)
            assertTrue("a pinned page is not evictable", 0 in window.cachedIndices())
            window.unpin(0)
            window.page(21)
            assertFalse(0 in window.cachedIndices())
        } finally {
            window.reset()
        }
    }
}
