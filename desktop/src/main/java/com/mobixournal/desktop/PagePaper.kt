package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.mobixournal.format.model.Background
import com.mobixournal.render.BackgroundGrid

/**
 * The page sheet: its colour and its ruling.
 *
 * The geometry and the default spacings are **not** re-derived here — they come from
 * `render/BackgroundGrid.kt`, the same platform-free object the Android backgrounds use, so both
 * shells rule a page at the same intervals with the same colours. Only the drawing primitives are
 * this module's (`drawLine` on a Compose canvas instead of `android.graphics.Canvas`).
 *
 * Deliberately out of scope in the preview: PDF and pixmap backgrounds (they need the asset/PDF
 * pipeline), isometric paper, and the optional red margin.
 */
internal fun DrawScope.drawPaper(background: Background, width: Double, height: Double, scale: Float) {
    drawRect(Color.White, size = size)
    val solid = background as? Background.Solid ?: return
    drawRect(Color(solid.color), size = size)

    val spacing = rulingSpacing(solid)
    val lineWidth = 0.6f * scale
    when (solid.style.lowercase()) {
        "ruled", "lined" -> {
            val line = Color(BackgroundGrid.LINED_RGB)
            for (y in BackgroundGrid.lines(height, spacing)) {
                val py = (y * scale).toFloat()
                drawLine(line, Offset(0f, py), Offset(size.width, py), strokeWidth = lineWidth)
            }
        }

        "graph", "squared", "grid" -> {
            val line = Color(BackgroundGrid.GRAPH_RGB)
            for (y in BackgroundGrid.lines(height, spacing)) {
                val py = (y * scale).toFloat()
                drawLine(line, Offset(0f, py), Offset(size.width, py), strokeWidth = lineWidth)
            }
            for (x in BackgroundGrid.lines(width, spacing)) {
                val px = (x * scale).toFloat()
                drawLine(line, Offset(px, 0f), Offset(px, size.height), strokeWidth = lineWidth)
            }
        }

        "dotted" -> {
            val dot = Color(BackgroundGrid.DOT_RGB)
            val radius = 0.7f * scale
            for (y in BackgroundGrid.lines(height, spacing)) {
                for (x in BackgroundGrid.lines(width, spacing)) {
                    drawCircle(dot, radius, Offset((x * scale).toFloat(), (y * scale).toFloat()))
                }
            }
        }
    }
}

/**
 * The `<background config="r1=…,m1=…">` spacing, or desktop's default for the style when the file
 * names none. `config` is a comma-separated `key=value` list; only `r1` (spacing / square size) is
 * read here — the full parser (`BackgroundRuling`) also covers margins and bold lines.
 */
private fun rulingSpacing(solid: Background.Solid): Double {
    val r1 = solid.config
        ?.split(',')
        ?.mapNotNull { entry ->
            val at = entry.lastIndexOf('=')
            if (at <= 0) null else entry.substring(0, at).trim() to entry.substring(at + 1).trim()
        }
        ?.firstOrNull { it.first == "r1" }
        ?.second
        ?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }
    if (r1 != null) return r1
    return when (solid.style.lowercase()) {
        "ruled", "lined" -> BackgroundGrid.RULE_SPACING_PT
        else -> BackgroundGrid.GRID_SPACING_PT
    }
}
