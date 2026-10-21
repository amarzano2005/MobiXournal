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

    /** The stroke's polyline as a Compose path, in device pixels (page pt × [scale]). */
    fun polyline(pts: List<StrokePoint>, scale: Float): Path = Path().apply {
        for ((i, p) in pts.withIndex()) {
            val x = (p.x * scale).toFloat()
            val y = (p.y * scale).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
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
