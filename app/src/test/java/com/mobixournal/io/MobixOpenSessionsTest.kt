package com.mobixournal.io

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The staged archive of an open `.mobix` outlives the open call.
 *
 * A deferred recording payload, the audio index's own reader and a repack save all read those bytes
 * after the open has returned, so a copy [DocumentIo] staged is the session's to keep — and to delete
 * with the document, never with the open. A tab's own snapshot is read through the same path and is
 * **not** the session's to delete.
 */
class MobixOpenSessionsTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun aStagedOpenArchiveIsRetainedUntilTheSessionLetsTheDocumentGo() {
        val staged = temp.newFile("open-staged.mobix").apply { writeText("archive") }
        val sessions = MobixOpenSessions()
        sessions.remember("doc_a", staged, "content://doc/a", owned = true)

        assertTrue("an open document keeps the bytes its records read", sessions.retains(staged))
        sessions.prune(listOf("doc_a"))
        assertTrue("a document still open keeps its archive", staged.isFile)

        sessions.prune(emptyList())
        assertFalse("the copy is the session's, so it goes with the document", staged.isFile)
    }

    @Test
    fun aSnapshotAnOpenWasReadFromIsNeverDeletedByTheSweep() {
        val snapshot = temp.newFile("tab-1.mobix").apply { writeText("snapshot") }
        val sessions = MobixOpenSessions()
        sessions.remember("doc_b", snapshot, null, owned = false)

        sessions.prune(emptyList())

        assertTrue("a tab's own snapshot is not ours to delete", snapshot.isFile)
    }
}
