package com.mobixournal.panes

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One undo history per document, shared by every view of it: the rule that lets a second pane (or a
 * second window) undo an edit made in the first without discarding the edit, which is what a
 * per-canvas history had to do.
 */
class DocumentSessionsTest {

    private fun document(title: String) = Document(
        title = title,
        pages = listOf(
            Page(
                width = 595.0,
                height = 842.0,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(emptyList())),
            ),
        ),
    )

    @Test
    fun twoViewsOfOneDocumentShareTheIdenticalHistory() {
        val sessions = DocumentSessions()
        val left = sessions.history("doc-1")
        val right = sessions.history("doc-1")
        assertSame("the same document has one history", left, right)
    }

    @Test
    fun differentDocumentsGetDifferentHistories() {
        val sessions = DocumentSessions()
        assertNotSame(sessions.history("doc-1"), sessions.history("doc-2"))
        assertEquals(setOf("doc-1", "doc-2"), sessions.keys())
    }

    @Test
    fun anEditRecordedThroughOneViewIsUndoneThroughTheOther() {
        val sessions = DocumentSessions()
        val left = sessions.history("doc-1")
        val right = sessions.history("doc-1")
        val before = document("before")
        val after = document("after")

        right.record(before)
        assertTrue("both views see the undo", left.canUndo)
        assertSame("and both undo to the same snapshot", before, left.undo(after))
        assertFalse(left.canUndo)
        assertTrue("the redo is shared too", right.canRedo)
        assertSame(after, right.redo(before))
    }

    @Test
    fun closingADocumentDropsItsHistoryAndTheNextOpenStartsClean() {
        val sessions = DocumentSessions()
        val history = sessions.history("doc-1")
        history.record(document("before"))
        assertTrue(sessions.has("doc-1"))

        sessions.forget("doc-1")

        assertNull(sessions.existing("doc-1"))
        assertFalse(sessions.has("doc-1"))
        val reopened = sessions.history("doc-1")
        assertNotSame("a reopened document is a new session", history, reopened)
        assertFalse("and it starts with nothing to undo", reopened.canUndo)
    }

    @Test
    fun clearDropsEverySession() {
        val sessions = DocumentSessions()
        sessions.history("doc-1")
        sessions.history("doc-2")

        sessions.clear()

        assertTrue(sessions.keys().isEmpty())
    }
}
