package com.mobixournal.desktop

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

    private val counters = IntArray(PageMenuMetrics.ROWS)

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

    /** Each row runs its own command — six rows, six counters, one click apiece. */
    @Test
    fun `every row of the page menu runs its command`() {
        val scene = panelScene(canStep = true, canRemove = true)
        try {
            var at = 2_000L
            for (index in 0 until PageMenuMetrics.ROWS) {
                tap(scene, PageMenuMetrics.width.value / 2f, rowCentre(index), at)
                at += 512L
            }
            for (index in 0 until PageMenuMetrics.ROWS) {
                assertEquals("row $index ran once", 1, counters[index])
            }
        } finally {
            scene.close()
        }
    }

    /** A one-page document leaves the two page-list commands dead, and the page's own two live. */
    @Test
    fun `with a single page the list commands are dead and the rest answer`() {
        val scene = panelScene(canStep = false, canRemove = false)
        try {
            var at = 3_000L
            tap(scene, PageMenuMetrics.width.value / 2f, rowCentre(0), at); at += 512L
            tap(scene, PageMenuMetrics.width.value / 2f, rowCentre(1), at); at += 512L
            tap(scene, PageMenuMetrics.width.value / 2f, rowCentre(4), at)

            assertEquals("add still works", 1, counters[0])
            assertEquals("remove is dead", 0, counters[1])
            assertEquals("format still works", 1, counters[4])
        } finally {
            scene.close()
        }
    }

    private fun rows() = PageMenuActions(
        addPage = { counters[0]++ },
        removePage = { counters[1]++ },
        previousPage = { counters[2]++ },
        nextPage = { counters[3]++ },
        format = { counters[4]++ },
        background = { counters[5]++ },
    )

    private fun panelScene(canStep: Boolean, canRemove: Boolean): ImageComposeScene {
        val actions = rows().copy(canStep = canStep, canRemove = canRemove)
        val height = PanelBottom.value + 8f
        return ImageComposeScene(
            PageMenuMetrics.width.value.toInt(),
            height.toInt(),
            Density(1f),
        ) {
            Box(Modifier.size(PageMenuMetrics.width, PanelBottom + 8.dp).background(DesktopTheme.desk)) {
                PageMenuPanel(actions) {}
            }
        }
    }

    private fun rowCentre(index: Int): Float =
        PageMenuMetrics.rowTop(index).value + PageMenuMetrics.rowHeight.value / 2f

    private val PanelBottom
        get() = PageMenuMetrics.rowTop(PageMenuMetrics.ROWS - 1) + PageMenuMetrics.rowHeight +
            PageMenuMetrics.padding

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
