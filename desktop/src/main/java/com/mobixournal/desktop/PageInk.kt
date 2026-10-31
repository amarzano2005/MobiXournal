package com.mobixournal.desktop

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.render.InkGeometry

/**
 * The Compose side of the ink rules: it turns the shared geometry into the two Compose types the
 * canvas consumes (a polyline [Path], a taper [Path] and a dash [PathEffect]).
 *
 * The rules themselves — highlighter alpha and multiply, the band width, the dash pattern, the
 * variable-width outline — are **not** here: they live in `InkGeometry` (`mobix-core`), the same
 * object `app`'s `StrokePainter` calls. That is what keeps a page looking the same in both shells;
 * only the raster backend differs.
 */
internal object PageInk {

    /**
     * The stroke's line as a Compose path, in device pixels (page pt × [scale]).
     *
     * It is the **smoothed** line ([smoothedInk]) for freehand pen strokes, not a chain of `lineTo`s: on the desktop the samples
     * are 8 ms apart at best, so joining them straight drew the sampling rate as facets along every
     * diagonal. Geometric figures pass [smooth] = false to keep their straight sides and sharp corners untouched.
     */
    fun polyline(pts: List<StrokePoint>, scale: Float, smooth: Boolean = true): Path = Path().apply {
        val first = pts.firstOrNull() ?: return@apply
        moveTo((first.x * scale).toFloat(), (first.y * scale).toFloat())
        if (smooth) {
            for (curve in smoothedInk(pts)) {
                quadraticBezierTo(
                    (curve.controlX * scale).toFloat(),
                    (curve.controlY * scale).toFloat(),
                    (curve.toX * scale).toFloat(),
                    (curve.toY * scale).toFloat(),
                )
            }
        } else {
            for (i in 1 until pts.size) {
                val p = pts[i]
                lineTo((p.x * scale).toFloat(), (p.y * scale).toFloat())
            }
        }
    }

    /** The pressure taper, filled as one polygon: [InkGeometry.taperOutline] converted to a [Path]. */
    fun taperPath(pts: List<StrokePoint>, scale: Float, smooth: Boolean = true): Path {
        val points = if (smooth && pts.size >= 3) smoothPointsForTaper(pts) else pts
        val outline = InkGeometry.taperOutline(points, scale, offsetX = 0f, offsetY = 0f)
        if (outline.size < 6) return Path()
        return Path().apply {
            moveTo(outline[0], outline[1])
            var i = 2
            while (i < outline.size) {
                lineTo(outline[i], outline[i + 1])
                i += 2
            }
            close()
        }
    }

    /** [InkGeometry.dashIntervalsPt] scaled to device pixels, as a Compose dash effect. */
    fun dashEffect(style: LineStyle, widthPt: Double, scale: Float): PathEffect? {
        val pattern = InkGeometry.dashIntervalsPt(style, widthPt) ?: return null
        return PathEffect.dashPathEffect(FloatArray(pattern.size) { pattern[it] * scale })
    }
}
