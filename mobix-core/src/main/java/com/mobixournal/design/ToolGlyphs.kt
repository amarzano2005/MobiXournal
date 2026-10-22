package com.mobixournal.design

/**
 * The **one catalogue of tool glyphs**, as plain geometry — no Compose, no platform.
 *
 * Both shells draw their toolbars from this file: the tablet builds an `ImageVector` from it, the
 * desktop draws the same parts into a `DrawScope`. That is what makes "the PC shows the same icons as
 * the tablet" a fact rather than an intention — before it, the rail carried Material's outline icons
 * and the window carried a second, hand-drawn set of its own (`docs/ui-modernization.md`, monoline
 * iconography). A glyph is now defined **once**, and a shell that draws a different picture of the pen
 * is a failing test rather than a drifting copy.
 *
 * Coordinates are on the **24-unit icon grid** (`DesignTokens.TOOL_GLYPH_GRID`) and the stroke is
 * [DesignTokens.TOOL_GLYPH_STROKE_UNITS] of that grid, so the art scales to any slot and the two
 * shells keep the same optical weight at the same size. Nothing here names a colour: ink arrives from
 * the shell's theme, exactly as it does for every other chrome glyph.
 *
 * The catalogue covers every tool either shell can put on a **toolbar slot face** — the rail's tools,
 * the STEM dock's families, and the window's fifteen. The tablet's *pickers* still hold a few deeper
 * members (the other circuit components, gate variants, chemistry bonds) on their existing vectors;
 * unifying those is tracked as its own task, and is the one seam this file does not pretend to close.
 */
enum class ToolGlyph {
    PEN,
    HIGHLIGHTER,
    ERASER,
    ERASER_WHOLE,
    HAND,
    MARQUEE,
    LASSO,
    TEXT,
    IMAGE,
    VSPACE,

    LINE,
    ARROW,
    DOUBLE_ARROW,
    DIMENSION,
    SPLINE,
    AXES,
    PLOT,

    RECTANGLE,
    ELLIPSE,
    TRIANGLE,
    SQUARE,
    RHOMBUS,
    TRAPEZOID,
    PENTAGON,
    HEXAGON,

    TABLE,
    RESISTOR,
    DIODE,
    AND_GATE,
    BENZENE,
}

/** One point on the 24-unit glyph grid. */
data class GlyphPoint(val x: Float, val y: Float)

/**
 * One primitive of a glyph. The five shapes below are all the tool art needs: a straight stroke, a run
 * of them (closed or open), a circle, and the two Béziers that make a curve or a gate's shoulder.
 */
sealed interface GlyphPart {

    /** A straight stroke between two points. */
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : GlyphPart

    /** A run of connected strokes — the usual way a figure is drawn. */
    data class Polyline(val points: List<GlyphPoint>, val closed: Boolean = false) : GlyphPart

    /** A stroked circle: the ellipse tool, a lasso loop, the aromatic ring, a plotted dot. */
    data class Circle(val cx: Float, val cy: Float, val radius: Float) : GlyphPart

    /** A cubic Bézier — the spline, the axes' plotted curve, the AND gate's shoulder. */
    data class Cubic(
        val x0: Float,
        val y0: Float,
        val cx1: Float,
        val cy1: Float,
        val cx2: Float,
        val cy2: Float,
        val x1: Float,
        val y1: Float,
    ) : GlyphPart

    /** A quadratic Bézier, for a curve that needs one control point and no more. */
    data class Quad(
        val x0: Float,
        val y0: Float,
        val cx: Float,
        val cy: Float,
        val x1: Float,
        val y1: Float,
    ) : GlyphPart
}

/**
 * The glyph's parts, by name. A `when` rather than a map so a new [ToolGlyph] cannot be added without
 * the compiler demanding its art — which is the mechanical half of "one catalogue".
 */
object ToolGlyphCatalogue {

    /** Every part of [glyph], in draw order, on the 24-unit grid. */
    fun parts(glyph: ToolGlyph): List<GlyphPart> = when (glyph) {
        ToolGlyph.PEN -> pencil()
        ToolGlyph.HIGHLIGHTER -> highlighter()
        ToolGlyph.ERASER -> eraser()
        ToolGlyph.ERASER_WHOLE -> eraserWhole()
        ToolGlyph.HAND -> pan()
        ToolGlyph.MARQUEE -> marquee()
        ToolGlyph.LASSO -> lasso()
        ToolGlyph.TEXT -> text()
        ToolGlyph.IMAGE -> image()
        ToolGlyph.VSPACE -> verticalSpace()
        ToolGlyph.LINE -> line()
        ToolGlyph.ARROW -> arrow()
        ToolGlyph.DOUBLE_ARROW -> doubleArrow()
        ToolGlyph.DIMENSION -> dimension()
        ToolGlyph.SPLINE -> spline()
        ToolGlyph.AXES -> axes()
        ToolGlyph.PLOT -> plot()
        ToolGlyph.RECTANGLE -> polygon(3f, 6f, 21f, 6f, 21f, 18f, 3f, 18f)
        ToolGlyph.ELLIPSE -> listOf(GlyphPart.Circle(12f, 12f, 9f))
        ToolGlyph.TRIANGLE -> polygon(12f, 4f, 20f, 18f, 4f, 18f)
        ToolGlyph.SQUARE -> polygon(4f, 4f, 20f, 4f, 20f, 20f, 4f, 20f)
        ToolGlyph.RHOMBUS -> polygon(12f, 3f, 21f, 12f, 12f, 21f, 3f, 12f)
        ToolGlyph.TRAPEZOID -> polygon(8f, 7f, 16f, 7f, 20f, 19f, 4f, 19f)
        ToolGlyph.PENTAGON -> polygon(*regular(5, radius = 9f))
        ToolGlyph.HEXAGON -> polygon(*regular(6, radius = 9f))
        ToolGlyph.TABLE -> table()
        ToolGlyph.RESISTOR -> resistor()
        ToolGlyph.DIODE -> diode()
        ToolGlyph.AND_GATE -> andGate()
        ToolGlyph.BENZENE -> benzene()
    }

    // --- Inking tools --------------------------------------------------------------------------

    /** A pencil: the body, its tip, and the ferrule where the wood meets the paint. */
    private fun pencil(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(4f, 20f), GlyphPoint(5.6f, 14.6f), GlyphPoint(16f, 4.2f),
                GlyphPoint(19.8f, 8f), GlyphPoint(9.4f, 18.4f),
            ),
            closed = true,
        ),
        GlyphPart.Line(14.4f, 5.8f, 18.2f, 9.6f),
    )

    /** A wide chisel nib over the band it just laid down. */
    private fun highlighter(): List<GlyphPart> = listOf(
        GlyphPart.Line(6.7f, 14.4f, 13.9f, 7.2f),
        GlyphPart.Line(10.1f, 17.8f, 17.3f, 10.6f),
        GlyphPart.Line(5.3f, 20.6f, 17.8f, 20.6f),
    )

    /** The block eraser: a parallelogram lying on its corner. */
    private fun eraser(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(7.2f, 15.8f), GlyphPoint(12f, 10.6f),
                GlyphPoint(18.7f, 14.4f), GlyphPoint(13.9f, 20.2f),
            ),
            closed = true,
        ),
    )

    /** The whole-stroke eraser: the same block, with two sweeps under it where the stroke was. */
    private fun eraserWhole(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(5.5f, 12.5f), GlyphPoint(10f, 7.5f),
                GlyphPoint(16f, 12f), GlyphPoint(11.5f, 17f),
            ),
            closed = true,
        ),
        GlyphPart.Line(5f, 20.5f, 19f, 20.5f),
        GlyphPart.Line(8.5f, 17.8f, 15.5f, 17.8f),
    )

    /**
     * Pan: a palm with four finger lines, the tablet's `PanTool` in monoline. A four-way arrow was the
     * other candidate and reads as "move object"; a hand is what every editor uses for "move the view".
     */
    private fun pan(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(7.5f, 11.5f), GlyphPoint(7.5f, 19f), GlyphPoint(9.5f, 21f),
                GlyphPoint(14.5f, 21f), GlyphPoint(17f, 18f), GlyphPoint(17f, 11.5f),
            ),
        ),
        GlyphPart.Line(9.7f, 11.5f, 9.7f, 5.6f),
        GlyphPart.Line(12f, 11.5f, 12f, 4.2f),
        GlyphPart.Line(14.3f, 11.5f, 14.3f, 6.4f),
        GlyphPart.Line(7.5f, 14.5f, 5f, 11.8f),
    )

    // --- Selection and authoring ---------------------------------------------------------------

    /**
     * A dashed boundary: a marquee selects a region, it does not make an object.
     *
     * The dashes are **real strokes**, not a path effect: `ImageVector` on the tablet has no path-effect
     * parameter, so a dash drawn as a style would be one picture on the desktop and a solid frame on the
     * tablet. Generated from the same rectangle on both sides, they cannot disagree.
     */
    private fun marquee(): List<GlyphPart> = dashedRect(4f, 5f, 20f, 19f)

    /** A loop with its tail. */
    private fun lasso(): List<GlyphPart> = listOf(
        GlyphPart.Circle(12f, 10f, 5.3f),
        GlyphPart.Line(10f, 15.3f, 7.2f, 20.2f),
    )

    /** The letter T. */
    private fun text(): List<GlyphPart> = listOf(
        GlyphPart.Line(5.8f, 5.8f, 18.2f, 5.8f),
        GlyphPart.Line(12f, 5.8f, 12f, 19.2f),
    )

    /** A picture: frame, horizon and a sun. */
    private fun image(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(3.5f, 5f), GlyphPoint(20.5f, 5f),
                GlyphPoint(20.5f, 19f), GlyphPoint(3.5f, 19f),
            ),
            closed = true,
        ),
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(5f, 17f), GlyphPoint(9.5f, 11f), GlyphPoint(13f, 15f),
                GlyphPoint(15.5f, 12.5f), GlyphPoint(19f, 17f),
            ),
        ),
        GlyphPart.Circle(8f, 8.5f, 1.4f),
    )

    /** Vertical space: two rules with an arrow between them. */
    private fun verticalSpace(): List<GlyphPart> = listOf(
        GlyphPart.Line(6f, 3.5f, 18f, 3.5f),
        GlyphPart.Line(6f, 20.5f, 18f, 20.5f),
        GlyphPart.Line(12f, 6.5f, 12f, 17.5f),
        GlyphPart.Line(9.5f, 9f, 12f, 6.5f),
        GlyphPart.Line(14.5f, 9f, 12f, 6.5f),
        GlyphPart.Line(9.5f, 15f, 12f, 17.5f),
        GlyphPart.Line(14.5f, 15f, 12f, 17.5f),
    )

    // --- Geometry ------------------------------------------------------------------------------

    /** A segment with its two ends marked. */
    private fun line(): List<GlyphPart> = listOf(
        GlyphPart.Line(4.5f, 19.5f, 19.5f, 4.5f),
        GlyphPart.Circle(4.5f, 19.5f, 1.2f),
        GlyphPart.Circle(19.5f, 4.5f, 1.2f),
    )

    private fun arrow(): List<GlyphPart> = listOf(
        GlyphPart.Line(4f, 20f, 19.5f, 4.5f),
        GlyphPart.Line(19.5f, 4.5f, 14.5f, 6f),
        GlyphPart.Line(19.5f, 4.5f, 18f, 9.5f),
    )

    private fun doubleArrow(): List<GlyphPart> = listOf(
        GlyphPart.Line(4f, 20f, 20f, 4f),
        GlyphPart.Line(4f, 20f, 9f, 18.5f),
        GlyphPart.Line(4f, 20f, 5.5f, 15f),
        GlyphPart.Line(20f, 4f, 15f, 5.5f),
        GlyphPart.Line(20f, 4f, 18.5f, 9f),
    )

    /** A measured span: the dimension line, its two heads, and the witness lines it sits between. */
    private fun dimension(): List<GlyphPart> = listOf(
        GlyphPart.Line(3.5f, 7f, 3.5f, 19f),
        GlyphPart.Line(20.5f, 7f, 20.5f, 19f),
        GlyphPart.Line(5f, 14f, 19f, 14f),
        GlyphPart.Line(5f, 14f, 8f, 11.8f),
        GlyphPart.Line(5f, 14f, 8f, 16.2f),
        GlyphPart.Line(19f, 14f, 16f, 11.8f),
        GlyphPart.Line(19f, 14f, 16f, 16.2f),
    )

    /** A free curve with its two ends. */
    private fun spline(): List<GlyphPart> = listOf(
        GlyphPart.Cubic(4f, 18.5f, 8f, 3.5f, 16f, 3.5f, 20f, 18.5f),
        GlyphPart.Circle(4f, 18.5f, 1.2f),
        GlyphPart.Circle(20f, 18.5f, 1.2f),
    )

    /** The coordinate plane: an arrowed pair of axes meeting at the origin. */
    private fun axes(): List<GlyphPart> = listOf(
        GlyphPart.Line(4.5f, 20f, 4.5f, 4f),
        GlyphPart.Line(2f, 7.5f, 4.5f, 4f),
        GlyphPart.Line(7f, 7.5f, 4.5f, 4f),
        GlyphPart.Line(4.5f, 20f, 20.5f, 20f),
        GlyphPart.Line(17f, 17.5f, 20.5f, 20f),
        GlyphPart.Line(17f, 22.5f, 20.5f, 20f),
    )

    /** A function plot: the axes with one plotted curve sweeping up through them. */
    private fun plot(): List<GlyphPart> = listOf(
        GlyphPart.Line(4f, 20.5f, 4f, 4f),
        GlyphPart.Line(4f, 20.5f, 20.5f, 20.5f),
        GlyphPart.Cubic(5.5f, 18.5f, 8.5f, 5.5f, 13f, 17f, 19f, 6.5f),
    )

    // --- STEM ----------------------------------------------------------------------------------

    /** A table: the frame, a header row, a body row and a column. */
    private fun table(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(3f, 4f), GlyphPoint(21f, 4f),
                GlyphPoint(21f, 20f), GlyphPoint(3f, 20f),
            ),
            closed = true,
        ),
        GlyphPart.Line(3f, 9f, 21f, 9f),
        GlyphPart.Line(3f, 14.5f, 21f, 14.5f),
        GlyphPart.Line(12f, 9f, 12f, 20f),
    )

    /** A resistor between two wires. */
    private fun resistor(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(2f, 12f), GlyphPoint(5f, 12f), GlyphPoint(7.5f, 6.5f),
                GlyphPoint(10.5f, 17.5f), GlyphPoint(13.5f, 6.5f), GlyphPoint(16.5f, 17.5f),
                GlyphPoint(19f, 12f), GlyphPoint(22f, 12f),
            ),
        ),
    )

    /** A diode: the triangle and the bar it points at, between two wires. */
    private fun diode(): List<GlyphPart> = listOf(
        GlyphPart.Line(2f, 12f, 8f, 12f),
        GlyphPart.Polyline(
            listOf(GlyphPoint(8f, 6f), GlyphPoint(15.5f, 12f), GlyphPoint(8f, 18f)),
            closed = true,
        ),
        GlyphPart.Line(15.5f, 5.5f, 15.5f, 18.5f),
        GlyphPart.Line(15.5f, 12f, 22f, 12f),
    )

    /** An AND gate: two inputs, the D-shaped body, one output. */
    private fun andGate(): List<GlyphPart> = listOf(
        GlyphPart.Line(2f, 8f, 7f, 8f),
        GlyphPart.Line(2f, 16f, 7f, 16f),
        GlyphPart.Polyline(listOf(GlyphPoint(7f, 5f), GlyphPoint(12f, 5f))),
        GlyphPart.Cubic(12f, 5f, 17f, 5f, 17f, 19f, 12f, 19f),
        GlyphPart.Line(12f, 19f, 7f, 19f),
        GlyphPart.Line(7f, 19f, 7f, 5f),
        GlyphPart.Line(16f, 12f, 22f, 12f),
    )

    /** Benzene: the ring with its aromatic circle. */
    private fun benzene(): List<GlyphPart> =
        polygon(*regular(6, radius = 8f, cx = 12f, cy = 13f)) +
            GlyphPart.Circle(12f, 13f, 4.6f)

    // --- helpers -------------------------------------------------------------------------------

    /** A closed straight-sided figure from x/y pairs, as a one-part glyph. */
    private fun polygon(vararg coordinates: Float): List<GlyphPart> {
        val points = coordinates.toList().chunked(2).map { (x, y) -> GlyphPoint(x, y) }
        return listOf(GlyphPart.Polyline(points, closed = true))
    }

    /** A rectangle drawn as dashes, each one a stroke of its own. */
    private fun dashedRect(left: Float, top: Float, right: Float, bottom: Float): List<GlyphPart> {
        val dash = 2.6f
        val gap = 1.9f
        val parts = mutableListOf<GlyphPart>()
        fun run(x1: Float, y1: Float, x2: Float, y2: Float) {
            val length = kotlin.math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
            var start = 0f
            while (start < length) {
                val end = minOf(start + dash, length)
                // A tail shorter than half a unit is a dot, not a dash: drop it rather than draw one.
                if (end - start > 0.6f) {
                    parts += GlyphPart.Line(
                        x1 + (x2 - x1) * (start / length), y1 + (y2 - y1) * (start / length),
                        x1 + (x2 - x1) * (end / length), y1 + (y2 - y1) * (end / length),
                    )
                }
                start = end + gap
            }
        }
        run(left, top, right, top)
        run(right, top, right, bottom)
        run(right, bottom, left, bottom)
        run(left, bottom, left, top)
        return parts
    }

    /** A regular [sides]-gon's vertices, laid on the grid from a vertex at the top. */
    private fun regular(sides: Int, radius: Float, cx: Float = 12f, cy: Float = 12f): FloatArray {
        val values = FloatArray(sides * 2)
        for (i in 0 until sides) {
            val angle = -Math.PI / 2 + 2 * Math.PI * i / sides
            values[i * 2] = cx + (radius * kotlin.math.cos(angle)).toFloat()
            values[i * 2 + 1] = cy + (radius * kotlin.math.sin(angle)).toFloat()
        }
        return values
    }
}
