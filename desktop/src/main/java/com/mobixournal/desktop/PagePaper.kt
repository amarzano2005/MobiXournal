package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
internal fun DrawScope.drawPaper(
    background: Background,
    width: Double,
    height: Double,
    scale: Float,
    sheet: Size,
) {
    drawRect(Color.White, size = sheet)
    val solid = background as? Background.Solid ?: return
    drawRect(Color(solid.color), size = sheet)

    val spacing = rulingSpacing(solid)
    val lineWidth = 0.6f * scale
    when (solid.style.lowercase()) {
        "ruled", "lined" -> {
            val line = rulingColour(BackgroundGrid.LINED_RGB)
            for (y in BackgroundGrid.lines(height, spacing)) {
                val py = (y * scale).toFloat()
                drawLine(line, Offset(0f, py), Offset(sheet.width, py), strokeWidth = lineWidth)
            }
        }

        "graph", "squared", "grid" -> {
            val line = rulingColour(BackgroundGrid.GRAPH_RGB)
            for (y in BackgroundGrid.lines(height, spacing)) {
                val py = (y * scale).toFloat()
                drawLine(line, Offset(0f, py), Offset(size.width, py), strokeWidth = lineWidth)
            }
            for (x in BackgroundGrid.lines(width, spacing)) {
                val px = (x * scale).toFloat()
                drawLine(line, Offset(px, 0f), Offset(px, sheet.height), strokeWidth = lineWidth)
            }
        }

        // Isometric sheets rule the same triangular mesh the tablet and the SVG/PDF exports do:
        // `BackgroundGrid.isometric` is the shared geometry, so this shell strokes the segments it
        // returns instead of deriving a second grid that could drift from the sheet next to it.
        "isometric", "isograph" -> {
            val line = rulingColour(BackgroundGrid.GRAPH_RGB)
            for (segment in BackgroundGrid.isometric(width, height, spacing)) {
                drawLine(
                    line,
                    Offset((segment[0] * scale).toFloat(), (segment[1] * scale).toFloat()),
                    Offset((segment[2] * scale).toFloat(), (segment[3] * scale).toFloat()),
                    strokeWidth = lineWidth,
                )
            }
        }

        "dotted" -> {
            val dot = rulingColour(BackgroundGrid.DOT_RGB)
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
 * A ruling colour, made **opaque**.
 *
 * `BackgroundGrid`'s RGB constants are `0xRRGGBB` — the tablet's painter ORs the alpha in, because
 * `android.graphics.Color` would read the same number the same way. `Compose`'s `Color(Int)` wants
 * `0xAARRGGBB`, so passing the constant straight through gave every ruling an alpha of **zero**: the
 * desktop's graph and ruled sheets were painted white on white, and the only reason nobody noticed is
 * that nobody had drawn an isometric one yet.
 */
private fun rulingColour(rgb: Int): Color = Color(rgb or 0xFF000000.toInt())

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
        // `r1` on an isometric sheet is the triangle's side, not a square's.
        "isometric", "isograph" -> BackgroundGrid.ISO_SPACING_PT
        else -> BackgroundGrid.GRID_SPACING_PT
    }
}
