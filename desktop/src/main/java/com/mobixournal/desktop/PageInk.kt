package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import kotlin.math.hypot

/**
 * The geometry and colour rules the desktop canvas paints with, kept pure so a JVM test can check
 * them without a window.
 *
 * They mirror the Android renderer's contract (`render/StrokePainter.kt`, `docs/rendering.md`): a
 * highlighter is a constant-width band forced to [HIGHLIGHTER_ALPHA] and composited with multiply,
 * while the pen keeps its pressure taper as one filled variable-width outline. Only the *raster
 * backend* differs — Skia through Compose here, `android.graphics.Canvas` there — so the two halves
 * of the app cannot disagree about what a stroke looks like. When the Phase 3 module split lands
 * (`docs/desktop-workstation.md`), these helpers move into `mobix-render` and both shells call the
 * same code instead of two copies.
 */
internal object PageInk {

    /** Desktop Xournal++'s highlighter opacity (`OPACITY_HIGHLIGHTER`): 0x78 of 255. */
    const val HIGHLIGHTER_ALPHA: Int = 0x78

    /** True when every vertex draws at the same width (a shape, a spline, any constant-width pen). */
    fun uniform(pts: List<StrokePoint>): Boolean =
        pts.isNotEmpty() && pts.all { kotlin.math.abs(it.width - pts[0].width) < 1e-6 }

    /** A highlighter's band is one width for the whole stroke: the mean vertex width, sane for odd input. */
    fun bandWidth(pts: List<StrokePoint>): Double =
        if (pts.isEmpty()) 0.0 else pts.sumOf { it.width } / pts.size

    /** The colour actually painted: the highlighter keeps its RGB and is forced to [HIGHLIGHTER_ALPHA]. */
    fun renderColor(tool: Tool, color: Int): Int =
        if (tool == Tool.HIGHLIGHTER) (color and 0x00FFFFFF) or (HIGHLIGHTER_ALPHA shl 24) else color

    /** The on/off dash pattern in pt, scaled by [widthPt] so a thick dashed line stays legible. */
    fun dashIntervals(style: LineStyle, widthPt: Double): FloatArray? {
        val u = maxOf(widthPt, 0.5).toFloat()
        return when (style) {
            LineStyle.PLAIN -> null
            LineStyle.DASHED -> floatArrayOf(4f * u, 3f * u)
            LineStyle.DASH_DOT -> floatArrayOf(4f * u, 3f * u, 0.01f * u, 3f * u)
            LineStyle.DOTTED -> floatArrayOf(0.01f * u, 2.5f * u)
        }
    }

    /** The stroke's polyline as a path, in device pixels (page pt × [scale]). */
    fun polyline(pts: List<StrokePoint>, scale: Float): Path = Path().apply {
        for ((i, p) in pts.withIndex()) {
            val x = (p.x * scale).toFloat()
            val y = (p.y * scale).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }

    /**
     * The pressure taper as **one filled polygon**: walk the left-hand offsets of every vertex, then
     * the right-hand ones back, so a varying width is a single fill rather than a line per segment
     * (which would bead at every join). This is the same `StrokeContour` idea desktop Xournal++ and
     * the Android `StrokePainter` use.
     */
    fun taperOutline(pts: List<StrokePoint>, scale: Float): Path {
        val n = pts.size
        val left = ArrayList<Offset>(n)
        val right = ArrayList<Offset>(n)
        for (i in 0 until n) {
            val prev = pts[if (i == 0) 0 else i - 1]
            val next = pts[if (i == n - 1) n - 1 else i + 1]
            var dx = (next.x - prev.x).toFloat()
            var dy = (next.y - prev.y).toFloat()
            val len = hypot(dx, dy)
            if (len < 1e-4f) {
                dx = 0f
                dy = 1f
            } else {
                dx /= len
                dy /= len
            }
            // The normal of the tangent; half the vertex width on each side of the centre line.
            val nx = -dy
            val ny = dx
            val half = (pts[i].width * 0.5 * scale).toFloat()
            val cx = (pts[i].x * scale).toFloat()
            val cy = (pts[i].y * scale).toFloat()
            left += Offset(cx + nx * half, cy + ny * half)
            right += Offset(cx - nx * half, cy - ny * half)
        }
        return Path().apply {
            moveTo(left[0].x, left[0].y)
            for (i in 1 until n) lineTo(left[i].x, left[i].y)
            for (i in n - 1 downTo 0) lineTo(right[i].x, right[i].y)
            close()
        }
    }
}
