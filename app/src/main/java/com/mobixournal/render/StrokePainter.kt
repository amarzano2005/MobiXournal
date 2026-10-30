package com.mobixournal.render

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import com.mobixournal.format.XoppColor.withAlpha
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool

/**
 * Paints a stroke's pressure-varying polyline onto a canvas at a given scale and offset. Shared by
 * the on-screen [DrawingSurfaceView] and [PdfExporter] so a stroke looks identical live and when
 * flattened. Highlighter strokes render distinctly from the pen: a broad, constant-width band drawn
 * as one translucent path (forced translucent even when the stored colour is opaque), whereas the
 * pen tapers with pressure, painted as a single filled variable-width outline. A [LineStyle] other than plain paints the outline as a
 * single dashed/dotted path (constant width), and a non-null fill floods the closed outline first.
 */
class StrokePainter {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Fills the interior of a closed stroke (shapes / highlighter fill). */
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Reused across highlighter strokes so a translucent band composites in a single pass. */
    private val path = Path()

    /** Fills the pen's variable-width outline; kept separate so the stroking [paint] stays a stroke. */
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /**
     * True when the platform has the *separable* multiply blend mode the highlighter needs. It is
     * `android.graphics.BlendMode` (API 29+, Skia's `kMultiply`); below that we fall back to a plain
     * alpha-over band. `PorterDuffXfermode(MULTIPLY)` is **not** a substitute — it is the Porter-Duff
     * variant, which also multiplies the *alpha* and so punches a translucent hole in the white page
     * (the band then composites against the window behind the surface and goes muddy).
     */
    private val highlighterMultiply = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun draw(
        canvas: Canvas,
        pts: List<StrokePoint>,
        tool: Tool,
        color: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        lineStyle: LineStyle = LineStyle.PLAIN,
        fill: Int? = null,
    ) {
        if (pts.size < 2) return
        if (fill != null) fillOutline(canvas, pts, color, fill, tool, scale, offsetX, offsetY)
        paint.color = renderColor(tool, color)
        when (val mode = RenderMode.of(tool, lineStyle)) {
            RenderMode.Styled -> drawStyledLine(canvas, pts, lineStyle, scale, offsetX, offsetY)
            RenderMode.Band -> drawBand(canvas, pts, scale, offsetX, offsetY)
            // A stroke whose points all share one width (a shape, any constant-width pen)
            // is a plain stroke, not a variable-width one — see [drawUniformLine]. Only a genuine
            // pressure taper needs the filled outline.
            RenderMode.Pressure ->
                if (uniformWidth(pts)) drawUniformLine(canvas, pts, scale, offsetX, offsetY)
                else drawPressureLine(canvas, pts, scale, offsetX, offsetY)
        }
    }

    /**
     * The pen at a constant width: the polyline stroked once with the shared width. Shapes (line,
     * rectangle, ellipse, arrow, polygon) all land here, and it is what keeps their sides an even
     * thickness with clean corners — the variable-width outline only approximates joins, and at a
     * shape's sharp 90° corner its averaged tangent cut the band thin. A constant-width stroke with
     * round joins is exact and costs one `drawPath`.
     */
    private fun drawUniformLine(
        canvas: Canvas, pts: List<StrokePoint>, scale: Float, offsetX: Float, offsetY: Float,
    ) {
        paint.strokeWidth = (pts[0].width * scale).toFloat()
        path.rewind()
        buildPath(pts, scale, offsetX, offsetY, close = false)
        canvas.drawPath(path, paint)
    }

    /** Flood the closed polyline with the stroke colour at the fill alpha (drawn under the outline). */
    private fun fillOutline(
        canvas: Canvas, pts: List<StrokePoint>, color: Int, fill: Int, tool: Tool,
        scale: Float, offsetX: Float, offsetY: Float,
    ) {
        fillPaint.color = color.withAlpha(fill)
        // A filled highlighter blends the same way an unfilled one does; every other fill is a
        // plain translucent wash.
        if (tool == Tool.HIGHLIGHTER) setMultiply(fillPaint) else clearMultiply(fillPaint)
        path.rewind()
        buildPath(pts, scale, offsetX, offsetY, close = true)
        canvas.drawPath(path, fillPaint)
        clearMultiply(fillPaint)
    }

    /**
     * The pen: the pressure polyline as ONE filled outline whose width **varies point to point**,
     * built and filled in a single `drawPath`.
     *
     * This mirrors desktop Xournal++ (`StrokeViewHelper::drawWithPressure`): on screen it paints a
     * `StrokeContour` of the variable-width stroke and fills it, so the taper is faithful without a
     * canvas call per segment. An earlier version stroked the whole polyline at the stroke's *mean*
     * width to escape that per-segment cost, but flattening the taper made a pen stroke look
     * uniformly thinner than a shape drawn at the same size setting; building the outline keeps the
     * taper and still submits one path per stroke.
     */
    private fun drawPressureLine(
        canvas: Canvas, pts: List<StrokePoint>, scale: Float, offsetX: Float, offsetY: Float,
    ) {
        if (pts.size < 2) return
        outlinePaint.color = paint.color
        buildPressureOutline(pts, scale, offsetX, offsetY)
        canvas.drawPath(path, outlinePaint)
    }

    /** Build [path] from the pure [pressureOutlinePoints] geometry, ready for a single fill. */
    private fun buildPressureOutline(
        pts: List<StrokePoint>, scale: Float, offsetX: Float, offsetY: Float,
    ) {
        val outline = InkGeometry.taperOutline(pts, scale, offsetX, offsetY)
        if (outline.size < 6) return
        path.rewind()
        path.moveTo(outline[0], outline[1])
        var i = 2
        while (i < outline.size) {
            path.lineTo(outline[i], outline[i + 1])
            i += 2
        }
        path.close()
    }

    /**
     * A dashed/dotted stroke: one constant-width [Path] with a [DashPathEffect]. Drawn in a single
     * pass (not per-segment) so the dash phase runs continuously along the whole polyline instead of
     * restarting at every vertex. Width is constant (the mean vertex width) — desktop dashed strokes
     * are uniform-width.
     */
    private fun drawStyledLine(
        canvas: Canvas, pts: List<StrokePoint>, lineStyle: LineStyle,
        scale: Float, offsetX: Float, offsetY: Float,
    ) {
        val w = bandWidth(pts)
        paint.strokeWidth = w.toFloat() * scale
        paint.pathEffect = dashEffect(lineStyle, w, scale)
        path.rewind()
        buildPath(pts, scale, offsetX, offsetY, close = false)
        canvas.drawPath(path, paint)
        paint.pathEffect = null
    }

    /**
     * The highlighter: one constant-width [Path] drawn in a single pass. Because the whole band is
     * rasterised once, its translucent alpha does not stack where the stroke overlaps itself at
     * joins (drawing segment-by-segment would bead into darker blobs at every vertex).
     *
     * It is painted with **multiply** blend at a fixed [HIGHLIGHTER_RENDER_ALPHA], mirroring desktop
     * Xournal++ (`StrokeView`: `OPACITY_HIGHLIGHTER = 0.47` + `CAIRO_OPERATOR_MULTIPLY`). Multiply is
     * what makes it read as a marker rather than a paint: the paper stays bright under a light
     * colour and ink beneath darkens instead of being veiled, where a plain alpha-over wash would
     * hide it.
     */
    private fun drawBand(
        canvas: Canvas, pts: List<StrokePoint>, scale: Float, offsetX: Float, offsetY: Float,
    ) {
        setMultiply(paint)
        paint.strokeWidth = bandWidth(pts).toFloat() * scale
        path.rewind()
        buildPath(pts, scale, offsetX, offsetY, close = false)
        canvas.drawPath(path, paint)
        clearMultiply(paint)
    }

    /** Switch [p] to multiply blend where the platform supports it (see [highlighterMultiply]). */
    private fun setMultiply(p: Paint) {
        if (highlighterMultiply) p.blendMode = BlendMode.MULTIPLY
    }

    /** Restore [p] to normal alpha-over compositing. */
    private fun clearMultiply(p: Paint) {
        if (highlighterMultiply) p.blendMode = BlendMode.SRC_OVER
    }

    /** Build a polyline Path from [pts], scaled and offset. Closes the path if [close] is true. */
    private fun buildPath(pts: List<StrokePoint>, scale: Float, offsetX: Float, offsetY: Float, close: Boolean) {
        path.moveTo(offsetX + (pts[0].x * scale).toFloat(), offsetY + (pts[0].y * scale).toFloat())
        for (i in 1 until pts.size) {
            path.lineTo(offsetX + (pts[i].x * scale).toFloat(), offsetY + (pts[i].y * scale).toFloat())
        }
        if (close) path.close()
    }

    /** The render mode a stroke uses — drives the same decision tree in screen and PDF painters. */
    sealed class RenderMode {
        data object Styled : RenderMode()
        data object Band : RenderMode()
        data object Pressure : RenderMode()

        companion object {
            fun of(tool: Tool, lineStyle: LineStyle): RenderMode =
                when (InkGeometry.renderMode(tool, lineStyle)) {
                    InkGeometry.RenderMode.Styled -> Styled
                    InkGeometry.RenderMode.Band -> Band
                    InkGeometry.RenderMode.Pressure -> Pressure
                }

            fun of(stroke: Stroke): RenderMode = of(stroke.tool, stroke.lineStyle)
        }
    }

    companion object {
        /**
         * The ink rules now live in [InkGeometry] (`mobix-core`), shared with the desktop painter.
         * These members stay as the Android-facing names the painters, the PDF exporter and their
         * tests already use — one implementation, no second copy to drift.
         */
        fun uniformWidth(pts: List<StrokePoint>): Boolean = InkGeometry.uniformWidth(pts)

        /** See [InkGeometry.HIGHLIGHTER_RENDER_ALPHA]. */
        val HIGHLIGHTER_RENDER_ALPHA: Int = InkGeometry.HIGHLIGHTER_RENDER_ALPHA

        /** See [InkGeometry.bandWidth]. */
        fun bandWidth(pts: List<StrokePoint>): Double = InkGeometry.bandWidth(pts)

        /** See [InkGeometry.renderColor]. */
        fun renderColor(tool: Tool, color: Int): Int = InkGeometry.renderColor(tool, color)

        /** See [InkGeometry.dashIntervalsPt]. */
        fun dashIntervalsPt(style: LineStyle, widthPt: Double): FloatArray? =
            InkGeometry.dashIntervalsPt(style, widthPt)

        /** The [dashIntervalsPt] pattern scaled to screen px by [scale], as an Android effect. */
        fun dashEffect(style: LineStyle, widthPt: Double, scale: Float): DashPathEffect? {
            val pt = dashIntervalsPt(style, widthPt) ?: return null
            return DashPathEffect(FloatArray(pt.size) { pt[it] * scale }, 0f)
        }
    }
}
