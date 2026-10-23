package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The tablet's dial through the window: **one notch, one action**.
 *
 * Windows sends the dial's turn as a plain `WM_MOUSEWHEEL` — the same message, the same ±120 delta and
 * the same absent modifier a mouse wheel produces (`docs/desktop-workstation.md` records the
 * measurement). The only thing that tells them apart is the device the raw report names, which is why
 * the canvas is handed a predicate ([PageCanvas]'s `dialWheel`) rather than a second event stream: the
 * notch Compose already received is routed to zoom when it was the dial's, and left scrolling when it
 * was the mouse's. This suite pushes both through a headless `ImageComposeScene`, so the claim is
 * checked on Compose's real dispatch rather than on the predicate alone.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DialWheelSceneTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val viewW = 500

    private val viewH = 700

    private fun notebook(name: String): File = temp.newFile(name).also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(4))
    }

    private fun tab(name: String): DesktopTab {
        val tab = DesktopTabs().open(DocumentLoader.load(notebook(name)))
        // Pinned, so the anchor arithmetic is the viewport's own and not a fit's.
        tab.fit = FitMode.NONE
        tab.viewport = Viewport(zoom = 1f)
        return tab
    }

    /**
     * One wheel notch at the middle of the view; answers the viewport the canvas published for it.
     *
     * The notch is the dial's clockwise one: Windows reports `WM_MOUSEWHEEL` with `+120`, which AWT turns
     * into a **negative** scroll delta — the direction that zooms *in*, as a mouse wheel pushed away from
     * the reader does.
     */
    private fun scroll(scene: ImageComposeScene, delta: Float = -1f): Viewport? {
        recorded = null
        scene.sendPointerEvent(
            eventType = PointerEventType.Scroll,
            position = Offset(viewW / 2f, viewH / 2f),
            scrollDelta = Offset(0f, delta),
        )
        Snapshot.sendApplyNotifications()
        scene.render()
        return recorded
    }

    /** The viewport the canvas published for the last notch it handled. */
    private var recorded: Viewport? = null

    private fun scene(tab: DesktopTab, dial: () -> Boolean): ImageComposeScene =
        ImageComposeScene(viewW, viewH, Density(1f)) {
            PageCanvas(
                tab = tab,
                panMode = false,
                onViewport = { recorded = it },
                onFit = { tab.fit = it },
                dialWheel = dial,
            )
        }

    @Test
    fun aDialNotchZoomsWhereTheMouseWheelScrolls() {
        val tab = tab("dial.mobix")
        val dial = scene(tab, dial = { true })
        try {
            val zoomed = scroll(dial)
            assertTrue("the notch reached the canvas", zoomed != null)
            assertTrue("the dial zoomed in (${zoomed!!.zoom})", zoomed.zoom > 1f)
            assertEquals("and it is no longer a fit", FitMode.NONE, tab.fit)
        } finally {
            dial.close()
            tab.document.close()
        }

        val mouseTab = tab("mouse.mobix")
        val mouse = scene(mouseTab, dial = { false })
        try {
            // The other way round: a notch read as *reading down* the page, which is the scroll the
            // clamped page can actually make from its top edge.
            val scrolled = scroll(mouse, delta = 1f)
            assertTrue("the notch reached the canvas", scrolled != null)
            assertEquals("the mouse wheel still scrolls, it does not zoom", 1f, scrolled!!.zoom, 1e-6f)
            assertNotEquals(
                "and it moved the page instead",
                0f to 0f,
                scrolled.offsetX to scrolled.offsetY,
            )
        } finally {
            mouse.close()
            mouseTab.document.close()
        }
    }
}
