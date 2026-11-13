package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.ink.shapeStroke
import com.mobixournal.desktop.io.DesktopDocuments
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Layer
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.rectRegion
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A figure's pins, as the window actually paints them: after an insert, and in a selection.
 *
 * The rule itself is `FigurePins.selectionMarks`' (`FigurePinsTest`); this file answers the half a person
 * sees. A page that holds the right marks and a sheet that draws none are the same thing to the reader — a
 * figure with no pins — so the probes are pixels, in the accent the marks are painted with.
 *
 * The figure is a rectangle and the element it can join is a **Line-tool line**: with one on the page the
 * sheet marks its two ends, and on a page that holds nothing to join it marks nothing at all. The insert is
 * driven as the real gesture — pen down, figure in flight, pen up — because the marks have to survive the
 * lift: the slot that drew the figure is gone by then, and only the selection keeps them there.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopSelectionPinsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 595
    private val height = 700

    /** A rectangle minted the way the Rectangle slot mints it: a figure, so it carries pins. */
    private fun figure(left: Double = 100.0, top: Double = 100.0, right: Double = 220.0, bottom: Double = 180.0) =
        shapeStroke(
            ShapeBuilder.build(ShapeKind.RECTANGLE, left, top, right, bottom, 2.0),
            0xFF000000.toInt(),
            2f,
        )!!

    /** A line minted the way the Line slot mints it — the one element a figure can be paired with. */
    private fun wire() = shapeStroke(
        ShapeBuilder.build(ShapeKind.LINE, 320.0, 450.0, 440.0, 450.0, 2.0),
        0xFF000000.toInt(),
        2f,
    )!!

    /** A tab shown at 1:1, holding the wire and — where asked — the rectangle, already selected. */
    private fun tab(withRectangle: Boolean, withWire: Boolean = true): DesktopTab {
        val document = DesktopDocuments.blank(File(temp.root, "pins-${withRectangle}-${withWire}.xopp"))
        val elements = listOfNotNull(wire().takeIf { withWire }, figure().takeIf { withRectangle })
        document.editPage(0) { page -> page.copy(layers = listOf(Layer(elements))) }
        val tab = DesktopTabs().open(document)
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        if (withRectangle) {
            tab.selection = PageSelection(
                region = rectRegion(98.0, 98.0, 222.0, 182.0),
                refs = setOf(ElementRef(layerIndex = 0, elementIndex = elements.lastIndex)),
            )
        }
        return tab
    }

    private fun scene(tab: DesktopTab, tool: DesktopTool): ImageComposeScene =
        ImageComposeScene(width, height, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, tool = tool, onViewport = {}, onFit = {})
        }

    private fun pixels(scene: ImageComposeScene): IntArray {
        Snapshot.sendApplyNotifications()
        val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
        val image = ImageIO.read(ByteArrayInputStream(png))
        return IntArray(image.width * image.height).also {
            image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
        }
    }

    /** True when [rgb] is the accent the pin marks are painted with, allowing for antialiasing. */
    private fun isAccent(rgb: Int): Boolean {
        val accent = DesktopTheme.accent.toArgb()
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        return abs(r - ((accent shr 16) and 0xFF)) <= 40 &&
            abs(g - ((accent shr 8) and 0xFF)) <= 40 &&
            abs(b - (accent and 0xFF)) <= 48
    }

    private fun accentNear(pixels: IntArray, x: Int, y: Int): Boolean {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val px = x + dx
                val py = y + dy
                if (px !in 0 until width || py !in 0 until height) continue
                if (isAccent(pixels[py * width + px])) return true
            }
        }
        return false
    }

    /** Both ends of the wire at y = 450 are marked as places this figure may be paired with. */
    private fun assertWireMarked(pixels: IntArray, when_: String) {
        assertTrue("$when_: the wire's left end is marked", accentNear(pixels, 320, 450))
        assertTrue("$when_: and its right end", accentNear(pixels, 440, 450))
    }

    @Test
    fun `a selected figure marks the wire it can be paired with`() {
        val tab = tab(withRectangle = true)
        val scene = scene(tab, DesktopTool.MARQUEE)
        try {
            assertWireMarked(pixels(scene), "selected")
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a figure with nothing to join marks no pin`() {
        val tab = tab(withRectangle = true, withWire = false)
        val scene = scene(tab, DesktopTool.MARQUEE)
        try {
            val pixels = pixels(scene)
            // The page's right half is empty paper: not one mark may be painted there.
            val stray = (400..690).sumOf { y -> (300..560).count { x -> isAccent(pixels[y * width + x]) } }
            assertFalse("a figure alone marks nothing ($stray px)", stray > 0)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * The insert itself: the marks are on screen while the figure is in flight **and** after the pen lifts.
     *
     * The second half is the report — the figure kept its pins, but the slot that drew it was gone on the lift
     * and with it every mark, so a fresh figure looked like one that had no pins at all.
     */
    @Test
    fun `a figure being drawn marks its compatible wire, and stays selected when the pen lifts`() {
        val tab = tab(withRectangle = false)
        val scene = scene(tab, DesktopTool.RECTANGLE)
        try {
            pointer(scene, PointerEventType.Press, 100f, 250f, 1_000L)
            pointer(scene, PointerEventType.Move, 160f, 290f, 1_016L)
            pointer(scene, PointerEventType.Move, 220f, 320f, 1_032L)
            assertWireMarked(pixels(scene), "in flight")

            pointer(scene, PointerEventType.Release, 220f, 320f, 1_048L, pressed = false)

            val page = tab.document.page(0)
            assertEquals("the rectangle landed", 2, page.layers.sumOf { it.elements.size })
            assertEquals(
                "and it is the selection, so its pins stay on the sheet",
                setOf(ElementRef(layerIndex = 0, elementIndex = 1)),
                tab.selection?.refs,
            )
            assertNotNull(tab.selection)
            assertWireMarked(pixels(scene), "after the lift")
        } finally {
            scene.close()
            tab.document.close()
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
