package com.mobixournal.ui

import com.mobixournal.render.OutlineRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Back on the tablet's Contents card**: the transient thing goes first.
 *
 * A name being typed confuses two doors — the platform's Back and the card's own close — and a reader who
 * pressed one of them mid-word lost the card, the filter and their place in a long book with it. So the
 * dismiss a card receives is read as the edit's door first: cancel, and leave everything else standing.
 */
class PdfContentsBackTest {

    private val row = OutlineRow(id = "bkm_0", title = "Chapter 1", depth = 0, page = 0)

    @Test
    fun `a card with nothing in progress closes`() {
        val editing = ContentsEditHost()
        var closed = 0
        editing.dismissOrCancel { closed++ }()
        assertEquals("the card's own door", 1, closed)
        assertNull("and nothing was being edited", editing.draft)
    }

    @Test
    fun `a name being typed is cancelled, and the card stays`() {
        val editing = ContentsEditHost()
        editing.renaming(row)
        editing.typed("Chapter one")
        var closed = 0
        editing.dismissOrCancel { closed++ }()
        assertEquals("the card is still open", 0, closed)
        assertNull("the retitling is gone", editing.draft)
    }

    @Test
    fun `a delete question is answered by closing it, not by closing the card`() {
        val editing = ContentsEditHost()
        editing.deleting(row)
        var closed = 0
        editing.dismissOrCancel { closed++ }()
        assertEquals("the card is still open", 0, closed)
        assertNull("the question is gone", editing.deleting)
        assertTrue("so a second Back can close the card", editing.draft == null)
    }
}
