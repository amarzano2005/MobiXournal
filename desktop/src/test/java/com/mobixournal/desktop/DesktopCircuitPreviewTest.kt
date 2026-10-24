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
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.render.ElementBounds
import com.mobixournal.render.ShapeKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A component is **pulled out of the sheet along the pen**, like a figure — not written only when the pen
 * lifts.
 *
 * The placement already sized the component from the drag (that is `LivingPlacement.circuit`), so what
 * this suite protects is the picture: while the pen is still down the sheet shows the part exactly where
 * and as big as it will land, and nothing of it is on the page yet, so the lift is still one edit and one
 * undo step.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopCircuitPreviewTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 595
    private val viewH = 700

    private fun notebook(): File = temp.newFile("circuit.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    private fun scene(tab: DesktopTab): ImageComposeScene {
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = DesktopTool.CIRCUIT,
                circuitKind = ShapeKind.RESISTOR,
                onViewport = {},
                onFit = {},
            )
        }
        scene.render()
        Snapshot.sendApplyNotifications()
        return scene
    }

    @Test
    fun `the component is on the sheet while the pen is still down`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val scene = scene(tab)
        try {
            pen(scene, PointerEventType.Press, 160f, 240f, 1_000L)
            pen(scene, PointerEventType.Move, 300f, 280f, 1_016L)
            scene.render()

            assertTrue("nothing is written before the lift", elements(tab).none { it is CircuitElement })
            val ink = inkIn(scene, left = 160, top = 230, right = 300, bottom = 290)
            assertTrue("the part is drawn where it will land, but nothing was: $ink", ink > 40)

            pen(scene, PointerEventType.Release, 300f, 280f, 1_032L, pressed = false)
            val part = elements(tab).filterIsInstance<CircuitElement>().single()
            val box = ElementBounds.of(part)
            assertEquals("the pen's own length", 140.0, box.right - box.left, 2.0)
            assertTrue("the lift is one undo step", tab.canUndo)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a click places the part's canonical size and draws it once`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        val scene = scene(tab)
        try {
            pen(scene, PointerEventType.Press, 300f, 300f, 1_000L)
            pen(scene, PointerEventType.Release, 300f, 300f, 1_032L, pressed = false)
            scene.render()

            val part = elements(tab).filterIsInstance<CircuitElement>().single()
            assertTrue("a click still places the family's default part", part.symbol.isNotEmpty())
            assertTrue("and it is drawn", inkIn(scene, left = 200, top = 260, right = 400, bottom = 340) > 40)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    private fun elements(tab: DesktopTab) = tab.document.page(tab.page).layers.flatMap { it.elements }

    /** Dark pixels inside a screen-space rectangle — a page's paper is white, so this counts ink. */
    private fun inkIn(scene: ImageComposeScene, left: Int, top: Int, right: Int, bottom: Int): Int {
        val bytes = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
        val image = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(bytes))
        var ink = 0
        for (y in top until minOf(bottom, image.height)) {
            for (x in left until minOf(right, image.width)) {
                val rgb = image.getRGB(x, y) and 0xFFFFFF
                if (maxOf(rgb shr 16, (rgb shr 8) and 0xFF, rgb and 0xFF) < 0x60) ink++
            }
        }
        return ink
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
