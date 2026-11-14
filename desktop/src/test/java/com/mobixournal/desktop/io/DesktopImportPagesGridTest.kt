package com.mobixournal.desktop.io

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.Density
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShell
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.WindowKeys
import com.mobixournal.render.ImportPdfMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **The page previews, from a keyboard.** The grid is a row of checkboxes under the answers, so `Tab` walks
 * into it and `Space` picks the page it is on — and, because the cell takes the key while it is focused
 * ([WindowKeys.focusControl]), picking a page never pans the page behind the card.
 *
 * The card here names a PDF that is not there: the previews have nothing to draw, and the cells are exactly
 * what this test is about — their number, their selection and their keys.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DesktopImportPagesGridTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun state(pages: String, count: Int = 8): DesktopShellState {
        val state = DesktopShellState(DesktopSettings(File(temp.root, "keys.properties")))
        state.importRequest = ImportPdfRequest(
            file = File(temp.root, "missing.pdf"),
            pageCount = count,
            mode = ImportPdfMode.APPEND,
            pages = pages,
            chooseMode = true,
        )
        return state
    }

    private fun scene(state: DesktopShellState) = ImageComposeScene(900, 640, Density(1f)) {
        WindowKeys.onKey = { event -> state.onKey(event, onOpenPicker = {}) }
        DesktopShell(
            state = state,
            onOpenPicker = {},
            modifier = Modifier.onPreviewKeyEvent { WindowKeys.handle(it) },
        )
    }

    private fun ImageComposeScene.press(key: Key) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        Snapshot.sendApplyNotifications()
        render()
    }

    @Test
    fun `Tab reaches a page preview and Space picks it`() {
        val state = state("2,4,7")
        val scene = scene(state)
        try {
            scene.render()
            var toggled: String? = null
            for (attempt in 1..16) {
                scene.press(Key.Tab)
                scene.press(Key.Spacebar)
                val pages = state.importRequest!!.pages
                if (pages != "2,4,7") {
                    toggled = pages
                    break
                }
            }
            assertNotNull("the grid must be reachable with Tab", toggled)
            assertNotEquals("and Space must change the selection", "", toggled)
            // Whatever the cell was, the answer is still a selection the field accepts.
            assertNotNull("the card is still usable", state.importRequest!!.chosen)
        } finally {
            scene.close()
            WindowKeys.onKey = null
        }
    }

    @Test
    fun `the card still takes its two answers while the grid is up`() {
        val state = state("1-8")
        val scene = scene(state)
        try {
            scene.render()
            var changed = false
            for (attempt in 1..6) {
                scene.press(Key.Tab)
                scene.press(Key.Spacebar)
                if (state.importRequest?.mode == ImportPdfMode.REPLACE) {
                    changed = true
                    break
                }
            }
            assertEquals("the answers stay ahead of the grid in the tab order", true, changed)
        } finally {
            scene.close()
            WindowKeys.onKey = null
        }
    }
}
