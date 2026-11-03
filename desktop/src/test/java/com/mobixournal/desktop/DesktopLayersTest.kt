package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.ink.withElement
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.shell.DesktopLayers
import com.mobixournal.desktop.shell.DesktopPages
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.desktop.shell.Header

import androidx.compose.runtime.Composable
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
import com.mobixournal.design.DesignTokens
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **layer manager**, and the capsule on the header band that holds its door.
 *
 * The commands are the tablet's own (`LayerOps`, now shared in `mobix-core`) and the rules under test are the
 * two the two shells must agree on: an edit is **one undo step**, and **the layer picked is the layer new ink
 * lands on**. The picture is a check too — the widened capsule has to show *two* glyphs, because a door with
 * no art reads as nothing between two rims.
 *
 * The band's arithmetic is the layout's own (a 10 dp edge, 36 dp capsules, the two groups' 12 dp gaps and the
 * 284 dp search field), so a capsule that drifted fails here rather than in the owner's eyes.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopLayersTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `adding a layer leaves an empty top layer and writes new ink into it`() {
        val (tabs, layers) = open()
        val tab = tabs.active!!
        val before = tab.document.page(tab.page)
        assertTrue(layers.addLayer())
        val page = tab.document.page(tab.page)
        assertEquals("one more layer above the page's own", before.layers.size + 1, page.layers.size)
        assertTrue("the fresh layer is empty", page.layers.last().elements.isEmpty())
        // "No layer picked" resolves to the top one, so the new layer is where the next stroke lands.
        assertEquals(page.layers.lastIndex, tab.inkLayer(page))
        val grown = page.withElement(stroke(), tab.inkLayer(page))
        assertEquals(1, grown.layers.last().elements.size)
        assertEquals(
            "the layers under it are untouched",
            before.layers.map { it.elements.size },
            grown.layers.dropLast(1).map { it.elements.size },
        )
    }

    @Test
    fun `the picked layer receives the next stroke`() {
        val (tabs, layers) = open()
        val tab = tabs.active!!
        layers.addLayer()
        layers.actions().activate(0)
        val page = tab.document.page(tab.page)
        val counts = page.layers.map { it.elements.size }
        assertEquals(0, tab.inkLayer(page))
        val grown = page.withElement(stroke(), tab.inkLayer(page))
        assertEquals("the bottom layer, because it was picked", counts[0] + 1, grown.layers[0].elements.size)
        assertEquals("and nothing landed on the top one", counts[1], grown.layers[1].elements.size)
    }

    @Test
    fun `a stroke cannot be lost when the picked layer is gone`() {
        val (tabs, _) = open()
        val tab = tabs.active!!
        tab.pickActiveLayer(7)
        val page = tab.document.page(tab.page)
        assertEquals("an index the page does not have falls back to the top layer", page.layers.lastIndex, tab.inkLayer(page))
    }

    @Test
    fun `a rename is one undo step and a blank name clears it`() {
        val (tabs, layers) = open()
        val tab = tabs.active!!
        layers.addLayer()
        layers.beginRename(1)
        assertEquals("the field starts on the name it has", "Layer 2", layers.draft)
        layers.editDraft("Ink")
        assertTrue(layers.commitRename())
        assertEquals("Ink", tab.document.page(tab.page).layers[1].name)
        assertTrue("one undo takes the name back", tab.undo())
        assertNull(tab.document.page(tab.page).layers[1].name)
    }

    @Test
    fun `merging down folds the picked layer into the one below as one step`() {
        val (tabs, layers) = open()
        val tab = tabs.active!!
        layers.addLayer()
        val page = tab.document.page(tab.page)
        val top = page.layers.lastIndex
        val under = top - 1
        val lower = page.layers[under].elements.size
        val upper = page.layers[top].elements.size
        layers.actions().activate(top)
        assertTrue(layers.mergeLayerDown(top))
        val merged = tab.document.page(tab.page)
        assertEquals("one layer fewer", page.layers.size - 1, merged.layers.size)
        assertEquals("the upper layer's ink lands after the lower one's", lower + upper, merged.layers[under].elements.size)
        assertTrue("the merge is one step", tab.undo())
        assertEquals(page.layers.size, tab.document.page(tab.page).layers.size)
    }

    @Test
    fun `the last layer is never deleted`() {
        val (tabs, layers) = open()
        val tab = tabs.active!!
        assertFalse("a page keeps one layer", layers.removeLayer(0))
        assertEquals(1, tab.document.page(tab.page).layers.size)
    }

    @Test
    fun `reordering keeps the picked layer picked`() {
        val (tabs, layers) = open()
        val tab = tabs.active!!
        layers.addLayer()
        layers.addLayer()
        layers.actions().activate(0)
        assertTrue(layers.moveLayer(0, 2))
        assertEquals("the layer follows its own ink", 2, tab.activeLayer)
        assertEquals("and the page has three layers still", 3, tab.document.page(tab.page).layers.size)
    }

    @Test
    fun `the document capsule draws the pages and layers doors`() {
        val (tabs, layers, pages) = header()
        val width = 900
        val scene = ImageComposeScene(width, DesktopTheme.headerHeight.value.toInt(), Density(1f)) {
            band(tabs, pages, layers)
        }
        val png = frame(scene)
        scene.close()
        File("build/desktop-render/header-document-capsule.png").apply { parentFile?.mkdirs() }.writeBytes(png)
        val image = ImageIO.read(ByteArrayInputStream(png))
        val right = capsuleRight(width)
        val half = DesktopTheme.headerControlSize.value / 2f
        // No hairline stands between the two slots, so the pages door is exactly the left half.
        val pagesCentre = right - 2 * DesktopTheme.headerControlSize.value + half
        val layersCentre = right - half
        for ((name, centre) in listOf("pages" to pagesCentre, "layers" to layersCentre)) {
            val inked = ((centre - half + 2f).toInt()..<(centre + half - 2f).toInt()).sumOf { x ->
                (0 until image.height).count { y -> image.getRGB(x, y) and 0xFFFFFF >= INK_FLOOR }
            }
            assertTrue("the $name door has its glyph drawn in it", inked > 8)
        }
    }

    @Test
    fun `the layers door opens its menu and the second click puts it away`() {
        val (tabs, layers, pages) = header()
        val width = 900
        val scene = ImageComposeScene(width, 420, Density(1f)) { band(tabs, pages, layers) }
        try {
            val rest = frame(scene)
            val x = capsuleRight(width) - DesktopTheme.headerControlSize.value / 2f
            val y = DesktopTheme.headerHeight.value / 2f
            tap(scene, x, y)
            val opened = frame(scene)
            assertFalse("the door opens a surface", rest.contentEquals(opened))
            tap(scene, x, y)
            val closed = frame(scene)
            assertFalse("and the same control closes it", opened.contentEquals(closed))
        } finally {
            scene.close()
        }
    }

    private fun header(): Triple<DesktopTabs, DesktopLayers, DesktopPages> {
        val tabs = DesktopTabs()
        tabs.open(DocumentLoader.load(twoPages()))
        return Triple(tabs, DesktopLayers(tabs) { _, _ -> }, DesktopPages(tabs, { _, _ -> }, { 595.0 to 842.0 }))
    }

    @Composable
    private fun band(tabs: DesktopTabs, pages: DesktopPages, layers: DesktopLayers) = Header(
        tabs = tabs,
        sidebarVisible = true,
        onActivate = {},
        onClose = {},
        onMove = { _, _ -> },
        onNew = {},
        onOpen = {},
        onSave = {},
        onSaveAs = {},
        onExportPdf = {},
        onExportSvg = {},
        onToggleSidebar = {},
        onSettings = {},
        onUndo = {},
        onRedo = {},
        pages = pages.actions({}, {}),
        layers = layers.actions(),
    )

    private fun open(): Pair<DesktopTabs, DesktopLayers> {
        val tabs = DesktopTabs()
        tabs.open(DocumentLoader.load(twoPages()))
        return tabs to DesktopLayers(tabs) { _, _ -> }
    }

    private fun stroke() = Stroke(Tool.PEN, 0, "round", listOf(StrokePoint(10.0, 10.0, 1.0)), true)

    private fun twoPages(): File = temp.newFile("layers.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    /** One frame of the open scene, with the snapshot notifications a frame loop would send. */
    private fun frame(scene: ImageComposeScene): ByteArray {
        Snapshot.sendApplyNotifications()
        return requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
    }

    /**
     * The capsule's right edge, counted from the window's right: the edge, the PDF and Save capsules, the two
     * groups' gaps, the history pair, the search field the capsule stands beside, and the **same gap** the
     * field keeps on its other side — the two distances either side of the field are one number.
     */
    private fun capsuleRight(width: Int): Float = width.toFloat() - HEADER_EDGE_DP - 4 * CONTROL -
        2 * GROUP_GAP - 2 * GAP - SEARCH_FIELD_WIDTH - GROUP_GAP

    /** A tap, on a clock that only ever moves forward (Compose drops an event older than the last one). */
    private fun tap(scene: ImageComposeScene, x: Float, y: Float) {
        mouse(scene, PointerEventType.Press, x, y, clock)
        mouse(scene, PointerEventType.Release, x, y, clock + 32L, pressed = false)
        clock += 512L
    }

    private var clock = 1_000L

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
        Snapshot.sendApplyNotifications()
        scene.render()
    }

    private companion object {
        /** Anything at or above this tone is a glyph stroke: the chrome ink is pure white. */
        const val INK_FLOOR = 0x909090

        /** The band's own side padding (`Header`). */
        const val HEADER_EDGE_DP = 10f

        /** A header control's capsule, in dp — `HEADER_CONTROL_SIZE_DP`. */
        const val CONTROL = DesignTokens.HEADER_CONTROL_SIZE_DP

        /** The gap either side of a group, and the air between two capsules of one group. */
        const val GROUP_GAP = 12f
        const val GAP = 6f

        /** The band's always-on search field (`HeaderSearch.SEARCH_FIELD_WIDTH`). */
        const val SEARCH_FIELD_WIDTH = 284f
    }
}
