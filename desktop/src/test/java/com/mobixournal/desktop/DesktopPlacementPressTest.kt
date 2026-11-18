package com.mobixournal.desktop

import com.mobixournal.desktop.ink.shapeStroke
import com.mobixournal.desktop.io.DesktopDocuments
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.tools.DesktopTool

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.ShapeBuilder
import com.mobixournal.render.ShapeKind
import com.mobixournal.render.rectRegion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **placement press**, driven as the gesture a hand makes: a figure slot on a figure it drew
 * selects that figure instead of stacking a second one on top, paper that holds none still draws the next
 * one, and a tap on empty paper only lets go of the selection.
 *
 * The rule itself is `PlacementPickTest`'s (pure page geometry); this file answers the half a person sees —
 * what the sheet writes and what stays selected when a press lands with the slot still in hand.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopPlacementPressTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 595
    private val height = 700

    /** A rectangle at [left]/[top], minted the way the Rectangle slot mints it. */
    private fun figure(left: Double = 100.0, top: Double = 100.0): Stroke = shapeStroke(
        ShapeBuilder.build(ShapeKind.RECTANGLE, left, top, left + 120.0, top + 80.0, 2.0),
        0xFF000000.toInt(),
        2f,
        kind = ShapeKind.RECTANGLE,
    )!!

    private fun tab(elements: List<Element>, selectLast: Boolean = false): DesktopTab {
        val document = DesktopDocuments.blank(File(temp.root, "placement-${elements.size}-$selectLast.xopp"))
        document.editPage(0) { page -> page.copy(layers = listOf(Layer(elements))) }
        val tab = DesktopTabs().open(document)
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        if (selectLast) {
            tab.selection = PageSelection(
                region = rectRegion(98.0, 98.0, 222.0, 182.0),
                refs = setOf(ElementRef(0, elements.lastIndex)),
            )
        }
        return tab
    }

    private fun scene(tab: DesktopTab, tool: DesktopTool): ImageComposeScene =
        ImageComposeScene(width, height, Density(1f)) {
            PageCanvas(tab = tab, panMode = false, tool = tool, onViewport = {}, onFit = {})
        }

    @Test
    fun `a press on a rectangle picks it instead of drawing a second one`() {
        val tab = tab(listOf(figure()))
        val scene = scene(tab, DesktopTool.RECTANGLE)
        try {
            pointer(scene, PointerEventType.Press, 160f, 100f, 1_000L)
            pointer(scene, PointerEventType.Move, 180f, 120f, 1_016L)
            pointer(scene, PointerEventType.Move, 200f, 140f, 1_032L)
            pointer(scene, PointerEventType.Release, 200f, 140f, 1_048L, pressed = false)

            val page = tab.document.page(0)
            assertEquals("no second figure was written", 1, page.layers.sumOf { it.elements.size })
            assertEquals(
                "and the press selected the figure it landed on",
                setOf(ElementRef(0, 0)),
                tab.selection?.refs,
            )
            val moved = page.layers[0].elements[0] as Stroke
            assertEquals("the same press carried it", 140.0, moved.points.first().x, 1.0)
            assertEquals(140.0, moved.points.first().y, 1.0)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a press on empty paper still draws the next figure`() {
        val tab = tab(listOf(figure()))
        val scene = scene(tab, DesktopTool.RECTANGLE)
        try {
            pointer(scene, PointerEventType.Press, 100f, 300f, 1_000L)
            pointer(scene, PointerEventType.Move, 200f, 380f, 1_016L)
            pointer(scene, PointerEventType.Release, 200f, 380f, 1_032L, pressed = false)

            assertEquals("a second figure landed", 2, tab.document.page(0).layers.sumOf { it.elements.size })
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a tap on empty paper only lets go of the selection`() {
        val tab = tab(listOf(figure()), selectLast = true)
        val scene = scene(tab, DesktopTool.RECTANGLE)
        try {
            pointer(scene, PointerEventType.Press, 420f, 620f, 1_000L)
            pointer(scene, PointerEventType.Release, 421f, 621f, 1_016L, pressed = false)

            assertNull("the selection was let go", tab.selection)
            assertEquals("and the tap wrote nothing", 1, tab.document.page(0).layers.sumOf { it.elements.size })
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
