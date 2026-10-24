package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The page cache is the **same page**, only rasterised once.
 *
 * `desktop-pen-lag-behind-the-nib`'s second measurement (a live stroke is only as fast as the frame it is
 * painted on) is why `PageRaster` exists: every frame used to re-paint the whole sheet, so a packet on a
 * dense page became ink in 30–48 ms while the same packet on a blank sheet took 13. Two claims are
 * checked here, and neither is "it drew something":
 *
 *  - **identical pixels** — the cached sheet matches what the direct paint produces, because the cache
 *    must be a cache, not a second renderer with its own idea of the page;
 *  - **reuse** — one rasterisation serves every frame while the viewport is still, and a scroll produces
 *    exactly one more.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PageRasterTest {

    private val width = 360
    private val height = 460

    /** A sheet with enough ink that the direct path is the cost being removed. */
    private fun page(strokes: Int = 120): Page = Page(
        width = width.toDouble(),
        height = height.toDouble(),
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(
            Layer(
                (0 until strokes).map { index ->
                    val y = (20 + index % 20 * 20).toDouble()
                    Stroke(
                        points = listOf(
                            StrokePoint(20.0, y, 2.0),
                            StrokePoint((40 + index % 12 * 20).toDouble(), y + 8, 2.0),
                        ),
                        color = 0xFF000000.toInt(),
                        tool = Tool.PEN,
                        capStyle = "round",
                        uniformWidth = true,
                    )
                },
            ),
        ),
    )

    /** One window painting [page], either through the cache or straight — the same page both ways. */
    private class Sheet(page: Page, width: Int, height: Int) {
        val raster = PageRaster()
        var cached by mutableStateOf(false)
        var viewport by mutableStateOf(Viewport(zoom = 1f))

        /** The stroke being drawn: it moves every frame, which is what repaints the canvas at all. */
        var live by mutableStateOf(10f)

        val scene: ImageComposeScene = ImageComposeScene(width, height, Density(1f)) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                if (cached) {
                    raster.draw(this, page, viewport, 1f, 1f, null, emptySet(), measurer)
                } else {
                    withTransform({ translate(viewport.offsetX, viewport.offsetY) }) {
                        drawPageContent(page, 1f, measurer)
                    }
                }
                drawLine(
                    color = Color(0xFF123456),
                    start = Offset(10f, live),
                    end = Offset(200f, live),
                    strokeWidth = 3f,
                )
            }
        }

        /** One frame's pixels, decoded so two renders can be compared. */
        fun frame(): IntArray {
            Snapshot.sendApplyNotifications()
            val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
            val image = ImageIO.read(ByteArrayInputStream(png))
            val out = IntArray(image.width * image.height)
            image.getRGB(0, 0, image.width, image.height, out, 0, image.width)
            return out
        }

        fun close() = scene.close()
    }

    @Test
    fun `the cached sheet is pixel-identical to the direct paint`() {
        val sheet = Sheet(page(), width, height)
        try {
            val direct = sheet.frame()
            assertTrue("the fixture page has ink on it", direct.count { (it and 0xFFFFFF) < 0x606060 } > 100)

            sheet.cached = true
            val cached = sheet.frame()
            assertEquals("same frame size", direct.size, cached.size)
            var worst = 0
            var differing = 0
            for (index in direct.indices) {
                var diff = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    val a = (direct[index] shr shift) and 0xFF
                    val b = (cached[index] shr shift) and 0xFF
                    diff = maxOf(diff, abs(a - b))
                }
                if (diff > 0) differing++
                worst = maxOf(worst, diff)
            }
            assertTrue(
                "the cached sheet is the painted one (worst channel difference $worst, " +
                    "$differing of ${direct.size} pixels)",
                worst <= 2 && differing < direct.size / 100,
            )
        } finally {
            sheet.close()
        }
    }

    @Test
    fun `one rasterisation serves every frame until the viewport moves`() {
        val sheet = Sheet(page(), width, height)
        try {
            sheet.cached = true
            // Four frames of a drawn stroke: the stroke moves, the page does not.
            for (frame in 1..4) {
                sheet.live = 10f + frame * 8f
                sheet.frame()
            }
            assertEquals("the page was rasterised once", 1, sheet.raster.builds)
            assertEquals("and blitted every frame", 4, sheet.raster.blits)

            // A scroll invalidates it exactly once; the frame after it is a blit again.
            sheet.viewport = Viewport(zoom = 1f, offsetY = -12f)
            sheet.live = 60f
            sheet.frame()
            assertEquals("a scroll re-rasterises the sheet", 2, sheet.raster.builds)
            sheet.live = 68f
            sheet.frame()
            assertEquals("and later frames are blits again", 6, sheet.raster.blits)
        } finally {
            sheet.close()
        }
    }
}
