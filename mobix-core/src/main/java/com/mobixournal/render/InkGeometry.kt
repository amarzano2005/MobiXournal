package com.mobixournal.render

import com.mobixournal.format.XoppColor
import com.mobixournal.format.XoppColor.withAlpha
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * What a stroke **is** — its colour, its band, its dash pattern and the shape of its variable-width
 * outline — with no canvas attached.
 *
 * One home for both shells: the Android painter (`render/StrokePainter.kt`) and the desktop painter
 * (`desktop/.../PageInk.kt`) call these rules, so a stroke cannot look like a pen on the tablet and a
 * different pen on the PC. Only the raster backend differs (`android.graphics.Canvas` there, Skia
 * through Compose here). Moving this out of the Android class is also what lets the geometry be
 * unit-tested on the JVM without Robolectric.
 *
 * The values are desktop Xournal++'s, not preferences: the highlighter is painted at a fixed
 * [HIGHLIGHTER_RENDER_ALPHA] with multiply (see `docs/rendering.md`), and a pen stroke keeps its
 * per-point width as one filled outline.
 */
object InkGeometry {

    /**
     * The highlighter's on-screen opacity, `/255` of desktop Xournal++'s `OPACITY_HIGHLIGHTER`
     * (`0.47`, `src/core/view/StrokeView.h`) — rounded to `0x78`. The stored colour alpha
     * ([XoppColor.HIGHLIGHTER_ALPHA], `0x7f`) is what the file carries; this is what it looks like,
     * and the highlighter is painted at this fixed value whatever the file says.
     */
    const val HIGHLIGHTER_RENDER_ALPHA: Int = 0x78

    /**
     * Highlighter width as a multiple of the pen's base width — desktop Xournal++'s highlighter is
     * about six times the pen at the same size slot (its pen medium is 1.41 pt, its highlighter
     * medium 8.50 pt). This is only an intermediate: the product is then snapped to one of
     * [HIGHLIGHTER_SIZES_PT], so a highlighter stroke is always one of the tablet's own tip sizes.
     */
    const val HIGHLIGHTER_WIDTH_FACTOR: Float = 6f

    /**
     * Desktop Xournal++'s highlighter tip sizes in points — its **fine / medium / thick** slots, the
     * 1 mm / 3 mm / 7 mm tips (`ToolHandler::initTools` upstream: `highlighter thicknesses =
     * {1, 2.83, 8.50, 19.84, 30}` and these three are the canonical set). A highlighter's width is
     * snapped onto this list rather than left as `penWidth × HIGHLIGHTER_WIDTH_FACTOR`, which keeps a
     * band the thickness the desktop app would draw, whatever the pen slots are set to.
     *
     * It lives here, beside the band's alpha and dash rules, so **both shells** take the same answer:
     * a marker that is a fat band on the tablet and a thin pen on the PC is one rule in two places.
     */
    val HIGHLIGHTER_SIZES_PT: List<Double> = listOf(2.83, 8.50, 19.84)

    /**
     * The highlighter tip size (pt) nearest the pen's [baseWidthPt] scaled by
     * [HIGHLIGHTER_WIDTH_FACTOR] — the pen's S/M/L slot read as a marker's tip.
     */
    fun highlighterWidthFor(baseWidthPt: Float): Double {
        val wanted = baseWidthPt * HIGHLIGHTER_WIDTH_FACTOR
        return HIGHLIGHTER_SIZES_PT.minByOrNull { kotlin.math.abs(it - wanted) } ?: HIGHLIGHTER_SIZES_PT[0]
    }

    /** True when every point carries the same width, i.e. the stroke has no pressure taper. */
    fun uniformWidth(pts: List<StrokePoint>): Boolean {
        if (pts.isEmpty()) return true
        val w = pts[0].width
        return pts.all { it.width == w }
    }

    /** A highlighter is uniform-width; use the mean vertex width so odd inputs still render sanely. */
    fun bandWidth(pts: List<StrokePoint>): Double =
        if (pts.isEmpty()) 0.0 else pts.sumOf { it.width } / pts.size

    /**
     * Highlighter always paints at [HIGHLIGHTER_RENDER_ALPHA], keeping its RGB — as desktop does,
     * which ignores the stored alpha when rendering the highlighter.
     */
    fun renderColor(tool: Tool, color: Int): Int =
        if (tool == Tool.HIGHLIGHTER) color.withAlpha(HIGHLIGHTER_RENDER_ALPHA) else color

    /**
     * The on/off dash pattern for [style] in pt, scaled by the stroke [widthPt] so a thick dashed
     * line has proportionally longer dashes. Dots are a near-zero “on” run rendered as a blob by the
     * round cap. Null for [LineStyle.PLAIN].
     */
    fun dashIntervalsPt(style: LineStyle, widthPt: Double): FloatArray? {
        val u = maxOf(widthPt, 0.5).toFloat()
        return when (style) {
            LineStyle.PLAIN -> null
            LineStyle.DASHED -> floatArrayOf(4f * u, 3f * u)
            LineStyle.DASH_DOT -> floatArrayOf(4f * u, 3f * u, 0.01f * u, 3f * u)
            LineStyle.DOTTED -> floatArrayOf(0.01f * u, 2.5f * u)
        }
    }

    /** The render mode a stroke uses — the same decision tree in screen, PDF and desktop painters. */
    fun renderMode(tool: Tool, lineStyle: LineStyle): RenderMode = when {
        lineStyle != LineStyle.PLAIN -> RenderMode.Styled
        tool == Tool.HIGHLIGHTER -> RenderMode.Band
        else -> RenderMode.Pressure
    }

    /** How a stroke is painted, once its tool and line style are known. */
    enum class RenderMode { Styled, Band, Pressure }

    /**
     * The outline of a stroke whose width varies per point, in **screen px**, as a flat
     * `[x0, y0, x1, y1, …]` closed polygon: the offset boundary on each side of the points, joined by
     * a round cap at each end. A single fill of this paints a round-capped, round-joined
     * variable-width line — the same shape desktop Xournal++ fills for a pressure stroke (a
     * `StrokeContour`) — and, unlike a per-segment stroke, it costs one path per stroke however many
     * points the stroke has.
     *
     * Each point's direction is the central difference through its neighbours, so the two boundaries
     * don't pinch at a vertex; a degenerate segment (a repeated point) reuses the previous direction
     * rather than dividing by zero. Half-widths are floored to a hairline so a zero-width sample
     * cannot collapse the outline. Returns an empty array for fewer than two points.
     */
    fun taperOutline(
        pts: List<StrokePoint>, scale: Float, offsetX: Float, offsetY: Float,
    ): FloatArray {
        val n = pts.size
        if (n < 2) return FloatArray(0)
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        val half = FloatArray(n)
        for (i in 0 until n) {
            xs[i] = offsetX + (pts[i].x * scale).toFloat()
            ys[i] = offsetY + (pts[i].y * scale).toFloat()
            half[i] = ((pts[i].width * scale) / 2.0).toFloat().coerceAtLeast(0.25f)
        }
        val tx = FloatArray(n)
        val ty = FloatArray(n)
        for (i in 0 until n) {
            val a = if (i == 0) 0 else i - 1
            val b = if (i == n - 1) n - 1 else i + 1
            var dx = xs[b] - xs[a]
            var dy = ys[b] - ys[a]
            if (dx == 0f && dy == 0f && i > 0) {
                dx = xs[i] - xs[i - 1]
                dy = ys[i] - ys[i - 1]
            }
            val len = hypot(dx, dy)
            when {
                len > 0f -> { tx[i] = dx / len; ty[i] = dy / len }
                i > 0 -> { tx[i] = tx[i - 1]; ty[i] = ty[i - 1] }
                else -> { tx[i] = 1f; ty[i] = 0f }
            }
        }
        // Exactly 2·n boundary points plus 8 per round cap, two floats each — no boxing, no resize.
        val out = FloatArray(4 * n + 32)
        var k = 0
        // Left boundary (normal = (-ty, tx)), start → end.
        for (i in 0 until n) {
            out[k++] = xs[i] - ty[i] * half[i]
            out[k++] = ys[i] + tx[i] * half[i]
        }
        // Leading round cap: left → forward tip → right.
        val piHalf = PI.toFloat() / 2f
        k = addCap(out, k, xs[n - 1], ys[n - 1], tx[n - 1], ty[n - 1], half[n - 1], piHalf, -piHalf)
        // Right boundary, end → start.
        for (i in n - 1 downTo 0) {
            out[k++] = xs[i] + ty[i] * half[i]
            out[k++] = ys[i] - tx[i] * half[i]
        }
        // Trailing round cap: right → backward tip → left.
        addCap(out, k, xs[0], ys[0], tx[0], ty[0], half[0], -piHalf, -3f * piHalf)
        return out
    }

    /**
     * Write a round cap around (cx, cy) into [out] at offset [k0]: points at
     * `p + r·(cos φ·tangent + sin φ·normal)` for φ from [startAngle] to [endAngle], skipping the
     * first angle so it continues the boundary already written. The tangent and normal are unit
     * vectors, so this traces a semicircle whose ends meet the two offset boundaries. Returns the
     * next write offset.
     */
    private fun addCap(
        out: FloatArray, k0: Int, cx: Float, cy: Float, dx: Float, dy: Float, r: Float,
        startAngle: Float, endAngle: Float,
    ): Int {
        val nx = -dy
        val ny = dx
        val steps = 8
        var k = k0
        for (s in 1..steps) {
            val phi = startAngle + (endAngle - startAngle) * s / steps
            val c = cos(phi)
            val sn = sin(phi)
            out[k++] = cx + r * (c * dx + sn * nx)
            out[k++] = cy + r * (c * dy + sn * ny)
        }
        return k
    }
}
