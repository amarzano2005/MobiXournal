package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
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
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.render.InkPointer
import com.mobixournal.render.InkPointerPhase
import com.mobixournal.render.PointerKind
import com.mobixournal.render.ShapeDwell
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Hold-to-perfect, in the window: a tip that rests for [ShapeDwell.MS] replaces the preview with the
 * recognised figure, and the lift writes that figure — one element and one undo step.
 *
 * Two levels, because the two halves fail differently. The gesture test drives [PageInkGesture]
 * directly and looks **at the draft the sheet paints**, which is the only place the preview exists: it
 * is what proves the dwell is a preview (the ink there is the figure's corners, not the wobble) and
 * that a stroke which grows afterwards gets its own samples back. The scene test pushes **real Compose
 * pointer events** through an [ImageComposeScene], because whether a rest followed by a lift reaches
 * the gesture at all is a property of the whole canvas, not of the gesture's arithmetic.
 *
 * The rest is measured on a wall clock by the gesture (`ShapeDwell.MS_NANOS`), so the gesture test
 * hands [PageInkGesture.dwellCheck] a timestamp just past the threshold instead of sleeping, and the
 * scene test — which cannot choose the canvas's clock — really waits.
 */
class DesktopDwellTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(): File = temp.newFile("dwell.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
    }

    private data class Fixture(
        val tab: DesktopTab,
        val gesture: PageInkGesture,
        val draft: SnapshotStateList<InkSample>,
    )

    private fun fixture(): Fixture {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val page = tab.document.page(0)
        val draft = mutableStateListOf<InkSample>()
        val gesture = PageInkGesture(
            tab = tab,
            tool = DesktopTool.PEN,
            ink = DesktopInk(width = 4f),
            view = { tab.viewport },
            pageW = page.width.toFloat(),
            pageH = page.height.toFloat(),
            density = 1f,
            draft = draft,
            onEdited = {},
            // The tablet's rule: a press is a pen on paper, so the stroke opens on the down.
            startOnDown = true,
        )
        return Fixture(tab, gesture, draft)
    }

    private fun pointer(x: Float, y: Float, phase: InkPointerPhase, timeMs: Long) = InkPointer(
        id = 1,
        x = x,
        y = y,
        pressure = 1f,
        timeMs = timeMs,
        tool = PointerKind.STYLUS,
        phase = phase,
        buttons = InkPointer.PRIMARY,
    )

    /** A hand-drawn square, 20 samples a side, as page points (the viewport is pinned at 1:1). */
    private fun drawSquare(gesture: PageInkGesture): Long {
        val corners = listOf(100f to 100f, 300f to 100f, 300f to 250f, 100f to 250f, 100f to 100f)
        var time = 1_000L
        gesture.down(pointer(corners[0].first, corners[0].second, InkPointerPhase.DOWN, time))
        for (edge in 1 until corners.size) {
            val (x0, y0) = corners[edge - 1]
            val (x1, y1) = corners[edge]
            for (step in 1..20) {
                time += 8
                val t = step / 20f
                gesture.move(pointer(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, InkPointerPhase.MOVE, time))
            }
        }
        return time
    }

    private fun elements(tab: DesktopTab) = tab.document.page(0).layers.flatMap { it.elements }

    /**
     * The preview is the figure, and the lift keeps it as a **living shape**: four corners on the sheet
     * during the hold, one `ParametricShapeElement` after it, and one undo step for the whole stroke.
     */
    @Test
    fun aRestingSquarePreviewsTheFigureAndCommitsOneShape() {
        val (tab, gesture, draft) = fixture()
        try {
            val strokesBefore = elements(tab).filterIsInstance<Stroke>().size
            val time = drawSquare(gesture)
            val wobble = draft.toList()
            // Not yet: the dwell has not been waited out.
            gesture.dwellCheck()
            assertEquals("the stroke is still the hand's", wobble, draft.toList())

            gesture.dwellCheck(System.nanoTime() + ShapeDwell.MS_NANOS + 1_000_000L)
            assertEquals("the preview is the figure: four corners and the closing repeat", 5, draft.size)
            assertEquals("and it is a rectangle, not the wobble", 2, draft.map { it.x.toInt() }.distinct().size)

            gesture.up(pointer(100f, 100f, InkPointerPhase.UP, time + 8))

            val shapes = elements(tab).filterIsInstance<ParametricShapeElement>()
            assertEquals("the lift wrote one living shape", 1, shapes.size)
            assertEquals(4, shapes[0].vertices.size)
            assertEquals(
                "and no freehand stroke beside it",
                strokesBefore,
                elements(tab).filterIsInstance<Stroke>().size,
            )
            assertTrue("one stroke, one undo step", tab.canUndo)
        } finally {
            tab.document.close()
        }
    }

    /**
     * A hand that carries on drawing after the snap gets its **own ink** back: the figure was a preview
     * of a stroke that no longer exists, so the samples come back and the lift writes the path the hand
     * actually drew — no shape, no cleaned corners.
     */
    @Test
    fun aStrokeThatGrowsAfterTheSnapGetsItsOwnInkBack() {
        val (tab, gesture, draft) = fixture()
        try {
            val time = drawSquare(gesture)
            val drawn = draft.size
            gesture.dwellCheck(System.nanoTime() + ShapeDwell.MS_NANOS + 1_000_000L)
            assertEquals("the dwell snapped the preview", 5, draft.size)

            gesture.move(pointer(150f, 320f, InkPointerPhase.MOVE, time + 8))
            assertEquals("the drawn samples are back, plus the new one", drawn + 1, draft.size)
            gesture.up(pointer(150f, 320f, InkPointerPhase.UP, time + 16))

            assertTrue(
                "the lift wrote the hand's stroke, not the figure",
                elements(tab).filterIsInstance<ParametricShapeElement>().isEmpty(),
            )
            val stroke = elements(tab).filterIsInstance<Stroke>().last()
            assertEquals("every sample is on the page, wobble included", drawn + 1, stroke.points.size)
        } finally {
            tab.document.close()
        }
    }

    /**
     * The case a real tablet makes, and the one a test without packets would miss: Wintab **keeps
     * sending packets** while the pen rests on the glass, at its own rate and with the same position.
     *
     * Stamping the dwell's clock on every sample made the rest impossible to reach — the age was always
     * a few milliseconds — so the window never snapped a held stroke, which is exactly what a hand saw.
     * A repeat inside the slop must leave the clock (and a preview it has already produced) alone.
     */
    @Test
    fun aDriverThatKeepsReportingWhileThePenRestsStillSnaps() {
        val (tab, gesture, draft) = fixture()
        try {
            drawSquare(gesture)
            Thread.sleep(ShapeDwell.MS + 60)
            // The pen does not move; the driver keeps talking about it.
            repeat(20) { i ->
                gesture.move(pointer(100f, 100f, InkPointerPhase.MOVE, 9_000L + i))
            }
            gesture.dwellCheck()
            assertEquals("the rest held under a repeating driver", 5, draft.size)
            assertEquals("and it is the figure's own corners", 2, draft.map { it.x.toInt() }.distinct().size)
        } finally {
            tab.document.close()
        }
    }

    /**
     * The whole canvas, end to end: a pen that rests on the glass and then lifts — the case where no
     * sample follows the rest — is recognised on the way out, so the window needs no timer to be
     * correct, only to *show* the figure while the pen is still down.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun aRestThenLiftThroughTheSceneWritesTheFigure() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val scene = ImageComposeScene(500, 700, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, onViewport = {}, onFit = {}, ink = DesktopInk(width = 4f))
        }
        try {
            val corners = listOf(100f to 100f, 300f to 100f, 300f to 250f, 100f to 250f, 100f to 100f)
            var time = 1_000L
            pen(scene, PointerEventType.Press, corners[0].first, corners[0].second, time = time)
            for (edge in 1 until corners.size) {
                val (x0, y0) = corners[edge - 1]
                val (x1, y1) = corners[edge]
                for (step in 1..20) {
                    time += 8
                    val t = step / 20f
                    pen(
                        scene,
                        PointerEventType.Move,
                        x0 + (x1 - x0) * t,
                        y0 + (y1 - y0) * t,
                        time = time,
                    )
                }
            }
            // The canvas cannot be told to jump its clock, so the rest is real: a hand at rest.
            Thread.sleep(ShapeDwell.MS + 80)
            pen(scene, PointerEventType.Release, 100f, 100f, time = time + 400, pressed = false)

            val shapes = tab.document.page(0).layers.flatMap { it.elements }
                .filterIsInstance<ParametricShapeElement>()
            assertEquals("a rest followed by a lift writes the figure", 1, shapes.size)
            assertEquals("a rectangle, four draggable corners", 4, shapes[0].vertices.size)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /** One stylus event as the scene's own pointer, positioned in window pixels. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun pen(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        time: Long,
        pressure: Float = 0.6f,
        pressed: Boolean = true,
    ) {
        val pointers = listOf(
            ComposeScenePointer(
                id = PointerId(1),
                position = Offset(x, y),
                pressed = pressed,
                type = PointerType.Stylus,
                pressure = pressure,
            ),
        )
        // The canvas only inks while the primary button is down (a pen's tip), and a release reports none.
        val buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons()
        scene.sendPointerEvent(
            eventType = type,
            pointers = pointers,
            buttons = buttons,
            keyboardModifiers = PointerKeyboardModifiers(),
            timeMillis = time,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}
