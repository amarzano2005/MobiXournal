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
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's picture is the **same page**, only recorded once.
 *
 * The frame budget at 144 Hz is 6.9 ms, and `drawPageContent` spends it on Kotlin: every living entity
 * recompiled, every text box measured, a `Path` per stroke. `PagePicture` records those calls once per
 * viewport and replays the result, so the two things worth proving are that the replay is *the same
 * pixels* — a cache that draws its own idea of the page would be a silent file/render divergence — and
 * that a frame of a live stroke does not record it again.
 */
@OptIn(ExperimentalComposeUiApi::class)
class PagePictureTest {

    private val width = 420
    private val height = 520

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
                } + TextElement(
                    font = "sans",
                    size = 14.0,
                    x = 30.0,
                    y = 470.0,
                    color = 0xFF000000.toInt(),
                    content = "The recorded text is measured once",
                ),
            ),
        ),
    )

    /** One window painting [page] either through the picture or straight. */
    private class Sheet(page: Page, width: Int, height: Int) {
        val picture = PagePicture()
        var recorded by mutableStateOf(false)
        var viewport by mutableStateOf(Viewport(zoom = 1f))

        /** The stroke being drawn: it moves every frame, which is what repaints the canvas at all. */
        var live by mutableStateOf(10f)

        val scene: ImageComposeScene = ImageComposeScene(width, height, Density(1f)) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                if (recorded) {
                    picture.draw(this, page, viewport, 1f, 1f, null, emptySet(), measurer)
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
    fun `the replayed sheet is pixel-identical to the direct paint`() {
        val sheet = Sheet(page(), width, height)
        try {
            val direct = sheet.frame()
            assertTrue("the fixture page has ink on it", direct.count { (it and 0xFFFFFF) < 0x606060 } > 100)

            sheet.recorded = true
            val replayed = sheet.frame()
            assertEquals("same frame size", direct.size, replayed.size)
            var worst = 0
            var differing = 0
            for (index in direct.indices) {
                var diff = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    val a = (direct[index] shr shift) and 0xFF
                    val b = (replayed[index] shr shift) and 0xFF
                    diff = maxOf(diff, abs(a - b))
                }
                if (diff > 0) differing++
                worst = maxOf(worst, diff)
            }
            assertTrue(
                "the replayed sheet is the painted one (worst channel difference $worst, " +
                    "$differing of ${direct.size} pixels)",
                worst <= 2 && differing < direct.size / 100,
            )
        } finally {
            sheet.close()
        }
    }

    @Test
    fun `a frame of a live stroke replays the page, and scrolling or zooming records nothing`() {
        val sheet = Sheet(page(), width, height)
        try {
            sheet.recorded = true
            for (frame in 1..4) {
                sheet.live = 10f + frame * 8f
                sheet.frame()
            }
            assertEquals("the page was recorded once", 1, sheet.picture.records)
            assertEquals("and replayed by every frame", 4, sheet.picture.replays)

            // The point of recording at document scale: a scroll is a translate of the same picture and a
            // zoom is a scale of it, so a wheel or a pinch costs a replay and never the Kotlin paint.
            sheet.viewport = Viewport(zoom = 1f, offsetY = -12f)
            sheet.live = 60f
            sheet.frame()
            assertEquals("a scroll does not record the sheet again", 1, sheet.picture.records)
            sheet.viewport = Viewport(zoom = 1.6f, offsetX = -40f, offsetY = -90f)
            sheet.live = 68f
            sheet.frame()
            assertEquals("and neither does a zoom", 1, sheet.picture.records)
            assertEquals("every frame was served by a replay", 6, sheet.picture.replays)
        } finally {
            sheet.close()
        }
    }

    /**
     * The scaled replay is still *the page*: the same content at the same viewport must look the same
     * whether it was recorded at the reader's zoom or placed by the canvas matrix. Skia replays vector
     * commands under the total matrix, so this is a difference of rounding at most — measured, not assumed.
     */
    @Test
    fun `the replayed sheet is the painted one at a zoom and a scroll`() {
        for (viewport in listOf(
            Viewport(zoom = 1.6f, offsetX = -40f, offsetY = -90f),
            Viewport(zoom = 0.75f, offsetY = 30f),
        )) {
            val page = page(strokes = 40)
            val direct = directFrame(page, viewport)
            val replayed = replayedFrame(page, viewport)
            var worst = 0
            var differing = 0
            for (index in direct.indices) {
                var diff = 0
                for (shift in intArrayOf(16, 8, 0)) {
                    val a = (direct[index] shr shift) and 0xFF
                    val b = (replayed[index] shr shift) and 0xFF
                    diff = maxOf(diff, abs(a - b))
                }
                if (diff > 0) differing++
                worst = maxOf(worst, diff)
            }
            assertTrue(
                "at ${viewport.zoom}× the replay is the painted page (worst channel difference $worst, " +
                    "$differing of ${direct.size} pixels)",
                worst <= 8 && differing < direct.size / 20,
            )
        }
    }

    /** The page drawn straight at [viewport] — the reference the replay is compared with. */
    private fun directFrame(page: Page, viewport: Viewport): IntArray = headless(width, height) {
        val measurer = rememberTextMeasurer()
        Canvas(Modifier.fillMaxSize()) {
            withTransform({ translate(viewport.offsetX, viewport.offsetY) }) {
                drawPageContent(page, viewport.zoom, measurer)
            }
        }
    }

    /** The same page and viewport, served by the recorded picture. */
    private fun replayedFrame(page: Page, viewport: Viewport): IntArray = headless(width, height) {
        val measurer = rememberTextMeasurer()
        val picture = PagePicture()
        Canvas(Modifier.fillMaxSize()) {
            picture.draw(this, page, viewport, viewport.zoom, 1f, null, emptySet(), measurer)
        }
    }

    /** One frame of a scene, as ARGB pixels. */
    private fun headless(width: Int, height: Int, content: @androidx.compose.runtime.Composable () -> Unit): IntArray {
        val scene = ImageComposeScene(width, height, Density(1f)) { content() }
        try {
            val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
            val image = ImageIO.read(ByteArrayInputStream(png))
            return IntArray(image.width * image.height).also {
                image.getRGB(0, 0, image.width, image.height, it, 0, image.width)
            }
        } finally {
            scene.close()
        }
    }
}
