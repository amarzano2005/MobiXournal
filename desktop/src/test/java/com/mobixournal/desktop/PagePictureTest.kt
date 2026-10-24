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
    fun `a frame of a live stroke replays the page and a scroll records it once more`() {
        val sheet = Sheet(page(), width, height)
        try {
            sheet.recorded = true
            for (frame in 1..4) {
                sheet.live = 10f + frame * 8f
                sheet.frame()
            }
            assertEquals("the page was recorded once", 1, sheet.picture.records)
            assertEquals("and replayed by every frame", 4, sheet.picture.replays)

            sheet.viewport = Viewport(zoom = 1f, offsetY = -12f)
            sheet.live = 60f
            sheet.frame()
            assertEquals("a scroll records the sheet again", 2, sheet.picture.records)
            sheet.live = 68f
            sheet.frame()
            assertEquals("later frames replay it again", 6, sheet.picture.replays)
        } finally {
            sheet.close()
        }
    }
}
