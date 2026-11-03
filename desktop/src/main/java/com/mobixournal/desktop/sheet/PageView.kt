package com.mobixournal.desktop.sheet
import com.mobixournal.desktop.ink.StrokePathCache
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopImageCache
import com.mobixournal.desktop.io.DesktopKaraoke
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.tools.drawTexImage
import com.mobixournal.desktop.tools.textFace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke as InkStroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.format.model.isLivingEntity
import com.mobixournal.render.InkGeometry
import com.mobixournal.render.KaraokeStrokeAnimator
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
    /** The one element not to paint — the text box the in-place field is retyping, if any. */
    skip: Element? = null,
    /**
     * The recording sounding over this page, or null for a page painted as finished.
     *
     * Karaoke is the one thing a recorded sheet cannot be replayed from: [PagePicture]'s commands say
     * every stroke as it was written, so a page with a clip sounding is painted from the elements
     * directly, one directive per stroke (`DesktopKaraoke`).
     */
    karaoke: KaraokeStrokeAnimator.State? = null,
) {
    // The sheet is the *page's* box, not the canvas': a zoomed page is drawn inside a clip that is the
    // window, so `size` here would paint the paper across the whole viewport.
    val sheet = Size((page.width * pxPerPt).toFloat(), (page.height * pxPerPt).toFloat())
    drawSheetUnderlay(page, pxPerPt, sheet, backdrop)
    // A hidden layer is skipped like an empty one: the page keeps its elements, the window just does
    // not paint them (the same view state that hides it in the sidebar's list).
    for ((index, layer) in page.layers.withIndex()) {
        if (index in hiddenLayers) continue
        drawElements(layer.elements, pxPerPt, textMeasurer, skip, karaoke)
    }
}

/**
 * Everything under the ink: the sheet's shadow, its paper and backdrop, and nothing else.
 *
 * Split out because a **second** picture needs to paint exactly this: a live vertical-space drag keeps the
 * block that stays above the line in one picture and the block that follows it in another, and only the
 * first of the two carries the paper — the gap the block opens has to show what the page is made of, not
 * the ink that used to be there (`ShiftPictures`).
 */
internal fun DrawScope.drawSheetUnderlay(
    page: Page,
    pxPerPt: Float,
    sheet: Size,
    backdrop: PageBackdrop?,
) {
    // No halo here: the shadow is **screen-space chrome**, drawn by the canvas around the sheet's on-screen
    // rectangle (`drawPageShadow`). Inside this painter it was clipped away by every surface that is exactly
    // the page — the thumbnail raster and both exports — and it is re-recorded with the page and replayed
    // through the viewport's scale, so a zoom scaled the falloff with it.
    drawPaper(page.background, page.width, page.height, pxPerPt, sheet)
    backdrop?.image(page.background)?.let { picture ->
        drawImage(
            picture,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(sheet.width.roundToInt(), sheet.height.roundToInt()),
        )
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
    /** The element the live field has taken over, or null to paint them all. */
    skip: Element? = null,
    /** The clip sounding over the page, or null when every stroke is drawn as finished. */
    karaoke: KaraokeStrokeAnimator.State? = null,
) {
    for (element in elements) {
        // Identity: the field holds the very element the page holds, so "the box being retyped" is one
        // reference, not a search for an element that looks the same.
        if (element === skip) continue
        drawElement(element, pxPerPt, textMeasurer, karaoke)
    }
}

/**
 * The window's one shadow, under the sheet.
 *
 * On a true-black desk a shadow cannot be darker than the desk, so this is drawn as the few units
 * *above* black that a soft falloff would leave: a faint halo hugging the paper, fading out within
 * `pageShadowDp`. Without it a page on `#000000` reads as a hole cut in the window rather than a sheet
 * lying on a desk — which is why the halo is the one place the desk is not literally black.
 */
internal fun DrawScope.drawPageShadow(sheet: Rect) {
    val radius = DesktopTheme.pageShadow.toPx()
    if (radius <= 0f) return
    val steps = 8
    // The halo is a **ring around the paper**, not a wash over it: the steps used to be eight filled
    // rectangles, each covering the whole sheet at 4.5 % — seven eighths of that blending happened under
    // the paper that is painted over it a moment later, and a translucent fill costs its **area** on a
    // software raster. Measured on the thumbnail path's own raster: a blank page went from 195 ms to under
    // one, and a tab switch from 353 ms to 89. What a reader sees is the same falloff, because the part the
    // paper covers was never visible in the first place.
    //
    // Each ring is **four filled rectangles**, not a stroked outline and not an even-odd path: a stroke has a
    // width, and the sheet is recorded into a picture the canvas replays through the viewport's scale — a
    // 1.5 px stroke replayed at 0.75× comes out thinner than the same halo painted directly, which
    // `PagePictureTest` caught. Four fills are areas at any scale, they tile the ring exactly, and they sit
    // flush against the paper (a centred stroke leaves half a band of bare desk at its edge).
    var inner = 0f
    for (step in 1..steps) {
        val spread = radius * step / steps
        val thickness = spread - inner
        // 12 %, not 4.5: on a true-black desk the token is `0x15151A`, so a 4.5 % ring lands on
        // 21 × 0.045 ≈ 0.9 of a channel and the outer rings of the ramp round to `#000000` — a halo that was
        // in the recipe and not in the raster (`DesktopShellTest` read the desk beside the sheet as pure
        // black). Twelve puts the ring nearest the paper at two or three units, which is the lift this is for.
        val alpha = 0.12f * (1f - (step - 1f) / steps)
        val colour = DesktopTheme.pageShadowColor.copy(alpha = alpha)
        val across = sheet.width + spread * 2f
        // Above and below the sheet, then either side of it between those two bands: the four tile the ring
        // with no overlap, so the halo has exactly the falloff eight nested rectangles used to give.
        drawRect(colour, topLeft = Offset(sheet.left - spread, sheet.top - spread), size = Size(across, thickness))
        drawRect(colour, topLeft = Offset(sheet.left - spread, sheet.bottom + inner), size = Size(across, thickness))
        drawRect(
            colour,
            topLeft = Offset(sheet.left - spread, sheet.top - inner),
            size = Size(thickness, sheet.height + inner * 2f),
        )
        drawRect(
            colour,
            topLeft = Offset(sheet.right + inner, sheet.top - inner),
            size = Size(thickness, sheet.height + inner * 2f),
        )
        inner = spread
    }
}

private fun DrawScope.drawElement(
    element: Element,
    scale: Float,
    textMeasurer: TextMeasurer,
    karaoke: KaraokeStrokeAnimator.State? = null,
) {
    when (element) {
        is InkStroke -> drawKaraokeInk(element, scale, karaoke)
        is TextElement -> drawTextBox(element, scale, textMeasurer)
        // A formula: a `<teximage>` read from a `.xopp`, and the one a living math block compiles to
        // (`LivingInk`). Typeset from its source through the shared layout, never left blank.
        is TexImageElement -> drawTexImage(element, scale, textMeasurer)
        is ImageElement -> drawImageElement(element, scale)
        // A living entity is a *recipe*, and `LivingInk` is the one place that compiles it into ink —
        // the same dispatch the Android canvas, `XoppSynthesis`, PDF and SVG export use. Drawing its
        // result here is what keeps a table or a graph identical on both shells without a second
        // renderer to drift: this shell only knows how to paint strokes and text, which is all a
        // compiled entity is.
        else -> if (element.isLivingEntity) {
            for (ink in LivingInk.of(element).elements) drawElement(ink, scale, textMeasurer, karaoke)
        }
    }
}

/**
 * One stroke at [scale] — shared with the canvas's **live preview** in `PageCanvas`, so the ink being
 * drawn and the ink the lift commits are painted by the same rule (highlighter band, taper, dash).
 */
internal fun DrawScope.drawInkStroke(stroke: InkStroke, scale: Float) {
    drawStrokeShapes(stroke, StrokePathCache.of(stroke, scale), scale)
}

/**
 * One stroke under the **karaoke** directive: whole, dimmed because the recording has not reached it,
 * or cut at the point being written.
 *
 * The in-progress stroke is drawn as its **prefix** through the draft cache (the recorded stroke is the
 * cache's identity, so each frame of the animation builds only the outline of the points that are new),
 * and its nib is marked with a glow the way the tablet's own tip painter does: a stroke that grew with no
 * head would read as the page moving rather than as writing.
 */
private fun DrawScope.drawImageElement(element: ImageElement, scale: Float) {
    val bitmap = DesktopImageCache.get(element) ?: return
    val x1 = minOf(element.left, element.right) * scale
    val y1 = minOf(element.top, element.bottom) * scale
    val x2 = maxOf(element.left, element.right) * scale
    val y2 = maxOf(element.top, element.bottom) * scale
    val width = (x2 - x1).roundToInt().coerceAtLeast(1)
    val height = (y2 - y1).roundToInt().coerceAtLeast(1)

    drawImage(
        image = bitmap,
        dstOffset = IntOffset(x1.roundToInt(), y1.roundToInt()),
        dstSize = IntSize(width, height),
    )
}

private fun DrawScope.drawKaraokeInk(
    stroke: InkStroke,
    scale: Float,
    karaoke: KaraokeStrokeAnimator.State?,
) {
    if (karaoke == null) {
        drawInkStroke(stroke, scale)
        return
    }
    when (val directive = KaraokeStrokeAnimator.resolve(stroke, karaoke)) {
        KaraokeStrokeAnimator.RenderDirective.Normal -> drawInkStroke(stroke, scale)
        is KaraokeStrokeAnimator.RenderDirective.Dimmed ->
            drawStrokeShapes(stroke, StrokePathCache.of(stroke, scale), scale, Color(directive.colorArgb))
        is KaraokeStrokeAnimator.RenderDirective.Animating -> {
            val visible = directive.visiblePoints
            if (visible.size >= 2) {
                val shapes = StrokePathCache.draft(
                    identity = stroke,
                    size = visible.size,
                    tool = stroke.tool,
                    lineStyle = stroke.lineStyle,
                    points = visible,
                    scale = scale,
                )
                drawStrokeShapes(stroke, shapes, scale, points = visible)
            }
            directive.activeTip?.let { drawWrittenTip(stroke, it, scale) }
        }
    }
}

/** The nib of the stroke being written: a soft halo and its own head, at the last point reached. */
private fun DrawScope.drawWrittenTip(stroke: InkStroke, tip: StrokePoint, scale: Float) {
    val colour = Color(InkGeometry.renderColor(stroke.tool, stroke.color))
    val at = Offset((tip.x * scale).toFloat(), (tip.y * scale).toFloat())
    val radius = (tip.width * scale).toFloat().coerceAtLeast(1f)
    drawCircle(colour.copy(alpha = 0.22f), radius = radius * 2f, center = at)
    drawCircle(colour, radius = radius * 0.55f, center = at)
}

/**
 * One stroke's cached geometry, painted — the half [drawInkStroke] and the live draft share.
 *
 * The paths come from [StrokePathCache], so a re-recorded page replays outlines it already built instead
 * of rebuilding a path per stroke per frame, and the draft being drawn is cached by its own length.
 */
internal fun DrawScope.drawStrokeShapes(
    stroke: InkStroke,
    shapes: StrokePathCache.Shapes,
    scale: Float,
    /** A colour the stroke is drawn in instead of its own — the karaoke dimming. */
    colour: Color? = null,
    /** The points [shapes] were built from, when they are a **prefix** of the stroke's own. */
    points: List<StrokePoint> = stroke.points,
) {
    val pts = points
    if (pts.size < 2) return
    val color = colour ?: Color(InkGeometry.renderColor(stroke.tool, stroke.color))

    if (stroke.tool == Tool.HIGHLIGHTER) {
        // One translucent band, multiplied into the paper: the marker reads over ink instead of
        // veiling it, exactly as the Android painter's `drawBand` does.
        drawPath(
            shapes.polyline,
            color,
            style = Stroke(
                width = shapes.bandWidthPx ?: 1f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
            blendMode = BlendMode.Multiply,
        )
        return
    }

    if (shapes.taper == null) {
        val widthPt = pts.first().width
        drawPath(
            shapes.polyline,
            color,
            style = Stroke(
                width = (widthPt * scale).toFloat().coerceAtLeast(0.5f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PageInk.dashEffect(stroke.lineStyle, widthPt, scale),
            ),
        )
    } else {
        // A pressure stroke keeps its taper by filling one variable-width outline.
        drawPath(shapes.taper, color)
    }
}

private fun DrawScope.drawTextBox(box: TextElement, scale: Float, textMeasurer: TextMeasurer) {
    if (box.content.isBlank()) return
    val face = textFace(box.font)
    val layout = textMeasurer.measure(
        text = box.content,
        style = TextStyle(
            color = Color(box.color),
            // [scale] is **pixels per point** (pt × zoom × density), so the size has to come back out of
            // px before it can be asked for in sp: `.sp` on a page point multiplied the display density
            // in a second time and painted every box — and every formula — [density] times too large,
            // while its position stayed right. `toSp` divides the canvas's own density back out.
            fontSize = (box.size * scale).toFloat().toSp(),
            fontFamily = face.family,
            fontWeight = face.weight,
            fontStyle = face.style,
        ),
    )
    drawText(layout, topLeft = Offset((box.x * scale).toFloat(), (box.y * scale).toFloat()))
}
