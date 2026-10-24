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
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.ElementBounds
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's vertical-space tool: a grab line, and everything below it following the pointer.
 *
 * The arithmetic is `VerticalSpaceOpsTest`'s (now in `mobix-core`, beside the maths); what this suite
 * proves is the **gesture and the edit window** — that a drag moves what lay below the line by exactly
 * the drag's travel, leaves an element the line passes through where it was, is one undo step, and that a
 * drag over empty space records nothing at all (an undo step that changes nothing is worse than no step).
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopVerticalSpaceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 595
    private val viewH = 700

    private fun notebook(): File = temp.newFile("vspace.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    /** A page with a stroke whose top edge is [top] — the thing the grab line is judged against. */
    private fun desktop(): Pair<DesktopTab, ImageComposeScene> {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook()))
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        tab.document.editPage(0) { page ->
            page.withElements(listOf(bar(500.0, 20.0), bar(500.0, 100.0), bar(500.0, 200.0)))
        }
        val scene = ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                tool = DesktopTool.VERTICAL_SPACE,
                ink = DesktopInk(width = 4f),
                onViewport = {},
                onFit = {},
            )
        }
        return tab to scene
    }

    private fun bar(x: Double, y: Double): Stroke = Stroke(
        points = listOf(StrokePoint(x, y, 4.0), StrokePoint(x + 60.0, y, 4.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = true,
    )

    private fun elements(page: Page): List<Element> = page.layers.flatMap { it.elements }

    private fun drag(scene: ImageComposeScene, fromY: Float, toY: Float, x: Float = 100f) {
        pen(scene, PointerEventType.Press, x, fromY, 1_000L)
        pen(scene, PointerEventType.Move, x, (fromY + toY) / 2f, 1_016L)
        pen(scene, PointerEventType.Move, x, toY, 1_032L)
        pen(scene, PointerEventType.Release, x, toY, 1_048L, pressed = false)
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
            keyboardModifiers = PointerKeyboardModifiers(),
            timeMillis = time,
        )
        Snapshot.sendApplyNotifications()
        scene.render()
    }

    @Test
    fun `a drag down moves what lay below the line and leaves what it crosses`() {
        val (tab, scene) = desktop()
        try {
            val before = elements(tab.document.page(0)).map { ElementBounds.of(it).top }
            drag(scene, fromY = 50f, toY = 90f)

            val after = elements(tab.document.page(0)).map { ElementBounds.of(it).top }
            assertEquals("no element appears or disappears", before.size, after.size)
            for (index in before.indices) {
                val expected = if (before[index] >= 50.0) before[index] + 40.0 else before[index]
                assertEquals(
                    "element $index at ${before[index]} pt",
                    expected,
                    after[index],
                    1e-6,
                )
            }
            assertTrue("the block really moved", after.any { it > 100.0 })
            assertTrue("the live drag edited the page it follows the pointer with", tab.document.edits > 1)
            assertTrue("and the whole gesture is one undo step", tab.canUndo)

            assertTrue("undo puts the block back", tab.undo())
            assertEquals(
                "exactly where it was",
                before,
                elements(tab.document.page(0)).map { ElementBounds.of(it).top },
            )
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    @Test
    fun `a drag over empty space records nothing`() {
        val (tab, scene) = desktop()
        try {
            val before = elements(tab.document.page(0)).map { ElementBounds.of(it).top }
            // Below every element: there is no block to move, so the page must not change at all.
            drag(scene, fromY = 600f, toY = 660f)

            assertEquals(
                "the page is untouched",
                before,
                elements(tab.document.page(0)).map { ElementBounds.of(it).top },
            )
            assertFalse("and no empty undo step was recorded", tab.canUndo)
            assertEquals("no edit was recorded either — only the fixture's own", 1, tab.document.edits)
        } finally {
            scene.close()
            tab.document.close()
        }
    }
}

/** [this] with [elements] added on top of its top layer — the fixture's own helper for this suite. */
private fun Page.withElements(elements: List<Element>): Page {
    val layers = layers.ifEmpty { listOf(com.mobixournal.format.model.Layer(emptyList())) }
    val top = layers.lastIndex
    val grown = layers.toMutableList().also {
        it[top] = com.mobixournal.format.model.Layer(it[top].elements + elements, it[top].name)
    }
    return copy(layers = grown)
}
