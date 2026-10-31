package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet's ruling stops at the sheet.
 *
 * The recorded picture is a page-sized canvas, so a ruling line that overran it was invisible for as long
 * as every frame came from that picture. The karaoke replay paints the page **directly** (a recorded
 * picture cannot show a page being written), and there the overrun showed: on a graph sheet the
 * horizontal lines ran on across the whole window, to the right of the paper, every time a recording was
 * played. The band checked here is exactly that region — the page is 200 pt wide and the canvas 400, so
 * anything opaque to the right of the sheet is a rule that did not stop.
 */
class DesktopPaperRulingTest {

    private val pageWidth = 200.0
    private val pageHeight = 200.0
    private val canvasWidth = 400
    private val canvasHeight = 200

    private val graphPage = Page(
        pageWidth,
        pageHeight,
        Background.Solid(0xFFFFFDF5.toInt(), "graph", "r1=10"),
        listOf(Layer(emptyList())),
    )

    @Test
    fun `a graph sheet's ruling stays inside the page when the frame is not clipped`() {
        val pixels = frame()

        // The page itself is ruled: some line must be there for the band test to mean anything.
        val ruled = (0 until canvasHeight).count { y -> alpha(pixels, 100, y) != 0 }
        assertTrue("the sheet is ruled inside the page ($ruled rows)", ruled > 5)

        // And nothing to the right of the sheet, where the overrun used to be painted.
        // From 20 pt out: the sheet's own shadow is a halo a few points wide, and it belongs there.
        var outside = 0
        var minX = Int.MAX_VALUE
        var maxX = -1
        val rows = sortedSetOf<Int>()
        for (x in 220 until canvasWidth) {
            for (y in 0 until canvasHeight) {
                if (alpha(pixels, x, y) != 0) {
                    outside++
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                    if (rows.size < 6) rows += y
                }
            }
        }
        assertEquals("no ruling beyond the sheet's right edge (x=$minX..$maxX rows=$rows)", 0, outside)
    }

    private fun alpha(pixels: IntArray, x: Int, y: Int): Int = (pixels[y * canvasWidth + x] ushr 24) and 0xFF

    /** One frame of [graphPage] painted the way the karaoke path paints it: unclipped, page at the origin. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun frame(): IntArray {
        lateinit var pixels: IntArray
        val scene = ImageComposeScene(canvasWidth, canvasHeight, Density(1f)) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                drawPageContent(graphPage, pxPerPt = 1f, textMeasurer = measurer)
            }
        }
        try {
            val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
            val image = ImageIO.read(ByteArrayInputStream(png))
            pixels = IntArray(image.width * image.height).also {
                image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
            }
        } finally {
            scene.close()
        }
        return pixels
    }
}
