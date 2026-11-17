package com.mobixournal.desktop.shell

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopNewDocumentFormat
import com.mobixournal.desktop.settings.DesktopSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class DesktopBookmarkNavigationTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val temp = TemporaryFolder()

    private fun show(): DesktopShellState {
        val settings = DesktopSettings(temp.newFile()).apply {
            newDocumentFormat = DesktopNewDocumentFormat.MOBIX
            documentFormatChosen = true
        }
        val state = DesktopShellState(settings).also { it.newDocument() }
        repeat(40) { state.pagesSection.addBookmark("Row $it") }
        val tab = state.tabs.active!!
        compose.setContent {
            MaterialTheme {
                ContentsPanel(tab, {}, ContentsEdits(
                    add = { title, page -> state.pagesSection.addBookmark(title, page = page) },
                    rename = state.pagesSection::renameBookmark,
                    remove = state.pagesSection::deleteBookmark,
                    move = state.pagesSection::moveBookmark,
                ), Modifier.width(320.dp).height(400.dp))
            }
        }
        return state
    }

    @Test fun addRenameUndoAndRedoRefreshTheOpenFilteredPanel() {
        val state = show()
        try {
            compose.onNodeWithText("Filter bookmarks").performTextInput("Row 39")
            compose.onNodeWithContentDescription("Row 39, page 1").assertExists()
            compose.onNodeWithText("Add bookmark").performClick()
            compose.onNodeWithContentDescription("Bookmark title").performTextInput("New item")
            compose.onNodeWithText("Add").performClick()
            compose.onNodeWithText("New item").assertIsDisplayed()
            compose.onNodeWithContentDescription("Actions for New item").performClick()
            compose.onNodeWithText("Rename").performClick()
            compose.onNodeWithContentDescription("Bookmark title").performTextReplacement("Renamed item")
            compose.onNodeWithText("Save").performClick()
            compose.onNodeWithContentDescription("Actions for Renamed item").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals("Renamed item", state.tabs.active!!.document.model.outline.nodes.last().title)
                state.tabs.active!!.undo()
            }
            compose.onNodeWithText("New item").assertIsDisplayed()
            compose.runOnIdle { state.tabs.active!!.redo() }
            compose.onNodeWithText("Renamed item").assertIsDisplayed()
        } finally { state.tabs.closeAll() }
    }

    @Test fun pointerDragReordersAndUndoRefreshesWithoutReopening() {
        val state = show()
        try {
            compose.onNodeWithText("Row 0").performMouseInput {
                moveTo(center); press()
                moveBy(Offset(0f, 80f), delayMillis = 300)
                release()
            }
            compose.runOnIdle {
                val tab = state.tabs.active!!
                assertNotEquals("Row 0", tab.document.model.outline.nodes.first().title)
                tab.undo()
            }
            compose.onNodeWithText("Row 0").assertIsDisplayed()
        } finally { state.tabs.closeAll() }
    }
}
