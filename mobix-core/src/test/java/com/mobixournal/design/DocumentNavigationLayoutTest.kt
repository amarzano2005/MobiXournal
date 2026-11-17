package com.mobixournal.design

import org.junit.Assert.*
import org.junit.Test

class DocumentNavigationLayoutTest {
    @Test fun previewsKeepPortraitLandscapeAndPanoramaProportions() {
        for ((width, height) in listOf(595.0 to 842.0, 842.0 to 595.0, 4000.0 to 500.0)) {
            val size = DocumentNavigationLayout.previewSize(width, height)
            assertEquals((width / height).toFloat(), size.width / size.height, 0.001f)
            assertTrue(size.width <= DocumentNavigationLayout.PREVIEW_WIDTH_DP)
            assertTrue(size.height <= DocumentNavigationLayout.PREVIEW_HEIGHT_DP)
        }
    }
    @Test fun compactNavigatorUsesTheAvailableSpaceWithoutRemovingTheCanvas() {
        for (workspace in listOf(320f, 360f, 480f, 599f)) {
            for (bookmarks in listOf(true, false)) {
                val width = DocumentNavigationLayout.width(workspace, 64f, bookmarks)
                assertTrue(DocumentNavigationLayout.compact(workspace))
                assertEquals(0f, workspace - 64f - width, 0.001f)
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
