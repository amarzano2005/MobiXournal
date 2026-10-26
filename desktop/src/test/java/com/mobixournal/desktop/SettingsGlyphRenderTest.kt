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
import com.mobixournal.design.SettingsGlyph
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Every settings glyph, **drawn** — the window's half of "a section's mark is the catalogue's glyph".
 *
 * `DesktopSettingsGlyphTest` proves the geometry is legal (on the grid, inside the margin, more than one
 * stroke); legal art can still be a picture nobody recognises, which is a fault only eyes catch. Each
 * entry is therefore painted through the same `drawSettingsGlyph` an index row uses, at the row's own
 * 16 dp and at a size big enough to see what the shape actually is, and both sheets are written to
 * `build/` so the family can be looked at as a family.
 */
class SettingsGlyphRenderTest {

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `every glyph draws ink at index size`() {
        for (glyph in SettingsGlyph.entries) {
            val ink = inkPixels(renderCell(glyph, INDEX_DP))
            assertTrue("$glyph draws no ink at ${INDEX_DP}dp", ink > INDEX_MIN_INK)
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `the contact sheets are written for a human to look at`() {
        val large = sheet(cellPx = 64, drawnAt = 48)
        val index = sheet(cellPx = 24, drawnAt = INDEX_DP)
        val first = File("build/settings-glyphs.png").apply { parentFile?.mkdirs() }
        val second = File("build/settings-glyphs-index.png").apply { parentFile?.mkdirs() }
        ImageIO.write(large, "png", first)
        ImageIO.write(index, "png", second)
        assertTrue("the contact sheet is a real image", first.length() > 2_000)
        assertTrue("the index sheet is a real image", second.length() > 1_000)
    }

    /** One glyph, painted by the window's own adapter into its own cell. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun renderCell(glyph: SettingsGlyph, dp: Int): BufferedImage {
        val scene = ImageComposeScene(width = dp + 8, height = dp + 8, density = Density(1f)) {
            GlyphCell(glyph, dp)
        }
        val png = try {
            scene.render().encodeToData()?.bytes
        } finally {
            scene.close()
        }
        requireNotNull(png) { "$glyph rendered nothing" }
        return ImageIO.read(png.inputStream())
    }

    /** All the glyphs in a row-per-line grid on white, so the strokes are seen against a page. */
    private fun sheet(cellPx: Int, drawnAt: Int): BufferedImage {
        val columns = 7
        val rows = (SettingsGlyph.entries.size + columns - 1) / columns
        val sheet = BufferedImage(cellPx * columns, cellPx * rows, BufferedImage.TYPE_INT_ARGB)
        val graphics = sheet.createGraphics()
        graphics.color = java.awt.Color.WHITE
        graphics.fillRect(0, 0, sheet.width, sheet.height)
        SettingsGlyph.entries.forEachIndexed { index, glyph ->
            val cell = renderCell(glyph, drawnAt)
            graphics.drawImage(cell, (index % columns) * cellPx, (index / columns) * cellPx, null)
        }
        graphics.dispose()
        return sheet
    }

    private fun inkPixels(image: BufferedImage): Int =
        (0 until image.width).sumOf { x -> (0 until image.height).count { y -> image.getRGB(x, y) ushr 24 != 0 } }

    private companion object {
        /** The size an index row draws a section's mark at (`SettingsSectionRow`). */
        const val INDEX_DP = 16

        /** A mark at 16 dp has to cover more than a stray pixel to count as drawn. */
        const val INDEX_MIN_INK = 20
    }
}

@Composable
private fun GlyphCell(glyph: SettingsGlyph, dp: Int) {
    Box(Modifier.size(dp.dp)) {
        Canvas(Modifier.size(dp.dp)) { drawSettingsGlyph(glyph, Color.Black) }
    }
}
