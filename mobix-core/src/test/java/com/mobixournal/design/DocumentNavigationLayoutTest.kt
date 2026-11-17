package com.mobixournal.design

import org.junit.Assert.*
import org.junit.Test

class DocumentNavigationLayoutTest {
    @Test fun compactNavigatorUsesTheAvailableSpaceWithoutRemovingTheCanvas() {
        for (workspace in listOf(320f, 360f, 480f, 599f)) {
            for (bookmarks in listOf(true, false)) {
                val width = DocumentNavigationLayout.width(workspace, 64f, bookmarks)
                assertTrue(DocumentNavigationLayout.compact(workspace))
                assertEquals(1f, workspace - 64f - width, 0.001f)
            }
        }
    }

    @Test fun tabletKeepsRoomForBothTheDocumentAndNavigation() {
        for (workspace in listOf(600f, 840f, 1280f)) {
            assertFalse(DocumentNavigationLayout.compact(workspace))
            val width = DocumentNavigationLayout.width(workspace, 64f, true)
            assertTrue(width >= 280f)
            assertTrue(workspace - 64f - width >= 216f)
            assertEquals(width, DocumentNavigationLayout.width(workspace, 64f, false), 0f)
        }
    }

    @Test fun verySmallConstraintsNeverProduceNegativePanelWidths() {
        assertEquals(0f, DocumentNavigationLayout.width(48f, 64f, true), 0f)
    }
}
