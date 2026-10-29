package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.KaraokeStrokeAnimator
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Karaoke on the window: a page painted **as it was written** says the same thing the tablet's does.
 *
 * [KaraokeStrokeAnimator] decides — the shared object, so nothing here re-derives which stroke is dimmed
 * or where it is cut — and this measures the pixels that decision produces: a stroke whose recording has
 * not reached it is lighter than the same stroke drawn as finished, and a stroke being written is inked
 * where the playhead has arrived and blank paper where it has not.
 *
 * The frames are written to `desktop/build/desktop-render/` so the three states can be looked at, the way
 * every other render test leaves its picture.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopKaraokeTest {

    /** One pen stroke from (40, 100) to (200, 100), written over `take.wav` from 1000 ms. */
    private val page = Page(
        width = WIDTH.toDouble(),
        height = HEIGHT.toDouble(),
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(
            Layer(
                listOf(
                    Stroke(
                        points = listOf(
                            StrokePoint(40.0, 100.0, 3.0, dtMs = 0),
                            StrokePoint(80.0, 100.0, 3.0, dtMs = 100),
                            StrokePoint(120.0, 100.0, 3.0, dtMs = 200),
                            StrokePoint(160.0, 100.0, 3.0, dtMs = 300),
                            StrokePoint(200.0, 100.0, 3.0, dtMs = 400),
                        ),
                        color = 0xFF000000.toInt(),
                        tool = Tool.PEN,
                        capStyle = "round",
                        uniformWidth = true,
                        extraAttrs = mapOf("fn" to "take.wav", "ts" to "1000"),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `a stroke the recording has not reached is dimmed`() {
        // Nothing has been said yet when the clip starts: the whole stroke is a pale ghost of itself.
        val before = ink(playheadMs = 500, x = 120)
        val finished = ink(playheadMs = 2_000, x = 120)
        assertTrue("dimmed ink reads as paper-lit ($before)", before > 150)
        assertTrue("a finished stroke is its own black ($finished)", finished < 90)
        assertTrue("and the dimmed one is visibly lighter", before > finished + 60)
    }

    @Test
    fun `a stroke being written is cut at the playhead`() {
        // 1200 ms is halfway through the stroke's 400 ms: the head is inked, the tail is still paper.
        val head = ink(playheadMs = 1_200, x = 70)
        val tail = ink(playheadMs = 1_200, x = 175)
        assertTrue("the part already written is ink ($head)", head < 150)
        assertTrue("the part not yet written is paper ($tail)", tail > 240)
    }

    @Test
    fun `nothing is on means the page is painted as finished`() {
        val karaoke = DesktopKaraoke()
        karaoke.publish("take.wav", 1_200, playing = true)
        assertNotNull("a clip is on", karaoke.state)
        karaoke.clear()
        assertNull("and turning away drops it", karaoke.state)
        // A page with no driver at all is the same answer as one cleared.
        assertEquals(ink(playheadMs = 2_000, x = 175), ink(playheadMs = null, x = 175))
    }

    /** The grey value of the stroke at page x [x] — 0 is black ink, 255 is the page's white. */
    private fun ink(playheadMs: Int?, x: Int): Int {
        val scene = ImageComposeScene(WIDTH, HEIGHT, Density(1f)) {
            val measurer = rememberTextMeasurer()
            val karaoke = playheadMs?.let {
                KaraokeStrokeAnimator.State("take.wav", it, isPlaying = true)
            }
            Canvas(Modifier.fillMaxSize()) {
                withTransform({ translate(0f, 0f) }) {
                    drawPageContent(page, pxPerPt = 1f, textMeasurer = measurer, karaoke = karaoke)
                }
            }
        }
        return try {
            Snapshot.sendApplyNotifications()
            val png = requireNotNull(scene.render().encodeToData()?.bytes) { "Skia produced no PNG" }
            val label = "karaoke-${playheadMs ?: "off"}-$x.png"
            File("build/desktop-render/$label").apply { parentFile?.mkdirs() }.writeBytes(png)
            val image = ImageIO.read(ByteArrayInputStream(png))
            // The darkest pixel in a small column around the asked point, as a grey value: the stroke is
            // three points wide, so the exact centre row is not worth pinning.
            (95..105).minOf { y -> image.getRGB(x, y) and 0xFF }
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val WIDTH = 260
        const val HEIGHT = 200
    }
}
