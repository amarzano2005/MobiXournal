package com.mobixournal.desktop.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** The narrow sidebar's editing state must remain legible, stable and operable. */
class DesktopBookmarkEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun navigationUsesBookmarksAndAnnouncesTheSelectedTab() {
        compose.setContent {
            var view by remember { mutableStateOf(DesktopSidebarView.CONTENTS) }
            MaterialTheme { Column(Modifier.width(240.dp)) { SidebarViewSwitch(view) { view = it } } }
        }
        compose.onNodeWithText("Bookmarks").assertIsSelected()
        compose.onNodeWithText("Contents").assertDoesNotExist()
        compose.onNodeWithText("Pages").performClick().assertIsSelected()
        compose.onNodeWithText("Bookmarks").assertIsNotSelected()
    }

    @Test fun draftKeepsActionsInPlaceAndFitsAFourDigitPage() {
        var saves = 0
        var cancels = 0
        compose.setContent {
            var title by remember { mutableStateOf("") }
            MaterialTheme {
                Column(Modifier.width(240.dp).testTag("sidebar")) {
                    ContentsAddDraft(1233, title, null, { title = it }, { saves++ }, { cancels++ })
                }
            }
        }
        val add = compose.onNodeWithText("Add").assertIsNotEnabled()
        val cancel = compose.onNodeWithText("Cancel").assertIsEnabled()
        val before = add.fetchSemanticsNode().boundsInRoot
        val page = compose.onNodeWithText("Page 1234").fetchSemanticsNode().boundsInRoot
        val cancelBounds = cancel.fetchSemanticsNode().boundsInRoot
        assertTrue(page.right <= cancelBounds.left)
        assertEquals(cancelBounds.top, before.top, 0.5f)
        assertTrue(before.right <= 240f && before.height >= 40f)
        compose.onNodeWithContentDescription("Bookmark title").performTextInput("Optical measurements")
        add.assertIsEnabled()
        assertEquals(before, add.fetchSemanticsNode().boundsInRoot)
        add.performClick()
        compose.runOnIdle { assertEquals(1, saves) }
        cancel.performClick()
        compose.runOnIdle { assertEquals(1, cancels) }
    }
}
