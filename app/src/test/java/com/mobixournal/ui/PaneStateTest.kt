package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PaneStateTest {

    @Test
    fun `pane state initializes with documentVersion 0 and inactive search`() {
        val pane = PaneState()
        assertEquals(0, pane.documentVersion)
        assertFalse(pane.searchOpen)
        assertEquals("", pane.searchQuery)
        assertEquals(0, pane.searchCurrent)
        assertEquals(0, pane.searchTotal)
        assertFalse(pane.searchIndexing)
    }

    @Test
    fun `documentVersion increments sequentially on document reloads`() {
        val pane = PaneState()
        assertEquals(0, pane.documentVersion)
        pane.documentVersion++
        assertEquals(1, pane.documentVersion)
        pane.documentVersion++
        assertEquals(2, pane.documentVersion)
    }
}
