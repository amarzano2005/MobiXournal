package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopSettingSection
import com.mobixournal.desktop.settings.DesktopNewDocumentFormat
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.settings.glyph
import com.mobixournal.desktop.shell.DesktopShell
import com.mobixournal.desktop.shell.DesktopShellState

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Settings surface, as **pixels**: it owns the window while it is up, and the section index picks the
 * page.
 *
 * The two claims that matter are structural, and neither can be seen by reading the composable: the sheet
 * must not be painted under the settings (that is what makes them a section of the window instead of a card
 * floating over the page, `docs/interface-contract.md`), and the index must actually change what is drawn.
 * A page-wide run of paper-white is the measurement — glyphs and text are thin, a sheet is not.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopSettingsSurfaceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 1000
    private val height = 700

    private fun notebook(): File = temp.newFile("settings.mobix").also {
        SyntheticNotebook.write(it, SyntheticNotebook.pages(4))
    }

    /** A window with a real document in it: four pages, so the page list would show without the toggle. */
    private fun state(): DesktopShellState {
        val state = DesktopShellState(DesktopSettings(File(temp.root, "surface.properties")))
        // No first-run question over this surface: it is a modal with a scrim, and this measures the page.
        state.prefs.chooseNewDocumentFormat(DesktopNewDocumentFormat.MOBIX)
        state.open(notebook())
        state.toggleSidebar()
        return state
    }

    private fun pixels(state: DesktopShellState, name: String): IntArray {
        val scene = ImageComposeScene(width, height, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        return try {
            Snapshot.sendApplyNotifications()
            scene.render() // Let measured scroll extents reach the scrollbar before capturing.
            val png = scene.render().encodeToData()!!.bytes
            File("build/desktop-render/$name.png").apply { parentFile?.mkdirs() }.writeBytes(png)
            val image = ImageIO.read(ByteArrayInputStream(png))
            IntArray(image.width * image.height).also {
                image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
            }
        } finally {
            scene.close()
        }
    }

    /** The longest unbroken run of paper-white on any row: a sheet is hundreds wide, a glyph is not. */
    private fun longestPaperRun(pixels: IntArray): Int {
        var best = 0
        for (y in 0 until height) {
            var run = 0
            for (x in 0 until width) {
                val paper = (pixels[y * width + x] and 0xFFFFFF) == 0xFFFFFF
                run = if (paper) run + 1 else 0
                if (run > best) best = run
            }
        }
        return best
    }

    /**
     * Settings **floats over the page**: the sheet is still drawn, and still readable beside the panel.
     *
     * It used to take the whole body between the header and the status bar, which threw away the page the
     * reader was comparing against — on a desktop that reads as a second window. The measurement is the
     * same one the old test used the other way round: a page-wide run of paper must survive the panel.
     */
    @Test
    fun `the surface floats over the page instead of replacing it`() {
        val state = state()
        val workspace = pixels(state, "settings-closed")
        assertTrue(
            "the fixture draws a sheet, so the measurement below means something: ${longestPaperRun(workspace)}",
            longestPaperRun(workspace) >= 40,
        )

        state.prefs.openAt()
        val surface = pixels(state, "settings-surface")
        // The body is not replaced: the panel is a bounded box with the page (dimmed) around it, so the
        // window's own edges are still the page and not the panel's fill.
        val panel = DesktopTheme.settingsPanel.toArgb() and 0xFFFFFF
        val middle = height / 2
        assertTrue(
            "the panel is drawn in the middle of the window",
            (0 until width).any { surface.rgb(it, middle) == panel },
        )
        assertTrue("the panel stops short of the window's left edge", surface.rgb(2, middle) != panel)
        assertTrue("and of its right edge", surface.rgb(width - 3, middle) != panel)
        val changed = workspace.indices.count { workspace[it] != surface[it] }
        assertTrue("and the panel is actually drawn on top: $changed pixels differ", changed > 20_000)
    }

    /**
     * The panel is a **modal**, drawn honestly: the page survives (it is what the setting changes) and goes
     * under the scrim, so nothing behind the panel reads as a control the pointer could still reach.
     *
     * The measurement is a pixel in the margin the centred panel leaves at the window's edge, in the light
     * palette — the scrim there is the slate at a quarter, so the margin has to get darker. The dark palette
     * is the desk itself and would say nothing.
     */
    @Test
    fun `the page behind the panel is dimmed`() {
        val state = state()
        state.prefs.appearance.chooseTheme(com.mobixournal.desktop.settings.DesktopThemeMode.LIGHT)
        try {
            val closed = pixels(state, "settings-scrim-closed")
            state.prefs.openAt()
            val open = pixels(state, "settings-scrim-open")
            val x = 6
            val y = height / 2
            val before = brightness(closed.rgb(x, y))
            val after = brightness(open.rgb(x, y))
            assertTrue("the page beside the panel is dimmed: $before -> $after", after < before)
        } finally {
            com.mobixournal.desktop.settings.DesktopTheme.use(false)
        }
    }

    private fun IntArray.rgb(x: Int, y: Int): Int = this[y * width + x] and 0xFFFFFF

    private fun brightness(rgb: Int): Int =
        ((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)

    @Test
    fun `the index picks the page that is drawn`() {
        val state = state()
        state.prefs.openAt(DesktopSettingSection.TOOLBAR)
        val toolbarPage = pixels(state, "settings-toolbar")
        state.prefs.openAt(DesktopSettingSection.STYLUS)
        val stylusPage = pixels(state, "settings-stylus")

        val different = toolbarPage.indices.count { toolbarPage[it] != stylusPage[it] }
        assertTrue("two sections are two pages, not one: $different pixels differ", different > 500)
    }

    @Test
    fun `every section draws without the window falling over`() {
        val state = state()
        state.prefs.openAt()
        val panel = DesktopTheme.settingsPanel.toArgb() and 0xFFFFFF
        for (section in DesktopSettingSection.entries) {
            state.prefs.show(section)
            val drawn = pixels(state, "settings-${section.name.lowercase()}")
            val filled = drawn.count { (it and 0xFFFFFF) == panel }
            assertTrue("$section draws its page over the page: $filled panel px", filled > 50_000)
        }
    }
    @Test fun appearanceUsesTheSharedDarkAndLightPalettes() {
        val state = state()
        state.prefs.openAt(DesktopSettingSection.APPEARANCE)
        try {
            state.prefs.appearance.chooseTheme(com.mobixournal.desktop.settings.DesktopThemeMode.DARK)
            val dark = pixels(state, "settings-appearance-dark")
            state.prefs.appearance.chooseTheme(com.mobixournal.desktop.settings.DesktopThemeMode.LIGHT)
            val light = pixels(state, "settings-appearance-light")
            assertTrue("the panel follows the chosen palette", dark.indices.count { dark[it] != light[it] } > dark.size / 3)
        } finally { com.mobixournal.desktop.settings.DesktopTheme.use(false) }
    }
}
