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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke as InkStroke
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.format.model.isLivingEntity
import com.mobixournal.render.InkGeometry
import com.mobixournal.render.LivingInk

/**
 * One page of a document, painted on a Compose/Skia canvas at [scale] (1.0 = page points as dp).
 *
 * The canvas is the *only* thing this preview renders with — there is no `SurfaceView` and no
 * bitmap cache yet — but it draws from the same [Page] model the Android editor draws from, so the
 * content is the document, not a screenshot of it.
 */
@Composable
fun PageView(
    page: Page,
    scale: Float,
    modifier: Modifier = Modifier,
    backdrop: PageBackdrop? = null,
    hiddenLayers: Set<Int> = emptySet(),
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier.size((page.width * scale).dp, (page.height * scale).dp)) {
        // The box is sized in dp, so the painter must convert page points to device pixels with the
        // display density: without it a HiDPI screen would draw the ink at 1:1 inside a larger box.
        drawPageContent(
            page,
            pxPerPt = scale * density,
            textMeasurer = measurer,
            backdrop = backdrop,
            hiddenLayers = hiddenLayers,
        )
    }
}

/**
 * Paints the sheet, its picture, then every layer's elements in document order (first = bottom, last =
 * top).
 *
 * The picture (a pixmap or an annotated PDF page) goes **between** the paper and the ink: it is what
 * the page is written on, so a highlighter multiplies into it and a stroke covers it, exactly as on the
 * tablet.
 */
internal fun DrawScope.drawPageContent(
    page: Page,
    pxPerPt: Float,
    textMeasurer: TextMeasurer,
    backdrop: PageBackdrop? = null,
    hiddenLayers: Set<Int> = emptySet(),
) {
    // The sheet is the *page's* box, not the canvas': a zoomed page is drawn inside a clip that is the
    // window, so `size` here would paint the paper across the whole viewport.
    val sheet = Size((page.width * pxPerPt).toFloat(), (page.height * pxPerPt).toFloat())
    drawPageShadow(sheet)
    drawPaper(page.background, page.width, page.height, pxPerPt, sheet)
    backdrop?.image(page.background)?.let { picture ->
        drawImage(
            picture,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(sheet.width.roundToInt(), sheet.height.roundToInt()),
        )
    }
    // A hidden layer is skipped like an empty one: the page keeps its elements, the window just does
    // not paint them (the same view state that hides it in the sidebar's list).
    for ((index, layer) in page.layers.withIndex()) {
        if (index in hiddenLayers) continue
        drawElements(layer.elements, pxPerPt, textMeasurer)
    }
}

/**
 * A page's elements in document order — the one loop that paints ink, and the one a *partial* record
 * reuses when only the strokes added since the last one are new (`PagePicture`).
 *
 * Shared rather than duplicated: a tail picture that painted its strokes with a second rule would let the
 * freshly drawn ink and the ink recorded before it drift apart at a seam. Hidden layers are the caller's
 * filter, so this is exactly the elements it was handed.
 */
internal fun DrawScope.drawElements(
    elements: List<Element>,
    pxPerPt: Float,
    textMeasurer: TextMeasurer,
) {
    for (element in elements) drawElement(element, pxPerPt, textMeasurer)
}

/**
 * The window's one shadow, under the sheet.
 *
 * On a true-black desk a shadow cannot be darker than the desk, so this is drawn as the few units
 * *above* black that a soft falloff would leave: a faint halo hugging the paper, fading out within
 * `pageShadowDp`. Without it a page on `#000000` reads as a hole cut in the window rather than a sheet
 * lying on a desk — which is why the halo is the one place the desk is not literally black.
 */
private fun DrawScope.drawPageShadow(sheet: Size) {
    val radius = DesktopTheme.pageShadow.toPx()
    if (radius <= 0f) return
    val steps = 8
    for (step in 1..steps) {
        val spread = radius * step / steps
        val alpha = 0.045f * (1f - (step - 1f) / steps)
        drawRect(
            color = DesktopTheme.pageShadowColor.copy(alpha = alpha),
            topLeft = Offset(-spread, -spread),
            size = Size(sheet.width + spread * 2f, sheet.height + spread * 2f),
        )
    }
}

private fun DrawScope.drawElement(element: Element, scale: Float, textMeasurer: TextMeasurer) {
    when (element) {
        is InkStroke -> drawInkStroke(element, scale)
        is TextElement -> drawTextBox(element, scale, textMeasurer)
        // A formula: a `<teximage>` read from a `.xopp`, and the one a living math block compiles to
        // (`LivingInk`). Typeset from its source through the shared layout, never left blank.
        is TexImageElement -> drawTexImage(element, scale, textMeasurer)
        // A living entity is a *recipe*, and `LivingInk` is the one place that compiles it into ink —
        // the same dispatch the Android canvas, `XoppSynthesis`, PDF and SVG export use. Drawing its
        // result here is what keeps a table or a graph identical on both shells without a second
        // renderer to drift: this shell only knows how to paint strokes and text, which is all a
        // compiled entity is.
        else -> if (element.isLivingEntity) {
            for (ink in LivingInk.of(element).elements) drawElement(ink, scale, textMeasurer)
        }
    }
}

/**
 * One stroke at [scale] — shared with the canvas's **live preview** in `PageCanvas`, so the ink being
 * drawn and the ink the lift commits are painted by the same rule (highlighter band, taper, dash).
 */
internal fun DrawScope.drawInkStroke(stroke: InkStroke, scale: Float) {
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

/**
 * The text box's face: `TextAuthoring`'s mapper, which the in-place Text field reads too — so the line
 * being typed breaks where the written one will.
 */
private fun TextElement.family(): FontFamily = textFontFamily(font)
