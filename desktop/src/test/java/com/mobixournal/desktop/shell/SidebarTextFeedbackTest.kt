package com.mobixournal.desktop.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.render.OutlineRow
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real pixels must stay unfilled when a text command is hovered, pressed or focused. */
@OptIn(ExperimentalTestApi::class)
class SidebarTextFeedbackTest {
    @get:Rule val compose = createComposeRule()

    private fun assertNoFill(node: SemanticsNodeInteraction) {
        val resting = node.captureToImage().toPixelMap()
        fun check() {
            val actual = node.captureToImage().toPixelMap()
            // The band above the text spans the former pill, without crossing the focus underline.
            for (x in 2 until actual.width - 2) for (y in 2..5) {
                assertEquals("no state background at $x,$y", resting[x, y], actual[x, y])
            }
        }
        node.performMouseInput { enter(); moveTo(center) }
        compose.waitForIdle()
        check()
        node.performMouseInput { press() }
        compose.waitForIdle()
        check()
        node.performMouseInput { release(); exit() }
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        node.assertIsFocused()
        check()
    }

    @Test fun pagesAndAddBookmarkNeverAcquireAPillBackground() {
        compose.setContent {
            DesktopTheme.Apply {
                Column(Modifier.width(240.dp).background(DesktopTheme.desk)) {
                    SidebarViewSwitch(DesktopSidebarView.CONTENTS) {}
                    ContentsHeaderAction("Add bookmark") {}
                }
            }
        }
        assertNoFill(compose.onNodeWithText("Pages"))
        assertNoFill(compose.onNodeWithText("Add bookmark"))
    }
    @Test fun overflowStaysUnfilledAndMenuHighlightsTheWholeRoundedEntry() {
        compose.setContent {
            DesktopTheme.Apply {
                Column(Modifier.width(240.dp).background(DesktopTheme.desk)) {
                    OutlineRowView(OutlineRow(id = "bkm_test", title = "Chapter", depth = 0, page = 1233),
                        240f, 1234, {}, onRename = {}, onDelete = {})
                }
            }
        }
        val overflow = compose.onNodeWithContentDescription("Actions for Chapter", useUnmergedTree = true)
        val resting = overflow.captureToImage().toPixelMap()[3, 3]
        overflow.performMouseInput { enter(); moveTo(center) }
        assertEquals(resting, overflow.captureToImage().toPixelMap()[3, 3])
        overflow.performClick()
        val rename = compose.onNodeWithText("Rename")
        val delete = compose.onNodeWithText("Delete")
        assertEquals(rename.fetchSemanticsNode().boundsInRoot.width,
            delete.fetchSemanticsNode().boundsInRoot.width, 0.5f)
        for (entry in listOf(rename, delete)) {
            val before = entry.captureToImage().toPixelMap()
            entry.performMouseInput { enter(); moveTo(center) }
            val after = entry.captureToImage().toPixelMap()
            assertNotEquals("padding at far edge lights with the whole entry",
                before[before.width - 4, before.height / 2], after[after.width - 4, after.height / 2])
            assertEquals("rounded corner stays outside the state layer", before[0, 0], after[0, 0])
            assertTrue(entry.fetchSemanticsNode().boundsInRoot.height >= 40f)
            entry.performMouseInput { exit() }
        }
    }

}

