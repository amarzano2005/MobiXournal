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
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.render.OutlineEdits
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

    @Test fun aMovedChapterCarriesItsChildren() {
        val state = show()
        try {
            val tab = state.tabs.active!!
            compose.runOnIdle {
                // `OutlineEdits.add` is the same rule the menu's own Add uses: the child is appended under
                // the chapter, so moving that chapter is the case where a move could lose a subtree.
                val parent = tab.document.model.outline.nodes[1]
                val added = OutlineEdits.add(tab.document.model.outline, "Child", parent.target!!, parent.id)
                tab.document.replaceOutline((added as OutlineEdits.Result.Ok).tree)
            }
            compose.onNodeWithContentDescription("Actions for Row 1").performClick()
            compose.onNodeWithText("Move down").performClick()
            compose.runOnIdle {
                val nodes = tab.document.model.outline.nodes
                assertEquals(listOf("Row 0", "Row 2", "Row 1"), nodes.take(3).map { it.title })
                assertEquals(listOf("Child"), nodes[2].children.map { it.title })
            }
        } finally { state.tabs.closeAll() }
    }

    @Test fun aFilteredListStillMovesAmongTheRealSiblings() {
        val state = show()
        try {
            val tab = state.tabs.active!!
            compose.runOnIdle {
                // Three roots, filtered down to one: availability is read from the whole tree, so the move
                // is still Alpha, Gamma, Beta rather than a reorder of whatever the filter happens to show.
                val target = tab.document.model.outline.nodes.first().target!!
                tab.document.replaceOutline(tab.document.model.outline.copy(nodes = listOf(
                    OutlineNode("bkm_alpha", "Alpha", target),
                    OutlineNode("bkm_beta", "Beta", target),
                    OutlineNode("bkm_gamma", "Gamma", target),
                )))
            }
            compose.onNodeWithText("Filter bookmarks").performTextInput("Beta")
            compose.onNodeWithContentDescription("Actions for Beta").performClick()
            compose.onNodeWithText("Move down").performClick()
            compose.runOnIdle {
                val titles = tab.document.model.outline.nodes.map { it.title }
                assertEquals(listOf("Alpha", "Gamma", "Beta"), titles)
            }
            // The row it moved is still the one the filter shows.
            compose.onNodeWithContentDescription("Beta, page 1").assertExists()
        } finally { state.tabs.closeAll() }
    }

    @Test fun theRowMenuMovesAmongSiblingsAndStopsAtTheEdges() {
        val state = show()
        try {
            // Row 0 opens the run: up has nowhere to go, down has.
            compose.onNodeWithContentDescription("Actions for Row 0").performClick()
            compose.onNodeWithText("Move up").assertIsNotEnabled()
            compose.onNodeWithText("Move down").performClick()
            compose.runOnIdle {
                val tab = state.tabs.active!!
                assertEquals(listOf("Row 1", "Row 0"),
                    tab.document.model.outline.nodes.take(2).map { it.title })
                // One command, one step: a single undo puts both rows back.
                tab.undo()
                assertEquals(listOf("Row 0", "Row 1"),
                    tab.document.model.outline.nodes.take(2).map { it.title })
            }
        } finally { state.tabs.closeAll() }
    }
}
