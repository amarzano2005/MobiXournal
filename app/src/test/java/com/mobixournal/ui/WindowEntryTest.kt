package com.mobixournal.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the window is the entry screen, and when it is a workspace with bars around it. */
class WindowEntryTest {

    private fun pane(vararg titles: String) = TabsUiState(titles = titles.toList())

    @Test
    fun aTabInTheVisiblePaneIsAWorkspace() {
        assertFalse(windowIsEmpty(listOf(pane("notes.xopp")), splitView = false))
    }

    @Test
    fun aHiddenPaneCannotKeepTheBarsOnScreen() {
        // The reported bug: split closed leaves the right pane's tab in memory, and the left one empty.
        assertTrue(
            "the pane that is not drawn must not count",
            windowIsEmpty(listOf(pane(), pane("deep-outline.mobix")), splitView = false),
        )
    }

    @Test
    fun oneHalfOfASplitIsStillAWorkspace() {
        assertFalse(windowIsEmpty(listOf(pane(), pane("Untitled")), splitView = true))
        assertFalse(windowIsEmpty(listOf(pane("Untitled"), pane()), splitView = true))
    }

    @Test
    fun bothHalvesEmptyIsTheEntryScreen() {
        assertTrue(windowIsEmpty(listOf(pane(), pane()), splitView = true))
    }

    @Test
    fun aWindowWithNoPanesIsEmpty() {
        assertTrue(windowIsEmpty(emptyList(), splitView = false))
        assertTrue(windowIsEmpty(emptyList(), splitView = true))
    }
}
