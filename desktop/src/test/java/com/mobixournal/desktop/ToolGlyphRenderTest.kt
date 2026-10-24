package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.design.ToolGlyph
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Every catalogue glyph, **drawn** — the window's half of "a slot face is the catalogue's glyph".
 *
 * `ToolGlyphCatalogueTest` proves the geometry is well-formed; this proves the window can render it:
 * each entry is painted through the same `drawToolGlyph` a slot uses, into an `ImageComposeScene`, and
 * the pixels are inspected. A glyph whose parts are legal but empty at this size (a stroke thinner than
 * the raster, a shape that lands off the grid) fails here and nowhere else.
 *
 * It also writes a contact sheet to `build/tool-glyphs.png` — one cell per glyph — so the family can be
 * looked at as a family. Two toolbars that draw the same parts cannot drift, but *art* can still be
 * wrong, and the only way to see that is to look.
 */
class ToolGlyphRenderTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `every glyph draws ink at slot size`() {
        val glyphs = ToolGlyph.entries
        for (glyph in glyphs) {
            val scene = ImageComposeScene(width = CELL, height = CELL, density = Density(1f)) {
                GlyphCell(glyph)
            }
            val png = try {
                scene.render().encodeToData()?.bytes
            } finally {
                scene.close()
            }
            // The scene starts transparent, so "drawn" is simply "not fully transparent": counting
            // opaque pixels measures the art, not the background it was rendered on.
            val ink = png?.let { bytes ->
                ImageIO.read(bytes.inputStream()).let { image ->
                    (0 until image.width).sumOf { x ->
                        (0 until image.height).count { y -> image.getRGB(x, y) ushr 24 != 0 }
                    }
                }
            } ?: 0
            assertTrue("$glyph draws no ink", ink > MIN_INK)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the contact sheet is written for a human to look at`() {
        val columns = 8
        val rows = (ToolGlyph.entries.size + columns - 1) / columns
        val sheet = BufferedImage(CELL * columns, CELL * rows, BufferedImage.TYPE_INT_ARGB)
        val graphics = sheet.createGraphics()
        graphics.color = java.awt.Color.WHITE
        graphics.fillRect(0, 0, sheet.width, sheet.height)
        ToolGlyph.entries.forEachIndexed { index, glyph ->
            val cell = renderCell(glyph)
            graphics.drawImage(cell, (index % columns) * CELL, (index / columns) * CELL, null)
        }
        graphics.dispose()
        val out = File("build/tool-glyphs.png").apply { parentFile?.mkdirs() }
        ImageIO.write(sheet, "png", out)
        assertTrue("the contact sheet is a real image", out.length() > 5_000)
    }

    /** One glyph, painted by the window's own adapter into its own cell. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun renderCell(glyph: ToolGlyph): BufferedImage {
        val scene = ImageComposeScene(width = CELL, height = CELL, density = Density(1f)) {
            GlyphCell(glyph)
        }
        val png = try {
            scene.render().encodeToData()?.bytes
        } finally {
            scene.close()
        }
        requireNotNull(png) { "$glyph rendered nothing" }
        return ImageIO.read(png.inputStream())
    }

    private companion object {
        /** One slot-sized cell, in pixels at density 1. */
        const val CELL = 48

        /** A glyph has to cover more than a stray pixel to count as drawn. */
        const val MIN_INK = 60
    }
}

@Composable
private fun GlyphCell(glyph: ToolGlyph) {
    Box(Modifier.size(48.dp)) {
        Canvas(Modifier.size(48.dp)) { drawToolGlyph(glyph, Color.Black) }
    }
}
