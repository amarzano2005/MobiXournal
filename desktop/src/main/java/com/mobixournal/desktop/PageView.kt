package com.mobixournal.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke as InkStroke
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.render.InkGeometry

/**
 * One page of a document, painted on a Compose/Skia canvas at [scale] (1.0 = page points as dp).
 *
 * The canvas is the *only* thing this preview renders with — there is no `SurfaceView` and no
 * bitmap cache yet — but it draws from the same [Page] model the Android editor draws from, so the
 * content is the document, not a screenshot of it.
 */
@Composable
fun PageView(page: Page, scale: Float, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size((page.width * scale).dp, (page.height * scale).dp)) {
        // The box is sized in dp, so the painter must convert page points to device pixels with the
        // display density: without it a HiDPI screen would draw the ink at 1:1 inside a larger box.
        drawPageContent(page, pxPerPt = scale * density, textMeasurer = measurer)
    }
}

/** Paints the sheet, then every layer's elements in document order (first = bottom, last = top). */
internal fun DrawScope.drawPageContent(page: Page, pxPerPt: Float, textMeasurer: TextMeasurer) {
    drawPaper(page.background, page.width, page.height, pxPerPt)
    for (layer in page.layers) {
        for (element in layer.elements) drawElement(element, pxPerPt, textMeasurer)
    }
}

private fun DrawScope.drawElement(element: Element, scale: Float, textMeasurer: TextMeasurer) {
    when (element) {
        is InkStroke -> drawInkStroke(element, scale)
        is TextElement -> drawTextBox(element, scale, textMeasurer)
        // Images and the living families (plots, tables, circuits, shapes) are the shared
        // `mobix-render` module's job; skipping them here is visible, not silent — the preview's
        // status in `docs/desktop-workstation.md` says so.
        else -> Unit
    }
}

private fun DrawScope.drawInkStroke(stroke: InkStroke, scale: Float) {
    val pts = stroke.points
    if (pts.size < 2) return
    val color = Color(InkGeometry.renderColor(stroke.tool, stroke.color))

    if (stroke.tool == Tool.HIGHLIGHTER) {
        // One translucent band, multiplied into the paper: the marker reads over ink instead of
        // veiling it, exactly as the Android painter's `drawBand` does.
        drawPath(
            PageInk.polyline(pts, scale),
            color,
            style = Stroke(
                width = (InkGeometry.bandWidth(pts) * scale).toFloat().coerceAtLeast(0.5f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = BlendMode.Multiply,
        )
        return
    }

    val strokeWidth = (pts.first().width * scale).toFloat().coerceAtLeast(0.5f)
    if (stroke.uniformWidth || InkGeometry.uniformWidth(pts)) {
        val widthPt = pts.first().width
        drawPath(
            PageInk.polyline(pts, scale),
            color,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PageInk.dashEffect(stroke.lineStyle, widthPt, scale),
            ),
        )
    } else {
        // A pressure stroke keeps its taper by filling one variable-width outline.
        drawPath(PageInk.taperPath(pts, scale), color)
    }
}

private fun DrawScope.drawTextBox(box: TextElement, scale: Float, textMeasurer: TextMeasurer) {
    if (box.content.isBlank()) return
    val layout = textMeasurer.measure(
        text = box.content,
        style = TextStyle(
            color = Color(box.color),
            fontSize = (box.size * scale).sp,
            fontFamily = box.family(),
        ),
    )
    drawText(layout, topLeft = Offset((box.x * scale).toFloat(), (box.y * scale).toFloat()))
}

/** Xournal++'s font families are CSS-ish names; map the ones we can name to a platform family. */
private fun TextElement.family(): FontFamily = when {
    font.contains("mono", ignoreCase = true) -> FontFamily.Monospace
    font.contains("serif", ignoreCase = true) && !font.contains("sans", ignoreCase = true) -> FontFamily.Serif
    else -> FontFamily.SansSerif
}
