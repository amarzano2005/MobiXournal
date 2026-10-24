package com.mobixournal.desktop

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
import com.mobixournal.format.model.TableElement
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingPlacement
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The grips **in a real scene**: a press on a table's divider really drags it, and a double-click on the
 * selected element really opens its editor.
 *
 * `LivingGripsTest` owns the arithmetic; what this suite protects is the wiring no pure test can see — the
 * grip is caught before the selection's own handles, the whole drag is **one** undo step (so a table's
 * column widths do not fill the history), the outline follows the ink, and a second click on the element
 * is the shell's Edit command rather than a move that goes nowhere.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopLivingGripsSceneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File = temp.newFile("grips.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    /** A tab with one table placed and selected, and a canvas with the marquee picked. */
    private fun tableScene(onEdit: (() -> Unit)? = null): Triple<DesktopTab, TableElement, ImageComposeScene> {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val table = LivingPlacement.table(Bounds(100.0, 100.0, 340.0, 220.0), rows = 3, cols = 3)
        assertTrue("the fixture places its table", placeLivingElement(tab, table))
        val scene = ImageComposeScene(595, 700, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = DesktopTool.MARQUEE,
                onViewport = {},
                onFit = {},
                onEditLiving = onEdit,
            )
        }
        scene.render()
        Snapshot.sendApplyNotifications()
        return Triple(tab, table, scene)
    }

    @Test
    fun `a divider drag is one undo step and the outline follows the ink`() {
        val (tab, table, scene) = tableScene()
        try {
            val divider = table.left + table.colWidths[0]
            val middle = table.top + table.height / 2
            drag(scene, divider.toFloat(), middle.toFloat(), (divider + 30).toFloat(), middle.toFloat())

            val after = elements(tab).filterIsInstance<TableElement>().single()
            assertEquals("the first column took the drag", table.colWidths[0] + 30.0, after.colWidths[0], 1.5)
            assertEquals("the grid did not grow", table.width, after.width, 1.5)
            assertEquals("under the same selection", 1, tab.selection?.refs?.size)
            assertEquals(
                "the outline still traces the element's box",
                after.left + after.width,
                tab.selection!!.bounds.right,
                1.5,
            )

            assertTrue(tab.undo())
            val undone = elements(tab).filterIsInstance<TableElement>().single()
            assertEquals("one undo takes the whole drag back", table.colWidths[0], undone.colWidths[0], 1e-6)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a drag that misses every grip moves the element instead of dividing it`() {
        val (tab, table, scene) = tableScene()
        try {
            // A press on the grid's body (not on a divider) is the selection's own move.
            drag(scene, 150f, 150f, 170f, 150f)
            val after = elements(tab).filterIsInstance<TableElement>().single()
            assertEquals("the columns are untouched", table.colWidths, after.colWidths)
            assertEquals("and the table moved", table.left + 20.0, after.left, 1.5)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a click on an element selects it, without a marquee around it`() {
        val (tab, table, scene) = tableScene()
        try {
            tab.selection = null
            scene.render()

            tap(scene, x = (table.left + table.width / 2).toFloat(), y = (table.top + 10).toFloat(), time = 1_000L)
            val picked = tab.selection
            assertNotNull("a click on the element picks it", picked)
            assertEquals(1, picked!!.refs.size)
            assertEquals(
                "and the outline is the element's own box",
                table.left, picked.bounds.left, 1.5,
            )

            // A click on empty paper lets it go again.
            tap(scene, x = 40f, y = 40f, time = 3_000L)
            assertNull("empty paper clears the selection", tab.selection)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a double-click on any living element opens its editor, selected or not`() {
        var edits = 0
        val (tab, table, scene) = tableScene(onEdit = { edits++ })
        try {
            tab.selection = null
            scene.render()
            val x = (table.left + table.width / 2).toFloat()
            val y = (table.top + table.height / 2).toFloat()
            tap(scene, x = x, y = y, time = 1_000L)
            assertNotNull("the first click selects the element", tab.selection)
            assertEquals("and opens nothing", 0, edits)
            tap(scene, x = x, y = y, time = 1_100L)
            assertEquals("the second click opens the lane", 1, edits)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a double-click on the selected element opens its editor`() {
        var edits = 0
        val (tab, table, scene) = tableScene(onEdit = { edits++ })
        try {
            val middle = table.top + table.height / 2
            tap(scene, x = (table.left + table.width / 2).toFloat(), y = middle.toFloat(), time = 1_000L)
            assertFalse("one click only selects", edits > 0)
            tap(scene, x = (table.left + table.width / 2).toFloat(), y = middle.toFloat(), time = 1_100L)
            assertTrue("the second click opens the editor", edits == 1)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    private fun elements(tab: DesktopTab) = tab.document.page(tab.page).layers.flatMap { it.elements }

    private fun drag(scene: ImageComposeScene, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        pen(scene, PointerEventType.Press, fromX, fromY, 1_000L)
        pen(scene, PointerEventType.Move, (fromX + toX) / 2f, (fromY + toY) / 2f, 1_016L)
        pen(scene, PointerEventType.Move, toX, toY, 1_032L)
        pen(scene, PointerEventType.Release, toX, toY, 1_048L, pressed = false)
    }

    private fun tap(scene: ImageComposeScene, x: Float, y: Float, time: Long) {
        pen(scene, PointerEventType.Press, x, y, time)
        pen(scene, PointerEventType.Release, x, y, time + 32, pressed = false)
    }

    private fun pen(
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
                    type = PointerType.Stylus,
                    pressure = 1f,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            timeMillis = time,
        )
    }
}
