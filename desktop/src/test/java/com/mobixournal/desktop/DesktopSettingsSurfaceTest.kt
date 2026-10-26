package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
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

    @Test
    fun `the surface takes the window and the page is not drawn under it`() {
        val state = state()
        val workspace = pixels(state, "settings-closed")
        assertTrue(
            "the fixture draws a sheet, so the measurement below means something: ${longestPaperRun(workspace)}",
            longestPaperRun(workspace) >= 40,
        )

        state.prefs.openAt()
        val surface = pixels(state, "settings-surface")
        assertTrue(
            "no sheet under the settings, so no page-wide run of paper",
            longestPaperRun(surface) < 40,
        )
    }

    @Test
    fun `the index picks the page that is drawn`() {
        val state = state()
        state.prefs.openAt(DesktopSettingSection.INTERFACE)
        val interfacePage = pixels(state, "settings-interface")
        state.prefs.openAt(DesktopSettingSection.TABLET)
        val tabletPage = pixels(state, "settings-tablet")

        val different = interfacePage.indices.count { interfacePage[it] != tabletPage[it] }
        assertTrue("two sections are two pages, not one: $different pixels differ", different > 500)
    }

    @Test
    fun `every section draws without the window falling over`() {
        val state = state()
        state.prefs.openAt()
        for (section in DesktopSettingSection.entries) {
            state.prefs.show(section)
            val drawn = pixels(state, "settings-${section.name.lowercase()}")
            assertTrue("$section draws a page", longestPaperRun(drawn) < 40)
        }
    }
}
