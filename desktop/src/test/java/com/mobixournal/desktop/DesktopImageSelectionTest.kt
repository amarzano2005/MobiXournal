package com.mobixournal.desktop

import com.mobixournal.desktop.ink.DesktopInk
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.io.resample
import com.mobixournal.desktop.sheet.FitMode
import com.mobixournal.desktop.sheet.PageCanvas
import com.mobixournal.desktop.sheet.SelectionHandle
import com.mobixournal.desktop.sheet.Viewport
import com.mobixournal.desktop.sheet.scaleFactors
import com.mobixournal.desktop.sheet.selectionHandleAt
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
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.render.Bounds
import com.mobixournal.render.ElementRef
import com.mobixournal.render.PageSelection
import com.mobixournal.render.rectRegion
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Verification of desktop selection handles, aspect ratio scaling, and non-resampling for [ImageElement].
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopImageSelectionTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 600
    private val viewH = 800

    private val sampleBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    private val imageBox = Bounds(100.0, 100.0, 300.0, 200.0) // 200 wide x 100 high (aspect ratio = 2.0)
    private val imageSelection = PageSelection(
        rectRegion(100.0, 100.0, 300.0, 200.0),
        setOf(ElementRef(0, 0)),
    )

    @Test
    fun `selectionHandleAt picks edge handles when enabled and suppresses rotate`() {
        // Without edges: top-midpoint moves the selection (BODY)
        val defaultMidTop = selectionHandleAt(imageSelection, 200.0, 100.0, tolPt = 4.0, includeEdges = false)
        assertEquals(SelectionHandle.BODY, defaultMidTop)

        // With edges enabled (as for ImageElement):
        val topEdge = selectionHandleAt(imageSelection, 200.0, 100.0, tolPt = 4.0, includeEdges = true)
        assertEquals(SelectionHandle.TOP, topEdge)

        val bottomEdge = selectionHandleAt(imageSelection, 200.0, 200.0, tolPt = 4.0, includeEdges = true)
        assertEquals(SelectionHandle.BOTTOM, bottomEdge)

        val leftEdge = selectionHandleAt(imageSelection, 100.0, 150.0, tolPt = 4.0, includeEdges = true)
        assertEquals(SelectionHandle.LEFT, leftEdge)

        val rightEdge = selectionHandleAt(imageSelection, 300.0, 150.0, tolPt = 4.0, includeEdges = true)
        assertEquals(SelectionHandle.RIGHT, rightEdge)

        // Rotate handle is omitted when includeRotate is false
        val rotateAt = selectionHandleAt(
            imageSelection, 200.0, 100.0 - 26.0, tolPt = 4.0,
            includeEdges = true, includeRotate = false,
        )
        assertEquals(SelectionHandle.NONE, rotateAt)
    }

    @Test
    fun `scaleFactors preserves aspect ratio when locked on corner drag`() {
        // Dragging BOTTOM_RIGHT outward: from (300, 200) to (500, 250)
        // dx = 400 - 100 = 300 (anchor is 100, 100; spanX = 200) -> sx = 300 / 200 = 1.5
        // dy = 300 - 100 = 200 (spanY = 100) -> sy = 200 / 100 = 2.0
        // Dominant is sy (abs(2.0 - 1.0) = 1.0 > abs(1.5 - 1.0) = 0.5)
        val (sx, sy) = scaleFactors(imageBox, SelectionHandle.BOTTOM_RIGHT, x = 400.0, y = 300.0, lockAspect = true)
        assertEquals("both scale factors must be equal when locked", sx, sy, 1e-6)
        assertEquals(2.0, sx, 1e-6)

        // Without lock: independent scaling
        val (freeSx, freeSy) = scaleFactors(imageBox, SelectionHandle.BOTTOM_RIGHT, x = 400.0, y = 300.0, lockAspect = false)
        assertEquals(1.5, freeSx, 1e-6)
        assertEquals(2.0, freeSy, 1e-6)
    }

    @Test
    fun `scaleFactors on edge handles stretches a single axis`() {
        // TOP edge drag moves y upward: anchor is bottom (200.0)
        val (topSx, topSy) = scaleFactors(imageBox, SelectionHandle.TOP, x = 200.0, y = 50.0)
        assertEquals("horizontal scale stays 1.0 on vertical edge handle", 1.0, topSx, 1e-6)
        // spanY = 100 - 200 = -100; y - anchorY = 50 - 200 = -150 -> sy = 1.5
        assertEquals(1.5, topSy, 1e-6)

        // RIGHT edge drag moves x outward: anchor is left (100.0)
        val (rightSx, rightSy) = scaleFactors(imageBox, SelectionHandle.RIGHT, x = 400.0, y = 150.0)
        assertEquals("vertical scale stays 1.0 on horizontal edge handle", 1.0, rightSy, 1e-6)
        // spanX = 300 - 100 = 200; x - anchorX = 400 - 100 = 300 -> sx = 1.5
        assertEquals(1.5, rightSx, 1e-6)
    }

    @Test
    fun `corner drag on ImageElement preserves aspect ratio and does not resample bytes`() {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)

        val image = ImageElement(
            left = 100.0,
            top = 100.0,
            right = 300.0,
            bottom = 200.0,
            data = sampleBytes.copyOf(),
        )
        tab.document.editPage(0) { page ->
            page.copy(layers = listOf(Layer(elements = listOf(image))))
        }
        tab.selection = PageSelection(rectRegion(100.0, 100.0, 300.0, 200.0), setOf(ElementRef(0, 0)))

        val scene = canvas(tab, DesktopTool.MARQUEE)
        try {
            scene.render()
            Snapshot.sendApplyNotifications()

            // Corner drag from bottom-right (300, 200) outward to (400, 300) without Shift
            drag(scene, 300f, 200f, 400f, 300f)

            val editedElement = tab.document.page(0).layers[0].elements[0] as ImageElement
            val width = editedElement.right - editedElement.left
            val height = editedElement.bottom - editedElement.top
            val aspectRatio = width / height

            assertEquals("aspect ratio (2.0) must be preserved", 2.0, aspectRatio, 1e-4)
            assertEquals("raw image bytes must not be resampled or modified", sampleBytes.size, editedElement.data.size)
            assertArrayEquals("image bytes must match original byte-for-byte", sampleBytes, editedElement.data)

            // Undo restores original bounds in a single step
            assertTrue("resize gesture must be undoable", tab.undo())
            val restored = tab.document.page(0).layers[0].elements[0] as ImageElement
            assertEquals(100.0, restored.left, 1e-6)
            assertEquals(100.0, restored.top, 1e-6)
            assertEquals(300.0, restored.right, 1e-6)
            assertEquals(200.0, restored.bottom, 1e-6)
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    private fun notebook(): File = temp.newFile("image_selection.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
    }

    private fun canvas(tab: DesktopTab, tool: DesktopTool): ImageComposeScene =
        ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = tool,
                ink = DesktopInk(width = 4f),
                onViewport = {},
                onFit = {},
            )
        }

    private fun drag(scene: ImageComposeScene, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        pointer(scene, PointerEventType.Press, fromX, fromY, 1_000L)
        pointer(scene, PointerEventType.Move, (fromX + toX) / 2f, (fromY + toY) / 2f, 1_016L)
        pointer(scene, PointerEventType.Move, toX, toY, 1_032L)
        pointer(scene, PointerEventType.Release, toX, toY, 1_048L, pressed = false)
    }

    private fun pointer(
        scene: ImageComposeScene,
        type: PointerEventType,
        x: Float,
        y: Float,
        time: Long,
        pressed: Boolean = true,
        modifiers: PointerKeyboardModifiers = PointerKeyboardModifiers(),
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
            keyboardModifiers = modifiers,
            timeMillis = time,
        )
    }
}
