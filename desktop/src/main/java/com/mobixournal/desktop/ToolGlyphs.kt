package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The strips' glyphs: every tool as two or three **monoline** strokes, the same rule the header and the
 * tabs follow (`docs/ui-modernization.md`: 1.5 dp strokes, never a filled Material icon).
 *
 * Drawn in code rather than shipped as vectors for the reason the rest of the window is: a glyph is a
 * handful of lines at a size the caller chooses, and a `DrawScope` drawing them reads the same at 20 dp
 * in the rail, 20 dp in the ribbon, and any scale a future 144 Hz surface wants. Coordinates are
 * **normalised** to the glyph's box, so a slot that grows does not need the art redrawn.
 *
 * The colour and stroke arrive as arguments — this file names no colour of its own, so the palette
 * stays in [DesktopTheme] and a selected slot's glyph is the chrome ink while the rest are secondary.
 * The three groups are drawn apart ([GlyphPen.inking], [GlyphPen.geometry], [GlyphPen.stem]) because a
 * single `when` over fifteen tools was already a hundred lines of one function.
 */
internal fun DrawScope.drawToolGlyph(tool: DesktopTool, color: Color, stroke: Float) {
    val pen = GlyphPen(this, color, stroke)
    when (tool) {
        DesktopTool.PEN, DesktopTool.HIGHLIGHTER, DesktopTool.ERASER,
        DesktopTool.LASSO, DesktopTool.MARQUEE,
        -> pen.inking(tool)
        DesktopTool.TEXT, DesktopTool.LINE, DesktopTool.ARROW,
        DesktopTool.RECTANGLE, DesktopTool.ELLIPSE, DesktopTool.SPLINE,
        -> pen.geometry(tool)
        DesktopTool.PLOT, DesktopTool.AXES, DesktopTool.TABLE, DesktopTool.CIRCUIT -> pen.stem(tool)
    }
}

/**
 * One glyph's pen: the normalised coordinates and the three or four primitives the art needs, so a
 * glyph reads as `line(0.2f, 0.8f, 0.8f, 0.2f)` instead of as trigonometry against `size`.
 */
private class GlyphPen(
    private val scope: DrawScope,
    private val color: Color,
    private val stroke: Float,
) {
    private val w = scope.size.width
    private val h = scope.size.height

    private fun at(x: Float, y: Float) = Offset(x * w, y * h)

    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        scope.drawLine(color, at(x1, y1), at(x2, y2), strokeWidth = stroke, cap = StrokeCap.Round)

    fun dots(vararg points: Pair<Float, Float>) {
        for ((x, y) in points) {
            scope.drawCircle(color, radius = w * 0.055f, center = at(x, y), style = Stroke(stroke))
        }
    }

    fun box(left: Float, top: Float, right: Float, bottom: Float, effect: PathEffect? = null) =
        scope.drawRect(
            color = color,
            topLeft = at(left, top),
            size = Size((right - left) * w, (bottom - top) * h),
            style = Stroke(stroke, pathEffect = effect),
        )

    fun circle(x: Float, y: Float, radius: Float) =
        scope.drawCircle(color, radius = w * radius, center = at(x, y), style = Stroke(stroke))

    fun polyline(points: List<Pair<Float, Float>>) {
        val path = Path().apply {
            moveTo(points.first().first * w, points.first().second * h)
            for ((x, y) in points.drop(1)) lineTo(x * w, y * h)
        }
        scope.drawPath(
            path,
            color,
            style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    fun curve(x0: Float, y0: Float, cx1: Float, cy1: Float, cx2: Float, cy2: Float, x1: Float, y1: Float) {
        val path = Path().apply {
            moveTo(x0 * w, y0 * h)
            cubicTo(cx1 * w, cy1 * h, cx2 * w, cy2 * h, x1 * w, y1 * h)
        }
        scope.drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }

    /** The core inking tools: what makes a mark on the page. */
    fun inking(tool: DesktopTool) {
        when (tool) {
            // A slanted nib: two edges and the butt end that holds them.
            DesktopTool.PEN -> {
                line(0.22f, 0.78f, 0.66f, 0.34f)
                line(0.34f, 0.86f, 0.78f, 0.42f)
                line(0.22f, 0.78f, 0.34f, 0.86f)
            }
            // A wide nib over the line it just laid down.
            DesktopTool.HIGHLIGHTER -> {
                line(0.28f, 0.60f, 0.58f, 0.30f)
                line(0.42f, 0.74f, 0.72f, 0.44f)
                line(0.22f, 0.86f, 0.74f, 0.86f)
            }
            // A parallelogram: the block eraser.
            DesktopTool.ERASER -> polyline(
                listOf(0.30f to 0.66f, 0.50f to 0.44f, 0.78f to 0.60f, 0.58f to 0.84f, 0.30f to 0.66f),
            )
            // A loop with its tail: the lasso.
            DesktopTool.LASSO -> {
                circle(0.50f, 0.42f, radius = 0.22f)
                line(0.42f, 0.60f, 0.30f, 0.84f)
            }
            // A dashed frame: a marquee is a boundary, not an object.
            DesktopTool.MARQUEE -> box(
                left = 0.20f,
                top = 0.22f,
                right = 0.80f,
                bottom = 0.78f,
                effect = PathEffect.dashPathEffect(floatArrayOf(w * 0.10f, w * 0.08f)),
            )
            else -> Unit
        }
    }

    /** The geometry tools: a line, an arrow, the closed shapes and the free spline. */
    fun geometry(tool: DesktopTool) {
        when (tool) {
            // The letter T.
            DesktopTool.TEXT -> {
                line(0.24f, 0.24f, 0.76f, 0.24f)
                line(0.50f, 0.24f, 0.50f, 0.80f)
            }
            // A segment with its two ends.
            DesktopTool.LINE -> {
                line(0.24f, 0.76f, 0.76f, 0.24f)
                dots(0.24f to 0.76f, 0.76f to 0.24f)
            }
            DesktopTool.ARROW -> {
                line(0.22f, 0.78f, 0.76f, 0.24f)
                line(0.76f, 0.24f, 0.56f, 0.28f)
                line(0.76f, 0.24f, 0.72f, 0.44f)
            }
            DesktopTool.RECTANGLE -> box(0.22f, 0.28f, 0.78f, 0.72f)
            DesktopTool.ELLIPSE -> circle(0.50f, 0.50f, radius = 0.28f)
            DesktopTool.SPLINE -> curve(0.18f, 0.78f, 0.34f, 0.18f, 0.66f, 0.18f, 0.82f, 0.78f)
            else -> Unit
        }
    }

    /** The STEM tools: axes, the curve that makes them worth drawing, a grid and a resistor. */
    fun stem(tool: DesktopTool) {
        when (tool) {
            DesktopTool.PLOT -> {
                line(0.20f, 0.82f, 0.20f, 0.18f)
                line(0.20f, 0.82f, 0.84f, 0.82f)
                polyline(listOf(0.28f to 0.70f, 0.44f to 0.70f, 0.66f to 0.30f, 0.78f to 0.26f))
            }
            // A cross with two arrowheads: the axes on their own.
            DesktopTool.AXES -> {
                line(0.50f, 0.20f, 0.50f, 0.80f)
                line(0.18f, 0.50f, 0.82f, 0.50f)
                line(0.50f, 0.20f, 0.42f, 0.30f)
                line(0.50f, 0.20f, 0.58f, 0.30f)
                line(0.82f, 0.50f, 0.72f, 0.42f)
                line(0.82f, 0.50f, 0.72f, 0.58f)
            }
            // A grid: rows and columns.
            DesktopTool.TABLE -> {
                box(0.18f, 0.24f, 0.82f, 0.76f)
                line(0.18f, 0.42f, 0.82f, 0.42f)
                line(0.50f, 0.24f, 0.50f, 0.76f)
            }
            // A resistor between two wires: the circuit family in one stroke.
            DesktopTool.CIRCUIT -> polyline(
                listOf(
                    0.16f to 0.50f, 0.30f to 0.50f, 0.36f to 0.32f, 0.44f to 0.68f,
                    0.52f to 0.32f, 0.60f to 0.68f, 0.66f to 0.50f, 0.84f to 0.50f,
                ),
            )
            else -> Unit
        }
    }
}
