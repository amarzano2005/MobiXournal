package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.drawPdfPreviewBands
import com.mobixournal.desktop.io.drawPdfTextSelection

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mobixournal.render.MagneticHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a PDF text selection and a snapping highlighter look like: drawn on a headless canvas, then read
 * back as pixels.
 *
 * The model tests say *which* words are selected; this one says the selection is **visible where the
 * words are** and nowhere else — a wash on the selected line and blank paper beside it — and that a
 * snapped band paints as a band. A geometry that is right in the model and painted one line off is the
 * kind of bug a model test cannot see.
 */
class DesktopPdfTextRenderTest {

    private val width = 240
    private val height = 60

    private fun render(draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit): ImageBitmap {
        val bitmap = ImageBitmap(width, height)
        val canvas = Canvas(bitmap)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat()), draw)
        return bitmap
    }

    @Test
    fun `the wash covers the selected words and nothing beside them`() {
        val word = com.mobixournal.render.PdfWord("Magnetic", 20.0, 20.0, 90.0, 34.0)
        val bitmap = render { drawPdfTextSelection(listOf(word), pxPerPt = 1f) }
        val pixels = bitmap.toPixelMap()
        // Inside the box: the accent's wash makes it not the (transparent) background.
        val inside = pixels[55, 27]
        assertTrue("the selected words are washed", inside.alpha > 0.1f)
        // Beside the box, and below the line: nothing is painted there.
        assertEquals("no wash beside the word", 0f, pixels[150, 27].alpha, 0.001f)
        assertEquals("and none under the line", 0f, pixels[55, 55].alpha, 0.001f)
    }

    @Test
    fun `the wash is one continuous band across the words and the space between them`() {
        // "Magnetic bands": a 12 pt space on a 14 pt line — one band, not two tiles.
        val words = listOf(
            com.mobixournal.render.PdfWord("Magnetic", 20.0, 20.0, 60.0, 34.0),
            com.mobixournal.render.PdfWord("bands", 72.0, 20.0, 110.0, 34.0),
        )
        val bitmap = render { drawPdfTextSelection(words, pxPerPt = 1f) }
        val pixels = bitmap.toPixelMap()
        assertTrue("the first word is washed", pixels[40, 27].alpha > 0.1f)
        assertTrue("the space between the words is washed too", pixels[66, 27].alpha > 0.1f)
        assertTrue("and the second word", pixels[90, 27].alpha > 0.1f)
        assertEquals("nothing is washed past the last word", 0f, pixels[150, 27].alpha, 0.001f)
    }

    @Test
    fun `no border is drawn per word, so the band's edge is its own wash`() {
        val word = com.mobixournal.render.PdfWord("Magnetic", 20.0, 20.0, 90.0, 34.0)
        val bitmap = render { drawPdfTextSelection(listOf(word), pxPerPt = 1f) }
        val pixels = bitmap.toPixelMap()
        val interior = pixels[55, 27].alpha
        assertEquals("a per-word hairline would darken the top edge", interior, pixels[55, 20].alpha, 0.01f)
        assertEquals("and the left edge", interior, pixels[20, 27].alpha, 0.01f)
    }

    @Test
    fun `two lines are two bands with unwashed paper between them`() {
        val words = listOf(
            com.mobixournal.render.PdfWord("one", 20.0, 20.0, 80.0, 34.0),
            com.mobixournal.render.PdfWord("two", 20.0, 40.0, 80.0, 54.0),
        )
        val bitmap = render { drawPdfTextSelection(words, pxPerPt = 1f) }
        val pixels = bitmap.toPixelMap()
        assertTrue("the first line is washed", pixels[50, 27].alpha > 0.1f)
        assertEquals("the gap between the lines is not", 0f, pixels[50, 37].alpha, 0.001f)
        assertTrue("and the second line is", pixels[50, 47].alpha > 0.1f)
    }

    @Test
    fun `a zoomed sheet scales the band without splitting it`() {
        val word = com.mobixournal.render.PdfWord("Magnetic", 10.0, 5.0, 40.0, 12.0)
        val bitmap = render { drawPdfTextSelection(listOf(word), pxPerPt = 2f) }
        val pixels = bitmap.toPixelMap()
        assertTrue("the band is twice as tall", pixels[40, 20].alpha > 0.1f)
        assertEquals("and ends where the sheet says", 0f, pixels[40, 30].alpha, 0.001f)
        assertEquals("it starts where the sheet says", 0f, pixels[40, 6].alpha, 0.001f)
    }

    @Test
    fun `a snapped band paints across its line`() {
        val band = MagneticHighlighter.Band(left = 10.0, top = 20.0, right = 120.0, bottom = 34.0)
        val bitmap = render { drawPdfPreviewBands(listOf(band), 0xFFFFEB3B.toInt(), pxPerPt = 1f) }
        val pixels = bitmap.toPixelMap()
        assertTrue("the band is visible", pixels[60, 27].alpha > 0.1f)
        assertEquals("but not outside it", 0f, pixels[200, 27].alpha, 0.001f)
    }
}
