package com.mobixournal.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.mobixournal.design.GlyphPart
import com.mobixournal.design.ToolGlyph
import com.mobixournal.design.ToolGlyphCatalogue

/**
 * The tablet's **Canvas** adapter for the shared tool glyphs — the third one, beside the
 * `ImageVector` builder the Compose chrome uses and the desktop's `DrawScope` painter.
 *
 * `ToolGlyphCatalogue` (`mobix-core`) holds geometry on the 24-unit grid; this file turns the same
 * parts into an `android.graphics.Path` so a glyph drawn under the pen (the radial HUD's tool slots)
 * is the same picture as the rail's, not a second hand-drawn set (`docs/interface-contract.md`,
 * *Icons: one catalogue*). The stroke is scaled with the slot, so a 20 dp glyph keeps the catalogue's
 * 1.5-unit weight rather than reading as a different icon at a different size.
 */
internal fun drawToolGlyph(
    canvas: Canvas,
    glyph: ToolGlyph,
    cx: Float,
    cy: Float,
    sizePx: Float,
    paint: Paint,
    path: Path,
) {
    val scale = sizePx / GLYPH_GRID.units
    val left = cx - sizePx / 2f
    val top = cy - sizePx / 2f
    paint.strokeWidth = (GLYPH_GRID.strokeUnits * scale).coerceAtLeast(1f)
    for (part in ToolGlyphCatalogue.parts(glyph)) {
        path.reset()
        when (part) {
            is GlyphPart.Line -> {
                path.moveTo(left + part.x1 * scale, top + part.y1 * scale)
                path.lineTo(left + part.x2 * scale, top + part.y2 * scale)
            }
            is GlyphPart.Polyline -> {
                val first = part.points.firstOrNull() ?: continue
                path.moveTo(left + first.x * scale, top + first.y * scale)
                for (point in part.points.drop(1)) path.lineTo(left + point.x * scale, top + point.y * scale)
                if (part.closed) path.close()
            }
            is GlyphPart.Circle -> path.addCircle(
                left + part.cx * scale,
                top + part.cy * scale,
                part.radius * scale,
                Path.Direction.CW,
            )
            is GlyphPart.Cubic -> {
                path.moveTo(left + part.x0 * scale, top + part.y0 * scale)
                path.cubicTo(
                    left + part.cx1 * scale, top + part.cy1 * scale,
                    left + part.cx2 * scale, top + part.cy2 * scale,
                    left + part.x1 * scale, top + part.y1 * scale,
                )
            }
            is GlyphPart.Quad -> {
                path.moveTo(left + part.x0 * scale, top + part.y0 * scale)
                path.quadTo(
                    left + part.cx * scale, top + part.cy * scale,
                    left + part.x1 * scale, top + part.y1 * scale,
                )
            }
        }
        canvas.drawPath(path, paint)
    }
}

/** The grid the catalogue draws on; named here so the adapter has no bare 24 in the arithmetic. */
private object GLYPH_GRID {
    const val units = 24f
    const val strokeUnits = 1.5f
}
