package com.mobixournal.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The overflow menu as the mobile bar draws it: four tiles, two export chips, the page list under a rule.
 *
 * A `Popup` cannot be rendered in a headless scene, which is why the panel is its own composable
 * (`MenuPanel`) and this draws it directly. The clicks use [MenuMetrics] — the very numbers the layout
 * reads — so the test proves the commands are wired and the row is where the panel says it is, without
 * hard-coding a coordinate that a change of padding would silently invalidate.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopMenuTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val counters = IntArray(7)

    private val width = MenuMetrics.width.value
    /** The scene fits the panel, Pen block included — a row clipped by the frame cannot be clicked. */
    private val height: Float = with(MenuMetrics) {
        (
            padding * 2 + tileHeight + sectionGap + chipHeight + sectionGap +
                rowHeight + sectionGap + dividerHeight + rowHeight * 2
            ).value
    }

    @Test
    fun `every row of the panel runs its command`() {
        val scene = scene()
        try {
            var at = 1_000L
            for (index in 0..3) {
                tap(scene, tileCentre(index), tileCentreY(), at); at += 512L
            }
            tap(scene, chipCentre(0), chipCentreY(), at); at += 512L
            tap(scene, chipCentre(1), chipCentreY(), at); at += 512L
            tap(scene, width / 2f, rowCentreY(), at)

            assertEquals("New", 1, counters[NEW])
            assertEquals("Open", 1, counters[OPEN])
            assertEquals("Save", 1, counters[SAVE])
            assertEquals("Save As", 1, counters[SAVE_AS])
            assertEquals("Export PDF", 1, counters[PDF])
            assertEquals("Export SVG", 1, counters[SVG])
            assertEquals("the page list's row", 1, counters[SIDEBAR])
        } finally {
            scene.close()
        }
    }

    @Test
    fun `with nothing open the file commands go dead and the rest still answer`() {
        val scene = scene(canSave = false, canExport = false)
        try {
            var at = 1_000L
            tap(scene, tileCentre(2), tileCentreY(), at); at += 512L
            tap(scene, chipCentre(0), chipCentreY(), at); at += 512L
            tap(scene, tileCentre(0), tileCentreY(), at)

            assertEquals("a clean document has nothing to save", 0, counters[SAVE])
            assertEquals("and nothing to export", 0, counters[PDF])
            assertEquals("but New still opens a page", 1, counters[NEW])
        } finally {
            scene.close()
        }
    }

    /**
     * The panel's picture: white art on the tiles, the PDF chip in the document red, the raster in the
     * accent — the tablet's own colour rule for its exports.
     *
     * The frame is filed in `desktop/build/desktop-render/` so the panel can be looked at next to the
     * tablet's, which is what "the same menu" has to mean in the end.
     */
    @Test
    fun `the tiles wear the chrome ink, the pdf chip the document red and the raster the accent`() {
        val scene = scene()
        val png = try {
            Snapshot.sendApplyNotifications()
            requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
        } finally {
            scene.close()
        }
        File("build/desktop-render/desktop-menu.png").apply { parentFile?.mkdirs() }.writeBytes(png)
        val image = ImageIO.read(ByteArrayInputStream(png))

        fun pixelsAround(x: Float, y: Float, half: Int): List<Int> = buildList {
            for (px in (x.toInt() - half)..(x.toInt() + half)) {
                for (py in (y.toInt() - half)..(y.toInt() + half)) {
                    add(image.getRGB(px, py))
                }
            }
        }

        for (index in 0..3) {
            val white = pixelsAround(tileCentre(index), tileCentreY(), 12)
                .count { (it and 0xFFFFFF) >= INK_FLOOR }
            assertTrue("tile $index draws its glyph and label", white > 12)
        }
        val pdfChip = pixelsAround(chipCentre(0), chipCentreY(), 16)
            .count { colour -> ((colour shr 16) and 0xFF) > (colour and 0xFF) + 60 }
        assertTrue("the PDF chip is written in the document red", pdfChip > 12)

        val rasterChip = pixelsAround(chipCentre(1), chipCentreY(), 16)
            .count { colour -> (colour and 0xFF) > ((colour shr 16) and 0xFF) + 40 }
        assertTrue("the raster chip is written in the accent", rasterChip > 12)
    }

    /**
     * The Pen block **with the pen's presets in it** — the block a reader opens mid-drawing, drawn whole so
     * the heading, the pressure rows and the chips can be looked at instead of trusted.
     *
     * The frame is filed as `desktop-pen-presets.png`. The second frame has no settings, so it draws no Pen
     * block; the two differing is what proves the picture is the block's and not the desk's.
     */
    @Test
    fun `the pen block draws the pressure rows and the presets`() {
        val policy = DesktopSettingsState(DesktopSettings(File(temp.root, "menu-pen.properties")))
        policy.beginPresetSave()
        policy.typePresetName("Light hand")
        policy.commitPresetSave()

        val drawn = panelFrame(policy)
        val bare = panelFrame(null)
        File("build/desktop-render/desktop-pen-presets.png").apply { parentFile?.mkdirs() }.writeBytes(drawn)
        assertTrue("the Pen block is drawn, not only the desk", !drawn.contentEquals(bare))
    }

    /**
     * The hover step on an export chip, drawn so the shape of the lit area can be looked at: the whole
     * capsule, not the label's box.
     */
    @Test
    fun `hovering an export chip lights the whole capsule`() {
        val scene = scene()
        val (plain, lit) = try {
            Snapshot.sendApplyNotifications()
            val before = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            mouse(scene, PointerEventType.Move, chipCentre(1), chipCentreY(), 1_000L, pressed = false)
            Snapshot.sendApplyNotifications()
            val after = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            before to after
        } finally {
            scene.close()
        }
        File("build/desktop-render/desktop-menu-hover.png").apply { parentFile?.mkdirs() }.writeBytes(lit)

        // The bug this pins: the hint used to be a layer above the capsule and could be painted at the
        // label's bounds, so the chip lit only where the words were. Both ends must brighten.
        val before = ImageIO.read(ByteArrayInputStream(plain))
        val after = ImageIO.read(ByteArrayInputStream(lit))
        val y = (MenuMetrics.padding + MenuMetrics.tileHeight + MenuMetrics.sectionGap +
            MenuMetrics.chipHeight / 2).value.toInt()
        fun brightened(x: Float): Boolean {
            val px = x.toInt()
            val from = before.getRGB(px, y) and 0xFF
            val to = after.getRGB(px, y) and 0xFF
            return to > from
        }
        assertTrue("the chip's left end lights up", brightened(chipCentre(1) - 55f))
        assertTrue("and its far right end does too", brightened(chipCentre(1) + 55f))
    }

    /** One frame of the panel, tall enough that nothing in the Pen block is clipped. */
    private fun panelFrame(policy: DesktopSettingsState?): ByteArray {
        val scene = ImageComposeScene(width.toInt(), 560, Density(1f)) {
            Box(Modifier.size(width.dp, 560.dp).background(DesktopTheme.desk)) {
                MenuPanel(
                    sidebarVisible = true,
                    canSave = true,
                    canExport = true,
                    onNew = {},
                    onOpen = {},
                    onSave = {},
                    onSaveAs = {},
                    onExportPdf = {},
                    onExportSvg = {},
                    onToggleSidebar = {},
                    onSettings = {},
                    onDismiss = {},
                    penPolicy = policy,
                )
            }
        }
        return try {
            Snapshot.sendApplyNotifications()
            requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
        } finally {
            scene.close()
        }
    }

    private fun scene(canSave: Boolean = true, canExport: Boolean = true): ImageComposeScene =
        ImageComposeScene(width.toInt(), height.toInt(), Density(1f)) {
            // The desk behind the panel, so the glass has something to be glass over — as it does in
            // the window, where a menu lies over the page.
            Box(Modifier.size(width.dp, height.dp).background(DesktopTheme.desk)) {
                MenuPanel(
                    sidebarVisible = true,
                    canSave = canSave,
                    canExport = canExport,
                    onNew = { counters[NEW]++ },
                    onOpen = { counters[OPEN]++ },
                    onSave = { counters[SAVE]++ },
                    onSaveAs = { counters[SAVE_AS]++ },
                    onExportPdf = { counters[PDF]++ },
                    onExportSvg = { counters[SVG]++ },
                    onToggleSidebar = { counters[SIDEBAR]++ },
                    onSettings = {},
                )
            }
        }

    private fun tileCentre(index: Int): Float = with(MenuMetrics) {
        (padding + tileWidth / 2 + (tileWidth + tileGap) * index).value
    }

    private fun tileCentreY(): Float = (MenuMetrics.padding + MenuMetrics.tileHeight / 2).value

    private fun chipCentre(index: Int): Float = with(MenuMetrics) {
        (padding + chipWidth / 2 + (chipWidth + chipGap) * index).value
    }

    private fun chipCentreY(): Float = with(MenuMetrics) {
        (padding + tileHeight + sectionGap + chipHeight / 2).value
    }

    /**
     * The page-list row, by the panel's own arithmetic: the tiles, the export chips, the **Pen heading**
     * (the block below it is the shell's settings, absent in this test), the rule, then the row.
     */
    private fun rowCentreY(): Float = with(MenuMetrics) {
        (
            padding + tileHeight + sectionGap + chipHeight + sectionGap +
                rowHeight + sectionGap + dividerHeight + rowHeight / 2
            ).value
    }

    private fun tap(scene: ImageComposeScene, x: Float, y: Float, time: Long) {
        mouse(scene, PointerEventType.Press, x, y, time)
        mouse(scene, PointerEventType.Release, x, y, time + 32L, pressed = false)
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
        Snapshot.sendApplyNotifications()
        scene.render()
    }

    private companion object {
        const val NEW = 0
        const val OPEN = 1
        const val SAVE = 2
        const val SAVE_AS = 3
        const val PDF = 4
        const val SVG = 5
        const val SIDEBAR = 6

        /** Anything at or above this tone is a glyph stroke: the chrome ink is pure white. */
        const val INK_FLOOR = 0x909090
    }
}
