package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.mobixournal.design.GlyphPart
import com.mobixournal.design.ToolGlyph
import com.mobixournal.design.ToolGlyphCatalogue

/**
 * The desktop's **adapter** for the shared tool glyphs — the window's half of the one catalogue.
 *
 * The art is not here any more: `ToolGlyphCatalogue` (plain Kotlin, `mobix-core`) names every part of
 * every glyph on the 24-unit grid, and this file turns those parts into `DrawScope` calls. The tablet's
 * adapter builds an `ImageVector` from the same parts, so the two toolbars cannot show different
 * pictures of the pen — which is exactly what they did while each shell kept its own hand-drawn set
 * (`docs/ui-modernization.md`).
 *
 * Coordinates and stroke both scale by `size / 24`, so a glyph drawn at 20 dp has the same optical
 * weight here as the same glyph at 20 dp on the tablet: a monoline icon is one line, not one width at
 * one zoom level.
 */
internal fun DrawScope.drawToolGlyph(tool: DesktopTool, color: Color) {
    val grid = DesktopTheme.TOOL_GLYPH_GRID
    val scale = size.minDimension / grid
    val stroke = DesktopTheme.TOOL_GLYPH_STROKE_UNITS * scale
    for (part in ToolGlyphCatalogue.parts(tool.glyph)) {
        when (part) {
            is GlyphPart.Line -> drawLine(
                color = color,
                start = Offset(part.x1 * scale, part.y1 * scale),
                end = Offset(part.x2 * scale, part.y2 * scale),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            is GlyphPart.Polyline -> {
                val path = Path().apply {
                    moveTo(part.points.first().x * scale, part.points.first().y * scale)
                    for (point in part.points.drop(1)) lineTo(point.x * scale, point.y * scale)
                    if (part.closed) close()
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = stroke,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            is GlyphPart.Circle -> drawCircle(
                color = color,
                radius = part.radius * scale,
                center = Offset(part.cx * scale, part.cy * scale),
                style = Stroke(width = stroke),
            )
            is GlyphPart.Cubic -> {
                val path = Path().apply {
                    moveTo(part.x0 * scale, part.y0 * scale)
                    cubicTo(
                        part.cx1 * scale, part.cy1 * scale,
                        part.cx2 * scale, part.cy2 * scale,
                        part.x1 * scale, part.y1 * scale,
                    )
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            is GlyphPart.Quad -> {
                val path = Path().apply {
                    moveTo(part.x0 * scale, part.y0 * scale)
                    quadraticBezierTo(
                        part.cx * scale, part.cy * scale,
                        part.x1 * scale, part.y1 * scale,
                    )
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * The glyph each of the window's fifteen tools draws — the tablet's own picture of that tool, by
 * identity: the pen is the tablet's pencil, the marquee its dashed frame, `CIRCUIT` its resistor.
 */
internal val DesktopTool.glyph: ToolGlyph
    get() = when (this) {
        DesktopTool.PEN -> ToolGlyph.PEN
        DesktopTool.HIGHLIGHTER -> ToolGlyph.HIGHLIGHTER
        DesktopTool.ERASER -> ToolGlyph.ERASER
        DesktopTool.LASSO -> ToolGlyph.LASSO
        DesktopTool.MARQUEE -> ToolGlyph.MARQUEE
        DesktopTool.TEXT -> ToolGlyph.TEXT
        DesktopTool.LINE -> ToolGlyph.LINE
        DesktopTool.ARROW -> ToolGlyph.ARROW
        DesktopTool.RECTANGLE -> ToolGlyph.RECTANGLE
        DesktopTool.ELLIPSE -> ToolGlyph.ELLIPSE
        DesktopTool.SPLINE -> ToolGlyph.SPLINE
        DesktopTool.PLOT -> ToolGlyph.PLOT
        DesktopTool.AXES -> ToolGlyph.AXES
        DesktopTool.TABLE -> ToolGlyph.TABLE
        DesktopTool.CIRCUIT -> ToolGlyph.RESISTOR
    }
