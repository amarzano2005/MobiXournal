package com.mobixournal.desktop.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mobixournal.render.OutlineRow
import org.jetbrains.skia.Image
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Measure actual descendants, including the tooltip's text and a four-digit destination. */
class DesktopContentsMeasurementTest {
    @get:Rule val compose = createComposeRule()
    private val title = "A very long chapter title about optical experiments and measurements"

    private fun show(expanded: Boolean) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(240.dp).testTag("sidebar")) {
                    SidebarViewSwitch(DesktopSidebarView.CONTENTS) {}
                    OutlineRowView(
                        OutlineRow(id = "bkm_parent", title = title, depth = 0, page = 1233,
                            branch = true, expanded = expanded),
                        240f, 1234, {}, onToggle = {}, onRename = {}, onDelete = {},
                    )
                    if (expanded) OutlineRowView(
                        OutlineRow(id = "bkm_child", title = "Child section with a long title", depth = 8, page = 12),
                        240f, 1234, {}, onRename = {}, onDelete = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun verifyRow(name: String, number: String) {
        val titleNode = compose.onNodeWithText(name, useUnmergedTree = true)
        val pageNode = compose.onNodeWithText(number, useUnmergedTree = true)
        val menuNode = compose.onNodeWithContentDescription("Actions for $name", useUnmergedTree = true)
        val bounds = compose.onNodeWithTag("sidebar").fetchSemanticsNode().boundsInRoot
        val parts = listOf(titleNode, pageNode, menuNode).map { it.fetchSemanticsNode().boundsInRoot }
        parts.forEach {
            assertTrue("$it inside $bounds", it.left >= bounds.left && it.right <= bounds.right)
            assertTrue(it.top >= bounds.top && it.bottom <= bounds.bottom)
            assertTrue(it.width > 0 && it.height > 0)
        }
        assertTrue(parts[0].right <= parts[1].left)
        assertTrue(parts[1].right <= parts[2].left)
        assertTrue("menu retains a 40 dp target", parts[2].width >= 40f)
        val layouts = mutableListOf<TextLayoutResult>()
        pageNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertFalse("all digits fit", layouts.single().hasVisualOverflow)
        assertTrue(layouts.single().getLineRight(0) <= parts[1].width)
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onNodeWithTag("sidebar").captureToImage().asSkiaBitmap()
        val file = File("build/contents-$name.png")
        file.parentFile.mkdirs()
        file.writeBytes(Image.makeFromBitmap(bitmap).encodeToData()!!.bytes)
    }

    @Test fun expandedRowsKeepEveryColumnInside240dp() {
        show(true)
        verifyRow(title, "1234")
        verifyRow("Child section with a long title", "13")
        val parent = compose.onNodeWithText("1234", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val child = compose.onNodeWithText("13", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(parent.right, child.right, 0.5f)
        val parentMenu = compose.onNodeWithContentDescription("Actions for $title", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val childMenu = compose.onNodeWithContentDescription("Actions for Child section with a long title", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(parentMenu.right, childMenu.right, 0.5f)
        val arrow = compose.onNodeWithContentDescription("Collapse $title", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(arrow.left >= 0 && arrow.right <= titleBounds.left)
        screenshot("expanded")
    }

    @Test fun collapsedChapterKeepsItsActionsAndNumberInside240dp() {
        show(false)
        verifyRow(title, "1234")
        compose.onNodeWithText("Child section with a long title").assertDoesNotExist()
        screenshot("collapsed")
    }
}

