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
    private val painters = ThreadLocal.withInitial { BackgroundPainter() }

    internal fun draw(
        canvas: Canvas, box: PageBox, scrollX: Float, scrollY: Float,
        pageImage: Bitmap? = null, tiles: List<PdfTile> = emptyList(),
        viewWidthPx: Float = 0f, viewHeightPx: Float = 0f,
    ) = painters.get()!!.draw(canvas, box, scrollX, scrollY, pageImage, tiles, viewWidthPx, viewHeightPx)
}

private class BackgroundPainter {

    // Spacing defaults live in [BackgroundGrid] and are resolved per page by [BackgroundRulings], so
    // the editor, the SVG writer and the PDF flatten share one copy.
    private val MARGIN_PT = BackgroundGrid.MARGIN_PT

    /** The ruled sheet's margin line, in pt (desktop rules it heavier than the ruling). */
    private val MARGIN_WIDTH_PT = 1.5

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

    internal fun draw(
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
            "lined" -> horizontals(canvas, box, left, top, style, rulingFor(solid.config), viewHeightPx)
            "ruled" -> {
                horizontals(canvas, box, left, top, style, rulingFor(solid.config), viewHeightPx)
                marginLine(canvas, box, left, top, rulingFor(solid.config))
            }
            "graph" -> grid(canvas, box, left, top, rulingFor(solid.config), viewWidthPx, viewHeightPx)
            "dotted" -> dots(canvas, box, left, top, rulingFor(solid.config), viewWidthPx, viewHeightPx)
            // Desktop's isometric paper; `isodotted` is its dotted variant, drawn here as the same
            // mesh (see the note in `docs/pages-and-paper.md`) rather than skipped.
            "isograph", "isodotted" ->
                isometric(canvas, box, left, top, style, rulingFor(solid.config), viewWidthPx, viewHeightPx)
            else -> Unit // "plain", unknown, or non-solid: bare sheet
        }
    }

    private data class LinesKey(val spanPt: Double, val spacing: Double)
    private data class IsoKey(val w: Double, val h: Double, val size: Double)

    private val linesCache = object : LinkedHashMap<LinesKey, DoubleArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LinesKey, DoubleArray>?): Boolean = size > 16
    }
    private val isoCache = object : LinkedHashMap<IsoKey, List<DoubleArray>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<IsoKey, List<DoubleArray>>?): Boolean = size > 8
    }
    private var dotBuffer = FloatArray(0)

    private fun cachedLines(spanPt: Double, spacing: Double): DoubleArray {
        val key = LinesKey(spanPt, spacing)
        return linesCache.getOrPut(key) { BackgroundGrid.lines(spanPt, spacing).toDoubleArray() }
    }

    private fun cachedIsometric(w: Double, h: Double, size: Double): List<DoubleArray> {
        val key = IsoKey(w, h, size)
        return isoCache.getOrPut(key) { BackgroundGrid.isometric(w, h, size) }
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
        viewHeightPx: Float,
    ) {
        val spacing = BackgroundRulings.spacingPt(style, ruling)
        val widthPt = BackgroundRulings.lineWidthPt(ruling)
        val boldInterval = BackgroundRulings.boldInterval(ruling)
        val boldWidthPt = BackgroundRulings.boldWidthPt(ruling, widthPt)
        val lines = cachedLines(box.page.height, spacing)
        for (i in lines.indices) {
            val y = lines[i]
            val py = top + (y * box.scale).toFloat()
            if (viewHeightPx > 0f && (py < -2f || py > viewHeightPx + 2f)) continue
            val relevant = BackgroundRulings.isBold(gridIndex(y, spacing, i), boldInterval)
            lined.strokeWidth = strokePx(if (relevant) boldWidthPt else widthPt, box)
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

    private fun grid(
        canvas: Canvas,
        box: PageBox,
        left: Float,
        top: Float,
        ruling: BackgroundRuling,
        viewWidthPx: Float,
        viewHeightPx: Float,
    ) {
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
        val yLines = cachedLines(y1 - y0, spacing)
        val lineLeft = left + (x0 * box.scale).toFloat()
        val lineRight = left + (x1 * box.scale).toFloat()
        for (i in yLines.indices) {
            val abs = y0 + yLines[i]
            val py = top + (abs * box.scale).toFloat()
            if (viewHeightPx > 0f && (py < -2f || py > viewHeightPx + 2f)) continue
            val relevant = BackgroundRulings.isBold(gridIndex(abs, spacing, i), boldInterval)
            graphLine.strokeWidth = strokePx(if (relevant) boldWidthPt else widthPt, box)
            canvas.drawLine(lineLeft, py, lineRight, py, graphLine)
        }
        val xLines = cachedLines(x1 - x0, spacing)
        val lineTop = top + (y0 * box.scale).toFloat()
        val lineBottom = top + (y1 * box.scale).toFloat()
        for (i in xLines.indices) {
            val abs = x0 + xLines[i]
            val px = left + (abs * box.scale).toFloat()
            if (viewWidthPx > 0f && (px < -2f || px > viewWidthPx + 2f)) continue
            val relevant = BackgroundRulings.isBold(gridIndex(abs, spacing, i), boldInterval)
            graphLine.strokeWidth = strokePx(if (relevant) boldWidthPt else widthPt, box)
            canvas.drawLine(px, lineTop, px, lineBottom, graphLine)
        }
    }

    private fun dots(
        canvas: Canvas,
        box: PageBox,
        left: Float,
        top: Float,
        ruling: BackgroundRuling,
        viewWidthPx: Float,
        viewHeightPx: Float,
    ) {
        val spacing = BackgroundRulings.spacingPt("dotted", ruling)
        val radius = (box.scale).coerceIn(1f, 2.5f)
        val m = BackgroundRulings.marginPt(ruling) ?: 0.0
        val x1 = (box.page.width - m).coerceAtLeast(m)
        val y1 = (box.page.height - m).coerceAtLeast(m)
        val ys = cachedLines(y1 - m, spacing)
        val xs = cachedLines(x1 - m, spacing)
        if (ys.isEmpty() || xs.isEmpty()) return
        val needed = xs.size * ys.size * 2
        if (dotBuffer.size < needed) dotBuffer = FloatArray(needed)
        var n = 0
        for (yi in ys.indices) {
            val py = top + ((m + ys[yi]) * box.scale).toFloat()
            if (viewHeightPx > 0f && (py < -radius || py > viewHeightPx + radius)) continue
            for (xi in xs.indices) {
                val px = left + ((m + xs[xi]) * box.scale).toFloat()
                if (viewWidthPx > 0f && (px < -radius || px > viewWidthPx + radius)) continue
                dotBuffer[n++] = px
                dotBuffer[n++] = py
            }
        }
        if (n > 0) {
            dot.strokeCap = Paint.Cap.ROUND
            dot.strokeWidth = radius * 2f
            canvas.drawPoints(dotBuffer, 0, n, dot)
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
        viewWidthPx: Float,
        viewHeightPx: Float,
    ) {
        val size = BackgroundRulings.spacingPt(style, ruling)
        graphLine.strokeWidth = strokePx(BackgroundRulings.lineWidthPt(ruling), box)
        for (seg in cachedIsometric(box.page.width, box.page.height, size)) {
            val x0 = left + (seg[0] * box.scale).toFloat()
            val y0 = top + (seg[1] * box.scale).toFloat()
            val x1 = left + (seg[2] * box.scale).toFloat()
            val y1 = top + (seg[3] * box.scale).toFloat()
            if (viewWidthPx > 0f && maxOf(x0, x1) < -2f || minOf(x0, x1) > viewWidthPx + 2f) continue
            if (viewHeightPx > 0f && maxOf(y0, y1) < -2f || minOf(y0, y1) > viewHeightPx + 2f) continue
            canvas.drawLine(x0, y0, x1, y1, graphLine)
        }
    }

    /** The grid index a ruling offset belongs to (used to decide which lines go bold). */
    private fun gridIndex(offsetPt: Double, spacing: Double, fallback: Int): Int =
        if (spacing > 0.0) Math.round(offsetPt / spacing).toInt() else fallback
}
