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
 * The strips float over the page (2026-10-26, `docs/interface-contract.md`), so the canvas keeps its whole
 * body and the sheet can run behind the glass. The **framing** still respects them: a fitted page is
 * measured in the room the strips leave and centred there, so a document opens on the desk beside the bar
 * — the same picture a lane used to give, without a lane. This file measures both halves: the opening
 * frame beside the strip, and the sheet's pixels behind the capsule once it is zoomed past the room.
 *
 * The second render zooms the page past the window, because a floating strip is only visible as floating
 * where the sheet reaches under it; a fitted page stops short of the lane and would pass either way.
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
     * A document opens **beside** the strip, on the black desk, exactly as it did when the strip took a
     * lane: the floating bar keeps its room out of the framing, not out of the canvas.
     *
     * The arithmetic is `DesktopChromeInsetTest`'s; this is the frame it produces. A rail on the left
     * shifts the sheet right by the rail's lane, a rail on the right leaves it at the window's edge, and a
     * ribbon pushes it down by its band — the three answers a lane used to give.
     */
    @Test
    fun `the page opens beside the strip, over the desk`() {
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
     * The sheet runs **behind** the strip, and the strip is the pane of glass lying on it.
     *
     * Zoomed past the window the page reaches both edges, so a rail's lane and a ribbon's band are over
     * paper — and the capsule sampled there is neither the paper nor the desk's own glass, which is what
     * a translucent surface over a sheet looks like.
     */
    @Test
    fun `the strips float over the sheet instead of taking a lane from it`() {
        val header = DesktopTheme.headerHeight.value.toInt()
        val lane = ToolStripMetrics.railWidth.value.toInt()
        for (placement in StripPlacement.entries) {
            val pixels = render(placement, zoom = 2f)
            // Near-white rather than exactly white: a sheet's own edge carries its hairline and a
            // little shading, so exact white fails on a page that is plainly there.
            fun sheetAt(x: Int, y: Int) = (pixels[y * width + x] and 0xFF) >= 0xD0
            // The lane is 64 dp of air around a 52 dp capsule, so its first pixels are the sheet itself:
            // a lane taken out of the canvas would be the desk's black there instead.
            val sheetUnderTheStrip = when {
                placement.showsRail && placement.railSide == RailSide.LEFT -> sheetAt(2, height / 2)
                placement.showsRail -> sheetAt(width - 3, height / 2)
                // The ribbon's band lies over the sheet too: its capsule floats 4 dp below the header.
                else -> sheetAt(width / 2, header + 2)
            }
            assertTrue(
                "$placement: the sheet reaches under the strip, so no lane is taken from the canvas",
                sheetUnderTheStrip,
            )

            if (placement.showsRail) {
                val centreX = if (placement.railSide == RailSide.LEFT) lane / 2 else width - lane / 2
                val capsule = pixels[(header + 200) * width + centreX] and 0xFFFFFF
                assertTrue(
                    "$placement: the rail is not drawn over the sheet (sampled #%06X)".format(capsule),
                    capsule != 0xFFFFFF,
                )
                assertTrue(
                    "$placement: and it is glass over paper, not the desk's glass (sampled #%06X)".format(capsule),
                    maxChannelGap(capsule, DesktopTheme.bandRgb) > 3,
                )
            }
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
