package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The header's **right hand**: undo, redo, save and the PDF export, as four real buttons.
 *
 * They are the commands the keys and the menu already reach (`Ctrl+Z`, `Ctrl+S`, `Ctrl+E`), so this is
 * the wiring and not the commands: a headless scene draws the real band, a real pointer press lands on
 * each capsule in turn, and what the button does is counted. The coordinates are the layout's own —
 * a 10 dp edge, four 36 dp capsules with the two groups' gaps — which is also the check that the
 * cluster is where the band puts it rather than somewhere the artist guessed.
 *
 * The band's own height is `DesktopThemeTest`'s; what is proved here is that the controls live **inside**
 * it (a press at their y reaches them, at that height) and that a disabled one is not a command.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopHeaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the right hand of the header is undo, redo, save and the pdf export`() {
        val tabs = DesktopTabs()
        val tab = tabs.open(DocumentLoader.load(twoPages()))
        // Two recorded steps and one taken back: both history commands are live, and the tab is dirty
        // for Save — a cluster where every capsule is enabled, which is what this test is about.
        tab.record(tab.document.snapshot())
        tab.record(tab.document.snapshot())
        tab.undo()

        val width = 900
        val counts = IntArray(4)
        val scene = ImageComposeScene(width, DesktopTheme.headerHeight.value.toInt(), Density(1f)) {
            Header(
                tabs = tabs,
                sidebarVisible = true,
                onActivate = {},
                onClose = {},
                onMove = { _, _ -> },
                onNew = {},
                onOpen = {},
                onSave = { counts[SAVE]++ },
                onSaveAs = {},
                onExportPdf = { counts[EXPORT]++ },
                onExportSvg = {},
                onToggleSidebar = {},
                onSettings = {},
                onUndo = { counts[UNDO]++ },
                onRedo = { counts[REDO]++ },
            )
        }
        try {
            tap(scene, capsuleCentre(width, 0), capsuleCentre(width, 0, y = true))
            tap(scene, capsuleCentre(width, 1), capsuleCentre(width, 1, y = true))
            tap(scene, capsuleCentre(width, 2), capsuleCentre(width, 2, y = true))
            tap(scene, capsuleCentre(width, 3), capsuleCentre(width, 3, y = true))
            assertEquals("undo", 1, counts[UNDO])
            assertEquals("redo", 1, counts[REDO])
            assertEquals("save", 1, counts[SAVE])
            assertEquals("export pdf", 1, counts[EXPORT])
        } finally {
            scene.close()
        }
    }

    /**
     * The band as it is drawn: four capsules in their places, each with a glyph in it.
     *
     * The counts above prove the wiring; this is the picture — a capsule whose art is empty (an empty
     * `chromeGlyphParts`, or art drawn outside the 20 dp box) reads as nothing between two rims, which is
     * exactly what a person would call a missing button. The frame is written to
     * `desktop/build/desktop-render/` so the band can be looked at, as every other render test does.
     */
    @Test
    fun `every capsule in the band has a glyph in it`() {
        val tabs = DesktopTabs()
        val tab = tabs.open(DocumentLoader.load(twoPages()))
        tab.record(tab.document.snapshot())
        tab.record(tab.document.snapshot())
        tab.undo()

        val width = 900
        val scene = ImageComposeScene(width, DesktopTheme.headerHeight.value.toInt(), Density(1f)) {
            Header(
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
            )
        }
        val png = frame(scene)
        scene.close()
        File("build/desktop-render/header-actions.png").apply { parentFile?.mkdirs() }.writeBytes(png)
        val image = ImageIO.read(ByteArrayInputStream(png))

        for (index in 0..3) {
            val centre = capsuleCentre(width, index)
            val half = DesktopTheme.headerControlSize.value / 2f
            val glyphPixels = (centre - half + 2f).toInt()..<(centre + half - 2f).toInt()
            // Ink is **white or one of the two named colours**: Save is the accent and the PDF export the
            // document red on this band, as on the tablet, and a "bright pixel" measure saw only the
            // white one (`docs/interface-contract.md` §2).
            val inked = glyphPixels.sumOf { x ->
                (0 until image.height).count { y -> isInk(image.getRGB(x, y)) }
            }
            assertTrue("capsule $index has its glyph drawn in it", inked > 8)
        }
    }

    /**
     * The pointer's answer: a capsule under the pointer takes the steel, one step short of pick — the
     * measure `ToolStripsTest` makes of the rail's slots, applied to the band's own controls.
     */
    @Test
    fun `the capsule under the pointer is brighter than the one beside it`() {
        val tabs = DesktopTabs()
        val tab = tabs.open(DocumentLoader.load(twoPages()))
        tab.record(tab.document.snapshot())
        tab.record(tab.document.snapshot())
        tab.undo()

        val width = 900
        val centre = capsuleCentre(width, 3)
        val probeX = (centre - 15f).toInt()
        val probeY = DesktopTheme.headerHeight.value.toInt() / 2

        // A fresh scene per state, as `ToolStripsTest` measures a slot: an `Enter` on a scene nothing has
        // pointed at yet is what a cursor arriving over the window sends.
        fun render(hovered: Boolean): Int {
            val scene = ImageComposeScene(width, DesktopTheme.headerHeight.value.toInt(), Density(1f)) {
                Header(
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
                )
            }
            return try {
                if (hovered) scene.sendPointerEvent(PointerEventType.Enter, Offset(centre, probeY.toFloat()))
                val image = ImageIO.read(ByteArrayInputStream(frame(scene)))
                image.getRGB(probeX, probeY) and 0xFFFFFF
            } finally {
                scene.close()
            }
        }

        val rest = render(hovered = false)
        val lit = render(hovered = true)
        // Inside the capsule's fill but clear of its 20 dp glyph.
        assertTrue("hovering the capsule changed nothing (#%06X)".format(rest), rest != lit)
        assertTrue(
            "a hovered control paints the hover step, not the picked steel (#%06X)".format(lit),
            lit != DesktopTheme.activeSlot.toArgb() and 0xFFFFFF,
        )
    }

    @Test
    fun `with nothing open the history and file commands are dead`() {
        val width = 900
        val counts = IntArray(4)
        val scene = ImageComposeScene(width, DesktopTheme.headerHeight.value.toInt(), Density(1f)) {
            Header(
                tabs = DesktopTabs(),
                sidebarVisible = false,
                onActivate = {},
                onClose = {},
                onMove = { _, _ -> },
                onNew = {},
                onOpen = {},
                onSave = { counts[SAVE]++ },
                onSaveAs = {},
                onExportPdf = { counts[EXPORT]++ },
                onExportSvg = {},
                onToggleSidebar = {},
                onSettings = {},
                onUndo = { counts[UNDO]++ },
                onRedo = { counts[REDO]++ },
            )
        }
        try {
            for (index in 0..3) tap(scene, capsuleCentre(width, index), capsuleCentre(width, index, y = true))
            assertEquals("nothing to undo", 0, counts[UNDO])
            assertEquals("nothing to redo", 0, counts[REDO])
            assertEquals("no document to save", 0, counts[SAVE])
            assertEquals("nothing to export", 0, counts[EXPORT])
        } finally {
            scene.close()
        }
    }

    private fun twoPages(): File = temp.newFile("header.mobix").also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
    }

    /** One frame of the open scene, with the snapshot notifications a frame loop would send. */
    private fun frame(scene: ImageComposeScene): ByteArray {
        Snapshot.sendApplyNotifications()
        return requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
    }

    /**
     * The centre of capsule [index] counted from the window's right edge: 0 is the PDF export, then Save,
     * redo and undo — the order the band lays them out, read backwards.
     */
    private fun capsuleCentre(width: Int, index: Int, y: Boolean = false): Float {
        if (y) return DesktopTheme.headerHeight.value / 2f
        val size = DesktopTheme.headerControlSize.value
        val edge = HEADER_EDGE_DP
        val gap = 6f
        val group = 12f
        val right = (width - edge).toFloat()
        return when (index) {
            0 -> right - size / 2f
            1 -> right - size - gap - size / 2f
            2 -> right - size - gap - size - group - size / 2f
            else -> right - size - gap - size - group - size - gap - size / 2f
        }
    }

    /**
     * A tap, on a clock that only ever moves forward: Compose drops an event whose time is behind the
     * last one it saw, so four taps at the same timestamp are one tap and three ignored presses.
     */
    private fun tap(scene: ImageComposeScene, x: Float, y: Float) {
        mouse(scene, PointerEventType.Press, x, y, clock)
        mouse(scene, PointerEventType.Release, x, y, clock + 32L, pressed = false)
        clock += 512L
    }

    /** The tap clock: monotonic across the whole test, and far from any frame the scene renders. */
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

        /** White art, or art in the accent/document red — the second is chromatic where the first is not. */
        fun isInk(argb: Int): Boolean {
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            val saturation = maxOf(r, g, b) - minOf(r, g, b)
            return argb and 0xFFFFFF >= INK_FLOOR || saturation > 40
        }

        const val UNDO = 0
        const val REDO = 1
        const val SAVE = 2
        const val EXPORT = 3

        /** The band's own side padding (`Header`). */
        const val HEADER_EDGE_DP = 10f
    }
}
