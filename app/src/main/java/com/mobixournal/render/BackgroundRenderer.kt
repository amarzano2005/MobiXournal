package com.mobixournal.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import com.mobixournal.format.model.Background

/**
 * Draws a page background — the base sheet colour plus its ruling (plain / lined / ruled / graph /
 * dotted) — into a [PageBox]'s rectangle. Line/dot positions come from [BackgroundGrid] and the
 * page's own `<background config=…>` parameters ([BackgroundRuling]: spacing, margin, line width,
 * bold lines); this class only maps them to canvas coordinates. A `pdf` background is drawn as its rasterised page and
 * a `pixmap` one as its decoded picture — both arrive through the same [pageImage] slot, supplied by
 * [PdfPageCache] and [ImageBackgroundCache] respectively. When no image is available (a `.xopp`
 * whose PDF or picture isn't present, or a decode still in flight) it falls back to a plain sheet.
 */
object BackgroundRenderer {

    // Spacing defaults live in [BackgroundGrid] and are resolved per page by [BackgroundRulings], so
    // the editor, the SVG writer and the PDF flatten share one copy.
    private const val MARGIN_PT = BackgroundGrid.MARGIN_PT

    /** The ruled sheet's margin line, in pt (desktop rules it heavier than the ruling). */
    private const val MARGIN_WIDTH_PT = 1.5

    private val fill = Paint()
    private val lined = Paint().apply { color = 0xFF000000.toInt() or BackgroundGrid.LINED_RGB; strokeWidth = 1f }
    private val graphLine = Paint().apply { color = 0xFF000000.toInt() or BackgroundGrid.GRAPH_RGB; strokeWidth = 1f }
    private val margin = Paint().apply { color = 0xFF000000.toInt() or BackgroundGrid.MARGIN_RGB; strokeWidth = 1.5f }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt() or BackgroundGrid.DOT_RGB; style = Paint.Style.FILL
    }
    private val image = Paint(Paint.FILTER_BITMAP_FLAG)

    // Reused across blits: [draw] runs on the drawing thread every frame, and a fresh RectF per
    // tile per frame was pure churn for the collector.
    private val dst = RectF()

    fun draw(
        canvas: Canvas,
        box: PageBox,
        scrollX: Float,
        scrollY: Float,
        pageImage: Bitmap? = null,
        tiles: List<PdfTile> = emptyList(),
        viewWidthPx: Float = 0f,
        viewHeightPx: Float = 0f,
    ) {
        val left = box.toViewX(0.0, scrollX)
        val top = box.toViewY(0.0, scrollY)
        val solid = box.page.background as? Background.Solid
        fill.color = solid?.color ?: AndroidColor.WHITE
        canvas.drawRect(left, top, left + box.widthPx, top + box.heightPx, fill)
        if (pageImage != null || tiles.isNotEmpty()) {
            // The whole-page bitmap is the coarse under-layer; tiles land on top at full resolution,
            // so a tile that hasn't rasterised yet shows the upscaled page rather than a hole.
            // …unless the tiles already cover every visible pixel, in which case the blit would
            // rasterise the whole (possibly many-screens-wide) page only to be painted over.
            if (pageImage != null && !tilesCover(tiles, box, left, top, viewWidthPx, viewHeightPx)) {
                dst.set(left, top, left + box.widthPx, top + box.heightPx)
                canvas.drawBitmap(pageImage, null, dst, image)
            }
            for (t in tiles) {
                dst.set(
                    left + t.left * box.widthPx, top + t.top * box.heightPx,
                    left + t.right * box.widthPx, top + t.bottom * box.heightPx,
                )
                canvas.drawBitmap(t.bitmap, null, dst, image)
            }
            return
        }
        val style = solid?.style
        when (style) {
            "lined" -> horizontals(canvas, box, left, top, style, rulingFor(solid.config))
            "ruled" -> {
                horizontals(canvas, box, left, top, style, rulingFor(solid.config))
                marginLine(canvas, box, left, top, rulingFor(solid.config))
            }
            "graph" -> grid(canvas, box, left, top, rulingFor(solid.config))
            "dotted" -> dots(canvas, box, left, top, rulingFor(solid.config))
            // Desktop's isometric paper; `isodotted` is its dotted variant, drawn here as the same
            // mesh (see the note in `docs/architecture.md`) rather than skipped.
            "isograph", "isodotted" -> isometric(canvas, box, left, top, style, rulingFor(solid.config))
            else -> Unit // "plain", unknown, or non-solid: bare sheet
        }
    }

    // The page's config is parsed once per distinct value, not once per frame: [draw] runs on the
    // drawing thread every frame, and a fresh parse (and a fresh allocation) per frame was pure churn
    // for the collector. Pages normally share one ruling, so the single-entry memo hits every frame
    // but the first.
    private var cachedConfig: String? = null
    private var cachedRuling: BackgroundRuling = BackgroundRuling.EMPTY

    private fun rulingFor(config: String?): BackgroundRuling {
        if (config == cachedConfig) return cachedRuling
        val parsed = BackgroundRuling.parse(config)
        cachedConfig = config
        cachedRuling = parsed
        return parsed
    }

    /** A pt line width as a canvas stroke width: scaled with the page, never thinner than one px. */
    private fun strokePx(widthPt: Double, box: PageBox): Float =
        (widthPt * box.scale).toFloat().coerceAtLeast(1f)

    /**
     * True if [tiles] leave no hole over the visible part of the page. Tiles are non-overlapping grid
     * cells, so a gap-free cover is exactly "their union rectangle contains the visible rectangle and
     * their areas add up to that union" — no per-cell bookkeeping needed. Returns false when the view
     * size isn't known (the PDF exporter, tests), preserving the coarse under-layer there.
     */
    private fun tilesCover(
        tiles: List<PdfTile>,
        box: PageBox,
        left: Float,
        top: Float,
        viewWidthPx: Float,
        viewHeightPx: Float,
    ): Boolean {
        if (tiles.isEmpty() || viewWidthPx <= 0f || viewHeightPx <= 0f) return false
        if (box.widthPx <= 0f || box.heightPx <= 0f) return false
        // The visible slice of the page, as 0..1 fractions like PdfTile's own coordinates.
        val vl = ((0f - left) / box.widthPx).coerceIn(0f, 1f)
        val vt = ((0f - top) / box.heightPx).coerceIn(0f, 1f)
        val vr = ((viewWidthPx - left) / box.widthPx).coerceIn(0f, 1f)
        val vb = ((viewHeightPx - top) / box.heightPx).coerceIn(0f, 1f)
        if (vr <= vl || vb <= vt) return true // nothing of this page is on screen
        var ul = Float.MAX_VALUE; var ut = Float.MAX_VALUE
        var ur = -Float.MAX_VALUE; var ub = -Float.MAX_VALUE
        var area = 0f
        for (t in tiles) {
            ul = minOf(ul, t.left); ut = minOf(ut, t.top)
            ur = maxOf(ur, t.right); ub = maxOf(ub, t.bottom)
            area += (t.right - t.left) * (t.bottom - t.top)
        }
        if (ul > vl || ut > vt || ur < vr || ub < vb) return false
        val unionArea = (ur - ul) * (ub - ut)
        return area >= unionArea - 1e-4f
    }

    private fun horizontals(
        canvas: Canvas,
        box: PageBox,
        left: Float,
        top: Float,
        style: String,
        ruling: BackgroundRuling,
    ) {
        val spacing = BackgroundRulings.spacingPt(style, ruling)
        val widthPt = BackgroundRulings.lineWidthPt(ruling)
        val boldInterval = BackgroundRulings.boldInterval(ruling)
        val boldWidthPt = BackgroundRulings.boldWidthPt(ruling, widthPt)
        for ((i, y) in BackgroundGrid.lines(box.page.height, spacing).withIndex()) {
            val relevant = BackgroundRulings.isBold(gridIndex(y, spacing, i), boldInterval)
            lined.strokeWidth = strokePx(if (relevant) boldWidthPt else widthPt, box)
            val py = top + (y * box.scale).toFloat()
            canvas.drawLine(left, py, left + box.widthPx, py, lined)
        }
    }

    /**
     * The ruled sheet's red margin line: the page's `m1`, defaulting to desktop's one-inch margin.
     * A ruling whose margin is zero draws none, which is how a page turns the line off.
     */
    private fun marginLine(
        canvas: Canvas,
        box: PageBox,
        left: Float,
        top: Float,
        ruling: BackgroundRuling,
    ) {
        val xPt = BackgroundRulings.marginPt(ruling) ?: MARGIN_PT
        margin.strokeWidth = strokePx(BackgroundRulings.lineWidthPt(ruling, MARGIN_WIDTH_PT), box)
        val x = left + (xPt * box.scale).toFloat()
        canvas.drawLine(x, top, x, top + box.heightPx, margin)
    }

    private fun grid(canvas: Canvas, box: PageBox, left: Float, top: Float, ruling: BackgroundRuling) {
        val spacing = BackgroundRulings.spacingPt("graph", ruling)
        val widthPt = BackgroundRulings.lineWidthPt(ruling)
        val boldInterval = BackgroundRulings.boldInterval(ruling)
        val boldWidthPt = BackgroundRulings.boldWidthPt(ruling, widthPt)
        // A graph page's margin insets the ruled area on every side, as the desktop's does.
        val m = BackgroundRulings.marginPt(ruling) ?: 0.0
        val x0 = m
        val y0 = m
        val x1 = (box.page.width - m).coerceAtLeast(x0)
        val y1 = (box.page.height - m).coerceAtLeast(y0)
        for ((i, y) in BackgroundGrid.lines(y1 - y0, spacing).withIndex()) {
            val abs = y0 + y
            val relevant = BackgroundRulings.isBold(gridIndex(abs, spacing, i), boldInterval)
            graphLine.strokeWidth = strokePx(if (relevant) boldWidthPt else widthPt, box)
            val py = top + (abs * box.scale).toFloat()
            canvas.drawLine(left + (x0 * box.scale).toFloat(), py, left + (x1 * box.scale).toFloat(), py, graphLine)
        }
        for ((i, x) in BackgroundGrid.lines(x1 - x0, spacing).withIndex()) {
            val abs = x0 + x
            val relevant = BackgroundRulings.isBold(gridIndex(abs, spacing, i), boldInterval)
            graphLine.strokeWidth = strokePx(if (relevant) boldWidthPt else widthPt, box)
            val px = left + (abs * box.scale).toFloat()
            canvas.drawLine(px, top + (y0 * box.scale).toFloat(), px, top + (y1 * box.scale).toFloat(), graphLine)
        }
    }

    private fun dots(canvas: Canvas, box: PageBox, left: Float, top: Float, ruling: BackgroundRuling) {
        val spacing = BackgroundRulings.spacingPt("dotted", ruling)
        val radius = (box.scale).coerceIn(1f, 2.5f)
        val m = BackgroundRulings.marginPt(ruling) ?: 0.0
        val x1 = (box.page.width - m).coerceAtLeast(m)
        val y1 = (box.page.height - m).coerceAtLeast(m)
        for (y in BackgroundGrid.lines(y1 - m, spacing)) {
            val py = top + ((m + y) * box.scale).toFloat()
            for (x in BackgroundGrid.lines(x1 - m, spacing)) {
                canvas.drawCircle(left + ((m + x) * box.scale).toFloat(), py, radius, dot)
            }
        }
    }

    /**
     * Desktop's isometric sheet: the two ±30° families that make the triangular mesh, from the pure
     * [BackgroundGrid.isometric] geometry, drawn with the page's own line width.
     */
    private fun isometric(
        canvas: Canvas,
        box: PageBox,
        left: Float,
        top: Float,
        style: String,
        ruling: BackgroundRuling,
    ) {
        val size = BackgroundRulings.spacingPt(style, ruling)
        graphLine.strokeWidth = strokePx(BackgroundRulings.lineWidthPt(ruling), box)
        for (seg in BackgroundGrid.isometric(box.page.width, box.page.height, size)) {
            canvas.drawLine(
                left + (seg[0] * box.scale).toFloat(),
                top + (seg[1] * box.scale).toFloat(),
                left + (seg[2] * box.scale).toFloat(),
                top + (seg[3] * box.scale).toFloat(),
                graphLine,
            )
        }
    }

    /** The grid index a ruling offset belongs to (used to decide which lines go bold). */
    private fun gridIndex(offsetPt: Double, spacing: Double, fallback: Int): Int =
        if (spacing > 0.0) Math.round(offsetPt / spacing).toInt() else fallback
}
