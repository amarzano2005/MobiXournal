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
     * It is the **smoothed** line ([smoothedInk]), not a chain of `lineTo`s: on the desktop the samples
     * are 8 ms apart at best, so joining them straight drew the sampling rate as facets along every
     * diagonal. The quadratics run through the midpoints of the samples, which is a fraction of a point of
     * deviation and no corner at all — and, because the samples themselves are untouched, the page is
     * unchanged for Android and for Xournal++.
     */
    fun polyline(pts: List<StrokePoint>, scale: Float): Path = Path().apply {
        val first = pts.firstOrNull() ?: return@apply
        moveTo((first.x * scale).toFloat(), (first.y * scale).toFloat())
        for (curve in smoothedInk(pts)) {
            quadraticBezierTo(
                (curve.controlX * scale).toFloat(),
                (curve.controlY * scale).toFloat(),
                (curve.toX * scale).toFloat(),
                (curve.toY * scale).toFloat(),
            )
        }
    }

    /** The pressure taper, filled as one polygon: [InkGeometry.taperOutline] converted to a [Path]. */
    fun taperPath(pts: List<StrokePoint>, scale: Float): Path {
        val outline = InkGeometry.taperOutline(pts, scale, offsetX = 0f, offsetY = 0f)
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
