package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.ShiftPictures
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.tools.DesktopTool

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
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.ElementBounds
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The vertical-space drag **previews** the block instead of rewriting the page.
 *
 * Rewriting it per pointer sample was measured at 5.2 ms of reflow plus a full picture re-record, and the
 * window's own watchdog caught the consequence: 48 page re-records a second during one drag, worst frame
 * 146 ms. The fix is that the sheet is recorded once in two halves — what stays above the line and what
 * follows it ([ShiftPictures]) — so a sample publishes an offset and the page is written once, on the lift.
 *
 * What this suite pins is exactly that split of responsibilities, and it is pinned twice over because
 * either half alone is satisfiable by the wrong code:
 *
 *  - **as a document**: mid-drag the page still holds the very element objects it held before the press
 *    (nothing was written), and on the lift it holds them 40 pt lower;
 *  - **as pixels**: mid-drag the ink really is painted at the offset — the bar that lay at y = 200 shows at
 *    y = 240 and the old band is empty paper — so the drag is not simply invisible until the hand lifts.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopShiftPreviewTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 595
    private val viewH = 700

    private fun notebook(): File = temp.newFile("shift-preview.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
    }

    /** A horizontal bar at [y] from x = 100 to x = 300, 4 pt wide. */
    private fun bar(y: Double): Stroke = Stroke(
        points = listOf(StrokePoint(100.0, y, 4.0), StrokePoint(300.0, y, 4.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = true,
    )

    private fun tab(elements: List<Element>): DesktopTab {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.document.editPage(0) { page -> page.copy(layers = listOf(Layer(elements))) }
        return tab
    }

    private fun scene(tab: DesktopTab): ImageComposeScene = ImageComposeScene(viewW, viewH, Density(1f)) {
        PageCanvas(
            tab = tab,
            panMode = false,
            tool = DesktopTool.VERTICAL_SPACE,
            ink = DesktopInk(width = 4f),
            onViewport = {},
            onFit = {},
        )
    }

    private fun mouse(
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

    /** The scene painted as an image, through the same PNG round trip the other render tests use. */
    private fun paint(scene: ImageComposeScene): BufferedImage {
        val bytes = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
        return ImageIO.read(ByteArrayInputStream(bytes))
    }

    /** How many pixels in the band are ink (the paper is white, the bar is black). */
    private fun inkIn(image: BufferedImage, xs: IntRange, ys: IntRange): Int =
        ys.sumOf { y -> xs.count { x -> ((image.getRGB(x, y) shr 16) and 0xFF) < 100 } }

    private fun elements(page: Page): List<Element> = page.layers.flatMap { it.elements }

    @Test
    fun `a drag moves the painted block, writes nothing, and commits once on the lift`() {
        val tab = tab(listOf(bar(100.0), bar(200.0)))
        val scene = scene(tab)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()
            val before = elements(tab.document.page(0))

            // The grab line at y = 150: the bar at 100 stays, the bar at 200 follows.
            mouse(scene, PointerEventType.Press, 200f, 150f, 1_000L)
            scene.render()
            mouse(scene, PointerEventType.Move, 200f, 190f, 1_016L)
            scene.render()
            Snapshot.sendApplyNotifications()

            val during = elements(tab.document.page(0))
            assertEquals("the drag painted nothing onto the page", before.size, during.size)
            for (index in before.indices) {
                assertSame(
                    "element $index is the very object it was: a sample wrote no page",
                    before[index],
                    during[index],
                )
            }

            val image = paint(scene)
            assertTrue(
                "the block is painted 40 pt lower (${inkIn(image, 150..250, 236..244)} px at y = 240)",
                inkIn(image, 150..250, 236..244) >= 60,
            )
            assertEquals("and the band it left is empty paper", 0, inkIn(image, 150..250, 196..204))
            assertTrue("the bar above the line did not move", inkIn(image, 150..250, 96..104) >= 60)

            mouse(scene, PointerEventType.Release, 200f, 190f, 1_032L, pressed = false)
            scene.render()
            Snapshot.sendApplyNotifications()

            val tops = elements(tab.document.page(0)).map { ElementBounds.of(it).top }
            // The ink's top is the bar's y less half its 4 pt width (100 → 98), and the block travelled 40.
            assertEquals("one commit, and it is the drag's own travel", listOf(98.0, 238.0), tops)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a page whose ink was not written top-down previews too`() {
        // Ink added above an older paragraph: a bar that stays is drawn *after* one that moves. The split is
        // by element, not by paint order, so the drag still previews — the block is painted over what it
        // passes, the trade Xournal++'s own mask makes (`ShiftPictures`).
        val tab = tab(listOf(bar(200.0), bar(100.0)))
        val scene = scene(tab)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()
            val before = elements(tab.document.page(0))

            mouse(scene, PointerEventType.Press, 200f, 150f, 1_000L)
            scene.render()
            mouse(scene, PointerEventType.Move, 200f, 190f, 1_016L)
            scene.render()
            Snapshot.sendApplyNotifications()
            assertSame(
                "nothing was written mid-drag, whatever the page's paint order",
                before[0],
                elements(tab.document.page(0))[0],
            )

            mouse(scene, PointerEventType.Release, 200f, 190f, 1_032L, pressed = false)
            scene.render()
            Snapshot.sendApplyNotifications()

            val tops = elements(tab.document.page(0)).map { ElementBounds.of(it).top }
            assertEquals("the block followed the pointer", listOf(238.0, 98.0), tops)
        } finally {
            scene.close()
            tab.document.close()
        }
    }
}
