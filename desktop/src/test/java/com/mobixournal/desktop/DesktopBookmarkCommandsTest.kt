package com.mobixournal.desktop

import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.io.saveAs
import com.mobixournal.desktop.settings.DesktopNewDocumentFormat
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.render.OutlineEdits
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Contents edits on the window** — the commands the Contents section will call (`DesktopPages`).
 *
 * The rules themselves live in `OutlineEdits`; what is checked here is the window's half of them: a row is
 * added on the page in view, every edit is **one undo step**, a refusal writes nothing, and the tree the
 * reader built is in the file after a save and a reopen.
 */
class DesktopBookmarkCommandsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun shell(): DesktopShellState {
        val settings = DesktopSettings(File(temp.root, "bookmarks.properties")).also { store ->
            store.newDocumentFormat = DesktopNewDocumentFormat.MOBIX
            store.documentFormatChosen = true
        }
        return DesktopShellState(settings).also { it.newDocument() }
    }

    private fun refused(result: OutlineEdits.Result): OutlineEdits.Refusal =
        (result as? OutlineEdits.Result.Refused)?.reason ?: error("expected a refusal, got $result")

    @Test
    fun `an added row opens the page in view and undoes as one step`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            tab.page = 0

            val result = state.pagesSection.addBookmark("  Introduction  ")

            assertEquals("the edit succeeded", OutlineEdits.Result.Ok::class, result::class)
            val row = tab.document.model.outline.nodes.single()
            val pageId = tab.document.model.pages[0].id!!
            assertEquals("the title is trimmed", "Introduction", row.title)
            assertEquals("a paper page is named by its id", OutlineTarget.Page(pageId), row.target)
            assertTrue("and the fresh page keeps the id the row names", pageId.startsWith("pg_"))

            assertTrue("one step", tab.undo())
            assertTrue("undo takes the row back", tab.document.model.outline.isEmpty)
            assertTrue("and redo puts it back", tab.redo())
            assertEquals("Introduction", tab.document.model.outline.nodes.single().title)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `renaming keeps the row's identity and target, and two rows may share a title`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.pagesSection.addBookmark("Intro")
            state.pagesSection.addBookmark("Intro")
            val rows = tab.document.model.outline.nodes
            assertEquals("two rows, same title", listOf("Intro", "Intro"), rows.map { it.title })

            val result = state.pagesSection.renameBookmark(rows.first().id, "Chapter One")

            val renamed = (result as OutlineEdits.Result.Ok).tree.nodes.first()
            assertEquals("the new title", "Chapter One", renamed.title)
            assertEquals("the same node", rows.first().id, renamed.id)
            assertEquals("the same destination", rows.first().target, renamed.target)
            assertEquals("and the other row is untouched", "Intro", tab.document.model.outline.nodes.last().title)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a blank title is refused and records no step`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.pagesSection.addBookmark("Intro")

            assertEquals(OutlineEdits.Refusal.EMPTY_TITLE, refused(state.pagesSection.addBookmark("   ")))
            val id = tab.document.model.outline.nodes.single().id
            assertEquals(OutlineEdits.Refusal.EMPTY_TITLE, refused(state.pagesSection.renameBookmark(id, "")))
            assertEquals("nothing was written", "Intro", tab.document.model.outline.nodes.single().title)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `deleting a row leaves the page where it was and undoes as one step`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.pagesSection.addBookmark("Intro")
            val id = tab.document.model.outline.nodes.single().id
            val pages = tab.document.pageCount

            assertTrue("the delete succeeded", state.pagesSection.deleteBookmark(id) is OutlineEdits.Result.Ok)

            assertTrue("the row is gone", tab.document.model.outline.isEmpty)
            assertEquals("the page is not", pages, tab.document.pageCount)
            assertTrue(tab.undo())
            assertEquals("undo brings the bookmark back", id, tab.document.model.outline.nodes.single().id)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `an unknown row is refused rather than silently ignored`() {
        val state = shell()
        try {
            assertEquals(OutlineEdits.Refusal.NOT_FOUND, refused(state.pagesSection.renameBookmark("bkm_missing", "X")))
            assertEquals(OutlineEdits.Refusal.NOT_FOUND, refused(state.pagesSection.deleteBookmark("bkm_missing")))
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `the rows survive a save and reopen`() {
        val state = shell()
        try {
            val tab = state.tabs.active!!
            state.pagesSection.addBookmark("Intro")
            val id = tab.document.model.outline.nodes.single().id
            state.pagesSection.renameBookmark(id, "Geometry")
            val target = File(temp.root, "bookmarks.mobix")

            state.saveAs(target, SaveFormat.MOBIX)
            assertNull(state.failure)

            DocumentLoader.load(target).use { reopened ->
                val row = reopened.model.outline.nodes.single()
                assertEquals("the title is the file's", "Geometry", row.title)
                assertEquals("and so is the id", id, row.id)
                assertEquals("and the destination", OutlineTarget.Page(tab.document.model.pages[0].id!!), row.target)
            }
        } finally {
            state.tabs.closeAll()
        }
    }
}
