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
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementBounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.SelectionRegion
import com.mobixournal.render.rectRegion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.PI

/**
 * The window's marquee selection: which handle a press grabbed, and what a real drag does to the page.
 *
 * The region and the edits are `PageSelectionTest`'s (they live in `mobix-core` with the maths, because the
 * tablet uses them too); what this suite owns is the window's half — the handle hit test in screen pixels,
 * the scale and turn a drag describes, and the gesture end to end: a marquee that catches a stroke, a drag
 * that moves it by exactly its travel as **one** undo step, and `Delete` that takes the caught ink away.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopSelectionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 595
    private val viewH = 700

    // --- the geometry, without a window ---------------------------------------------------------

    private val box = Bounds(100.0, 100.0, 200.0, 160.0)
    private val selection = PageSelection(rectRegion(100.0, 100.0, 200.0, 160.0), setOf(ElementRef(0, 0)))

    @Test
    fun `a press picks the handle it landed on`() {
        assertEquals(SelectionHandle.TOP_LEFT, selectionHandleAt(selection, 100.0, 100.0, tolPt = 4.0))
        assertEquals(SelectionHandle.BOTTOM_RIGHT, selectionHandleAt(selection, 200.0, 160.0, tolPt = 4.0))
        assertEquals("the body moves the selection", SelectionHandle.BODY, selectionHandleAt(selection, 150.0, 130.0, tolPt = 4.0))
        assertEquals("off the box a new region starts", SelectionHandle.NONE, selectionHandleAt(selection, 400.0, 400.0, tolPt = 4.0))
    }

    @Test
    fun `the turn handle sits above the outline and is grabbed first`() {
        assertEquals(SelectionHandle.ROTATE, selectionHandleAt(selection, 150.0, 100.0 - 26.0, tolPt = 4.0))
        // A press that lands between the ring and the edge is still the ring's: nothing else is up there.
        assertEquals(SelectionHandle.ROTATE, selectionHandleAt(selection, 150.0, 100.0 - 24.0, tolPt = 4.0))
    }

    @Test
    fun `a corner drag scales about the opposite corner`() {
        val (bottomRightX, bottomRightY) = anchorOf(box, SelectionHandle.TOP_LEFT)
        assertEquals(200.0, bottomRightX, 1e-6)
        assertEquals(160.0, bottomRightY, 1e-6)
        val (sx, sy) = scaleFactors(box, SelectionHandle.TOP_LEFT, x = 0.0, y = 0.0)
        assertEquals("the drag reached the far side of the anchor", 2.0, sx, 1e-6)
        assertEquals("the vertical drag reached twice the anchor's own distance", 8.0 / 3.0, sy, 1e-6)
    }

    @Test
    fun `a scale never inverts the selection`() {
        val (sx, sy) = scaleFactors(box, SelectionHandle.BOTTOM_RIGHT, x = 0.0, y = 0.0)
        assertEquals(MIN_SELECTION_SCALE, sx, 1e-9)
        assertEquals(MIN_SELECTION_SCALE, sy, 1e-9)
    }

    @Test
    fun `a turn is the angle the pointer swept, and Shift snaps it`() {
        val quarter = rotationBetween(selection, fromX = 150.0, fromY = 100.0, toX = 260.0, toY = 130.0)
        assertEquals(PI / 2, quarter, 1e-6)
        val snapped = rotationBetween(selection, 150.0, 100.0, 255.0, 135.0, snap = true)
        assertEquals("Shift lands on a 15 degree step", PI / 2, snapped, 1e-9)
    }

    @Test
    fun `a region that caught nothing leaves no selection`() {
        assertNull(finishedSelection(rectRegion(0.0, 0.0, 5.0, 5.0), emptySet()))
        assertNotNull(finishedSelection(rectRegion(0.0, 0.0, 5.0, 5.0), setOf(ElementRef(0, 0))))
    }

    @Test
    fun `the strips offer both marquee tools in one slot`() {
        assertEquals(SelectionTool.MARQUEE, selectionToolOf(DesktopTool.MARQUEE))
        assertEquals(SelectionTool.LASSO, selectionToolOf(DesktopTool.LASSO))
        assertNull(selectionToolOf(DesktopTool.PEN))
        val group = groupOf(DesktopTool.MARQUEE)!!
        assertEquals(listOf(DesktopTool.MARQUEE, DesktopTool.LASSO), group.members)
        assertTrue("a selection is not ink, so the pen's packets must not take the drag", !DesktopTool.MARQUEE.inksByPackets())
        assertTrue(!DesktopTool.LASSO.inksByPackets())
    }

    // --- the gesture, on a real canvas ----------------------------------------------------------

    @Test
    fun `a marquee catches a stroke, a drag moves it, and one undo step takes it back`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.document.editPage(0) { page -> page.withElements(listOf(bar(100.0, 100.0), bar(400.0, 400.0))) }
        val scene = canvas(tab, DesktopTool.MARQUEE)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()

            // A marquee around the first bar only.
            drag(scene, 80f, 80f, 200f, 140f)
            val selection = tab.selection
            assertNotNull("the drag left a selection", selection)
            assertEquals("it caught exactly one stroke", 1, selection!!.refs.size)
            val caught = selection.refs.single()
            assertEquals(
                "and it is the bar the rectangle closed around",
                98.0,
                ElementBounds.of(tab.document.page(0).layers[caught.layerIndex].elements[caught.elementIndex]).left,
                1e-6,
            )

            // A drag from inside it moves the ink by the drag's own travel.
            drag(scene, 120f, 100f, 150f, 130f)
            val moved = tab.document.page(0).layers[caught.layerIndex].elements[caught.elementIndex]
            assertEquals(128.0, ElementBounds.of(moved).left, 1e-6)
            assertEquals(128.0, ElementBounds.of(moved).top, 1e-6)
            assertEquals("the other bar stayed", 398.0, ElementBounds.of(elements(tab.document.page(0)).last()).left, 1e-6)
            assertEquals("the outline followed the ink", 110.0, tab.selection!!.bounds.left, 1e-6)

            // Move, resize and turn are one undo step each — not one per pointer sample.
            assertTrue("the move is undoable", tab.undo())
            assertEquals(98.0, ElementBounds.of(elements(tab.document.page(0))[caught.elementIndex]).left, 1e-6)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `Delete removes what the marquee caught, and does nothing without a selection`() {
        val settings = DesktopSettings(File(temp.root, "delete.properties"))
        val state = DesktopShellState(settings)
        // The command goes through the shell, so the document has to be the shell's own open tab.
        state.open(notebook())
        val tab = state.active!!
        tab.document.editPage(0) { page -> page.withElements(listOf(bar(100.0, 100.0), bar(400.0, 400.0))) }

        assertTrue("nothing selected: the command reports itself unavailable", !state.apply(ShellCommand.DELETE_SELECTION))

        val before = elements(tab.document.page(0)).size
        tab.selection = PageSelection(rectRegion(80.0, 80.0, 200.0, 140.0), setOf(ElementRef(0, 0)))
        assertTrue(state.apply(ShellCommand.DELETE_SELECTION))
        assertEquals("the caught stroke is gone", before - 1, elements(tab.document.page(0)).size)
        assertNull("and the outline with it", tab.selection)
        assertTrue("the delete is one undo step", tab.undo())
        assertEquals(before, elements(tab.document.page(0)).size)
        tab.document.close()
    }

    @Test
    fun `the lasso traces a loop rather than a box`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.document.editPage(0) { page -> page.withElements(listOf(bar(100.0, 100.0))) }
        val scene = canvas(tab, DesktopTool.LASSO)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()
            pen(scene, PointerEventType.Press, 80f, 80f, 1_000L)
            pen(scene, PointerEventType.Move, 200f, 80f, 1_016L)
            pen(scene, PointerEventType.Move, 200f, 180f, 1_032L)
            pen(scene, PointerEventType.Move, 80f, 180f, 1_048L)
            pen(scene, PointerEventType.Release, 80f, 80f, 1_064L, pressed = false)
            val selection = tab.selection
            assertNotNull("the loop caught the bar", selection)
            assertTrue("a lasso is a polygon, not a rectangle", selection!!.region is SelectionRegion.Polygon)
            assertEquals("it caught the bar and nothing else", 1, selection.refs.size)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    private fun notebook(): File = temp.newFile("selection.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    private fun canvas(tab: DesktopTab, tool: DesktopTool): ImageComposeScene =
        ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = tool,
                ink = DesktopInk(width = 4f),
                onViewport = {},
                onFit = {},
            )
        }

    private fun bar(x: Double, y: Double): Stroke = Stroke(
        points = listOf(StrokePoint(x, y, 4.0), StrokePoint(x + 60.0, y, 4.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = true,
    )

    private fun elements(page: Page): List<Element> = page.layers.flatMap { it.elements }

    /** [page] with [elements] on top of its last layer — the two strokes a marquee is judged against. */
    private fun Page.withElements(elements: List<Element>): Page {
        val layers = layers.ifEmpty { listOf(com.mobixournal.format.model.Layer(emptyList())) }
        val top = layers.lastIndex
        val grown = layers.toMutableList().also {
            it[top] = com.mobixournal.format.model.Layer(it[top].elements + elements, it[top].name)
        }
        return copy(layers = grown)
    }

    private fun drag(scene: ImageComposeScene, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        pen(scene, PointerEventType.Press, fromX, fromY, 1_000L)
        pen(scene, PointerEventType.Move, (fromX + toX) / 2f, (fromY + toY) / 2f, 1_016L)
        pen(scene, PointerEventType.Move, toX, toY, 1_032L)
        pen(scene, PointerEventType.Release, toX, toY, 1_048L, pressed = false)
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
