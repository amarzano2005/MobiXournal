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
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.CircuitPin
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.rectRegion
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A part **selected after it was inserted** and then dragged onto another part's pin: the model and the
 * sheet, together.
 *
 * The gesture half was already covered (`SelectionMagnetTest` for the arithmetic, `DesktopSelectionTest` for
 * the drag); what could still go wrong — and did — is that the *screen* and the *page* disagree, which is
 * indistinguishable from a magnet that does not work: the hand drags the part, the outline travels with the
 * pointer, and the ink stays where it was. So this test drives the real canvas and then reads **both**: the
 * page's own element, and the pixels the sheet painted.
 *
 * The two probes are the moved part's old centre and the junction it should land on: after the drag the ink
 * must be at the junction and gone from where the part used to be.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SelectionMovePaintTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 595
    private val height = 700

    private fun resistor(id: String, x: Double, y: Double) = CircuitElement(
        id = id,
        symbol = ShapeKind.RESISTOR.name,
        positionX = x,
        positionY = y,
        pins = listOf(CircuitPin("p1", -40.0, 0.0), CircuitPin("p2", 40.0, 0.0)),
    )

    /** The anchored part at (150, 150) and the one to be dragged at (350, 150). */
    private val anchor = resistor("a", 150.0, 150.0)
    private val movable = resistor("b", 350.0, 150.0)

    private fun page(): Page = Page(
        width = width.toDouble(),
        height = 842.0,
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(Layer(listOf(anchor, movable))),
    )

    private fun tab(): DesktopTab {
        val document = DesktopDocuments.blank(File(temp.root, "move.xopp"))
        document.editPage(0) { page() }
        val tab = DesktopTabs().open(document)
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        // The whole of the second part, as the insertion itself leaves it.
        tab.selection = PageSelection(
            region = rectRegion(310.0, 150.0, 390.0, 150.0),
            refs = setOf(ElementRef(layerIndex = 0, elementIndex = 1)),
        )
        return tab
    }

    @Test
    fun `a dragged part lands on the pin and the sheet shows it there`() {
        val tab = tab()
        val scene = ImageComposeScene(width, height, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, tool = DesktopTool.MARQUEE, onViewport = {}, onFit = {})
        }
        try {
            Snapshot.sendApplyNotifications()
            scene.render()

            // Pressed on the body, dragged to 4 pt short of the anchor's right pin: only the magnet can
            // close that gap, so a landing on (190,150) is the magnet and not the hand.
            pointer(scene, PointerEventType.Press, 350f, 150f, 1_000L)
            pointer(scene, PointerEventType.Move, 300f, 152f, 1_016L)
            pointer(scene, PointerEventType.Move, 234f, 156f, 1_032L)
            pointer(scene, PointerEventType.Release, 234f, 156f, 1_048L, pressed = false)

            val moved = pageOf(tab).layers[0].elements[1] as CircuitElement
            assertEquals("the left pin joins the anchor's right pin", 190.0, moved.positionX - 40.0, 1e-6)
            assertEquals(150.0, moved.positionY, 1e-6)

            val pixels = frame(scene)
            assertTrue("the ink is where the part was dropped", inkNear(pixels, 190, 150))
            assertTrue("and not where it used to be", !inkNear(pixels, 350, 150))
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * The same part, but picked up **later** — a click on it after the insertion is long gone.
     *
     * The window's own path, not a selection written into the tab: the press picks the element it landed on
     * (`pickForDrag`), the click makes it the selection, and the drag that follows must still find the pins
     * it can join. A part that only snaps while its insertion is still holding the selection would work once
     * and never again.
     */
    @Test
    fun `a part clicked after the insertion still snaps to a pin`() {
        val tab = tab()
        tab.selection = null
        val scene = ImageComposeScene(width, height, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, tool = DesktopTool.MARQUEE, onViewport = {}, onFit = {})
        }
        try {
            Snapshot.sendApplyNotifications()
            scene.render()

            // A click on the part: press and release with nothing in between.
            pointer(scene, PointerEventType.Press, 350f, 150f, 1_000L)
            pointer(scene, PointerEventType.Release, 350f, 150f, 1_060L, pressed = false)
            assertEquals("the click selects the part", 1, tab.selection?.refs?.size)

            pointer(scene, PointerEventType.Press, 350f, 150f, 2_000L)
            pointer(scene, PointerEventType.Move, 300f, 152f, 2_016L)
            pointer(scene, PointerEventType.Move, 234f, 156f, 2_032L)
            pointer(scene, PointerEventType.Release, 234f, 156f, 2_048L, pressed = false)

            val moved = pageOf(tab).layers[0].elements[1] as CircuitElement
            assertEquals("the pin still joins the anchor's pin", 190.0, moved.positionX - 40.0, 1e-6)
            assertEquals(150.0, moved.positionY, 1e-6)

            // And again, now that the part has a move behind it: the element a magnetised drag wrote must
            // still be a component with pins, or the second attempt would find nothing to join.
            pointer(scene, PointerEventType.Press, 230f, 150f, 3_000L)
            pointer(scene, PointerEventType.Release, 230f, 150f, 3_060L, pressed = false)
            pointer(scene, PointerEventType.Press, 230f, 150f, 4_000L)
            pointer(scene, PointerEventType.Move, 260f, 300f, 4_016L)
            pointer(scene, PointerEventType.Move, 320f, 400f, 4_032L)
            pointer(scene, PointerEventType.Release, 320f, 400f, 4_048L, pressed = false)
            val twice = pageOf(tab).layers[0].elements[1]
            assertTrue("a moved part is still a component", twice is CircuitElement)
            assertEquals(
                "and its pins are still targets for the next drag",
                320.0 - 40.0,
                (twice as CircuitElement).positionX - 40.0,
                1e-6,
            )
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    private fun pageOf(tab: DesktopTab): Page = tab.document.page(0)

    /** Whether any pixel in a small window around ([x], [y]) is ink rather than paper. */
    private fun inkNear(pixels: IntArray, x: Int, y: Int): Boolean {
        for (dy in -3..3) {
            for (dx in -3..3) {
                val px = x + dx
                val py = y + dy
                if (px !in 0 until width || py !in 0 until height) continue
                val rgb = pixels[py * width + px]
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                // Paper is white and the fixture's sheet carries no ruling: anything below mid-grey is
                // this page's own ink (the parts are drawn at the pen's hairline width).
                if (r + g + b < 690) return true
            }
        }
        return false
    }

    private fun frame(scene: ImageComposeScene): IntArray {
        Snapshot.sendApplyNotifications()
        val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
        val image = ImageIO.read(ByteArrayInputStream(png))
        return IntArray(image.width * image.height).also {
            image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
        }
    }

    private fun pointer(
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
                    type = PointerType.Mouse,
                    pressure = 1f,
                ),
            ),
            buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
            timeMillis = time,
        )
    }
}
