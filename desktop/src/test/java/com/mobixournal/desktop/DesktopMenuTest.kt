package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.settings.DesktopSettingsState
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.shell.MenuMetrics
import com.mobixournal.desktop.shell.MenuPanel

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
 * The overflow menu as the flat workstation draws it: **one row per command**, three groups split by a
 * hairline, and the Pen block folded behind its own row.
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

    private val counters = IntArray(11)

    private val width = MenuMetrics.width.value

    /** The scene fits the panel with the Pen block **folded**: ten rows, two rules and the inset. */
    private val height: Float = with(MenuMetrics) {
        (inset * 2 + rowHeight * ROWS + dividerHeight * 2).value + 4f
    }

    @Test
    fun `every row of the panel runs its command`() {
        val scene = scene()
        try {
            var at = 1_000L
            for (row in 0..6) {
                tap(scene, width / 2f, rowCentreY(row), at); at += 512L
            }
            tap(scene, width / 2f, rowCentreY(SIDEBAR_ROW), at)

            assertEquals("New", 1, counters[NEW])
            assertEquals("Open", 1, counters[OPEN])
            assertEquals("Save", 1, counters[SAVE])
            assertEquals("Save As", 1, counters[SAVE_AS])
            assertEquals("Import PDF", 1, counters[IMPORT])
            assertEquals("Export PDF", 1, counters[PDF])
            assertEquals("Export As", 1, counters[EXPORT_AS])
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
            tap(scene, width / 2f, rowCentreY(SAVE_ROW), at); at += 512L
            tap(scene, width / 2f, rowCentreY(PDF_ROW), at); at += 512L
            tap(scene, width / 2f, rowCentreY(EXPORT_AS_ROW), at); at += 512L
            tap(scene, width / 2f, rowCentreY(IMPORT_ROW), at); at += 512L
            tap(scene, width / 2f, rowCentreY(NEW_ROW), at)

            assertEquals("a clean document has nothing to save", 0, counters[SAVE])
            assertEquals("and nothing to export", 0, counters[PDF])
            assertEquals("neither PDF nor an export", 0, counters[EXPORT_AS])
            assertEquals(
                "but a PDF can still be brought in, which opens a tab of its own",
                1,
                counters[IMPORT],
            )
            assertEquals("and New still opens a page", 1, counters[NEW])
        } finally {
            scene.close()
        }
    }

    /**
     * The panel's picture: every row a glyph and a word in the chrome ink, and the two PDF rows wearing
     * the **document red on the glyph alone** — a red word would read as a warning, and the flat menu has
     * one label tone.
     *
     * The frame is filed in `desktop/build/desktop-render/` so the panel can be looked at next to the
     * flat header it opens from.
     */
    @Test
    fun `every row is one list, and only the pdf glyphs are red`() {
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

        val glyphX = with(MenuMetrics) { (inset + rowInset + glyph / 2).value }
        for (row in 0..6) {
            val white = pixelsAround(glyphX, rowCentreY(row), 10)
                .count { (it and 0xFFFFFF) >= INK_FLOOR }
            assertTrue("row $row draws its glyph", white > 8)
        }
        for (row in listOf(IMPORT_ROW, PDF_ROW)) {
            val red = pixelsAround(glyphX, rowCentreY(row), 10)
                .count { colour -> ((colour shr 16) and 0xFF) > (colour and 0xFF) + 60 }
            assertTrue("the PDF glyph of row $row is the document red", red > 8)
        }
        val exportGlyph = pixelsAround(glyphX, rowCentreY(EXPORT_AS_ROW), 10)
            .count { colour -> ((colour shr 16) and 0xFF) > (colour and 0xFF) + 60 }
        assertEquals("an export is not a PDF: its glyph keeps the ink", 0, exportGlyph)
    }

    /**
     * The Pen block **with the pen's presets in it** — the block a reader opens mid-drawing, drawn whole so
     * the folded row, the pressure answers and the chips can be looked at instead of trusted.
     *
     * The frame is filed as `desktop-pen-presets.png`. Without settings the same tap draws no block, so
     * the two frames differing is what proves the picture is the block's and not the desk's.
     */
    @Test
    fun `the pen row folds out the pressure answers and the presets`() {
        val policy = DesktopSettingsState(DesktopSettings(File(temp.root, "menu-pen.properties")))
        policy.beginPresetSave()
        policy.typePresetName("Light hand")
        policy.commitPresetSave()

        val drawn = penFrame(policy)
        val bare = penFrame(null)
        File("build/desktop-render/desktop-pen-presets.png").apply { parentFile?.mkdirs() }.writeBytes(drawn)
        assertTrue("the Pen block is drawn, not only the desk", !drawn.contentEquals(bare))
    }

    /** The stationery is the same at rest and folded: the Pen row only opens its block. */
    @Test
    fun `the folded panel draws the same list whether or not there are settings`() {
        val policy = DesktopSettingsState(DesktopSettings(File(temp.root, "menu-pen.properties")))
        val withSettings = foldedFrame(policy)
        val without = foldedFrame(null)
        assertTrue("a folded Pen block hides its answers", withSettings.contentEquals(without))
    }

    /**
     * The hover step of a row, drawn so the shape of the lit area can be looked at: the whole 4 dp row,
     * not the label's box — a menu row is one target across the panel.
     */
    @Test
    fun `hovering a row lights the whole row`() {
        val scene = scene()
        val (plain, lit) = try {
            Snapshot.sendApplyNotifications()
            val before = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            mouse(scene, PointerEventType.Move, width / 2f, rowCentreY(PDF_ROW), 1_000L, pressed = false)
            Snapshot.sendApplyNotifications()
            val after = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            before to after
        } finally {
            scene.close()
        }
        File("build/desktop-render/desktop-menu-hover.png").apply { parentFile?.mkdirs() }.writeBytes(lit)

        val before = ImageIO.read(ByteArrayInputStream(plain))
        val after = ImageIO.read(ByteArrayInputStream(lit))
        val y = rowCentreY(PDF_ROW).toInt()
        fun brightened(x: Float): Boolean {
            val px = x.toInt()
            val from = before.getRGB(px, y) and 0xFF
            val to = after.getRGB(px, y) and 0xFF
            return to > from
        }
        assertTrue("the row's left end lights up", brightened(MenuMetrics.inset.value + 4f))
        assertTrue("and its far right end does too", brightened(width - MenuMetrics.inset.value - 4f))
    }

    /** One frame of the panel, tall enough that the opened Pen block is not clipped. */
    private fun penFrame(policy: DesktopSettingsState?): ByteArray {
        val scene = ImageComposeScene(width.toInt(), PEN_HEIGHT.toInt(), Density(1f)) {
            Box(Modifier.size(width.dp, PEN_HEIGHT.dp).background(DesktopTheme.desk)) {
                panel(policy, counters = null)
            }
        }
        return try {
            Snapshot.sendApplyNotifications()
            scene.render()
            // The row that folds the block is the one after the second rule and the PDF trio.
            tap(scene, width / 2f, rowCentreY(PEN_ROW), 1_000L)
            Snapshot.sendApplyNotifications()
            requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
        } finally {
            scene.close()
        }
    }

    /** One frame of the folded panel, with the settings the Pen block would read. */
    private fun foldedFrame(policy: DesktopSettingsState?): ByteArray {
        val scene = ImageComposeScene(width.toInt(), height.toInt(), Density(1f)) {
            Box(Modifier.size(width.dp, height.dp).background(DesktopTheme.desk)) {
                panel(policy, counters = null)
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
            // The desk behind the panel, so the surface has something to stand on — as it does in the
            // window, where a menu lies over the page.
            Box(Modifier.size(width.dp, height.dp).background(DesktopTheme.desk)) {
                panel(policy = null, counters = counters, canSave = canSave, canExport = canExport)
            }
        }

    /** The panel itself, with every command counted when a row can be clicked. */
    @androidx.compose.runtime.Composable
    private fun panel(
        policy: DesktopSettingsState?,
        counters: IntArray?,
        canSave: Boolean = true,
        canExport: Boolean = true,
    ) {
        MenuPanel(
            sidebarVisible = true,
            canSave = canSave,
            canExport = canExport,
            onNew = { counters?.set(NEW, counters[NEW] + 1) },
            onOpen = { counters?.set(OPEN, counters[OPEN] + 1) },
            onSave = { counters?.set(SAVE, counters[SAVE] + 1) },
            onSaveAs = { counters?.set(SAVE_AS, counters[SAVE_AS] + 1) },
            onExportPdf = { counters?.set(PDF, counters[PDF] + 1) },
            onExportSvg = { counters?.set(SVG, counters[SVG] + 1) },
            onExportPng = { counters?.set(PNG, counters[PNG] + 1) },
            onExportAs = { counters?.set(EXPORT_AS, counters[EXPORT_AS] + 1) },
            onImportPdf = { counters?.set(IMPORT, counters[IMPORT] + 1) },
            onPortableCopy = { counters?.set(PORTABLE, counters[PORTABLE] + 1) },
            onToggleSidebar = { counters?.set(SIDEBAR, counters[SIDEBAR] + 1) },
            onSettings = {},
            onDismiss = {},
            penPolicy = policy,
        )
    }

    /**
     * A row's centre, by the panel's own arithmetic: the inset, the rows above it, and the rule each
     * group boundary costs.
     */
    private fun rowCentreY(row: Int): Float = with(MenuMetrics) {
        val rules = when {
            row < IMPORT_ROW -> 0
            row < PEN_ROW -> 1
            else -> 2
        }
        (inset + rowHeight * row + dividerHeight * rules + rowHeight / 2).value
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
        /** The rows of the folded panel, in the order the panel draws them. */
        const val NEW_ROW = 0
        const val SAVE_ROW = 2
        const val IMPORT_ROW = 4
        const val PDF_ROW = 5
        const val EXPORT_AS_ROW = 6
        const val PEN_ROW = 7
        const val SIDEBAR_ROW = 8
        const val ROWS = 10

        const val NEW = 0
        const val OPEN = 1
        const val SAVE = 2
        const val SAVE_AS = 3
        const val IMPORT = 4
        const val PDF = 5
        const val PNG = 6
        const val SVG = 7
        const val SIDEBAR = 8
        const val PORTABLE = 9
        const val EXPORT_AS = 10

        /** Anything at or above this tone is a glyph stroke: the chrome ink is pure white. */
        const val INK_FLOOR = 0x909090

        /** The panel with the Pen block open: ten rows, two rules and the pressure answers under them. */
        const val PEN_HEIGHT = 620f
    }
}
