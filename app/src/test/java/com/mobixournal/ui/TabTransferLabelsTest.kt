package com.mobixournal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabTransferLabelsTest {

    @Test
    fun `split view names the other pane`() {
        assertEquals(
            TabTransferLabels("Move to pane 2", "Mirror in pane 2"),
            tabTransferLabels(paneIndex = 0, paneCount = 2),
        )
        assertEquals(
            TabTransferLabels("Move to pane 1", "Mirror in pane 1"),
            tabTransferLabels(paneIndex = 1, paneCount = 2),
        )
    }

    @Test
    fun `closed split says the action opens one`() {
        assertEquals(
            TabTransferLabels("Move to split view", "Mirror in split view"),
            tabTransferLabels(paneIndex = 0, paneCount = 1),
        )
    }

    @Test
    fun `a strip in a single-pane layout has no pane to name`() {
        assertEquals(1, TabsUiState().paneCount)
        assertEquals(0, TabsUiState().paneIndex)
        assertEquals(true, TabsUiState().focused)
    }
}
