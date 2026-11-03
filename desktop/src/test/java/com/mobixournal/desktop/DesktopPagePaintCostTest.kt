package com.mobixournal.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mobixournal.desktop.sheet.drawPageContent
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import org.jetbrains.skia.Surface
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A page costs what it holds**, measured on the raster the thumbnail lane paints.
 *
 * The sheet's halo used to be eight *filled* translucent rectangles over the whole page, so every paint of
 * every page paid to blend eight page-areas that the paper then covered: a blank page cost the same 195 ms
 * as one with 1 600 strokes, a tab switch cost a quarter of a second, and the sidebar's thumbnails — painted
 * one at a time on a software raster — were the "considerable delay" a reader felt when changing document.
 * The halo is now eight thin **bands around** the paper, which is the only part of it anyone can see.
 *
 * The budget is generous because a CI box is not the owner's PC: it is set to catch a return of a cost that
 * is paid per page rather than per stroke, not to grade a millisecond. The readings are printed as well —
 * "is it quick?" is a question about a picture, and a number in a log is what makes a regression visible.
 */
class DesktopPagePaintCostTest {

    private fun page(strokes: Int): Page = Page(
        width = 850.0,
        height = 1100.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer((0 until strokes).map { SyntheticNotebook.stroke(40.0 + it % 700) })),
        id = "pg_bench",
    )

    private fun median(label: String, times: Int = 5, body: () -> Unit): Double {
        repeat(2) { body() }
        val samples = (0 until times).map {
            val started = System.nanoTime()
            body()
            (System.nanoTime() - started) / 1_000_000.0
        }.sorted()
        val median = samples[samples.size / 2]
        println("%-32s median %7.2f ms  max %7.2f ms".format(label, median, samples.last()))
        return median
    }

    private fun draw(width: Int, height: Int, size: Size, block: DrawScope.() -> Unit) {
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            CanvasDrawScope().draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = surface.canvas.asComposeCanvas(),
                size = size,
                block = block,
            )
        } finally {
            surface.close()
        }
    }

    private fun paint(label: String, page: Page, size: Size = Size(510f, 660f)): Double {
        val measurer = TextMeasurer(
            defaultFontFamilyResolver = createFontFamilyResolver(),
            defaultDensity = Density(1f),
            defaultLayoutDirection = LayoutDirection.Ltr,
        )
        return median(label) {
            draw(size.width.toInt(), size.height.toInt(), size) {
                drawPageContent(page, pxPerPt = 0.6f, textMeasurer = measurer)
            }
        }
    }

    @Test
    fun `a page costs what it holds, not a fixed wash`() {
        val blank = paint("drawPageContent, blank page", page(0))
        val written = paint("drawPageContent, 200 strokes", page(200))
        val dense = paint("drawPageContent, 1600 strokes", page(1600))

        // A page of paper and its halo is a rectangle or two — anywhere near a frame budget means the
        // halo is being blended over the sheet's area again.
        assertTrue("a blank page paints in tens of milliseconds, not hundreds ($blank ms)", blank < 60.0)
        assertTrue("a written page paints like a written page ($written ms)", written < 60.0)
        assertTrue("a dense page paints like a dense page ($dense ms)", dense < 90.0)
    }
}
