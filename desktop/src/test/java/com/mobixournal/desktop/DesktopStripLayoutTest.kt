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
 * **Where the strips stand**, measured on the sheet they leave behind.
 *
 * The placement is a layout claim, and the honest way to check it is the page: standing the rail on the
 * other edge has to move the sheet with it, and choosing the top has to give the ribbon a band without a
 * rail's lane. Each case is rendered and the paper's own origin is found, so a strip that drew in the wrong
 * lane — or twice — is a moved page rather than a plausible-looking one.
 *
 * The rule the whole thing serves: a strip is a band in **its own lane**, never drawn over the page
 * (`docs/chrome.md`), which is why the page can move at all.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopStripLayoutTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 1000
    private val height = 700

    /** Paper is white; a glyph or a label is thin, so a run of [PAPER_RUN] is the sheet's own edge. */
    private val paperRun = 40

    /** One notebook for both renders: the frames differ by layout, never by document. */
    private val notebook: File by lazy {
        temp.newFile("strips.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(4)) }
    }

    private fun render(placement: StripPlacement): IntArray {
        val name = "layout-${placement.name.lowercase()}"
        val settings = DesktopSettings(File(temp.root, "$name.properties"))
        val state = DesktopShellState(settings)
        state.open(notebook)
        // The page list would take the outer edge and hide half of what this measures.
        state.toggleSidebar()
        state.prefs.chooseStripPlacement(placement)
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

    private fun paper(pixels: IntArray, x: Int, y: Int): Boolean =
        (pixels[y * width + x] and 0xFFFFFF) == 0xFFFFFF

    /** The top-left corner of the sheet: the first column and the first row carrying a page-wide run. */
    private fun paperOrigin(pixels: IntArray): Pair<Int, Int> {
        val left = (0 until width).firstOrNull { x ->
            var run = 0
            var best = 0
            for (y in 0 until height) {
                run = if (paper(pixels, x, y)) run + 1 else 0
                if (run > best) best = run
            }
            best >= paperRun
        } ?: error("no sheet in the frame")
        val top = (0 until height).firstOrNull { y ->
            var run = 0
            var best = 0
            for (x in 0 until width) {
                run = if (paper(pixels, x, y)) run + 1 else 0
                if (run > best) best = run
            }
            best >= paperRun
        } ?: error("no sheet in the frame")
        return left to top
    }

    /** The tablet's handedness answer, applied to the window: the rail's edge moves the page with it. */
    @Test
    fun `the rail's edge moves the sheet with it`() {
        val leftRail = paperOrigin(render(StripPlacement.LEFT))
        val rightRail = paperOrigin(render(StripPlacement.RIGHT))

        assertTrue(
            "a left rail pushes the page right: $leftRail against $rightRail",
            leftRail.first > rightRail.first + 8,
        )
    }

    /** `Top` is the ribbon's band under the header and no rail at all — both halves of the claim. */
    @Test
    fun `the top placement draws the ribbon and no rail`() {
        val side = paperOrigin(render(StripPlacement.LEFT))
        val top = paperOrigin(render(StripPlacement.TOP))

        assertTrue("the ribbon takes a band, so the sheet starts lower: $top against $side", top.second > side.second + 8)
        assertTrue("and the rail's lane is gone, so it starts further left", top.first < side.first - 8)
    }
}
