package com.mobixournal.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The shell's parts, rendered and driven headlessly.
 *
 * The window itself needs a real frame (`FrameWindowScope`), so what is checked here is every piece the
 * window is made of: the strip with two tabs, the sidebar (and the thumbnail cache that keeps it from
 * repainting pages), and the two helpers the keyboard calls. The renders are kept under
 * `build/desktop-render/` so a reviewer can look at the chrome instead of trusting a byte count.
 */
class DesktopShellTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun document(name: String, pages: Int = 6): DesktopDocument {
        val file = temp.newFile(name)
        SyntheticNotebook.write(file, SyntheticNotebook.pages(pages))
        return DocumentLoader.load(file)
    }

    private fun tabsOf(vararg names: String): DesktopTabs = DesktopTabs().apply {
        for (name in names) open(document(name))
    }

    @Test
    fun `the strip renders two tabs and marks the active one`() {
        val tabs = tabsOf("capitolo-1.mobix", "capitolo-2.mobix")
        tabs.activate(1)

        val png = renderStrip(tabs, name = "shell-tabs")
        val image = ImageIO.read(png)
        assertEquals(420, image.width)
        // Two tabs and a "+" are three surfaces of different shades: the strip is not one flat band.
        val shades = mutableSetOf<Int>()
        for (x in 0 until image.width step 3) {
            for (y in 0 until image.height step 3) shades += image.getRGB(x, y) and 0xFFFFFF
        }
        assertTrue("the strip has more than one surface (${shades.size} shades)", shades.size >= 2)
        assertTrue("the active tab is drawn lighter", shades.any { it == 0x2A2A31 })
    }

    @Test
    fun `the sidebar renders thumbnails and keeps them cached`() {
        val tab = tabsOf("quaderno.mobix", "secondo.mobix").active!!
        val cache = mutableMapOf<Int, ImageBitmap>()

        val first = thumbnail(tab, 0, THUMB_WIDTH.value, cache)
        assertNotNull("the first page produced a thumbnail", first)
        assertTrue("and was cached", cache.containsKey(0))
        val second = thumbnail(tab, 0, THUMB_WIDTH.value, cache)
        assertSame("the second ask reuses the bitmap instead of repainting the page", first, second)

        val png = renderSidebar(tab, cache, name = "shell-sidebar")
        assertTrue("the sidebar rendered", png.length() > 2_000)
    }

    @Test
    fun `selecting a page clamps and re-centres the viewport`() {
        val tab = tabsOf("pagine.mobix").active!!
        tab.page = 2
        tab.viewport = Viewport(1.5f, -220f, -90f)

        selectPage(tab, 99)
        assertEquals("clamped to the last page", tab.pageCount - 1, tab.page)
        assertEquals("and the free pan is reset", 0f, tab.viewport.offsetX, 0f)
        assertEquals(0f, tab.viewport.offsetY, 0f)

        selectPage(tab, -5)
        assertEquals(0, tab.page)
    }

    @Test
    fun `a keyboard zoom step leaves the fit mode and stops at the range`() {
        val tab = tabsOf("zoom.mobix").active!!
        tab.fit = FitMode.PAGE
        tab.viewport = Viewport(1f, 0f, 0f)

        zoomBy(tab, ZOOM_STEP)
        assertEquals("a manual zoom is no longer a fit", FitMode.NONE, tab.fit)
        assertEquals(ZOOM_STEP, tab.viewport.zoom, 1e-4f)

        repeat(20) { zoomBy(tab, ZOOM_STEP) }
        assertEquals("never past 400%", Viewport.MAX_ZOOM, tab.viewport.zoom, 1e-4f)

        repeat(40) { zoomBy(tab, 1f / ZOOM_STEP) }
        assertEquals("never under 25%", Viewport.MIN_ZOOM, tab.viewport.zoom, 1e-4f)
    }

    @Test
    fun `the status bar's figures follow the tab`() {
        val tab = tabsOf("stato.mobix", "altro.mobix").active!!
        assertEquals("Page 1 / 6", pageLabel(0, 6))
        assertEquals("250%", zoomLabel(2.5f))
        assertEquals(1f, pageProgress(5, 6), 1e-4f)
        assertEquals(0.5f, pageProgress(2, 6), 1e-4f)
        assertEquals("a document of one page is complete", 1f, pageProgress(0, 1), 0f)
        assertEquals("a Layer without a name is numbered", "Layer 2", tab.document.page(0).layers.first().label(1))
    }

    @Test
    fun `zoom in is a physical key, not a character`() {
        // `Ctrl + =` on a US layout, `Ctrl + Shift + 0` on an Italian one (where `=` is Shift+0),
        // and the keypad's `+`, which is the only one every layout agrees on.
        assertTrue(isZoomIn(Key.Equals, shift = false))
        assertTrue(isZoomIn(Key.Zero, shift = true))
        assertTrue(isZoomIn(Key.NumPadAdd, shift = false))
        assertTrue(isZoomIn(Key.Plus, shift = false))
        assertFalse("Ctrl + 0 stays fit page", isZoomIn(Key.Zero, shift = false))
        assertFalse(isZoomIn(Key.Minus, shift = false))

        assertTrue(isZoomOut(Key.Minus))
        assertTrue(isZoomOut(Key.NumPadSubtract))
        assertFalse(isZoomOut(Key.Equals))
    }

    /**
     * The regression this guards: the strip is composed once and then has to *hear* about a document
     * opening. When [DesktopTabs] held plain `var`s, opening a file changed no snapshot state, so the
     * strip was never redrawn — the window showed `≡` and `+` with a page open beside them.
     */
    @Test
    fun `a document opened after the strip is on screen shows up in it`() {
        val tabs = DesktopTabs()
        val scene = ImageComposeScene(420, 34, Density(1f)) {
            TabStrip(
                tabs = tabs,
                onActivate = {},
                onClose = {},
                onMove = { _, _ -> },
                onOpen = {},
            )
        }
        try {
            val empty = frame(scene, "shell-tabs-empty")
            tabs.open(document("tardi.mobix"))
            val filled = frame(scene, "shell-tabs-late-open")

            assertFalse("opening a document repaints the strip", empty.contentEquals(filled))
            assertTrue("and draws the new active tab", shades(filled).contains(0x2A2A31))

            tabs.close(0)
            assertFalse("closing it repaints the strip too", filled.contentEquals(frame(scene, "shell-tabs-closed")))
        } finally {
            scene.close()
        }
    }

    /**
     * The strip's whole point, in a window of its own: the page is fitted to its **width**, so the
     * writing is readable, and what you cannot see is below the fold instead of shrunk into a sliver.
     *
     * The fixture is the shape the tablet app really writes when a page is filled to the bottom
     * (850 × 2976 pt): fitting it whole is 27 %, which is a thumbnail with a zoom readout.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `a strip page opens fitted to its width, not shrunk to fit the window`() {
        val file = temp.newFile("strip.mobix")
        SyntheticNotebook.write(
            file,
            listOf(
                Page(
                    width = 850.0,
                    height = 2976.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "graph"),
                    layers = listOf(Layer(listOf(SyntheticNotebook.stroke(40.0)))),
                    id = "pg_strip",
                ),
            ),
        )
        val tab = DesktopTabs().open(DocumentLoader.load(file))
        try {
            assertEquals("a strip asks for a width fit", FitMode.WIDTH, tab.fitFor(850f, 2976f))

            val side = 700
            val scene = ImageComposeScene(side, side, Density(1f)) {
                Box(Modifier.size(side.dp, side.dp)) {
                    PageCanvas(
                        tab = tab,
                        panMode = false,
                        onViewport = { tab.viewport = it },
                        onFit = { tab.fit = it },
                    )
                }
            }
            try {
                val png = frame(scene, "shell-strip-fit")
                val expected = Viewport.fitWidthZoom(850f, 700f)
                assertEquals("the page is shown at its width fit", expected, tab.viewport.zoom, 0.01f)
                assertTrue("which is readable, not a sliver: ${tab.viewport.zoom}", tab.viewport.zoom > 0.5f)
                assertTrue(
                    "and the rest of the sheet is below the fold",
                    2976f * tab.viewport.zoom > side,
                )

                // The pixels say the sheet really is stretched across the window: a row through its
                // middle is paper from one margin to the other, not a narrow column on the desk.
                val image = ImageIO.read(ByteArrayInputStream(png))
                var paper = 0
                for (x in 0 until image.width) {
                    val rgb = image.getRGB(x, side / 2) and 0xFFFFFF
                    if (maxOf(rgb shr 16, (rgb shr 8) and 0xFF, rgb and 0xFF) > 0xC0) paper++
                }
                assertTrue("the sheet spans the window's width ($paper of $side px)", paper >= side - 80)
            } finally {
                scene.close()
            }
        } finally {
            tab.document.close()
        }
    }

    // --- rendering ------------------------------------------------------------------------------

    @OptIn(ExperimentalComposeUiApi::class)
    private fun renderStrip(tabs: DesktopTabs, name: String): File {
        val scene = ImageComposeScene(420, 34, Density(1f)) {
            TabStrip(
                tabs = tabs,
                onActivate = {},
                onClose = {},
                onMove = { _, _ -> },
                onOpen = {},
            )
        }
        return save(scene, name)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun renderSidebar(tab: DesktopTab, cache: MutableMap<Int, ImageBitmap>, name: String): File {
        val scene = ImageComposeScene(240, 460, Density(1f)) {
            PageSidebar(tab = tab, cache = cache, onSelectPage = {})
        }
        return save(scene, name)
    }

    /**
     * One frame of a scene that stays open, kept on disk so the frames can be compared by eye too.
     *
     * Outside a composition nothing advances the snapshot's write notifications, so a state write made
     * between two renders would **sometimes** not have reached the recomposer yet — a test that passes
     * or fails with the machine's mood. This call is what a running frame loop does every frame.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun frame(scene: ImageComposeScene, name: String): ByteArray {
        Snapshot.sendApplyNotifications()
        val png = scene.render().encodeToData()?.bytes
        assertNotNull("Skia produced a PNG", png)
        File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(png!!)
        return png
    }

    /** The distinct colours in a rendered frame, for "is anything drawn here" assertions. */
    private fun shades(png: ByteArray): Set<Int> {
        val image = ImageIO.read(ByteArrayInputStream(png))
        val found = mutableSetOf<Int>()
        for (x in 0 until image.width step 3) {
            for (y in 0 until image.height step 3) found += image.getRGB(x, y) and 0xFFFFFF
        }
        return found
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun save(scene: ImageComposeScene, name: String): File {
        val png = try {
            scene.render().encodeToData()?.bytes
        } finally {
            scene.close()
        }
        assertNotNull("Skia produced a PNG", png)
        val kept = File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }
        kept.writeBytes(png!!)
        return kept
    }
}
