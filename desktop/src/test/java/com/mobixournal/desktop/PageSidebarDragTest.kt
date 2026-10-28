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
 * The sidebar's **drag-to-reorder**, driven by real pointer events: a press on a thumbnail, a drag down the
 * list, and the release that commits one move.
 *
 * The arithmetic behind it is `PageReorderTest`'s; what this file holds is that the gesture reaches it at
 * all, that a drag ends in exactly one command, and that a plain click is still a selection rather than a
 * move — the two gestures share a row, so they must not take each other's press.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PageSidebarDragTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val moves = mutableListOf<Pair<Int, Int>>()
    private val picks = mutableListOf<Int>()

    private fun tab(): DesktopTab {
        val file: File = temp.newFile("drag.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(8)) }
        return DesktopTabs().also { it.open(DocumentLoader.load(file)) }.active!!
    }

    private fun scene(tab: DesktopTab): ImageComposeScene = ImageComposeScene(240, 600, Density(1f)) {
        PageSidebar(
            tab = tab,
            cache = mutableMapOf<Int, ImageBitmap>(),
            onSelectPage = { picks += it },
            revision = 0,
            onMovePage = { from, to -> moves += from to to },
            modifier = Modifier.size(240.dp, 600.dp),
        )
    }

    @Test
    fun `a drag down the list commits one move`() {
        val tab = tab()
        val scene = scene(tab)
        try {
            // Anywhere in the top rows: whichever one is hit, the drag ends in a forward move of it.
            var at = 1_000L
            mouse(scene, PointerEventType.Press, 120f, 120f, at)
            for (step in 1..6) {
                at += 16L
                mouse(scene, PointerEventType.Move, 120f, 120f + step * 30f, at)
            }
            mouse(scene, PointerEventType.Release, 120f, 300f, at + 16L, pressed = false)
            Snapshot.sendApplyNotifications()

            assertEquals("one release, one move", 1, moves.size)
            assertTrue("and it went somewhere below where it started", moves.single().second > moves.single().first)
            assertTrue("a drag is not a click", picks.isEmpty())
        } finally {
            scene.close()
        }
    }

    @Test
    fun `a click on a thumbnail still selects it`() {
        val tab = tab()
        val scene = scene(tab)
        try {
            mouse(scene, PointerEventType.Press, 120f, 120f, 2_000L)
            mouse(scene, PointerEventType.Release, 120f, 120f, 2_032L, pressed = false)
            Snapshot.sendApplyNotifications()

            assertEquals("the click reached the row", 1, picks.size)
            assertTrue("and moved nothing", moves.isEmpty())
        } finally {
            scene.close()
        }
    }

    private fun mouse(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        at: Long,
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
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            timeMillis = at,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}
