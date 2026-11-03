package com.mobixournal.ui.entry

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
