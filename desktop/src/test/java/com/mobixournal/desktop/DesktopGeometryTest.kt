package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.EraserMode
import com.mobixournal.render.InkPointer
import com.mobixournal.render.InkPointerRouter
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.TriangleKind
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

/**
 * The window's geometry slots, its second eraser and its vertical-space tool — the three tools that turn
 * a drag into something other than a freehand stroke.
 *
 * Two layers of proof, because the tools have two halves: the pure mapping ([shapeFromDrag],
 * [DesktopTool.shapeKind], [DesktopTool.eraserMode]) is asserted without a window, and the gestures are
 * driven through a real headless [ImageComposeScene] into [PageCanvas], where what the model receives is
 * what a save would write. A shape is an ordinary stroke, so the interesting claims are about the
 * **stroke**: one element, a constant width, one undo step, and nothing at all when the gesture was
 * cancelled.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopGeometryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 595
    private val viewH = 700

    private fun notebook(name: String = "geometry.mobix"): File = temp.newFile(name).also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(3))
    }

    private fun strokes(page: Page): List<Stroke> = page.layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    /** The window's page at 1:1 with no fit, so a pointer pixel is a page point. */
    private fun canvas(tab: DesktopTab, tool: DesktopTool, onLiveDrag: ((() -> Unit)?) -> Unit = {}): ImageComposeScene =
        ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = tool,
                ink = DesktopInk(width = 4f),
                onLiveDrag = onLiveDrag,
                onViewport = {},
                onFit = {},
            )
        }

    private fun pen(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        time: Long,
        pressed: Boolean = true,
        shift: Boolean = false,
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
            keyboardModifiers = PointerKeyboardModifiers(isShiftPressed = shift),
            timeMillis = time,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }

    /**
     * A figure is drawn at the width **the strips are set to**, end to end: the width control writes the
     * ink the figure reads, and the stroke that lands carries it on every point.
     *
     * The two inks are per tool on the tablet, and a figure is not the highlighter: it writes with the
     * pen's ink whatever is in hand, which is exactly why the chain is worth a test — a rectangle drawn
     * while the marker is picked must be the pen's colour **and** the pen's width, not the marker's band.
     */
    @Test
    fun `a figure lands at the width the window is set to`() {
        val state = DesktopShellState().also { it.open(notebook("figure-width.mobix")) }
        try {
            state.selectTool(DesktopTool.RECTANGLE)
            state.pickWidth(6f)
            // A figure has its own width (the Figures section): picking a pen width does not change it,
            // which is what lets a rectangle match a light pen stroke instead of the pen's slot.
            assertEquals("the strip writes the pen's width", 6f, state.ink.width, 1e-6f)
            state.prefs.figures.chooseWidth(6f)
            assertEquals("and the figure reads its own", 6f, state.shapeInk.width, 1e-6f)

            val points = shapeFromDrag(
                ShapeKind.RECTANGLE, 10.0, 10.0, 60.0, 40.0, state.shapeInk.width, constrained = false,
            )
            val stroke = shapeStroke(points, state.shapeInk.colour, state.shapeInk.width, state.shapeInk.lineStyle)
            requireNotNull(stroke)
            assertTrue("every corner carries the set width", stroke.points.all { it.width == 6.0 })

            // The marker's own band is not the figure's width, even with the highlighter in hand.
            state.selectTool(DesktopTool.HIGHLIGHTER)
            assertEquals(
                "a figure still writes with the pen, not the marker's band",
                6f,
                state.shapeInk.width,
                1e-6f,
            )
        } finally {
            state.tabs.closeAll()
        }
    }

    // ---------------------------------------------------------------- the pure mapping

    @Test
    fun `every geometry slot names a shape and no ink slot does`() {
        val expected = mapOf(
            DesktopTool.LINE to ShapeKind.LINE,
            DesktopTool.ARROW to ShapeKind.ARROW,
            DesktopTool.DOUBLE_ARROW to ShapeKind.DOUBLE_ARROW,
            DesktopTool.AXES to ShapeKind.COORDINATE_AXIS,
            DesktopTool.RECTANGLE to ShapeKind.RECTANGLE,
            DesktopTool.ELLIPSE to ShapeKind.ELLIPSE,
            DesktopTool.TRIANGLE to ShapeKind.TRIANGLE,
            DesktopTool.RHOMBUS to ShapeKind.RHOMBUS,
            DesktopTool.TRAPEZOID to ShapeKind.TRAPEZOID,
            DesktopTool.POLYGON to ShapeKind.POLYGON,
        )
        for ((tool, kind) in expected) {
            assertEquals("$tool draws $kind", kind, tool.shapeKind())
            assertEquals("and previews with the pen", Tool.PEN, tool.previewTool())
        }
        for (tool in DesktopTool.entries - expected.keys) {
            assertNull("$tool is not a shape", tool.shapeKind())
        }
        assertEquals("the pen previews with its own tool", Tool.PEN, DesktopTool.PEN.previewTool())
        assertEquals(Tool.HIGHLIGHTER, DesktopTool.HIGHLIGHTER.previewTool())
        assertNull("the eraser previews no stroke", DesktopTool.ERASER.previewTool())
    }

    @Test
    fun `a figure's variant comes from FigureShape, not from the drag`() {
        // A box drag, not a flat one: a zero-height triangle is the builder's line, whatever its kind.
        val drag = { shape: FigureShape ->
            shapeFromDrag(ShapeKind.TRIANGLE, 0.0, 0.0, 100.0, 80.0, 2f, constrained = false, figures = shape)
        }
        assertNotEquals(
            "an equilateral and a right-angled triangle are different figures",
            drag(FigureShape(triangleKind = TriangleKind.EQUILATERAL)),
            drag(FigureShape(triangleKind = TriangleKind.RIGHT)),
        )
        // A regular polygon takes no advice from the settings: its side count is its own, and it closes.
        val polygon = shapeFromDrag(ShapeKind.POLYGON, 0.0, 0.0, 100.0, 100.0, 2f, constrained = false)
        assertEquals("six sides plus the closing point at the factory count", 7, polygon.size)
        assertEquals(polygon.first().x, polygon.last().x, 1e-9)
        assertEquals(polygon.first().y, polygon.last().y, 1e-9)
    }

    @Test
    fun `a shape is the builder's answer for the drag, not a second geometry`() {
        val line = shapeFromDrag(ShapeKind.LINE, 10.0, 20.0, 110.0, 120.0, 4f, constrained = false)
        assertEquals("a line is two points", 2, line.size)
        assertEquals(10.0, line.first().x, 1e-9)
        assertEquals(110.0, line.last().x, 1e-9)
        assertEquals("and the tool's width", 4.0, line.first().width, 1e-9)

        val rectangle = shapeFromDrag(ShapeKind.RECTANGLE, 10.0, 10.0, 110.0, 60.0, 2f, constrained = false)
        assertEquals("a rectangle is closed on four corners", 5, rectangle.size)
        assertEquals("the last vertex returns to the first", rectangle.first(), rectangle.last())

        val ellipse = shapeFromDrag(ShapeKind.ELLIPSE, 0.0, 0.0, 200.0, 100.0, 2f, constrained = false)
        assertTrue("an ellipse is a sampled closed curve", ellipse.size in 25..97)
        assertEquals("closing on its first vertex", ellipse.first().x, ellipse.last().x, 1e-9)
        assertEquals(ellipse.first().y, ellipse.last().y, 1e-9)

        val arrow = shapeFromDrag(ShapeKind.ARROW, 0.0, 0.0, 100.0, 0.0, 2f, constrained = false)
        assertTrue("an arrow is a shaft plus a head", arrow.size >= 3)
        assertEquals(
            "the same points the shared builder makes",
            ShapeBuilder.build(ShapeKind.ARROW, 0.0, 0.0, 100.0, 0.0, 2.0),
            arrow,
        )
    }

    @Test
    fun `shift snaps the drag's direction to 45 degrees and keeps its length`() {
        // 100 right and 13 down is nearly horizontal: Shift makes it exactly horizontal.
        val snapped = shapeFromDrag(ShapeKind.LINE, 0.0, 0.0, 100.0, 13.0, 2f, constrained = true)
        assertEquals("the line is horizontal", 0.0, snapped.last().y, 1e-6)
        assertEquals(
            "and keeps the drag's length",
            kotlin.math.hypot(100.0, 13.0),
            snapped.last().x,
            1e-6,
        )

        // A diagonal drag stays diagonal, and a mostly vertical one becomes vertical (pointing the way it
        // was dragged).
        val diagonal = shapeFromDrag(ShapeKind.LINE, 0.0, 0.0, 50.0, 50.0, 2f, constrained = true)
        assertEquals(50.0, diagonal.last().x, 1e-3)
        assertEquals(50.0, diagonal.last().y, 1e-3)
        val vertical = shapeFromDrag(ShapeKind.LINE, 0.0, 0.0, 3.0, -80.0, 2f, constrained = true)
        assertEquals("straight up", 0.0, vertical.last().x, 1e-6)
        assertTrue("and it points up", vertical.last().y < 0.0)
    }

    @Test
    fun `a click with a shape tool draws nothing`() {
        assertTrue(shapeFromDrag(ShapeKind.LINE, 10.0, 10.0, 11.0, 11.0, 2f, false).isEmpty())
        assertTrue(shapeFromDrag(ShapeKind.RECTANGLE, 10.0, 10.0, 12.0, 10.0, 2f, false).isEmpty())
    }

    @Test
    fun `the two erasers differ by their mode, not by their implementation`() {
        assertEquals(EraserMode.STANDARD, DesktopTool.ERASER.eraserMode())
        assertEquals(EraserMode.WHOLE_STROKE, DesktopTool.ERASER_WHOLE.eraserMode())
        for (tool in DesktopTool.entries - setOf(DesktopTool.ERASER, DesktopTool.ERASER_WHOLE)) {
            assertEquals("$tool is the standard mode", EraserMode.STANDARD, tool.eraserMode())
        }
        assertEquals("both erasers are the same tool to the router", DesktopTool.ERASER.activeTool(), DesktopTool.ERASER_WHOLE.activeTool())
        assertNotNull("and the whole eraser shows a tip", toolCursorDiameterPx(DesktopTool.ERASER_WHOLE, 4f, 1f, 1f))
    }

    // ---------------------------------------------------------------- through the scene

    @Test
    fun `a line drag lands one uniform stroke and one undo step`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val scene = canvas(tab, DesktopTool.LINE)
        try {
            val before = strokes(tab.document.page(0)).size
            pen(scene, PointerEventType.Press, 100f, 100f, 1_000L)
            pen(scene, PointerEventType.Move, 200f, 180f, 1_016L)
            assertEquals("the preview is not a page edit yet", before, strokes(tab.document.page(0)).size)
            pen(scene, PointerEventType.Release, 300f, 260f, 1_032L, pressed = false)

            val page = tab.document.page(0)
            assertEquals("the lift committed one stroke", before + 1, strokes(page).size)
            val line = strokes(page).last()
            assertEquals("a line is two points", 2, line.points.size)
            assertEquals(100.0, line.points[0].x, 0.5)
            assertEquals(300.0, line.points[1].x, 0.5)
            assertEquals(260.0, line.points[1].y, 0.5)
            assertTrue("a figure is drawn at one width", line.uniformWidth)
            assertEquals(4.0, line.points[0].width, 1e-5)
            assertEquals(Tool.PEN, line.tool)
            assertTrue("the figure is one undo step", tab.canUndo)
            assertEquals("the page window recorded one edit", 1, tab.document.edits)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a rectangle drag lands a closed four-corner stroke`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val scene = canvas(tab, DesktopTool.RECTANGLE)
        try {
            val before = strokes(tab.document.page(0)).size
            pen(scene, PointerEventType.Press, 40f, 40f, 2_000L)
            pen(scene, PointerEventType.Move, 240f, 140f, 2_016L)
            pen(scene, PointerEventType.Release, 240f, 140f, 2_032L, pressed = false)

            assertEquals(before + 1, strokes(tab.document.page(0)).size)
            val box = strokes(tab.document.page(0)).last()
            assertEquals("four corners and back to the start", 5, box.points.size)
            assertEquals(box.points.first().x, box.points.last().x, 1e-9)
            assertEquals(box.points.first().y, box.points.last().y, 1e-9)
            assertEquals("the drag's box", 40.0, box.points[0].x, 0.5)
            assertEquals(240.0, box.points[1].x, 0.5)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `escape cancels the figure and leaves the page untouched`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        var live: (() -> Unit)? = null
        val scene = canvas(tab, DesktopTool.LINE, onLiveDrag = { live = it })
        try {
            val before = strokes(tab.document.page(0)).size
            pen(scene, PointerEventType.Press, 60f, 60f, 3_000L)
            pen(scene, PointerEventType.Move, 200f, 200f, 3_016L)
            assertNotNull("the drag is offered to the shell while it runs", live)

            live!!.invoke()
            pen(scene, PointerEventType.Release, 200f, 200f, 3_032L, pressed = false)

            assertEquals("a cancelled drag writes nothing", before, strokes(tab.document.page(0)).size)
            assertFalse("and records no undo step", tab.canUndo)
            assertNull("the drag is over", live)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `the shell answers escape by cancelling the drag in flight`() {
        val state = DesktopShellState(DesktopSettings(temp.newFile("escape-settings.properties")))
        try {
            state.open(notebook("escape.mobix"))
            var cancelled = false
            state.liveDrag = { cancelled = true }
            assertTrue("Esc is handled while a drag runs", state.apply(ShellCommand.DISMISS_FAILURE))
            assertTrue("and it cancelled the drag", cancelled)
            assertNull("the drag is no longer live", state.liveDrag)
            assertFalse("with nothing live, Esc is not handled", state.apply(ShellCommand.DISMISS_FAILURE))
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a whole-stroke eraser removes the bar the partial one would cut`() {
        for ((tool, mode) in listOf(DesktopTool.ERASER to EraserMode.STANDARD, DesktopTool.ERASER_WHOLE to EraserMode.WHOLE_STROKE)) {
            val tab = DesktopTabs().open(DocumentLoader.load(notebook("erase-$mode.mobix")))
            try {
                tab.document.editPage(0) { page -> page.withElement(bar()) }
                val before = strokes(tab.document.page(0)).size
                val draft = mutableStateListOfOf()
                val gesture = PageInkGesture(
                    tab = tab,
                    tool = tool,
                    ink = DesktopInk(width = 4f),
                    view = { Viewport(zoom = 1f) },
                    pageW = SyntheticNotebook.WIDTH_PT.toFloat(),
                    pageH = SyntheticNotebook.HEIGHT_PT.toFloat(),
                    density = 1f,
                    draft = draft,
                    onEdited = {},
                )
                gesture.down(change(250f, 200f, pressed = true, time = 4_000L), InkPointer.PRIMARY)
                gesture.move(change(260f, 200f, pressed = true, time = 4_016L), InkPointer.PRIMARY)
                gesture.up(change(260f, 200f, pressed = false, time = 4_032L), 0)

                val after = strokes(tab.document.page(0)).size
                when (mode) {
                    EraserMode.STANDARD -> assertEquals(
                        "the partial eraser cuts the bar in two, beside the fixture's stroke",
                        before + 1,
                        after,
                    )
                    EraserMode.WHOLE_STROKE -> assertEquals(
                        "the whole-stroke eraser takes the bar and leaves the fixture's stroke",
                        before - 1,
                        after,
                    )
                }
                assertTrue("one gesture is one undo step", tab.canUndo)
                assertTrue("and undo brings the bar back", tab.undo())
                assertEquals(before, strokes(tab.document.page(0)).size)
            } finally {
                tab.document.close()
            }
        }
    }

    /** A wide horizontal bar across the middle of the page, at the eraser's height. */
    private fun bar(): Stroke = Stroke(
        points = listOf(StrokePoint(60.0, 200.0, 4.0), StrokePoint(500.0, 200.0, 4.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = true,
    )

    private fun mutableStateListOfOf() = androidx.compose.runtime.mutableStateListOf<InkSample>()

    /** One pointer sample as Compose reports it, for the gesture-level test above. */
    private fun change(x: Float, y: Float, pressed: Boolean, time: Long) =
        androidx.compose.ui.input.pointer.PointerInputChange(
            id = PointerId(1),
            uptimeMillis = time,
            position = Offset(x, y),
            pressed = pressed,
            previousUptimeMillis = time - 1,
            previousPosition = Offset(x, y),
            previousPressed = pressed,
            isInitiallyConsumed = false,
            type = PointerType.Stylus,
            scrollDelta = Offset.Zero,
        )
}
