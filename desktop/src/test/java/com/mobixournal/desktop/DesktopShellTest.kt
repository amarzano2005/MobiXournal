package com.mobixournal.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
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
        assertTrue("the active tab is drawn in the active slot", shades.any { it == activeSlotArgb })
    }

    @Test
    fun `the sidebar renders thumbnails and keeps them cached`() {
        val tab = tabsOf("quaderno.mobix", "secondo.mobix").active!!
        val cache = mutableMapOf<Int, ImageBitmap>()

        val first = thumbnail(tab, 0, THUMB_WIDTH.value, cache)
        assertNotNull("the first page produced a thumbnail", first)
        assertTrue("and was cached", cache.containsKey(0))
        // Painted onto a raster surface rather than through a scene (`renderPageBitmap`), so this is the
        // check that the sheet and its ink really landed on it instead of an empty tile.
        val pixels = first!!.toPixelMap()
        val shades = mutableSetOf<androidx.compose.ui.graphics.Color>()
        for (x in 0 until pixels.width step 4) {
            for (y in 0 until pixels.height step 4) shades += pixels[x, y]
        }
        assertTrue("the thumbnail is a page, not an empty tile (${shades.size} shades)", shades.size >= 2)
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
        assertEquals("never past the deepest zoom", Viewport.MAX_ZOOM, tab.viewport.zoom, 1e-4f)

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
                onNew = {},
            )
        }
        try {
            val empty = frame(scene, "shell-tabs-empty")
            tabs.open(document("tardi.mobix"))
            val filled = frame(scene, "shell-tabs-late-open")

            assertFalse("opening a document repaints the strip", empty.contentEquals(filled))
            assertTrue("and draws the new active tab", shades(filled).contains(activeSlotArgb))

            tabs.close(0)
            assertFalse("closing it repaints the strip too", filled.contentEquals(frame(scene, "shell-tabs-closed")))
        } finally {
            scene.close()
        }
    }

    /**
     * The shortcut table, as a decision: which key is which command, and what a command does.
     *
     * `onKey` itself cannot be driven here — Compose's `KeyEvent` cannot be constructed outside a real
     * window — which is why the translation ([shellCommand]) and the action ([DesktopShellState.apply])
     * are separate. This exercises both halves, including the ordering that made `Ctrl+Shift+0` a
     * zoom-in rather than a fit-page.
     */
    @Test
    fun `the window's keys decide and act on the open documents`() {
        assertSame(ShellCommand.ZOOM_IN, shellCommand(Key.Equals, ctrl = true, shift = false))
        assertSame("Ctrl+Shift+0 is zoom in, not fit page", ShellCommand.ZOOM_IN, shellCommand(Key.Zero, ctrl = true, shift = true))
        assertSame(ShellCommand.FIT_PAGE, shellCommand(Key.Zero, ctrl = true, shift = false))
        assertSame(ShellCommand.FIT_WIDTH, shellCommand(Key.One, ctrl = true, shift = false))
        assertSame("Ctrl+Z is undo", ShellCommand.UNDO, shellCommand(Key.Z, ctrl = true, shift = false))
        assertSame("Ctrl+Shift+Z is redo", ShellCommand.REDO, shellCommand(Key.Z, ctrl = true, shift = true))
        assertSame(ShellCommand.NEXT_PAGE, shellCommand(Key.PageDown, ctrl = false, shift = false))
        assertSame("Ctrl+PageDown is not page-down", null, shellCommand(Key.PageDown, ctrl = true, shift = false))
        assertSame(ShellCommand.DISMISS_FAILURE, shellCommand(Key.Escape, ctrl = false, shift = false))

        val state = DesktopShellState()
        state.open(temp.newFile("primo.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(3)) })
        state.open(temp.newFile("secondo.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(3)) })
        assertEquals("two documents open", 2, state.tabs.size)

        state.apply(ShellCommand.NEXT_PAGE)
        assertEquals("Page Down advances the active document", 1, state.active!!.page)

        state.apply(ShellCommand.CLOSE_TAB)
        assertEquals("Ctrl+W closes it", 1, state.tabs.size)

        state.apply(ShellCommand.TOGGLE_SIDEBAR)
        assertFalse("Ctrl+B hides the sidebar", state.sidebarVisible)

        state.tabs.closeAll()

        var picked = false
        assertTrue(
            "Ctrl+T opens the picker with nothing open — the shortcut that gets a first document on screen",
            state.apply(ShellCommand.OPEN, onOpenPicker = { picked = true }),
        )
        assertTrue("and it really asked for the picker", picked)
        assertFalse("Esc with no message to dismiss is not consumed", state.apply(ShellCommand.DISMISS_FAILURE))
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

    /**
     * The whole window, painted: the empty state and a document, written to `build/desktop-render/shell.png`
     * so it can be looked at — and checked where a screenshot cannot be trusted.
     *
     * Three claims: the header is **glass over the desk** (no paper showing through it), the page is
     * drawn in the canvas **below** the header rather than under it, and the sheet carries the window's
     * one shadow — a pixel just outside its edge is no longer pure black.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the shell paints chrome above a page that starts below the header`() {
        // A settings file of this test's own: the placement a developer happens to have picked on this
        // machine must not change what the shell's chrome test measures.
        val state = DesktopShellState(DesktopSettings(temp.newFile("settings.properties")))
        val scene = ImageComposeScene(1000, 700, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            val empty = frame(scene, "shell-empty")
            state.open(temp.newFile("shell.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(6)) })
            val filled = frame(scene, "shell")
            assertFalse("opening a document repaints the shell", empty.contentEquals(filled))

            val image = ImageIO.read(ByteArrayInputStream(filled))
            val header = DesktopTheme.headerHeight.value.toInt()
            assertTrue("the header has a height", header in 1 until image.height)

            // The band is the glass, never the sheet: no white pixel (the paper) may appear inside it.
            // The chrome's own ink is near-white, so only the exact paper colour is rejected.
            for (y in 0 until header) {
                for (x in 0 until image.width step 5) {
                    val rgb = image.getRGB(x, y) and 0xFFFFFF
                    assertTrue(
                        "the page shows through the header at ($x, $y): #%06X".format(rgb),
                        rgb != 0xFFFFFF,
                    )
                }
            }

            // The page is in the canvas: a row below the header holds paper.
            val row = header + 120
            var paper = 0
            var edge = -1
            for (x in 0 until image.width) {
                if ((image.getRGB(x, row) and 0xFFFFFF) == 0xFFFFFF) {
                    paper++
                    if (edge < 0) edge = x
                }
            }
            assertTrue("the page is painted below the header ($paper px)", paper > image.width / 4)
            assertTrue("the sheet has a left edge", edge > 3)

            // …and it carries the window's one shadow: the desk beside it is lifted off black.
            val beside = image.getRGB(edge - 4, row) and 0xFFFFFF
            assertTrue(
                "the sheet must cast its shadow on the desk (found #%06X)".format(beside),
                beside != 0,
            )
        } finally {
            scene.close()
            state.tabs.closeAll()
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
                onNew = {},
            )
        }
        return save(scene, name)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun renderSidebar(tab: DesktopTab, cache: MutableMap<Int, ImageBitmap>, name: String): File {
        val scene = ImageComposeScene(240, 460, Density(1f)) {
            PageSidebar(tab = tab, cache = cache, onSelectPage = {}, revision = 0)
        }
        return save(scene, name)
    }

    /**
     * A page thumbnail is painted **once the hand stops**, never inside the frames the hand is producing.
     *
     * Painting one means rendering a whole page through a second `ImageComposeScene` — 29–39 ms on
     * `udiff.xopp`, measured — so keying the sidebar on the document's edit count made the window drop
     * frames on every finished letter and on every bite of the eraser. The sidebar now keys on the shell's
     * deferred revision: an edit drops the stale bitmap, and the quiet moment re-renders that one page
     * (the other rows still hit the cache).
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `an edit does not repaint a thumbnail until the edits go quiet`() {
        val tab = DesktopTabs().open(document("thumbs.mobix", pages = 3))
        val cache = CountingCache()
        val revision = mutableStateOf(0)
        val scene = ImageComposeScene(240, 460, Density(1f)) {
            PageSidebar(tab = tab, cache = cache, onSelectPage = {}, revision = revision.value)
        }
        try {
            // The paint is **off the UI thread** — that is the fix — so the test waits for the hand-over
            // instead of expecting the bitmap inside the frame that asked for it.
            val shown = awaitSettled(scene, cache)
            assertTrue("the sidebar painted the pages it shows ($shown)", shown > 0)

            // What a committed stroke does: the document edits, and the shell drops that page's bitmap.
            tab.document.editPage(0) { page -> page.withElement(SyntheticNotebook.stroke(400.0)) }
            cache.remove(0)
            Snapshot.sendApplyNotifications()
            scene.render()
            // A re-render would land within a frame or two: give it several and expect nothing.
            Thread.sleep(200)
            scene.render()
            assertEquals("no thumbnail was painted mid-stroke", shown, cache.writes)

            // …and the quiet moment the shell schedules afterwards: one page painted again, not the list.
            revision.value = 1
            Snapshot.sendApplyNotifications()
            scene.render()
            assertEquals("the edited page alone is painted again", shown + 1, awaitSettled(scene, cache))
        } finally {
            scene.close()
            tab.document.close()
        }
    }

    /**
     * Frames until the sidebar's thumbnails have **stopped arriving**, and how many there were — the paint
     * is a background job now, so a row that has not been handed its bitmap yet is a row still painting.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun awaitSettled(scene: ImageComposeScene, cache: CountingCache): Int {
        val deadline = System.currentTimeMillis() + 10_000L
        var quiet = 0
        var last = -1
        while (System.currentTimeMillis() < deadline) {
            Snapshot.sendApplyNotifications()
            scene.render()
            quiet = if (cache.writes == last) quiet + 1 else 0
            last = cache.writes
            if (quiet >= 8) return last
            Thread.sleep(20)
        }
        return last
    }

    /** A thumbnail cache that counts what was painted into it — the render happens only on a miss. */
    private class CountingCache(
        private val delegate: MutableMap<Int, ImageBitmap> = java.util.concurrent.ConcurrentHashMap(),
    ) : MutableMap<Int, ImageBitmap> by delegate {
        /** Written from the sidebar's own painting thread (`ThumbnailPaint`), read from the test's. */
        @Volatile
        var writes: Int = 0
            private set

        override fun put(key: Int, value: ImageBitmap): ImageBitmap? {
            writes++
            return delegate.put(key, value)
        }
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

    /**
     * The active slot's rgb, read from the theme: the strip's "is this tab selected" pixel check must
     * follow the token, not a literal that a token change would silently outdate.
     */
    private val activeSlotArgb: Int get() = DesktopTheme.activeSlot.toArgb() and 0xFFFFFF

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
