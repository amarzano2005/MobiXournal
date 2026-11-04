package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.io.save
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.shell.DesktopPages
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.shell.PageMenuActions
import com.mobixournal.desktop.shell.PageMenuMetrics
import com.mobixournal.desktop.shell.PageMenuPanel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The **Pages** section — the tablet's page menu, on the window's strip.
 *
 * Two halves, like the header's tests: the commands against a real tab (adding a sheet, taking it back
 * with `Ctrl+Z`, refusing to empty the document), and the panel's own rows, clicked by
 * `PageMenuMetrics`' arithmetic rather than a guessed coordinate.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopPagesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var add = 0
    private var remove = 0
    private var up = 0
    private var down = 0
    private var previous = 0
    private var next = 0
    private var manage = 0
    private var format = 0
    private var background = 0
    private var sized = 0

    private fun pagesOf(tabs: DesktopTabs): DesktopPages =
        DesktopPages(tabs, { _, _ -> }, { DEFAULT_WIDTH to DEFAULT_HEIGHT })

    private fun tabs(): DesktopTabs {
        val file: File = temp.newFile("pages.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(2)) }
        return DesktopTabs().also { it.open(DocumentLoader.load(file)) }
    }

    @Test
    fun `add page inserts a sheet after the one in view and steps onto it`() {
        val tabs = tabs()
        val tab = tabs.active!!
        tab.page = 0
        val before = tab.document.pageCount

        assertTrue("the sheet is added", pagesOf(tabs).addPage())

        assertEquals("one more page", before + 1, tab.document.pageCount)
        assertEquals("the view moved onto it", 1, tab.page)
        val added = tab.document.page(1)
        assertEquals("a blank sheet", 1, added.layers.size)
        assertTrue("and empty", added.layers.single().elements.isEmpty())
        assertEquals("born at the default size", DEFAULT_WIDTH, added.width, 0.01)
        assertTrue("and the document is dirty", tab.isDirty)
        assertTrue("with a step to undo", tab.canUndo)
    }

    @Test
    fun `undo takes the added page back`() {
        val tabs = tabs()
        val tab = tabs.active!!
        val before = tab.document.pageCount
        pagesOf(tabs).addPage()

        assertTrue(tab.undo())

        assertEquals("the list is what it was", before, tab.document.pageCount)
        assertFalse("and nothing is left to undo", tab.canUndo)
    }

    @Test
    fun `remove page drops the sheet in view and keeps the next one in view`() {
        val tabs = tabs()
        val tab = tabs.active!!
        tab.page = 0
        val before = tab.document.pageCount

        assertTrue(pagesOf(tabs).removePage())

        assertEquals("one fewer page", before - 1, tab.document.pageCount)
        assertEquals("the view stays in range", 0, tab.page)
        assertTrue(tab.undo())
        assertEquals("undo puts it back", before, tab.document.pageCount)
    }

    @Test
    fun `the last page is never removed`() {
        val tabs = tabs()
        val tab = tabs.active!!
        tab.page = 0
        repeat(tab.document.pageCount - 1) { pagesOf(tabs).removePage() }
        assertEquals("one page left", 1, tab.document.pageCount)

        assertFalse("and it cannot be removed", pagesOf(tabs).removePage())
        assertEquals(1, tab.document.pageCount)
    }

    /** The page is not a picture on screen: a save writes the list, and reopening finds the sheet. */
    @Test
    fun `an added page is in the file a save writes`() {
        val tabs = tabs()
        val tab = tabs.active!!
        pagesOf(tabs).addPage()

        tab.saveBack()
        val reopened = DocumentLoader.load(tab.file)
        try {
            assertEquals("the written file carries the added sheet", 3, reopened.pageCount)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `step walks the page list and stops at both ends`() {
        val tabs = tabs()
        val tab = tabs.active!!
        tab.page = 0

        assertTrue(pagesOf(tabs).step(1))
        assertEquals(1, tab.page)
        assertFalse("no page past the last", pagesOf(tabs).step(1))
        assertTrue(pagesOf(tabs).step(-1))
        assertEquals(0, tab.page)
        assertFalse("and none before the first", pagesOf(tabs).step(-1))
    }

    /** Each row runs its own command, one click apiece, by the panel's own arithmetic. */
    @Test
    fun `every row of the page menu runs its command`() {
        val scene = panelScene(canStep = true, canRemove = true)
        try {
            var at = 2_000L
            for (item in listOf(
                PageMenuMetrics.Item.ADD,
                PageMenuMetrics.Item.REMOVE,
                PageMenuMetrics.Item.FORMAT,
                PageMenuMetrics.Item.BACKGROUND,
                PageMenuMetrics.Item.MOVE_UP,
                PageMenuMetrics.Item.MOVE_DOWN,
                PageMenuMetrics.Item.MANAGE_PAGES,
            )) {
                tap(scene, PageMenuMetrics.width.value / 2f, PageMenuMetrics.centre(item).value, at)
                at += 512L
            }

            assertEquals("add ran", 1, add)
            assertEquals("remove ran", 1, remove)
            assertEquals("format ran", 1, format)
            assertEquals("and so did the paper", 1, background)
            assertEquals("move up ran", 1, up)
            assertEquals("move down ran", 1, down)
            assertEquals("the typed range ran", 1, manage)
        } finally {
            scene.close()
        }
    }

    /** The navigator's two arrows step the page, and the quick sizes hand back a nudged sheet. */
    @Test
    fun `the navigator and the quick sizes answer`() {
        val scene = panelScene(canStep = true, canRemove = true)
        try {
            var at = 4_000L
            fun tapAt(x: Float, y: Float) {
                tap(scene, x, y, at)
                at += 512L
            }
            val nav = PageMenuMetrics.centre(PageMenuMetrics.Item.NAV).value
            tapAt(PageMenuMetrics.navCentre(next = false).value, nav)
            tapAt(PageMenuMetrics.navCentre(next = true).value, nav)
            val width = PageMenuMetrics.centre(PageMenuMetrics.Item.WIDTH).value
            tapAt(PageMenuMetrics.stepCentre(plus = true).value, width)
            tapAt(PageMenuMetrics.stepCentre(plus = false).value, width)
            val height = PageMenuMetrics.centre(PageMenuMetrics.Item.HEIGHT).value
            tapAt(PageMenuMetrics.stepCentre(plus = true).value, height)

            assertEquals("previous ran", 1, previous)
            assertEquals("next ran", 1, next)
            assertEquals("three nudges of the sheet", 3, sized)
        } finally {
            scene.close()
        }
    }

    /** A one-page document leaves the list commands dead, and the page's own two live. */
    @Test
    fun `with a single page the list commands are dead and the rest answer`() {
        val scene = panelScene(canStep = false, canRemove = false)
        try {
            var at = 3_000L
            for (item in listOf(
                PageMenuMetrics.Item.ADD,
                PageMenuMetrics.Item.REMOVE,
                PageMenuMetrics.Item.MOVE_UP,
                PageMenuMetrics.Item.MOVE_DOWN,
                PageMenuMetrics.Item.FORMAT,
                PageMenuMetrics.Item.BACKGROUND,
            )) {
                tap(scene, PageMenuMetrics.width.value / 2f, PageMenuMetrics.centre(item).value, at)
                at += 512L
            }

            assertEquals("add still works", 1, add)
            assertEquals("remove is dead", 0, remove)
            assertEquals("moving up is dead", 0, up)
            assertEquals("and so is moving down", 0, down)
            assertEquals("format still works", 1, format)
            assertEquals("and so does the paper", 1, background)
        } finally {
            scene.close()
        }
    }

    private fun rows() = PageMenuActions(
        addPage = { add++ },
        removePage = { remove++ },
        movePageUp = { up++ },
        movePageDown = { down++ },
        previousPage = { previous++ },
        nextPage = { next++ },
        managePages = { manage++ },
        format = { format++ },
        background = { background++ },
        // One past the first, so both arrows have somewhere to go: at page 0 the back arrow is dead,
        // which is the panel's rule and would make the navigator's test assert the wrong thing.
        page = 1,
        pageCount = 4,
        pageSize = DEFAULT_WIDTH to DEFAULT_HEIGHT,
        // The arrows hand back a **count of steps**, so a counting lambda here is the whole panel's contract.
        onNudge = { _, _ -> sized++ },
    )

    private fun panelScene(canStep: Boolean, canRemove: Boolean): ImageComposeScene {
        val actions = rows().copy(
            canStep = canStep,
            canRemove = canRemove,
            canMoveUp = canStep,
            canMoveDown = canStep,
            canManage = true,
        )
        val height = PageMenuMetrics.bottom.value + 8f
        return ImageComposeScene(
            PageMenuMetrics.width.value.toInt(),
            height.toInt(),
            Density(1f),
        ) {
            Box(Modifier.size(PageMenuMetrics.width, PageMenuMetrics.bottom + 8.dp).background(DesktopTheme.desk)) {
                PageMenuPanel(actions) {}
            }
        }
    }

    private fun tap(scene: ImageComposeScene, x: Float, y: Float, at: Long) {
        mouse(scene, PointerEventType.Press, x, y, at)
        mouse(scene, PointerEventType.Release, x, y, at + 32L, pressed = false)
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
    }

    private companion object {
        const val DEFAULT_WIDTH = 595.0
        const val DEFAULT_HEIGHT = 842.0
    }
}
