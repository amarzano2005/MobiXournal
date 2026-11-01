package com.mobixournal.render

import com.mobixournal.format.model.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gesture as a transaction: **one gesture is one undo step**, and an aborted one records nothing.
 *
 * The rules are the object's, not the canvas's, so they are checked without a `View`, a `MotionEvent` or
 * an emulator. Where a step has to be counted, the real [EditHistory] is used: "one step" is then a
 * question about the undo stack rather than a flag somebody remembered to set.
 */
class GestureSessionTest {

    private val session = GestureSession()

    /** Distinct snapshots of a document — identity is what the history compares, not equality. */
    private fun doc(name: String) = Document(title = name)

    @Test
    fun `a gesture that changed nothing is no undo step`() {
        val start = doc("start")
        session.open(start)

        assertNull(session.commit(start))
        assertFalse(session.open)
    }

    @Test
    fun `one gesture is one undo step however many edits it made`() {
        val before = doc("before")
        val history = EditHistory<Document>()
        session.open(before)
        // Two commits inside one gesture — the ink, then the erase the barrel turned it into.
        session.commit(doc("first edit"))?.let(history::record)
        session.commit(doc("second edit"))?.let(history::record)

        assertTrue(history.canUndo)
        assertSame(before, history.undo(doc("second edit")))
        assertFalse("two commits must not leave two steps behind", history.canUndo)
    }

    @Test
    fun `commit hands back the document the gesture opened on`() {
        val before = doc("before")
        session.open(before)

        assertSame(before, session.commit(doc("after")))
    }

    @Test
    fun `abort records nothing`() {
        val history = EditHistory<Document>()
        session.open(doc("before"))
        session.abort()

        // A document that changed after the abort cannot be charged to the gesture that was dropped.
        assertNull(session.commit(doc("after")))
        assertFalse(history.canUndo)
    }

    @Test
    fun `a gesture keeps the baseline it opened with`() {
        // The barrel turning a stroke into an erase: the erase opens a gesture too, and must not move
        // the baseline onto the half-edited document.
        val penDown = doc("pen down")
        session.open(penDown)

        assertFalse(session.open(doc("mid-gesture")))

        assertSame(penDown, session.commit(doc("released")))
    }

    @Test
    fun `commit closes the gesture, owner and router flag alike`() {
        session.open(doc("before"))
        session.pointerId = 7
        session.penOwned = true
        session.routed = true

        session.commit(doc("after"))

        assertFalse(session.open)
        assertEquals(-1, session.pointerId)
        assertFalse(session.penOwned)
        assertFalse(session.routed)
    }

    @Test
    fun `abort closes the gesture the same way`() {
        session.open(doc("before"))
        session.pointerId = 7
        session.penOwned = true
        session.routed = true

        session.abort()

        assertFalse(session.open)
        assertEquals(-1, session.pointerId)
        assertFalse(session.penOwned)
        assertFalse(session.routed)
    }

    @Test
    fun `the next gesture opens on the document it starts from`() {
        session.open(doc("first"))
        session.commit(doc("first edited"))

        val second = doc("second")
        assertTrue(session.open(second))
        assertSame(second, session.commit(doc("second edited")))
    }
}
