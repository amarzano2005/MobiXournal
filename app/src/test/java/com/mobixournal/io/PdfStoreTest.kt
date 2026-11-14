package com.mobixournal.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The store's one promise: a file handed out is never handed out again, so a PDF a canvas is still
 * rasterising can't be overwritten by the next document that opens.
 */
class PdfStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun store() = PdfStore(tmp.newFolder())

    @Test fun allocationsAreUnique() {
        val store = store()
        val files = (1..50).map { store.newFile().apply { writeText("pdf $it") } }
        assertEquals(50, files.map { it.absolutePath }.toSet().size)
        files.forEachIndexed { i, f -> assertEquals("pdf ${i + 1}", f.readText()) }
    }

    @Test fun newFileCreatesTheFolderOnDemand() {
        val dir = java.io.File(tmp.newFolder(), "missing")
        val file = PdfStore(dir).newFile().apply { writeText("%PDF") }
        assertTrue(file.isFile)
    }

    @Test fun pruneKeepsOnlyReferencedFiles() {
        val store = store()
        val kept = store.newFile().apply { writeText("keep") }
        val dropped = store.newFile().apply { writeText("drop") }

        store.prune(listOf(kept.absolutePath, null, "/somewhere/else.pdf"))

        assertTrue(kept.isFile)
        assertFalse(dropped.exists())
    }

    @Test fun pruneWithNothingLiveEmptiesTheStore() {
        val store = store()
        val file = store.newFile().apply { writeText("drop") }
        store.prune(emptyList())
        assertFalse(file.exists())
    }

    /** Three 10-byte cached entries, stamped oldest-first so eviction order is deterministic. */
    private fun cachedTrio(store: PdfStore) = (1..3).map { i ->
        store.cached("key$i") { it.writeText("0123456789") }.apply { setLastModified(1_000L * i) }
    }

    @Test fun pruneKeepsCachedEntriesWhoseTabHasClosed() {
        val store = store()
        val cached = store.cached("notes.txt") { it.writeText("pdf") }
        store.prune(emptyList())
        // Nothing live, but the cache index still refers to it — that is what makes reopening cheap.
        assertTrue(cached.isFile)
    }

    @Test fun theBudgetEvictsCachedEntriesOldestFirst() {
        val store = store()
        val files = cachedTrio(store)

        // 30 bytes cached against a 25-byte budget: the oldest entry goes, the rest stay.
        store.prune(emptyList(), maxBytes = 25)

        assertFalse(files[0].exists())
        assertTrue(files[1].isFile)
        assertTrue(files[2].isFile)
    }

    @Test fun theBudgetNeverEvictsALiveFile() {
        val store = store()
        val files = cachedTrio(store)

        // Every file is open in a tab, so the budget has nothing it may take and the store stays over.
        store.prune(files.map { it.absolutePath }, maxBytes = 5)

        assertTrue(files.all { it.isFile })
    }

    @Test fun anEvictedEntryIsDroppedFromTheIndexSoItRegenerates() {
        val store = store()
        cachedTrio(store)
        store.prune(emptyList(), maxBytes = 25)

        var regenerated = false
        store.cached("key1") { regenerated = true; it.writeText("0123456789") }
        assertTrue(regenerated)
    }

    @Test fun pruneKeepsAPinnedFileAfterItsTabHasClosed() {
        val store = store()
        val joined = store.newFile().apply { writeText("%PDF") }
        assertTrue("the store owns the file, so it can pin it", store.pin(joined))

        store.prune(emptyList())

        // Nothing is open and no cache key regenerates it, but the saved document names this path.
        assertTrue("a saved .xopp still resolves its background", joined.isFile)
    }

    @Test fun theBudgetNeverEvictsAPinnedFile() {
        val store = store()
        val joined = store.newFile().apply { writeText("0123456789") }
        store.pin(joined)

        store.prune(emptyList(), maxBytes = 1)

        assertTrue("a saved document's dependency outranks the cache budget", joined.isFile)
    }

    @Test fun aPinSurvivesUntilItIsLiftedAndDropsOutWhenTheFileGoes() {
        val store = store()
        val joined = store.newFile().apply { writeText("%PDF") }
        store.pin(joined)
        store.unpin(joined)
        store.prune(emptyList())
        assertFalse("an unpinned file nothing refers to is swept", joined.exists())

        val gone = store.newFile().apply { writeText("%PDF") }
        store.pin(gone)
        gone.delete()
        store.prune(emptyList())
        // The pin was dropped with the file, so a later sweep does not keep chasing it.
        val again = store.newFile().apply { writeText("%PDF") }
        store.prune(emptyList())
        assertFalse(again.exists())
    }

    @Test fun aFileOutsideTheStoreIsNotPinned() {
        val store = store()
        val elsewhere = tmp.newFile("reader.pdf").apply { writeText("%PDF") }

        assertFalse("a reader's own PDF is not this store's to keep", store.pin(elsewhere))
        store.prune(emptyList())
        assertTrue(elsewhere.isFile)
    }
}
