package com.mobixournal.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The sidebar's **multi-page selection**, driven by real pointer events: a Ctrl-click collects rows, and
 * the pick is then what the bar's delete acts on and what a drag carries.
 *
 * `PageSelectionTest` holds the rule and `DesktopPageSelectionCommandTest` the page edits; what only a
 * scene can answer is whether a Ctrl-click on a row reaches that rule at all — the row's press is shared
 * with the drag-to-reorder gesture, and a handler that swallowed the keyboard would leave every pick a
 * single page without a test noticing.
 *
 * Row positions are read back from the selections the sidebar reports rather than assumed: whichever two
 * rows the two clicks land on are the two the assertions talk about.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PageSelectionSceneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val picks = mutableListOf<Int>()
    private val removed = mutableListOf<Set<Int>>()
    private val moved = mutableListOf<Pair<Set<Int>, Int>>()

    private fun tab(): DesktopTab {
        val file: File = temp.newFile("pick.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(8)) }
        return DesktopTabs().also { it.open(DocumentLoader.load(file)) }.active!!
    }

    private fun scene(tab: DesktopTab): ImageComposeScene = ImageComposeScene(240, 1100, Density(1f)) {
        PageSidebar(
            tab = tab,
            cache = mutableMapOf<Int, ImageBitmap>(),
            onSelectPage = { picks += it },
            revision = 0,
            onMovePages = { pages, target -> moved += pages to target },
            onRemovePages = { pages -> removed += pages },
            modifier = Modifier.size(240.dp, 1100.dp),
        )
    }

    @Test
    fun `ctrl-clicked rows are deleted together from the bar`() {
        val tab = tab()
        val scene = scene(tab)
        try {
            click(scene, 120f, 120f, at = 1_000L, ctrl = true)
            click(scene, 120f, 280f, at = 1_200L, ctrl = true)
            assertEquals("two rows were picked", 2, picks.size)

            // The bar sits at the bottom of the list while pages are picked, its delete on the left.
            click(scene, 60f, 1_050f, at = 1_400L)

            assertEquals("one delete", 1, removed.size)
            assertEquals("and it names both picked rows", picks.toSet(), removed.single())
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a drag on a picked row carries the whole pick`() {
        val tab = tab()
        val scene = scene(tab)
        try {
            click(scene, 120f, 120f, at = 2_000L, ctrl = true)
            click(scene, 120f, 280f, at = 2_200L, ctrl = true)

            var at = 2_400L
            press(scene, 120f, 120f, at, ctrl = true)
            for (step in 1..6) {
                at += 16L
                move(scene, 120f, 120f + step * 30f, at)
            }
            release(scene, 120f, 300f, at + 16L)

            assertEquals("one release, one move", 1, moved.size)
            assertEquals("it carries the pick", picks.toSet(), moved.single().first)
            assertTrue("and lands below it", moved.single().second > picks.min())
            assertTrue("no single-page move was asked for", removed.isEmpty())
        } finally {
            scene.close()
        }
    }

    /** A press and release that never moves: one click, with or without Ctrl held. */
    private fun click(scene: ImageComposeScene, x: Float, y: Float, at: Long, ctrl: Boolean = false) {
        press(scene, x, y, at, ctrl)
        release(scene, x, y, at + 32L, ctrl)
    }

    private fun press(scene: ImageComposeScene, x: Float, y: Float, at: Long, ctrl: Boolean = false) =
        mouse(scene, PointerEventType.Press, x, y, at, pressed = true, ctrl = ctrl)

    private fun release(scene: ImageComposeScene, x: Float, y: Float, at: Long, ctrl: Boolean = false) =
        mouse(scene, PointerEventType.Release, x, y, at, pressed = false, ctrl = ctrl)

    private fun move(scene: ImageComposeScene, x: Float, y: Float, at: Long) =
        mouse(scene, PointerEventType.Move, x, y, at, pressed = true)

    private fun mouse(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        at: Long,
        pressed: Boolean,
        ctrl: Boolean = false,
    ) {
        scene.sendPointerEvent(
            eventType = type,
            pointers = listOf(
                ComposeScenePointer(
                    id = PointerId(1),
                    position = Offset(x, y),
                    pressed = pressed,
                    type = PointerType.Mouse,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = ctrl),
            timeMillis = at,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}
