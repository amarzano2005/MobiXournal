package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A text box is painted at its **point size**, on any display — and the one being retyped is left to
 * the in-place field instead of being painted under it.
 *
 * The page is measured in pt and painted in px (`pxPerPt = zoom × density`), so a 40 pt box has to be
 * 40 px tall per point of density — twice as many pixels at density 2, not four times. Asking for `.sp`
 * on a page point multiplied the density in a second time, so every box (and every formula, which asks
 * the same question — `TexPainter`) was painted [density] times too large while its own position stayed
 * right: on a 150 % Windows display the words spilled out of the place they were written.
 *
 * The ratio is measured through a real scene, because the density is the scene's and the size is only
 * visible in the pixels it produces.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopTextPaintTest {

    private fun page(): Page = Page(
        width = 200.0,
        height = 60.0,
        background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain"),
        layers = listOf(
            Layer(
                listOf(
                    TextElement(
                        font = "Sans",
                        size = 40.0,
                        x = 10.0,
                        y = 8.0,
                        color = 0xFF000000.toInt(),
                        content = "Hxg",
                    ),
                ),
            ),
        ),
    )

    /** How many device pixels tall the words are, painted at [density]. */
    private fun inkHeight(density: Float): Int {
        val page = page()
        val scene = ImageComposeScene(
            (page.width * density).toInt(),
            (page.height * density).toInt(),
            Density(density),
        ) { PageView(page = page, scale = 1f) }
        try {
            val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
            val image = ImageIO.read(ByteArrayInputStream(png))
            var top = Int.MAX_VALUE
            var bottom = -1
            val pixels = IntArray(image.width * image.height)
            image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = pixels[y * image.width + x]
                    // Transparent is the desk around the sheet; the paper is white. Only the words
                    // (and their antialiasing) are dark and opaque.
                    if (argb ushr 24 and 0xFF != 0xFF) continue
                    val r = argb shr 16 and 0xFF
                    val g = argb shr 8 and 0xFF
                    val b = argb and 0xFF
                    if ((r + g + b) / 3 >= 160) continue
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            assertTrue("the fixture paints some words at density $density", bottom >= 0)
            return bottom - top + 1
        } finally {
            scene.close()
        }
    }

    /**
     * The sheet leaves the box the in-place field has taken over to the field.
     *
     * Without it the old words are painted under the new ones for the whole of a retype — the doubled
     * text that made correcting a box unreadable. The skip is part of the picture's key, so opening and
     * closing a box re-records the sheet once each: that is the price of hiding an element already in
     * the recording, and it is paid once per edit, not per frame.
     */
    @Test
    fun `the box being retyped is not painted under the field`() {
        val page = page()
        val box = page.layers.single().elements.single()
        val picture = PagePicture()
        var skip by mutableStateOf<Element?>(null)
        val scene = ImageComposeScene(200, 60, Density(1f)) {
            val measurer = rememberTextMeasurer()
            Canvas(Modifier.fillMaxSize()) {
                picture.draw(this, page, Viewport(zoom = 1f), 1f, 1f, null, emptySet(), measurer, skip)
            }
        }
        try {
            assertTrue("the words are painted while nothing is being retyped", darkPixels(scene.render()) > 20)

            skip = box
            Snapshot.sendApplyNotifications()
            scene.render()

            assertEquals("with the box hidden, nothing of it is left on the sheet", 0, darkPixels(scene.render()))
            assertEquals("and hiding it recorded the sheet once", 2, picture.records)
        } finally {
            scene.close()
            picture.close()
        }
    }

    /** The opaque, dark pixels of a frame — the words, without the desk or the paper. */
    private fun darkPixels(frame: org.jetbrains.skia.Image): Int {
        val png = frame.encodeToData()?.bytes ?: error("Skia produced no image")
        val image = ImageIO.read(ByteArrayInputStream(png))
        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        return pixels.count { argb ->
            argb ushr 24 and 0xFF == 0xFF && ((argb shr 16 and 0xFF) + (argb shr 8 and 0xFF) + (argb and 0xFF)) / 3 < 160
        }
    }

    /**
     * The in-place field is the **page's own ink**, not a surface over it.
     *
     * It used to wear the window's floating glass, so typing opened a charcoal slab across a third of a
     * white sheet — the one material class the element does not belong to (what is being typed is page
     * content, like the strokes a draft draws). With no fill, the paper showing through a point inside the
     * box's width and away from its glyphs is the check that the slab is gone.
     */
    @Test
    fun `the field paints no slab over the page`() {
        val draft = TextBoxDraft(
            tabKey = "field.mobix",
            page = 0,
            pageW = 300.0,
            pageH = 200.0,
            x = 20.0,
            y = 20.0,
            size = 20.0,
            colour = 0xFF000000.toInt(),
            typing = TextFieldValue("Hi"),
        )
        val scene = ImageComposeScene(300, 200, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TextBoxHost(
                    box = draft,
                    zoom = 1f,
                    density = 1f,
                    offsetX = 0f,
                    offsetY = 0f,
                    onChange = {},
                )
            }
        }
        try {
            val png = scene.render().encodeToData()?.bytes ?: error("Skia produced no image")
            val image = ImageIO.read(ByteArrayInputStream(png))
            // Inside the box's third of the page (20…120 pt) and past the two glyphs, so this pixel is
            // either the field's fill or the paper.
            val inside = image.getRGB(100, 26)
            assertTrue("the box's fill is gone, not merely faint: ${Integer.toHexString(inside)}", isPaper(inside))
        } finally {
            scene.close()
        }
    }

    /** Opaque white, to within antialiasing's tolerance. */
    private fun isPaper(argb: Int): Boolean {
        if (argb ushr 24 and 0xFF != 0xFF) return false
        return (argb shr 16 and 0xFF) >= 250 && (argb shr 8 and 0xFF) >= 250 && (argb and 0xFF) >= 250
    }

    @Test
    fun `a text box doubles its pixels with the density, instead of quadrupling them`() {
        val single = inkHeight(1f)
        val double = inkHeight(2f)

        // 40 pt at density 1 is a cap-plus-descender height of roughly 30 px; the exact number is the
        // font's, so the assertion is the ratio.
        val ratio = double.toDouble() / single
        assertTrue(
            "the words are twice as tall at density 2 (were $single px then $double px, ×$ratio)",
            ratio in 1.7..2.3,
        )
    }
}
