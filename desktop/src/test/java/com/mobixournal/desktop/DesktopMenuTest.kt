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
    private val height: Float = with(MenuMetrics) {
        (padding * 2 + tileHeight + sectionGap + chipHeight + sectionGap + dividerHeight + rowHeight).value
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

    private fun rowCentreY(): Float = with(MenuMetrics) {
        (padding + tileHeight + sectionGap + chipHeight + sectionGap + dividerHeight + rowHeight / 2).value
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
