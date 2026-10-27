package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Stroke
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **A drag lands under the pointer on a fitted page beside the strips** — the case the old mapping got
 * wrong, end to end through a real Compose scene.
 *
 * `PointerMappingTest` proves the arithmetic (the sheet is painted and pressed through one viewport);
 * this proves the wiring: a [PageCanvas] given a [ChromeInset] frames the page in the room the rail
 * leaves, and a stroke drawn through Compose's own dispatch has to come out at the page point the painter
 * put under the pointer. Before the fix the press was mapped through a viewport derived a second way
 * (`clamp`ed in the whole window), so every point of the stroke sat half a rail's lane off — worse the
 * further out the reader zoomed.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PagePressSceneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 900
    private val viewH = 700
    private val chrome = ChromeInset(left = 64f, top = 48f)

    private fun strokes(document: DesktopDocument) =
        document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    @Test
    fun `a stroke on a fitted page lands where the sheet was painted`() {
        val density = 1.5f
        val notebook = temp.newFile("press.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(4)) }
        val tab = DesktopTabs().open(DocumentLoader.load(notebook))
        // Fitted whole: the canvas centres the sheet in the room the rail leaves, and that is the
        // difference this test is about (a pinned 100 % page has no chrome-derived offset at all).
        tab.fit = FitMode.PAGE
        val scene = ImageComposeScene(viewW, viewH, Density(density)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                onViewport = {},
                onFit = {},
                ink = DesktopInk(width = 4f),
                chrome = chrome,
            )
        }
        var clock = 1_000L
        fun send(type: PointerEventType, x: Float, y: Float, pressed: Boolean = true) {
            scene.sendPointerEvent(
                eventType = type,
                pointers = listOf(ComposeScenePointer(PointerId(1), Offset(x, y), pressed, PointerType.Mouse, 1f)),
                buttons = if (pressed) PointerButtons(isPrimaryPressed = true) else PointerButtons(),
                timeMillis = clock,
            )
            clock += 16L
            Snapshot.sendApplyNotifications()
            scene.render()
        }
        try {
            val page = tab.document.page(0)
            val pageW = page.width.toFloat()
            val pageH = page.height.toFloat()
            // What the painter does: fit in the room beside the rail, then centre there. The canvas
            // lays a page out in **dp** (the viewport's own unit), so the box is divided out first.
            val viewWdp = viewW / density
            val viewHdp = viewH / density
            val zoom = Viewport.fitZoom(pageW, pageH, viewWdp - chrome.width, viewHdp - chrome.height)
            val painted = Viewport(zoom, 0f, 0f).inChrome(chrome, pageW, pageH, viewWdp, viewHdp)

            send(PointerEventType.Press, 400f, 300f)
            send(PointerEventType.Move, 520f, 380f)
            send(PointerEventType.Release, 520f, 380f, pressed = false)

            val drawn = strokes(tab.document).lastOrNull()
            assertNotNull("the drag committed a stroke", drawn)
            assertEquals(
                "the press is the page point under the pointer",
                ((400f - painted.offsetX * density) / (zoom * density)).toDouble(),
                drawn!!.points.first().x,
                0.01,
            )
            assertEquals(
                ((300f - painted.offsetY * density) / (zoom * density)).toDouble(),
                drawn.points.first().y,
                0.01,
            )
            assertEquals(
                "and the last sample too",
                ((520f - painted.offsetX * density) / (zoom * density)).toDouble(),
                drawn.points.last().x,
                0.01,
            )
        } finally {
            scene.close()
            tab.document.close()
        }
    }
}
