package com.mobixournal.format.model

import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.mobix.TrackedEntrySource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A one-page edit is one page: [Document.withPage] overlays the page that moved instead of rebuilding
 * the list, so a document opened page by page (a `.mobix`) is not read in full the moment a stroke
 * lands — which is what a `toMutableList()` would do, and what the canvas used to do on every edit.
 *
 * The counter is a `TrackedEntrySource` again: "the edit read no other page" is a count of real
 * inflations.
 */
class DocumentPagesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class Counted(val document: Document, val reader: MobixPagedReader, val entries: TrackedEntrySource)

    private fun notebook(file: File, pageCount: Int): Counted {
        SyntheticNotebook.write(file, SyntheticNotebook.pages(pageCount))
        val entries = TrackedEntrySource(MobixEntrySource.fromFile(file))
        val reader = MobixPagedReader(entries, cacheBudget = 3)
        return Counted(MobixDocument.openPaged(reader).toDocument(), reader, entries)
    }

    @Test
    fun anEditTouchesOnePageAndLeavesTheRestUnread() {
        val counted = notebook(temp.newFile("notebook.mobix"), pageCount = 200)
        counted.reader.use {
            val document = counted.document
            val page = document.pages[7].let { it.copy(layers = emptyList()) }

            val edited = document.withPage(7, page)

            assertEquals("the document still declares every page", 200, edited.pages.size)
            assertEquals("and not one page was inflated to change it", 0, counted.entries.openCount(SyntheticNotebook.entryOf(0)))
            assertEquals(0, counted.entries.openCount(SyntheticNotebook.entryOf(199)))

            // The edited page is the one that comes back …
            assertSame(page, edited.pages[7])
            // … and the edit itself read nothing: the one read of page 7 was the caller's own, made
            // to have a page to edit.
            assertEquals(1, counted.entries.openCount(SyntheticNotebook.entryOf(7)))
            // The original document is untouched: the edit is a new document.
            assertEquals(1, document.pages[7].layers.size)

            // A page nobody edited is read when, and only when, someone asks for it.
            assertEquals("pg_001", edited.pages[0].id)
            assertEquals(1, counted.entries.openCount(SyntheticNotebook.entryOf(0)))
        }
    }

    @Test
    fun repeatedEditsOfOneDocumentStayOneViewDeep() {
        val counted = notebook(temp.newFile("notebook.mobix"), pageCount = 20)
        counted.reader.use {
            var document = counted.document
            repeat(5) { index ->
                document = document.withPage(index, document.pages[index].copy(layers = emptyList()))
            }
            // Five edits, one wrapper over the same lazy base — not a chain of five.
            assertEquals(5, (document.pages as EditedPages).editedIndices.size)
            assertEquals(20, document.pages.size)
            assertEquals(0, counted.entries.openCount(SyntheticNotebook.entryOf(19)))
            // An unedited page is still read on demand, through the view.
            assertEquals("pg_020", document.pages[19].id)
            assertEquals(1, counted.entries.openCount(SyntheticNotebook.entryOf(19)))
        }
    }

    @Test
    fun anIndexOutsideTheDocumentIsRefused() {
        val counted = notebook(temp.newFile("notebook.mobix"), pageCount = 3)
        counted.reader.use {
            val document = counted.document
            assertThrows(IndexOutOfBoundsException::class.java) {
                document.withPage(3, document.pages[0])
            }
        }
    }
}
