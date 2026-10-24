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
import com.mobixournal.format.model.PlotElement
import com.mobixournal.render.Bounds
import com.mobixournal.render.LivingSlot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The canvas's **edge scroll** as a gesture: a drag resting against the border keeps travelling, and the
 * gesture is not cut short by the scroll it asked for.
 *
 * Both halves of the claim are here. The arithmetic is `EdgePanTest`'s; this drives the real pointer path
 * through a scene, because two things can only go wrong there — a tick that never comes (`awaitPointerEvent`
 * asked to time out, and the drag waiting on it forever) and a pointer handler whose key changes with the
 * viewport, which would cancel the very gesture that moved the sheet.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopEdgePanTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 1000
    private val viewH = 700

    private fun notebook(): File = temp.newFile("edge.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    /**
     * A zoomed page wider than the window, so there is somewhere to scroll to: at 200 % an A4 sheet is
     * ~1 200 pt and the canvas shows 1 000 px of it.
     */
    private fun state(): DesktopShellState = DesktopShellState().also { state ->
        state.open(notebook())
        val tab = state.tabs.active!!
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 2f, offsetX = 0f, offsetY = 0f)
        state.selectTool(DesktopTool.MARQUEE)
    }

    @Test
    fun `a marquee held against the edge scrolls the page under it`() {
        val state = state()
        val tab = state.tabs.active!!
        val startX = tab.viewport.offsetX
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            // Press, then hold: not one further pointer event is sent, which is the whole point — the
            // sheet has to come to the pointer.
            event(scene, PointerEventType.Press, viewW - 6f, viewH / 2f, pressed = true, at = 1_000L)
            repeat(24) {
                Thread.sleep(8)
                Snapshot.sendApplyNotifications()
                scene.render()
            }

            assertTrue(
                "the sheet travelled left ($startX → ${tab.viewport.offsetX})",
                tab.viewport.offsetX < startX - 0.5f,
            )
            assertTrue(
                "and the drag is still running, not cancelled by the scroll it asked for",
                state.liveDrag != null,
            )

            event(scene, PointerEventType.Release, viewW - 6f, viewH / 2f, pressed = false, at = 2_000L)
            assertEquals("the gesture ends on the lift", null, state.liveDrag)
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    @Test
    fun `an element dragged against the edge travels with the sheet`() {
        val state = state()
        val tab = state.tabs.active!!
        // A plot at the page's own right border, so the press that grabs its body is already inside the
        // canvas's band: the sidebar and the rail take 304 px of the window, and at 200 % the page's x 360
        // is window x 1 024 — off screen — so the plot has to end just short of that.
        state.selectTool(DesktopTool.PLOT)
        state.placeOrAsk(tab, LivingSlot.PLOT, Bounds(200.0, 140.0, 360.0, 260.0))
        state.confirmPlacement()
        val before = plots(tab).single().left
        val startX = tab.viewport.offsetX

        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            // Inside the selection's outline, against the edge: this grabs the body, not a handle.
            event(scene, PointerEventType.Press, viewW - 10f, 400f, pressed = true, at = 1_000L)
            repeat(16) {
                Thread.sleep(8)
                Snapshot.sendApplyNotifications()
                scene.render()
            }
            event(scene, PointerEventType.Release, viewW - 10f, 400f, pressed = false, at = 2_000L)

            assertTrue(
                "the sheet scrolled under the pointer ($startX → ${tab.viewport.offsetX})",
                tab.viewport.offsetX < startX - 0.5f,
            )
            assertTrue(
                "and the element moved with it, staying under the pointer ($before → ${plots(tab).single().left})",
                plots(tab).single().left > before + 0.5,
            )
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    @Test
    fun `a marquee away from the edge leaves the page where it is`() {
        val state = state()
        val tab = state.tabs.active!!
        val startX = tab.viewport.offsetX
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            event(scene, PointerEventType.Press, viewW / 2f, viewH / 2f, pressed = true, at = 1_000L)
            repeat(10) {
                Thread.sleep(8)
                Snapshot.sendApplyNotifications()
                scene.render()
            }
            assertEquals("a drag in the middle scrolls nothing", startX, tab.viewport.offsetX, 0.001f)
            event(scene, PointerEventType.Release, viewW / 2f, viewH / 2f, pressed = false, at = 2_000L)
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    @Test
    fun `the deepest zoom still paints the page`() {
        // The ceiling is a promise about the renderer as much as about the arithmetic: at 3 200 % a page
        // point is 32 screen pixels, and a matrix that broke there would show as an empty canvas.
        val state = state()
        val tab = state.tabs.active!!
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = Viewport.MAX_ZOOM, offsetX = 0f, offsetY = 0f)
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            Snapshot.sendApplyNotifications()
            val png = requireNotNull(scene.render().encodeToData()).bytes
            val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(png))
            var paper = 0
            for (x in 400 until 700) {
                for (y in 100 until 500) {
                    if ((image.getRGB(x, y) and 0xFFFFFF) > 0xF0F0F0) paper++
                }
            }
            assertTrue("the sheet is still drawn at ${Viewport.MAX_ZOOM}x ($paper px)", paper > 1_000)
        } finally {
            scene.close()
            state.tabs.closeAll()
        }
    }

    private fun plots(tab: DesktopTab): List<PlotElement> =
        tab.document.page(tab.page).layers.flatMap { it.elements }.filterIsInstance<PlotElement>()

    /** One mouse event at ([x], [y]) in window pixels. */
    private fun event(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        pressed: Boolean,
        at: Long,
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
            keyboardModifiers = PointerKeyboardModifiers(),
            timeMillis = at,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }
}
