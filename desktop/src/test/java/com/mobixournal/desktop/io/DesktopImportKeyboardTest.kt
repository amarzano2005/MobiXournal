package com.mobixournal.desktop.io

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.Density
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShell
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.WindowKeys
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.render.ImportPdfMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **The import card from the keyboard** — the promise the answers make to a reader who never touches the
 * mouse: Tab walks into the card, Space takes the answer that has focus, and Esc puts the question away
 * without importing anything.
 *
 * These are real key events through a real `DesktopShell` (`ImageComposeScene`), so what is checked is the
 * window's own key handling and the rows' own semantics — not a helper called directly. The answer is read
 * from the request the card edits, which is the same value both shells confirm with.
 *
 * Tab's order is the composition's, not this test's, so the walk presses Tab until the answer changes rather
 * than asserting "three Tabs and a Space": what matters is that the answers are **reachable at all** by
 * keyboard, which is what a pointer-only row silently was not.
 */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class DesktopImportKeyboardTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File = temp.newFile("keys.mobix").also {
        SyntheticNotebook.write(it, SyntheticNotebook.pages(2))
    }

    /** The shell with an import card standing over it, as *Menu → Import PDF* leaves it. */
    private fun state(): DesktopShellState {
        val state = DesktopShellState(DesktopSettings(File(temp.root, "keys.properties")))
        state.open(notebook())
        state.importRequest = ImportPdfRequest(
            file = File("lecture-handout.pdf"),
            pageCount = 120,
            mode = ImportPdfMode.APPEND,
            pages = "1-120",
            chooseMode = true,
        )
        return state
    }

    /**
     * The window's own key hook, as `DesktopApp` installs it: the shell answers the keys and the preview
     * handler on the root hands them over before any child sees them.
     */
    private fun scene(state: DesktopShellState): ImageComposeScene {
        WindowKeys.onKey = { event -> state.onKey(event, onOpenPicker = {}) }
        return ImageComposeScene(900, 640, Density(1f)) {
            DesktopShell(
                state = state,
                onOpenPicker = {},
                modifier = Modifier.onPreviewKeyEvent { WindowKeys.handle(it) },
            )
        }
    }

    private fun ImageComposeScene.settle() {
        Snapshot.sendApplyNotifications()
        render()
    }

    private fun ImageComposeScene.press(key: Key) {
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyDown))
        sendKeyEvent(KeyEvent(key, KeyEventType.KeyUp))
        settle()
    }

    @Test
    fun `Tab reaches the answers and Space takes the one that has focus`() {
        val state = state()
        val scene = scene(state)
        try {
            scene.settle()
            var changed = false
            for (attempt in 1..12) {
                scene.press(Key.Tab)
                scene.press(Key.Spacebar)
                if (state.importRequest?.mode == ImportPdfMode.REPLACE) {
                    changed = true
                    break
                }
            }
            assertTrue("Tab must reach the answers and Space must take one", changed)
        } finally {
            scene.close()
            WindowKeys.onKey = null
        }
    }

    @Test
    fun `Space does not import, the card waits for its own button`() {
        val state = state()
        val scene = scene(state)
        try {
            scene.settle()
            for (attempt in 1..6) {
                scene.press(Key.Tab)
                scene.press(Key.Spacebar)
            }
            assertNotNull("the card must still be up", state.importRequest)
            assertEquals("and nothing imported", 0, state.active!!.document.model.pages.size - 2)
        } finally {
            scene.close()
            WindowKeys.onKey = null
        }
    }

    @Test
    fun `Escape puts the card away and imports nothing`() {
        val state = state()
        val scene = scene(state)
        try {
            scene.settle()
            // The window hands its root the focus (DesktopApp's focusRequester); this scene has no such
            // request, so the first Tab puts the keyboard inside the shell, as it already is in the real
            // window, and Esc then travels the same path it does there.
            scene.press(Key.Tab)
            scene.press(Key.Escape)
            assertNull("Esc is the card's Cancel", state.importRequest)
            assertEquals("the document is untouched", 2, state.active!!.document.model.pages.size)
        } finally {
            scene.close()
            WindowKeys.onKey = null
        }
    }

    @Test
    fun `the range's error is carried with the fields, and only the card's own imports`() {
        val state = state()
        state.editImportPdf { it.copy(pages = "1-999") }
        val request = state.importRequest!!
        assertTrue("999 pages out of 120 is not a range the card can import", request.range is com.mobixournal.render.PageRangePlan.Invalid)
        val scene = scene(state)
        try {
            // The card is still up and the confirm answer is the disabled one: pressing Space over it must
            // not import a range the card has already refused.
            scene.settle()
            for (attempt in 1..8) {
                scene.press(Key.Tab)
                scene.press(Key.Spacebar)
            }
            assertNotNull(state.importRequest)
            assertEquals(2, state.active!!.document.model.pages.size)
        } finally {
            scene.close()
            WindowKeys.onKey = null
        }
    }
}
