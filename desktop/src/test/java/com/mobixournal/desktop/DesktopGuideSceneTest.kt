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
import com.mobixournal.format.model.Stroke
import com.mobixournal.render.DrawingGuide
import com.mobixournal.render.GuideKind
import java.io.File
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The guide's drag through a **real** window: a press on a placed instrument and the pointer events that
 * follow, into `PageCanvas`.
 *
 * The arithmetic is `DesktopGuideGestureTest`'s; what this proves is the wiring: the press is answered
 * before the ink router, the tab's pose moves by exactly the drag, Shift snaps the aim, a pen stroke is
 * never stolen from the page, and the whole gesture writes **no** page edit and no undo step.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopGuideSceneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 595
    private val viewH = 700

    private fun notebook(): File = temp.newFile("guides.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
    }

    /**
     * A tab whose page carries a placed instrument, and a canvas showing it at 1:1.
     *
     * The fresh setsquare is anchored at (110, 245) — its right-angle corner: the long leg runs along
     * y = 245 to the handle at (290, 245), and the body reaches down to (110, 349), so (150, 300) is inside.
     */
    private fun desktop(tool: DesktopTool, kind: GuideKind = GuideKind.SETSQUARE): Pair<DesktopTab, ImageComposeScene> {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.guide = placedGuideOn(kind, page = 0, centre = 200.0 to 200.0)
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = tool,
                ink = DesktopInk(width = 4f),
                onViewport = {},
                onFit = {},
            )
        }
        return tab to scene
    }

    @Test
    fun `a mouse drag on the body slides the instrument and writes no edit`() {
        val (tab, scene) = desktop(DesktopTool.SETSQUARE)
        try {
            val before = tab.guide!!
            val elementsBefore = tab.document.page(0).layers.sumOf { it.elements.size }
            scene.render()
            Snapshot.sendApplyNotifications()

            drag(scene, fromX = 150f, fromY = 300f, toX = 190f, toY = 340f, type = PointerType.Mouse)

            val after = tab.guide!!
            assertEquals("the same instrument, slid", before.page, after.page)
            assertEquals(before.guide.x + 40.0, after.guide.x, 1e-6)
            assertEquals(before.guide.y + 40.0, after.guide.y, 1e-6)
            assertEquals(
                "a guide is view state: the page is untouched",
                elementsBefore,
                tab.document.page(0).layers.sumOf { it.elements.size },
            )
            assertFalse("and a pose is not an undo step", tab.canUndo)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a tip drag with Shift held aims the setsquare at 45 degrees`() {
        val (tab, scene) = desktop(DesktopTool.SETSQUARE)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()
            // The handle is the long-leg tip; 135 pt right and 135 pt up from the anchor is -45 degrees.
            drag(
                scene, fromX = 290f, fromY = 245f, toX = 245f, toY = 110f,
                type = PointerType.Mouse, shift = true,
            )
            val posed = tab.guide!!.guide as DrawingGuide.Setsquare
            assertEquals(-PI / 4, posed.angle, 1e-6)
            assertEquals(kotlin.math.hypot(135.0, 135.0), posed.size, 1e-6)
            assertFalse(tab.canUndo)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a tip drag opens the compass to the drag distance`() {
        val (tab, scene) = desktop(DesktopTool.COMPASS, GuideKind.COMPASS)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()
            // The compass stands at (200, 200) opened to 100 pt: its handle is the pencil point at (300, 200).
            drag(scene, fromX = 300f, fromY = 200f, toX = 200f, toY = 260f, type = PointerType.Mouse)
            assertEquals(60.0, (tab.guide!!.guide as DrawingGuide.Compass).radius, 1e-6)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a pen stroke is not stolen from the page while the pen is in hand`() {
        val (tab, scene) = desktop(DesktopTool.PEN)
        try {
            val before = tab.guide!!
            scene.render()
            Snapshot.sendApplyNotifications()

            // A stylus press on the instrument's body: the pen draws, so the instrument must not move.
            drag(scene, fromX = 150f, fromY = 300f, toX = 190f, toY = 340f, type = PointerType.Stylus)

            assertEquals("the guide the pen must not drag", before.guide.x, tab.guide!!.guide.x, 1e-6)
            assertEquals(before.guide.y, tab.guide!!.guide.y, 1e-6)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `the guides slot lets the stylus pose the instrument`() {
        val (tab, scene) = desktop(DesktopTool.SETSQUARE)
        try {
            val before = tab.guide!!
            scene.render()
            Snapshot.sendApplyNotifications()

            drag(scene, fromX = 150f, fromY = 300f, toX = 190f, toY = 340f, type = PointerType.Stylus)

            assertNotNull(tab.guide)
            assertEquals("from the guides slot the pen holds the instrument", before.guide.x + 40.0, tab.guide!!.guide.x, 1e-6)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a stroke drawn beside the setsquare is written along its edge`() {
        val (tab, scene) = desktop(DesktopTool.PEN)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()

            // A wobble 3-7 pt below the long leg (y = 245), drawn with the pen: the guide pulls every
            // vertex onto the edge, and the stroke that lands is ordinary ink of the tool's width.
            stroke(scene, listOf(140f to 250f, 175f to 248f, 205f to 252f, 235f to 249f))

            val strokes = tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()
            val written = strokes.lastOrNull() ?: error("the stroke never reached the page")
            assertEquals("the stroke really has the drag's vertices", 4, written.points.size)
            for (point in written.points) {
                assertEquals("every vertex sits on the guide's edge", 245.0, point.y, 1e-6)
            }
            assertTrue("the stroke is one undo step", tab.canUndo)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a stroke on the far side of the page is written exactly as drawn`() {
        val (tab, scene) = desktop(DesktopTool.PEN)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()
            stroke(scene, listOf(60f to 500f, 100f to 505f, 140f to 502f))
            val written = tab.document.page(0).layers.flatMap { it.elements }
                .filterIsInstance<Stroke>().lastOrNull() ?: error("the stroke never reached the page")
            val ys = written.points.map { it.y }
            assertTrue("the hand's own wobble survived", ys.toSet().size > 1)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /** A pen stroke through [points]: press, move past the tap slop, then a move at each point, then lift. */
    private fun stroke(scene: ImageComposeScene, points: List<Pair<Float, Float>>) {
        val (startX, startY) = points.first()
        send(scene, PointerEventType.Press, startX, startY, 1_000L, PointerType.Stylus, pressed = true, shift = false)
        points.drop(1).forEachIndexed { index, (x, y) ->
            send(
                scene,
                PointerEventType.Move,
                x,
                y,
                1_016L + index * 16L,
                PointerType.Stylus,
                pressed = true,
                shift = false,
            )
        }
        val (endX, endY) = points.last()
        send(scene, PointerEventType.Release, endX, endY, 1_100L, PointerType.Stylus, pressed = false, shift = false)
    }

    /** A press, two moves and a release — one gesture, in the pointer type the test is about. */
    private fun drag(
        scene: ImageComposeScene,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        type: PointerType,
        shift: Boolean = false,
    ) {
        send(scene, PointerEventType.Press, fromX, fromY, 1_000L, type, pressed = true, shift = shift)
        send(scene, PointerEventType.Move, (fromX + toX) / 2f, (fromY + toY) / 2f, 1_016L, type, pressed = true, shift = shift)
        send(scene, PointerEventType.Move, toX, toY, 1_032L, type, pressed = true, shift = shift)
        send(scene, PointerEventType.Release, toX, toY, 1_048L, type, pressed = false, shift = shift)
    }

    private fun send(
        scene: ImageComposeScene,
        eventType: PointerEventType,
        x: Float,
        y: Float,
        time: Long,
        type: PointerType,
        pressed: Boolean,
        shift: Boolean,
    ) {
        scene.sendPointerEvent(
            eventType = eventType,
            pointers = listOf(
                ComposeScenePointer(
                    id = PointerId(1),
                    position = Offset(x, y),
                    pressed = pressed,
                    type = type,
                    pressure = 1f,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            keyboardModifiers = if (shift) PointerKeyboardModifiers(isShiftPressed = true) else PointerKeyboardModifiers(),
            timeMillis = time,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}
