package com.mobixournal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.mobixournal.design.DesignTokens
import com.mobixournal.design.GlyphPart
import com.mobixournal.design.ToolGlyph
import com.mobixournal.design.ToolGlyphCatalogue

/**
 * The tablet's **adapter** for the shared tool glyphs (`ToolGlyphCatalogue`, plain Kotlin in
 * `mobix-core`).
 *
 * The catalogue holds geometry only; [glyphVector] turns it into an `ImageVector` on the same 24-unit grid
 * Material's icons use, so a rail slot, a picker row and the settings list keep using `Icon` — its tint,
 * its content description and its size — while drawing the **same** picture the desktop draws from the
 * same parts. The stroke is [DesignTokens.TOOL_GLYPH_STROKE_UNITS] of the grid, which is what makes the
 * two shells' toolbars match at the same rendered size instead of merely looking similar.
 *
 * The builder is shared with the settings catalogue ([com.mobixournal.design.SettingsGlyphCatalogue]):
 * one adapter for every stroke glyph the app has, so the settings index cannot end up drawing a heavier
 * line than a tool slot does.
 *
 * The vectors are built once per glyph: an icon is immutable, and the rail asks for the same few dozens
 * on every composition.
 */
private val toolGlyphVectors = HashMap<ToolGlyph, ImageVector>()

/** The [ImageVector] for this glyph — built once, then reused. */
fun ToolGlyph.imageVector(): ImageVector =
    toolGlyphVectors.getOrPut(this) { glyphVector("ToolGlyph.${name.lowercase()}", ToolGlyphCatalogue.parts(this)) }

/**
 * The stroke glyph [parts] as an icon — the one place a [GlyphPart] list becomes an `ImageVector`.
 *
 * [name] is the vector's own identity, which is what a preview or a debugging tool prints; the geometry is
 * the 24-unit grid every glyph in the catalogue is authored on.
 */
fun glyphVector(name: String, parts: List<GlyphPart>): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = DesignTokens.TOOL_GLYPH_GRID.dp,
        defaultHeight = DesignTokens.TOOL_GLYPH_GRID.dp,
        viewportWidth = DesignTokens.TOOL_GLYPH_GRID,
        viewportHeight = DesignTokens.TOOL_GLYPH_GRID,
    )
    for (part in parts) {
        builder.path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = DesignTokens.TOOL_GLYPH_STROKE_UNITS,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            when (part) {
                is GlyphPart.Line -> {
                    moveTo(part.x1, part.y1)
                    lineTo(part.x2, part.y2)
                }
                is GlyphPart.Polyline -> {
                    moveTo(part.points.first().x, part.points.first().y)
                    for (point in part.points.drop(1)) lineTo(point.x, point.y)
                    if (part.closed) close()
                }
                is GlyphPart.Circle -> {
                    // Two half arcs: the vector builder has no oval shorthand, and a stroked circle
                    // drawn as four Béziers would be a different curve at every radius.
                    moveTo(part.cx - part.radius, part.cy)
                    arcTo(part.radius, part.radius, 0f, true, true, part.cx + part.radius, part.cy)
                    arcTo(part.radius, part.radius, 0f, true, true, part.cx - part.radius, part.cy)
                    close()
                }
                is GlyphPart.Cubic -> {
                    moveTo(part.x0, part.y0)
                    curveTo(part.cx1, part.cy1, part.cx2, part.cy2, part.x1, part.y1)
                }
                is GlyphPart.Quad -> {
                    moveTo(part.x0, part.y0)
                    quadTo(part.cx, part.cy, part.x1, part.y1)
                }
            }
        }
    }
    return builder.build()
}
