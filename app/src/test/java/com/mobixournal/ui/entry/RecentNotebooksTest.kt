package com.mobixournal.ui.entry

import com.mobixournal.render.blankDocument
import com.mobixournal.tabs.OpenTab
import com.mobixournal.tabs.TabSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The entry screen's recents are a short, ordered, de-duplicated list — and its text has to survive a restart. */
class RecentNotebooksTest {

    private fun notebook(id: String) = RecentNotebook("content://docs/$id", id)

    @Test
    fun theNewestOpenIsTheFirstCard() {
        val list = RecentNotebooks.record(RecentNotebooks.record(emptyList(), notebook("a")), notebook("b"))
        assertEquals(listOf("b", "a"), list.map { it.name })
    }

    @Test
    fun openingTheSameNotebookTwiceLeavesOneCard() {
        val twice = RecentNotebooks.record(listOf(notebook("a"), notebook("b")), notebook("b"))
        assertEquals(listOf("b", "a"), twice.map { it.name })
    }

    @Test
    fun theListStopsAtSix() {
        var list = emptyList<RecentNotebook>()
        repeat(9) { i -> list = RecentNotebooks.record(list, notebook("n$i")) }
        assertEquals(RecentNotebooks.LIMIT, list.size)
        assertEquals("n8", list.first().name)
    }

    @Test
    fun aForgottenNotebookLeavesNoCard() {
        val list = RecentNotebooks.forget(listOf(notebook("a"), notebook("b")), "content://docs/a")
        assertEquals(listOf("b"), list.map { it.name })
    }

    @Test
    fun theListSurvivesItsOwnText() {
        val list = listOf(notebook("a"), notebook("b"))
        assertEquals(list, RecentNotebooks.decode(RecentNotebooks.encode(list)))
    }

    @Test
    fun aNameCannotSplitACardInTwo() {
        val odd = RecentNotebook("content://docs/a", "a\u001Eb\u001Fc")
        val back = RecentNotebooks.decode(RecentNotebooks.encode(listOf(odd)))
        assertEquals(1, back.size)
        assertEquals("content://docs/a", back.single().uri)
        assertTrue("the separators are not carried into the name", '\u001E' !in back.single().name)
    }

    @Test
    fun aSessionCardKeepsTheTabItStandsFor() {
        val card = RecentNotebook("content://docs/a", "a", sessionTab = "tab-1")
        assertEquals(listOf(card), RecentNotebooks.decode(RecentNotebooks.encode(listOf(card))))
        assertTrue(card.fromPreviousSession)
    }

    @Test
    fun theLastRunsTabsComeFirstAndReplaceTheirOwnRecentCard() {
        val session = TabSession(
            tabs = listOf(
                OpenTab("t1", "Untitled", blankDocument()),
                OpenTab("t2", "notes.xopp", blankDocument(), uri = "content://docs/a"),
            ),
            activeIndex = 0,
        )
        val cards = EntryCards.of(session, listOf(notebook("a"), notebook("b")))
        assertEquals(listOf("Untitled", "notes.xopp", "b"), cards.map { it.name })
        assertEquals(listOf(true, true, false), cards.map { it.fromPreviousSession })
        assertEquals("t2", cards[1].sessionTab)
    }

    @Test
    fun noSessionLeavesTheRecentsAsTheyAre() {
        val list = listOf(notebook("a"), notebook("b"))
        assertEquals(list, EntryCards.of(null, list))
    }

    @Test
    fun nothingReadableIsAnEmptyList() {
        assertEquals(emptyList<RecentNotebook>(), RecentNotebooks.decode(null))
        assertEquals(emptyList<RecentNotebook>(), RecentNotebooks.decode(""))
        assertEquals(emptyList<RecentNotebook>(), RecentNotebooks.decode("a row without a separator"))
    }

    @Test
    fun aRowWithoutANameFallsBackToItsUri() {
        val back = RecentNotebooks.decode("content://docs/a\u001F")
        assertEquals("content://docs/a", back.single().name)
    }
}
