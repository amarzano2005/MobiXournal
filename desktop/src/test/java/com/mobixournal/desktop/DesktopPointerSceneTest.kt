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
import com.mobixournal.format.model.Tool
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's pen, end to end: **real Compose pointer events** pushed through a headless
 * [ImageComposeScene] into the page canvas, and the stroke that comes out of the model.
 *
 * The pure translation (`PageInkGesture` fed by hand-built `PointerInputChange`s) is covered in
 * [DesktopEditingTest]; this suite is the half that only a scene can prove — that Compose's own
 * dispatch reaches the canvas' gesture handler at all, that the pressure it carries survives the trip,
 * and that the width the file gets is the shared filter's answer rather than the tool's width for
 * every sample. It is the desktop counterpart of what `StylusInputTest` proves on the device.
 *
 * Since Compose 1.7 the scene takes a list of `ComposeScenePointer`s — which carry a pressure — so a
 * ramp can be injected without a tablet; the single-position overload cannot, which is why this test
 * does not use it.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopPointerSceneTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A view **narrower** than the page, so the page is not centred and pointer = page point. */
    private val viewW = 500

    private val viewH = 700

    private fun notebook(): File = temp.newFile("desktop-pointer.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(4))
    }

    private fun strokes(document: DesktopDocument) =
        document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    /**
     * The pen drag the whole desktop editing path has to survive: press, two moves with rising
     * pressure, release — and one stroke on the page whose vertices are the press, the sample that
     * crossed the tap slop and the last sample, at the widths the shared pressure filter gives them.
     */
    @Test
    fun aPenDragThroughTheSceneInksThePageAtTheFilteredPressure() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        // Pinned rather than fitted: a centred page would move the pointer-to-page mapping under the
        // assertion, and the point of this test is the input path, not the fit.
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, onViewport = {}, onFit = {}, ink = DesktopInk(width = 4f))
        }
        try {
            val before = strokes(tab.document).size
            assertEquals("the fixture's page holds one stroke", 1, before)

            pen(scene, PointerEventType.Press, 100f, 100f, pressure = 0.2f, time = 1_000L)
            pen(scene, PointerEventType.Move, 104f, 100f, pressure = 0.2f, time = 1_016L)
            assertTrue("a wobble inside the tap slop is not ink", strokes(tab.document).size == before)

            pen(scene, PointerEventType.Move, 160f, 120f, pressure = 0.5f, time = 1_032L)
            pen(scene, PointerEventType.Move, 260f, 160f, pressure = 1.0f, time = 1_048L)
            assertTrue("nothing is on the page until the lift", strokes(tab.document).size == before)

            pen(scene, PointerEventType.Release, 260f, 160f, pressure = 0f, time = 1_064L, pressed = false)

            val drawn: Stroke? = strokes(tab.document).takeIf { it.size == before + 1 }?.last()
            assertNotNull("the lift committed one stroke to the model", drawn)
            drawn!!
            assertEquals("the press, the slop crossing and the last sample", 3, drawn.points.size)
            assertEquals("the stroke is page points, not window pixels", 100.0, drawn.points[0].x, 0.5)
            assertEquals(260.0, drawn.points[2].x, 0.5)
            // max(minimumPressure, pressure × multiplier) × the tool's width — desktop Xournal++'s
            // filter, the one `PressureCurve` implements for both shells.
            assertEquals("a light press draws light", 4.0 * 0.2f, drawn.points[0].width, 1e-5)
            assertEquals("and a full press draws at the tool's width", 4.0, drawn.points[2].width, 1e-5)
            assertFalse("the widths differ, so the stroke is a taper", drawn.uniformWidth)
            assertEquals(Tool.PEN, drawn.tool)
            assertTrue("the lift recorded one undo step", tab.canUndo)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * A press and release that never moves is a click, and on the desktop a click selects — it must not
     * leave a dot. That is the one thing `startOnDown = false` buys the window (`docs/stylus.md`), and it
     * is asserted through the scene because it is a property of the whole gesture, not of the router.
     */
    @Test
    fun aTapInTheWindowSelectsAndLeavesNoDot() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, onViewport = {}, onFit = {}, ink = DesktopInk(width = 4f))
        }
        try {
            pen(scene, PointerEventType.Press, 120f, 120f, pressure = 0.5f, time = 1_000L)
            pen(scene, PointerEventType.Release, 120f, 120f, pressure = 0f, time = 1_060L, pressed = false)

            assertEquals("a tap drew nothing", 1, strokes(tab.document).size)
            assertFalse("a tap is not an edit", tab.canUndo)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /** One stylus event as the scene's own pointer, positioned in window pixels. */
    private fun pen(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        pressure: Float,
        time: Long,
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
