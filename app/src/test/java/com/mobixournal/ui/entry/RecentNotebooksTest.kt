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
        val cards = EntryCards.of(session, null, listOf(notebook("a"), notebook("b")))
        assertEquals(listOf("Untitled", "notes.xopp", "b"), cards.map { it.name })
        assertEquals(listOf(true, true, false), cards.map { it.fromPreviousSession })
        assertEquals("t2", cards[1].sessionTab)
    }

    @Test
    fun noSessionLeavesTheRecentsAsTheyAre() {
        val list = listOf(notebook("a"), notebook("b"))
        assertEquals(list, EntryCards.of(null, null, list))
    }

    @Test
    fun aRunClosedInSplitViewIsOneCardForBothHalves() {
        val left = TabSession(
            listOf(OpenTab("l1", "sql.mobix", blankDocument(), uri = "content://docs/a")),
            activeIndex = 0,
        )
        val right = TabSession(
            listOf(OpenTab("r1", "deep-outline.mobix", blankDocument(), uri = "content://docs/b")),
            activeIndex = 0,
        )
        val cards = EntryCards.of(left, right, listOf(notebook("a"), notebook("b"), notebook("c")))
        // The workspace is one card, its two documents are not also listed on their own.
        assertEquals(listOf("sql.mobix · deep-outline.mobix", "c"), cards.map { it.name })
        assertTrue(cards[0].fromSplitSession)
        assertEquals("l1", cards[0].sessionTab)
        assertEquals("r1", cards[0].splitTab)
    }

    @Test
    fun aRightPaneWithNothingOpenIsNoSplitCard() {
        val right = TabSession(listOf(OpenTab("r1", "notes.xopp", blankDocument())), activeIndex = 0)
        val cards = EntryCards.of(null, right, listOf(notebook("a")))
        assertEquals(listOf("a"), cards.map { it.name })
        assertTrue(cards.none { it.fromSplitSession })
    }

    @Test
    fun aSplitCardKeepsTheRightHalfsTabToo() {
        val card = RecentNotebook("content://docs/a", "a · b", sessionTab = "l1", splitTab = "r1")
        assertEquals(listOf(card), RecentNotebooks.decode(RecentNotebooks.encode(listOf(card))))
        assertTrue(card.fromSplitSession)
    }

    @Test
    fun aListWrittenBeforeSplitCardsStillReads() {
        // Three fields: a build that knew no split (see `RecentNotebooks.encode`).
        val row = "content://docs/a\u001Fa\u001Ftab-1"
        val back = RecentNotebooks.decode(row)
        assertEquals("tab-1", back.single().sessionTab)
        assertEquals(null, back.single().splitTab)
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
