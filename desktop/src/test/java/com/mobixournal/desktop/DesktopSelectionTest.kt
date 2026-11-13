package com.mobixournal.desktop

import com.mobixournal.desktop.ink.inksByPackets
import com.mobixournal.desktop.ink.withElement

import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.MIN_SELECTION_SCALE
import com.mobixournal.desktop.sheet.MAX_SELECTION_SCALE
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.SelectionHandle
import com.mobixournal.desktop.sheet.SelectionTool
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.sheet.anchorOf
import com.mobixournal.desktop.sheet.cornerLocksAspect
import com.mobixournal.format.model.ImageElement
import com.mobixournal.desktop.sheet.ROTATE_HANDLE_GAP_PT
import com.mobixournal.render.SelectionShaft
import com.mobixournal.render.shaftTurn
import com.mobixournal.desktop.sheet.finishedSelection
import com.mobixournal.desktop.sheet.rotationBetween
import com.mobixournal.desktop.sheet.scaleFactors
import com.mobixournal.desktop.sheet.selectionHandleAt
import com.mobixournal.desktop.sheet.selectionToolOf
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.shell.ShellCommand
import com.mobixournal.desktop.tools.DesktopTool
import com.mobixournal.desktop.tools.groupOf

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `the edge midpoints stretch one axis, as the tablet's do`() {
        assertEquals("the top side", SelectionHandle.TOP, selectionHandleAt(selection, 150.0, 100.0, tolPt = 4.0))
        assertEquals("the bottom side", SelectionHandle.BOTTOM, selectionHandleAt(selection, 150.0, 160.0, tolPt = 4.0))
        assertEquals("the left side", SelectionHandle.LEFT, selectionHandleAt(selection, 100.0, 130.0, tolPt = 4.0))
        assertEquals("the right side", SelectionHandle.RIGHT, selectionHandleAt(selection, 200.0, 130.0, tolPt = 4.0))
        val (sx, sy) = scaleFactors(box, SelectionHandle.RIGHT, x = 300.0, y = 130.0)
        assertEquals("a side drag widens the box", 2.0, sx, 1e-6)
        assertEquals("and leaves the height alone", 1.0, sy, 1e-6)
        val (lockedSx, lockedSy) = scaleFactors(box, SelectionHandle.TOP_LEFT, x = 0.0, y = 0.0, lockAspect = true)
        assertEquals("a corner keeps both axes together, as the tablet's does", lockedSx, lockedSy, 1e-9)
    }

    @Test
    fun `a tiny selection keeps its middle for the move`() {
        // The tablet caps a handle's reach at a third of the selection, or a 15 pt figure could not be
        // dragged at all: every touch would land within the radius of some handle.
        val tiny = PageSelection(rectRegion(100.0, 100.0, 115.0, 112.0), setOf(ElementRef(0, 0)))
        assertEquals(
            "the middle is still the body",
            SelectionHandle.BODY,
            selectionHandleAt(tiny, 107.5, 106.0, tolPt = 30.0),
        )
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

    /**
     * A **line** is stretched from its ends, and only from its ends.
     *
     * Its box is only as thick as the pen that drew it, so the report it answers — a line that behaved
     * differently on every drag — came from the grab radius being capped by that thickness: the end handle
     * had a reach of a point or two, a press beside it carried the whole line instead, and one a hair further
     * out dropped the selection and started a new marquee. Two handles with the full radius, no corner stacked
     * on them, and no edge across the thickness make the same press mean the same thing every time.
     */
    @Test
    fun `a line wears two end handles and stretches only along its length`() {
        // 64 pt long and 4 pt of box: a line drawn at its pen's own width.
        val line = PageSelection(rectRegion(100.0, 100.0, 164.0, 104.0), setOf(ElementRef(0, 0)))
        assertEquals("the left end", SelectionHandle.LEFT, selectionHandleAt(line, 100.0, 102.0, tolPt = 9.0))
        assertEquals("the right end", SelectionHandle.RIGHT, selectionHandleAt(line, 164.0, 102.0, tolPt = 9.0))
        // The corner collapses into the end handle: a press there lengthens the line, it does not scale it.
        assertEquals(SelectionHandle.RIGHT, selectionHandleAt(line, 164.0, 104.0, tolPt = 9.0))
        // The band above and below the line carries it — where the edge handle that could only multiply by
        // one used to sit and leave the drag looking dead.
        assertEquals("the middle", SelectionHandle.BODY, selectionHandleAt(line, 132.0, 102.0, tolPt = 9.0))
        assertEquals("the band above it", SelectionHandle.BODY, selectionHandleAt(line, 132.0, 96.0, tolPt = 9.0))

        val (sx, sy) = scaleFactors(line.bounds, SelectionHandle.RIGHT, x = 228.0, y = 102.0)
        assertEquals("the drag's own ratio about the far end", 2.0, sx, 1e-6)
        assertEquals("a line has no thickness to stretch", 1.0, sy, 1e-6)
        val (cornerSx, cornerSy) = scaleFactors(
            line.bounds, SelectionHandle.TOP_LEFT, x = 0.0, y = 102.0, lockAspect = true,
        )
        assertEquals("a corner on a line is its end handle", 2.5625, cornerSx, 1e-6)
        assertEquals(1.0, cornerSy, 1e-6)
    }

    @Test
    fun `a stretch is bounded at both ends`() {
        val (far, _) = scaleFactors(box, SelectionHandle.RIGHT, x = 100_000.0, y = 130.0)
        assertEquals(MAX_SELECTION_SCALE, far, 1e-9)
        val (near, _) = scaleFactors(box, SelectionHandle.RIGHT, x = 0.0, y = 130.0)
        assertEquals(MIN_SELECTION_SCALE, near, 1e-9)
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

    /**
     * The report this suite grew from, end to end: a **line** pulled longer from its end.
     *
     * A click-selected stroke's box is only as thick as its ink, so the handles' reach was capped by that
     * thickness — the end handle had a point of slack, and the same press carried the line, resized it or
     * dropped the selection. Now the end is a real target and the drag is one axis: the line is longer, its
     * ink thickness is untouched and the lift is one undo step.
     */
    @Test
    fun `a click-selected line lengthens from its end`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.document.editPage(0) { page -> page.withElements(listOf(bar(100.0, 100.0))) }
        val scene = canvas(tab, DesktopTool.MARQUEE)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()

            // A line has no box: its own ends are its two handles (`SelectionShaft`).
            click(scene, 130f, 100f, at = 1_000L)
            assertEquals("the click selected the line", 1, tab.selection!!.refs.size)

            // The tip's grip is taken out to 226: the tail is what stays, so the line is 126 pt long and
            // the tip is under the pointer it followed.
            drag(scene, 160f, 100f, 226f, 100f)
            val line = elements(tab.document.page(0)).last() as Stroke
            assertEquals("the far end is the anchor", 100.0, line.points.first().x, 1e-6)
            assertEquals("the end followed the pointer", 226.0, line.points.last().x, 1e-6)
            assertEquals("on the line it was drawn on", 100.0, line.points.last().y, 1e-6)
            assertEquals("a longer line is not a thicker one", 4.0, line.points.last().width, 1e-6)
            assertTrue("one drag, one undo step", tab.undo())
            assertEquals(160.0, (elements(tab.document.page(0)).last() as Stroke).points.last().x, 1e-6)
        } finally {
            scene.close()
            tab.document.close()
        }
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

    /**
     * A double-click **inside** the outline belongs to the selection, never to what lies under it.
     *
     * The group a marquee caught is the thing the user is holding: drilling into its contents — replacing
     * two strokes with the one the second click happened to land on — is the opposite of what selecting them
     * together was for, and on a dense page it made the outline seem to pick itself apart.
     */
    @Test
    fun `a double-click inside the selection does not select an inner element`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.document.editPage(0) { page -> page.withElements(listOf(bar(100.0, 100.0), bar(100.0, 160.0))) }
        val scene = canvas(tab, DesktopTool.MARQUEE)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()

            drag(scene, 80f, 80f, 200f, 200f)
            val group = requireNotNull(tab.selection) { "the marquee caught nothing" }
            assertEquals("both bars were caught", 2, group.refs.size)

            // Two clicks on one of them, inside the outline.
            click(scene, 120f, 100f, at = 2_000L)
            click(scene, 120f, 100f, at = 2_300L)

            assertEquals("the selection is still the group", group.refs, tab.selection!!.refs)
            assertEquals("and the outline never moved", group.bounds, tab.selection!!.bounds)
            assertFalse("a click that travels nowhere writes nothing", tab.canUndo)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * A click in the **empty part of a stroke's box** selects nothing, because the box is not the element.
     *
     * A diagonal line's box is a square of blank paper around it; reading that square as the stroke made the
     * selection tool pick ink the pointer was nowhere near.
     */
    @Test
    fun `a click in a diagonal stroke's box but off its ink selects nothing`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.document.editPage(0) { page -> page.withElements(listOf(diagonal(100.0, 100.0))) }
        val scene = canvas(tab, DesktopTool.MARQUEE)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()

            // Inside the box (the line runs 100,100 → 200,200) and ~56 pt from the ink.
            click(scene, 190f, 110f, at = 1_000L)
            assertNull("the box of the line is not the line", tab.selection)

            // On the ink, within the tap's own reach.
            click(scene, 150f, 152f, at = 2_000L)
            assertNotNull("the line itself is pickable", tab.selection)
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

    /** A line from ([x], [y]) to ([x] + 100, [y] + 100) — the diagonal a box cannot describe. */
    private fun diagonal(x: Double, y: Double): Stroke = Stroke(
        points = listOf(StrokePoint(x, y, 4.0), StrokePoint(x + 100.0, y + 100.0, 4.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = true,
    )

    /** A press and a lift that never move: one click. */
    private fun click(scene: ImageComposeScene, x: Float, y: Float, at: Long) {
        pen(scene, PointerEventType.Press, x, y, at)
        pen(scene, PointerEventType.Release, x, y, at + 16L, pressed = false)
    }

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

    @Test
    fun `a line's shaft wears only its two end handles`() {
        // A **diagonal** line: its box has width and height, so a box-only rule would give it eight handles
        // and a two-axis stretch where the user asked for a length.
        val line = PageSelection(rectRegion(100.0, 100.0, 200.0, 200.0), setOf(ElementRef(0, 0)))
        val shaft = SelectionShaft(100.0, 100.0, 200.0, 200.0)
        assertEquals(
            SelectionHandle.SHAFT_TAIL,
            selectionHandleAt(line, 100.0, 100.0, tolPt = 4.0, shaft = shaft),
        )
        assertEquals(
            SelectionHandle.SHAFT_TIP,
            selectionHandleAt(line, 200.0, 200.0, tolPt = 4.0, shaft = shaft),
        )
        // The box's own corner is not a handle of a shaft: nothing there may change the drawing's shape.
        assertNotEquals(
            SelectionHandle.TOP_RIGHT,
            selectionHandleAt(line, 200.0, 100.0, tolPt = 4.0, shaft = shaft),
        )
        assertEquals(
            SelectionHandle.BODY,
            selectionHandleAt(line, 200.0, 100.0, tolPt = 4.0, shaft = shaft),
        )
        // The turn is still offered, and its ring hangs off the **middle of the ink**: rotating a line does not
        // stretch it.
        val turn = shaftTurn(shaft, ROTATE_HANDLE_GAP_PT)
        assertEquals(
            SelectionHandle.ROTATE,
            selectionHandleAt(line, turn.ringX, turn.ringY, tolPt = 4.0, shaft = shaft),
        )
    }

    @Test
    fun `a figure's corner is free while an image's keeps its proportions`() {
        assertFalse("a figure's corner stretches freely", cornerLocksAspect(null, shift = false))
        assertTrue("shift locks the corner", cornerLocksAspect(null, shift = true))
        val image = ImageElement(0.0, 0.0, 100.0, 80.0, ByteArray(0))
        assertTrue("an image is never reshaped", cornerLocksAspect(image, shift = false))
    }
}
