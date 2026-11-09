package com.mobixournal.desktop

import com.mobixournal.desktop.settings.DesktopSettingSection
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShell
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.selectTool

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Settings surface is **over** the page, so the page must not answer for it.
 *
 * Compose hands every press to every node under the pointer, and the sheet reads its presses with
 * `requireUnconsumed = false` on purpose (a contact the ring answered is still that contact). Covering the
 * page was therefore never enough: clicking a row also started a stroke behind the panel, and a drag on a
 * slider drew a line where nothing could be seen. The window's answer is that a surface takes the pointer for
 * as long as it is up, which is what these two drags measure — the same gesture, once with the panel over the
 * page and once without it, so a page that had simply stopped drawing could not pass.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopSettingsClickThroughTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 1000
    private val height = 700

    private fun notebook(): File = temp.newFile("click-through.mobix").also {
        SyntheticNotebook.write(it, SyntheticNotebook.pages(1))
    }

    private fun state(settingsOpen: Boolean): DesktopShellState {
        // A returning window: the first run's format card would otherwise stand over the page and take
        // the drags this file measures.
        val settings = DesktopSettings(File(temp.root, "click-through.properties")).also {
            it.documentFormatChosen = true
        }
        val state = DesktopShellState(settings)
        state.open(notebook())
        state.selectTool(DesktopTool.PEN)
        if (settingsOpen) state.prefs.openAt(DesktopSettingSection.STYLUS) else state.prefs.close()
        return state
    }

    private fun ink(state: DesktopShellState): Int {
        val tab = state.tabs.active!!
        return tab.document.page(tab.page).layers.sumOf { it.elements.size }
    }

    /** A drag across the surface's middle: over the sheet, but under the panel (900 × 616, centred). */
    private fun drag(state: DesktopShellState) {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            Snapshot.sendApplyNotifications()
            scene.render()
            mouse(scene, PointerEventType.Press, 440f, 300f, 1_000L)
            mouse(scene, PointerEventType.Move, 500f, 350f, 1_016L)
            mouse(scene, PointerEventType.Move, 560f, 400f, 1_032L)
            mouse(scene, PointerEventType.Release, 560f, 400f, 1_048L, pressed = false)
            Snapshot.sendApplyNotifications()
            scene.render()
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a drag on the settings panel is not a stroke on the page`() {
        val state = state(settingsOpen = true)
        val before = ink(state)
        drag(state)
        assertEquals(
            "the press landed on a Settings row, so the sheet behind it holds nothing new",
            before,
            ink(state),
        )
    }

    @Test
    fun `the same drag draws once the panel is gone`() {
        val state = state(settingsOpen = false)
        val before = ink(state)
        drag(state)
        assertTrue("the pen inks the page when nothing is over it", ink(state) > before)
    }

    private fun mouse(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        time: Long,
        pressed: Boolean = true,
    ) {
        scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(
                ComposeScenePointer(
                    id = PointerId(1),
                    position = Offset(x, y),
                    pressed = pressed,
                    type = PointerType.Mouse,
                    pressure = 1f,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            timeMillis = time,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}
