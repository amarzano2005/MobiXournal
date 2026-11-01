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
    fun `a snapped band paints across its line`() {
        val band = MagneticHighlighter.Band(left = 10.0, top = 20.0, right = 120.0, bottom = 34.0)
        val bitmap = render { drawPdfPreviewBands(listOf(band), 0xFFFFEB3B.toInt(), pxPerPt = 1f) }
        val pixels = bitmap.toPixelMap()
        assertTrue("the band is visible", pixels[60, 27].alpha > 0.1f)
        assertEquals("but not outside it", 0f, pixels[200, 27].alpha, 0.001f)
    }
}
