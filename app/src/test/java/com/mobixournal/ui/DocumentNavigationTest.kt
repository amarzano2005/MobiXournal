package com.mobixournal.ui

import org.junit.Assert.*
import org.junit.Test

/** Direct access, tab switching and Back must never turn a navigation choice into a close or an app exit. */
class DocumentNavigationTest {
    private fun ui() = EditorUiState(EditorTool.PEN, 0, 1f, 0, 1f)

    @Test fun bookmarksOpenDirectlyFromAClosedNavigator() {
        val ui = ui()
        ui.toggleNavigation(NavigationTab.BOOKMARKS)
        assertTrue(ui.pageListOpen)
        assertEquals(NavigationTab.BOOKMARKS, ui.navigationTab)
    }

    @Test fun switchingFromPagesToBookmarksKeepsTheNavigatorOpenAndClearsPagePicks() {
        val ui = ui()
        ui.toggleNavigation(NavigationTab.PAGES)
        ui.pageSelection = setOf(1, 3)
        ui.toggleNavigation(NavigationTab.BOOKMARKS)
        assertTrue(ui.pageListOpen)
        assertEquals(NavigationTab.BOOKMARKS, ui.navigationTab)
        assertTrue(ui.pageSelection.isEmpty())
        ui.toggleNavigation(NavigationTab.PAGES)
        assertTrue(ui.pageListOpen)
        assertEquals(NavigationTab.PAGES, ui.navigationTab)
    }

    @Test fun selectingTheActiveEntryClosesItAndRetainsTheSelectedView() {
        val ui = ui()
        ui.toggleNavigation(NavigationTab.BOOKMARKS)
        ui.toggleNavigation(NavigationTab.BOOKMARKS)
        assertFalse(ui.pageListOpen)
        assertEquals(NavigationTab.BOOKMARKS, ui.navigationTab)
    }

    @Test fun backCancelsBookmarkDraftBeforeClosingNavigation() {
        val ui = ui()
        val session = ContentsSession()
        ui.toggleNavigation(NavigationTab.BOOKMARKS)
        session.editing.adding(3)
        session.editing.typed("Chapter")
        session.editing.dismissOrCancel { ui.closeNavigation() }()
        assertTrue(ui.pageListOpen)
        assertNull(session.editing.draft)
        session.editing.dismissOrCancel { ui.closeNavigation() }()
        assertFalse(ui.pageListOpen)
    }
}
