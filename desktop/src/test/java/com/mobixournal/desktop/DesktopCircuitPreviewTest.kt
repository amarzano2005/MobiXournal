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
import com.mobixournal.render.CircuitSnapping
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

    /**
     * The magnet, through the **real pointer path**: a second part dragged until its end reaches the first
     * part's pin must land *on* it, not a few points beside it.
     *
     * This is the end-to-end half of `CircuitGestureTest`'s pure span: a span can be right while the canvas
     * never uses it, which is exactly what a reader who says "the pins don't attach" is describing.
     */
    @Test
    fun `a dragged part lands its end on the pin of the one already there`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        placeResistor(tab, 200.0, 200.0)
        val scene = scene(tab)
        try {
            // Press three points off the resistor's right pin (240, 200) and pull right: the press is the
            // new part's first end, and it has to be pulled onto that pin.
            pen(scene, PointerEventType.Press, 243f, 203f, 1_000L)
            pen(scene, PointerEventType.Move, 420f, 203f, 1_016L)
            pen(scene, PointerEventType.Release, 420f, 203f, 1_032L, pressed = false)
            scene.render()

            val placed = elements(tab).filterIsInstance<CircuitElement>()
            assertEquals("the first part and the new one", 2, placed.size)
            val pins = placed.flatMap { part ->
                part.pins.map { pin -> CircuitSnapping.pinAbsolutePosition(part, pin) }
            }
            val joined = pins.count { (x, y) -> kotlin.math.abs(x - 240.0) < 1e-6 && kotlin.math.abs(y - 200.0) < 1e-6 }
            assertEquals("two parts, one shared pin position: $pins", 2, joined)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * The magnet on a part **selected after insertion**: the drag of a caught component is snapped too.
     *
     * The reader's second way to build a circuit — place the parts, then drag them until they meet — and
     * the one the window never magnetised: its move was the pointer's exact translation, so a pin could
     * only ever be joined while the part was still being drawn.
     */
    @Test
    fun `a selected part is pulled onto a pin when it is dragged`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        placeResistor(tab, 200.0, 200.0)
        placeResistor(tab, 500.0, 200.0)
        val scene = marqueeScene(tab)
        try {
            // Catch the second part where it lies, then drag it left until its left pin is 3 pt off the
            // first part's right pin (240, 200).
            pen(scene, PointerEventType.Press, 500f, 200f, 1_000L, tool = DesktopTool.MARQUEE)
            pen(scene, PointerEventType.Release, 500f, 200f, 1_016L, pressed = false, tool = DesktopTool.MARQUEE)
            scene.render()
            assertTrue("the press caught the part", tab.selection?.refs?.isNotEmpty() == true)

            // A different point from the catching click: two presses in the same place are a double-click
            // (`TapTracker`), which opens an editor instead of moving the ink.
            pen(scene, PointerEventType.Press, 520f, 200f, 2_000L, tool = DesktopTool.MARQUEE)
            pen(scene, PointerEventType.Move, 303f, 200f, 2_016L, tool = DesktopTool.MARQUEE)
            pen(scene, PointerEventType.Release, 303f, 200f, 2_032L, pressed = false, tool = DesktopTool.MARQUEE)
            scene.render()

            val pins = elements(tab).filterIsInstance<CircuitElement>().flatMap { part ->
                part.pins.map { pin -> CircuitSnapping.pinAbsolutePosition(part, pin) }
            }
            val joined = pins.count { (x, y) -> kotlin.math.abs(x - 240.0) < 1e-6 && kotlin.math.abs(y - 200.0) < 1e-6 }
            assertEquals("the dragged part's pin sits on the anchor's: $pins", 2, joined)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * A part **caught by its pin**: the press lands past its end, over bare paper, and still moves it.
     *
     * The reader aims at the pin — that is the thing being joined — so the ink picker's reach, which stops
     * where the drawn part stops, used to answer nothing and trace a marquee instead. The part stayed put,
     * and the complaint was that the pins "did not attach": nothing had been picked up to move.
     */
    @Test
    fun `a press on a pin carries the part onto the pin it was dropped on`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        placeResistor(tab, 200.0, 200.0)
        placeResistor(tab, 500.0, 200.0)
        val scene = marqueeScene(tab)
        try {
            // The second part's right-hand pin is at (540, 200); six points further out is bare paper.
            pen(scene, PointerEventType.Press, 546f, 200f, 1_000L, tool = DesktopTool.MARQUEE)
            pen(scene, PointerEventType.Move, 326f, 200f, 1_016L, tool = DesktopTool.MARQUEE)
            pen(scene, PointerEventType.Release, 326f, 200f, 1_032L, pressed = false, tool = DesktopTool.MARQUEE)
            scene.render()

            val pins = elements(tab).filterIsInstance<CircuitElement>().flatMap { part ->
                part.pins.map { pin -> CircuitSnapping.pinAbsolutePosition(part, pin) }
            }
            val joined = pins.count { (x, y) -> kotlin.math.abs(x - 240.0) < 1e-6 && kotlin.math.abs(y - 200.0) < 1e-6 }
            assertEquals("the carried part's pin sits on the anchor's: $pins", 2, joined)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    private fun marqueeScene(tab: DesktopTab): ImageComposeScene {
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, tool = DesktopTool.MARQUEE, onViewport = {}, onFit = {})
        }
        scene.render()
        Snapshot.sendApplyNotifications()
        return scene
    }

    /** A resistor placed as the reader would: a drag with the circuit slot, committed on the lift. */
    private fun placeResistor(tab: DesktopTab, x: Double, y: Double) {
        val part = com.mobixournal.render.CircuitElementFactory.circuitElementFor(
            symbol = ShapeKind.RESISTOR.name,
            startX = x - 40.0,
            startY = y,
            endX = x + 40.0,
            endY = y,
        ) ?: error("the resistor is in the library")
        val index = tab.page
        val before = tab.document.snapshot()
        tab.document.editPage(index) { page -> page.withElement(part) }
        tab.record(before)
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
        tool: DesktopTool = DesktopTool.CIRCUIT,
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
