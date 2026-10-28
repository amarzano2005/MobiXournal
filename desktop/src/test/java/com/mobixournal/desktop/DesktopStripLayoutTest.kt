package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
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
 * **Where the strips stand**, measured on the sheet they leave behind.
 *
 * Since 2026-10-28 the strips **take a lane** on the window, exactly as the tablet's rail does: the rail is
 * a band down an edge with its capsule standing in it, the ribbon a band under the header, and the canvas
 * is handed the rectangle that is left. Nothing of the chrome lies over the sheet, so the page can neither
 * be drawn under a bar nor have its ink swallowed by one. This file measures both halves: the opening frame
 * beside the lane, and the lane itself — a band, not the sheet, with a different material standing in it.
 *
 * The second render zooms the page past the window, because that is the case a lane has to survive: the paper
 * fills its own canvas to both edges, and the lane still shows beside it instead of the sheet running under
 * the bar.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopStripLayoutTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val width = 1000
    private val height = 700

    /** One notebook for every render: the frames differ by layout, never by document. */
    private val notebook: File by lazy {
        temp.newFile("strips.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(4)) }
    }

    private fun render(placement: StripPlacement, zoom: Float? = null): IntArray {
        val name = "layout-${placement.name.lowercase()}${zoom?.let { "-z$it" } ?: ""}"
        val settings = DesktopSettings(File(temp.root, "$name.properties"))
        val state = DesktopShellState(settings)
        state.open(notebook)
        // The page list would take the outer edge and hide half of what this measures.
        state.toggleSidebar()
        state.prefs.chooseStripPlacement(placement)
        if (zoom != null) {
            val tab = state.tabs.active!!
            tab.fit = FitMode.NONE
            tab.viewport = Viewport(zoom = zoom, offsetX = 0f, offsetY = 0f)
        }
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
            state.tabs.closeAll()
        }
    }

    private fun paper(pixels: IntArray, x: Int, y: Int): Boolean =
        (pixels[y * width + x] and 0xFFFFFF) == 0xFFFFFF

    /**
     * The sheet's left edge, read along a row under the strips: a row at the foot of the window is below
     * every capsule, so what it finds is the page and nothing else.
     */
    private fun sheetLeft(pixels: IntArray): Int {
        val y = height - 60
        return (0 until width).firstOrNull { x -> paper(pixels, x, y) }
            ?: error("no sheet in the row at $y")
    }

    /** The sheet's top edge, read down the middle of it: above the page there is only the desk. */
    private fun sheetTop(pixels: IntArray): Int {
        val x = (0 until width).firstOrNull { px ->
            (0 until height).count { y -> paper(pixels, px, y) } > 100
        } ?: error("no sheet in the frame")
        return (0 until height).firstOrNull { y -> paper(pixels, x, y) }
            ?: error("no sheet above the desk")
    }

    /**
     * A document opens **beside** the lane, on the black desk — the tablet's own picture: the page begins
     * where the bar ends, never under it.
     *
     * A rail on the left shifts the sheet right by the rail's lane, a rail on the right leaves it at the
     * window's edge, and a ribbon pushes it down by its band: three answers, one rule.
     */
    @Test
    fun `the page opens beside the strip's lane, on the desk`() {
        val lane = ToolStripMetrics.railWidth.value.toInt()
        val band = ToolStripMetrics.ribbonHeight.value.toInt()
        val header = DesktopTheme.headerHeight.value.toInt()
        val left = render(StripPlacement.LEFT)
        val right = render(StripPlacement.RIGHT)
        val top = render(StripPlacement.TOP)

        val leftEdge = sheetLeft(left)
        val rightEdge = sheetLeft(right)
        assertEquals(
            "a rail on the left moves the sheet right by its lane ($leftEdge against $rightEdge)",
            lane,
            leftEdge - rightEdge,
        )
        assertTrue(
            "and the sheet starts past the rail's own column: $leftEdge",
            leftEdge >= lane,
        )
        assertTrue(
            "a ribbon pushes the sheet under its band (top at ${sheetTop(top)} of ${header + band})",
            sheetTop(top) >= header + band,
        )
    }

    /**
     * **The lane is a band, and the sheet starts past it.**
     *
     * Zoomed past the window the page fills its canvas to both edges, so the pixels just inside the lane
     * are the case that matters: the band's own tone there, never paper. That is the assertion the floating
     * layout could not pass — and the one that keeps a bar from ever lying on the ink again. The capsule
     * standing in the band is a second, lighter material, which is what "the tools in a glass pill, in a
     * bar" looks like in pixels.
     */
    @Test
    fun `the rail takes a lane with its capsule in it`() {
        val header = DesktopTheme.headerHeight.value.toInt()
        val lane = ToolStripMetrics.railWidth.value.toInt()
        val band = ToolStripMetrics.ribbonHeight.value.toInt()
        for (placement in StripPlacement.entries) {
            val pixels = render(placement, zoom = 2f)
            fun at(point: Pair<Int, Int>) = pixels[point.second * width + point.first] and 0xFFFFFF
            // Near-white rather than exactly white: a sheet's own edge carries its hairline and a
            // little shading, so exact white fails on a page that is plainly there.
            fun sheetAt(point: Pair<Int, Int>) = (pixels[point.second * width + point.first] and 0xFF) >= 0xD0

            // The band's first pixels, the capsule's centre, and the sheet just past the board: the three
            // samples a lane is made of, wherever the strip stands.
            val (bandAt, capsuleAt, paperAt) = when {
                placement.showsRail && placement.railSide == RailSide.LEFT ->
                    Triple(2 to height / 2, lane / 2 to header + 200, lane + 8 to height / 2)
                placement.showsRail ->
                    Triple(
                        width - 3 to height / 2,
                        width - lane / 2 to header + 200,
                        width - lane - 8 to height / 2,
                    )
                else -> Triple(
                    width / 2 to header + 2,
                    width / 2 to header + band / 2,
                    width / 2 to header + band + 8,
                )
            }

            assertTrue(
                "$placement: the strip's lane is a band, not the sheet (sampled #%06X)".format(at(bandAt)),
                !sheetAt(bandAt),
            )
            assertEquals(
                "$placement: and it wears the window's own band (sampled #%06X)".format(at(bandAt)),
                DesktopTheme.bandRgb,
                at(bandAt),
            )
            assertTrue(
                "$placement: the canvas begins past the lane — the sheet is at $paperAt",
                sheetAt(paperAt),
            )
            assertTrue(
                "$placement: the capsule stands in the band as its own material (sampled #%06X)"
                    .format(at(capsuleAt)),
                maxChannelGap(at(capsuleAt), DesktopTheme.bandRgb) > 3,
            )
        }
    }

    private fun maxChannelGap(a: Int, b: Int): Int {
        fun channel(rgb: Int, shift: Int) = (rgb shr shift) and 0xFF
        return maxOf(
            kotlin.math.abs(channel(a, 16) - channel(b, 16)),
            kotlin.math.abs(channel(a, 8) - channel(b, 8)),
            kotlin.math.abs(channel(a, 0) - channel(b, 0)),
        )
    }
}
