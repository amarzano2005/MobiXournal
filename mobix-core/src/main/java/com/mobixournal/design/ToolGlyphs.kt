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
 * the STEM dock's families, the window's fifteen, and every member a STEM picker row can show. It is
 * the **only** source of tool art: the tablet's vectors for the deeper circuit, gate and bond members
 * were transcribed into [ToolGlyphsCircuit] and [ToolGlyphsChemistry] and deleted, so a picker row and
 * a rail slot now show the same picture by construction rather than by two artists agreeing.
 */
enum class ToolGlyph {
    PEN,
    HIGHLIGHTER,
    ERASER,
    ERASER_WHOLE,
    HAND,
    MARQUEE,
    LASSO,

    SELECT_TEXT,
    SELECT_BG,
    TEXT,
    IMAGE,
    TEXIMAGE,
    VSPACE,
    PLAY,

    /** The page itself — what the sheet *is* (its paper and ruling). */
    PAGE,

    /** The page's measured format — its size on the sheet. */
    PAGE_SIZE,

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
    TRIANGLE_EQUILATERAL,
    TRIANGLE_ISOSCELES,
    TRIANGLE_RIGHT,
    TRIANGLE_SCALENE,
    SQUARE,
    RHOMBUS,
    TRAPEZOID,
    TRAPEZOID_RIGHT,
    TRAPEZOID_SCALENE,
    PENTAGON,
    HEXAGON,

    TABLE,
    RESISTOR,
    CAPACITOR,
    INDUCTOR,
    GROUND,
    DIODE,
    LED,
    ZENER_DIODE,
    OPAMP,
    BJT_NPN,
    BJT_PNP,
    DC_SOURCE,
    CURRENT_SOURCE,
    TRANSFORMER,
    SWITCH_OPEN,
    SWITCH_CLOSED,
    JUNCTION,

    AND_GATE,
    OR_GATE,
    NOT_GATE,
    NAND_GATE,
    NOR_GATE,
    XOR_GATE,
    XNOR_GATE,
    BUFFER_GATE,

    BENZENE,
    CHEM_BOND_SINGLE,
    CHEM_BOND_DOUBLE,
    CHEM_BOND_TRIPLE,
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
        ToolGlyph.SELECT_TEXT -> selectText()
        ToolGlyph.SELECT_BG -> selectBackground()
        ToolGlyph.TEXT -> text()
        ToolGlyph.IMAGE -> image()
        ToolGlyph.TEXIMAGE -> texImage()
        ToolGlyph.VSPACE -> verticalSpace()
        ToolGlyph.PLAY -> play()
        ToolGlyph.PAGE -> page()
        ToolGlyph.PAGE_SIZE -> pageSize()
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
        // The variants the rail can put on the slot face when the user picks one: each one's
        // proportions are what tells it apart at 18 dp, so they are the tool's own outlines.
        ToolGlyph.TRIANGLE_EQUILATERAL -> polygon(12f, 4f, 20f, 17f, 4f, 17f)
        ToolGlyph.TRIANGLE_ISOSCELES -> polygon(12f, 2.5f, 18f, 19f, 6f, 19f)
        ToolGlyph.TRIANGLE_RIGHT -> polygon(4f, 4f, 4f, 19f, 20f, 19f)
        ToolGlyph.TRIANGLE_SCALENE -> polygon(8.5f, 3f, 20f, 19f, 4f, 19f)
        ToolGlyph.SQUARE -> polygon(4f, 4f, 20f, 4f, 20f, 20f, 4f, 20f)
        ToolGlyph.RHOMBUS -> polygon(12f, 3f, 21f, 12f, 12f, 21f, 3f, 12f)
        ToolGlyph.TRAPEZOID -> polygon(8f, 7f, 16f, 7f, 20f, 19f, 4f, 19f)
        ToolGlyph.TRAPEZOID_RIGHT -> polygon(5f, 7f, 12f, 7f, 20f, 19f, 5f, 19f)
        ToolGlyph.TRAPEZOID_SCALENE -> polygon(9f, 7f, 17f, 7f, 20f, 19f, 4f, 19f)
        ToolGlyph.PENTAGON -> polygon(*regular(5, radius = 9f))
        ToolGlyph.HEXAGON -> polygon(*regular(6, radius = 9f))
        ToolGlyph.TABLE -> table()
        ToolGlyph.RESISTOR -> resistor()
        ToolGlyph.CAPACITOR -> capacitor()
        ToolGlyph.INDUCTOR -> inductor()
        ToolGlyph.GROUND -> ground()
        ToolGlyph.DIODE -> diode()
        ToolGlyph.LED -> led()
        ToolGlyph.ZENER_DIODE -> zenerDiode()
        ToolGlyph.OPAMP -> opAmp()
        ToolGlyph.BJT_NPN -> bjtNpn()
        ToolGlyph.BJT_PNP -> bjtPnp()
        ToolGlyph.DC_SOURCE -> dcSource()
        ToolGlyph.CURRENT_SOURCE -> currentSource()
        ToolGlyph.TRANSFORMER -> transformer()
        ToolGlyph.SWITCH_OPEN -> switchOpen()
        ToolGlyph.SWITCH_CLOSED -> switchClosed()
        ToolGlyph.JUNCTION -> junction()
        ToolGlyph.AND_GATE -> andGate()
        ToolGlyph.OR_GATE -> orGate()
        ToolGlyph.NOT_GATE -> notGate()
        ToolGlyph.NAND_GATE -> nandGate()
        ToolGlyph.NOR_GATE -> norGate()
        ToolGlyph.XOR_GATE -> xorGate()
        ToolGlyph.XNOR_GATE -> xnorGate()
        ToolGlyph.BUFFER_GATE -> bufferGate()
        ToolGlyph.BENZENE -> benzene()
        ToolGlyph.CHEM_BOND_SINGLE -> bondSingle()
        ToolGlyph.CHEM_BOND_DOUBLE -> bondDouble()
        ToolGlyph.CHEM_BOND_TRIPLE -> bondTriple()
    }

    /**
     * A sheet with its corner folded — the page-setup command that restyles the paper under the ink.
     *
     * It is the page's own shape and nothing else: a ruled page would not survive 18 dp, and a running
     * head is noise at that size, so the fold is the one detail that says *a sheet* rather than *a box*.
     */
    private fun page(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(5f, 3f), GlyphPoint(14f, 3f), GlyphPoint(19f, 8f),
                GlyphPoint(19f, 21f), GlyphPoint(5f, 21f),
            ),
            closed = true,
        ),
        // The fold itself, so the corner reads as turned paper rather than a clipped rectangle.
        GlyphPart.Polyline(
            listOf(GlyphPoint(14f, 3f), GlyphPoint(14f, 8f), GlyphPoint(19f, 8f)),
        ),
    )

    /**
     * The same sheet, **measured**: a dimension line under it, between two end ticks.
     *
     * The pair only has to be told apart at a slot's 20 dp: a sheet with a fold is the paper, a sheet
     * over a measured span is the format. Nothing else is added, because a second fold or a ruler's
     * ticks inside the sheet turn both glyphs into the same smudge at that size.
     */
    private fun pageSize(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(4f, 3f), GlyphPoint(20f, 3f),
                GlyphPoint(20f, 15f), GlyphPoint(4f, 15f),
            ),
            closed = true,
        ),
        GlyphPart.Line(4f, 20f, 20f, 20f),
        GlyphPart.Line(4f, 17.5f, 4f, 21f),
        GlyphPart.Line(20f, 17.5f, 20f, 21f),
    )

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

    /**
     * A marker on the **opposite 45°** from the pencil and the eraser, over the thick band it laid.
     *
     * Interference on the rail was the problem, not detail: the barrel used to run from bottom-left to
     * top-right — the eraser's own diagonal — at the eraser's own width, so beside it the two read as
     * one picture and only the small nib told them apart. Four things now separate them at 20 dp:
     * the marker runs the other way, its barrel **tapers** (a wedge, not a block), it carries a collar
     * near the cap, and the band it paints is a **closed, thick swipe** rather than a hairline at the
     * grid's edge, which is what makes "I write with this" legible without a caption.
     *
     * A highlighter is recognised by its body being fatter than a pen and by the wide chisel that body
     * ends in; the swipe underneath is what keeps it apart from the pencil beside it in the rail.
     */
    private fun highlighter(): List<GlyphPart> = listOf(
        // The barrel: narrower at the cap than at the nib, so it reads as a wedge held in the hand and
        // not as the eraser's long, even block.
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(4.2f, 8.3f), GlyphPoint(8.3f, 4.2f),
                GlyphPoint(15.3f, 11.3f), GlyphPoint(10.4f, 15.4f),
            ),
            closed = true,
        ),
        // The collar: the one line that says the wide end is a *nib* and the narrow end a cap.
        GlyphPart.Line(6f, 10.5f, 10.5f, 6f),
        // The chisel: a straight taper from the barrel's width to a flat tip, which is what a marker
        // ends in — an eraser has nothing at its end at all.
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(10.4f, 15.4f), GlyphPoint(15.3f, 11.3f),
                GlyphPoint(16.9f, 13.8f), GlyphPoint(14.1f, 16.9f),
            ),
            closed = true,
        ),
        // The band it laid: closed, and only as tall as the stroke is thick plus a hairline, so at
        // 20 dp its two long edges read as one painted stroke rather than as an empty tray — a taller
        // loop leaves a visible slit and stops looking like colour.
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(2.2f, 19.6f), GlyphPoint(21.8f, 19.6f),
                GlyphPoint(21.8f, 21.6f), GlyphPoint(2.2f, 21.6f),
            ),
            closed = true,
        ),
    )

    /**
     * The block eraser: a rubber lying on its corner, **split by the crease** where rubber meets sleeve.
     *
     * A bare parallelogram is the rhombus tool with a tilt; the crease is the one stroke that says
     * "eraser" and the only thing that tells this glyph from the whole-stroke one below it.
     */
    private fun eraser(): List<GlyphPart> = listOf(
        // A long, low block, not a small diamond: at 20 dp the old 10-unit square was a blob, and an
        // eraser needs its length to read as something held in the hand.
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(18f, 5.5f), GlyphPoint(20.9f, 11.6f),
                GlyphPoint(6f, 18.6f), GlyphPoint(3.1f, 12.5f),
            ),
            closed = true,
        ),
        // The crease sits a third of the way along, not across the middle: a rubber with its sleeve at
        // one end reads as an eraser, a block split in half reads as a folded card.
        GlyphPart.Line(12.9f, 7.8f, 15.8f, 14f),
    )

    /**
     * The whole-stroke eraser: the same split block, smaller, over the two sweeps the stroke left.
     *
     * The sweeps are what mark it as *whole stroke* rather than *pixel*: the block alone would be the
     * eraser above, and a second, subtly different eraser is the worst pair a rail can show.
     */
    private fun eraserWhole(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(17.1f, 6.4f), GlyphPoint(19.5f, 11.7f),
                GlyphPoint(6.9f, 17.6f), GlyphPoint(4.5f, 12.3f),
            ),
            closed = true,
        ),
        GlyphPart.Line(12.8f, 8.4f, 15.2f, 13.7f),
        GlyphPart.Line(2.6f, 20.2f, 21.4f, 20.2f),
        GlyphPart.Line(6.2f, 22.6f, 17.8f, 22.6f),
    )

    /**
     * Pan: a palm with four finger lines, the tablet's `PanTool` in monoline. A four-way arrow was the
     * other candidate and reads as "move object"; a hand is what every editor uses for "move the view".
     */
    /**
     * The pan hand: a **U** of palm under four fingers, and a thumb hanging off the wrist.
     *
     * The first version closed the palm into a box and let the fingers rise out of its top edge, which at
     * slot size read as a cup with sticks in it — the edge crossed the fingers as one hard line, the box's
     * right side doubled the little finger, and the thumb sat beside the shape instead of on it. A hand is
     * a *silhouette*: the palm's mouth is open (no line between the fingers), the fingers are the top of
     * that silhouette, and the thumb is a curve of the same outline that reaches across the palm.
     */
    private fun pan(): List<GlyphPart> = listOf(
        // The palm: left side, round heel, and right side — the top stays open for the fingers, so no
        // line runs under them.
        GlyphPart.Line(8.5f, 13.4f, 8.5f, 16.6f),
        GlyphPart.Cubic(8.5f, 16.6f, 8.5f, 18.4f, 9.8f, 19.4f, 11.5f, 19.4f),
        GlyphPart.Line(11.5f, 19.4f, 15.5f, 19.4f),
        GlyphPart.Cubic(15.5f, 19.4f, 17.2f, 19.4f, 18.5f, 18.4f, 18.5f, 16.6f),
        GlyphPart.Line(18.5f, 16.6f, 18.5f, 13.4f),
        // Four fingers, tallest in the middle and clearly longer than they are wide, so the silhouette
        // is a hand rather than the teeth of a comb.
        GlyphPart.Line(10.2f, 13.4f, 10.2f, 6.0f),
        GlyphPart.Line(12.8f, 13.4f, 12.8f, 3.8f),
        GlyphPart.Line(15.4f, 13.4f, 15.4f, 4.6f),
        GlyphPart.Line(17.9f, 13.4f, 17.9f, 7.2f),
        // The thumb is a lobe of the same hand: it leaves the palm's **mouth** on the index side and
        // curves up beside the fingers, so it reads as a thumb rather than a stray tick on the wrist.
        GlyphPart.Cubic(8.5f, 13.4f, 6.8f, 13.1f, 5.6f, 11.7f, 5.6f, 9.8f),
    )

    // --- Selection and authoring ---------------------------------------------------------------

    /**
     * A dashed boundary: a marquee selects a region, it does not make an object.
     *
     * The dashes are **real strokes**, not a path effect: `ImageVector` on the tablet has no path-effect
     * parameter, so a dash drawn as a style would be one picture on the desktop and a solid frame on the
     * tablet. Generated from the same rectangle on both sides, they cannot disagree.
     */
    private fun marquee(): List<GlyphPart> = listOf(
        // Four solid corner brackets: the frame's own corners, which is what the eye reads as a
        // selection handle. Without them the dashes are a rectangle and nothing more.
        GlyphPart.Line(4f, 8f, 4f, 5f),
        GlyphPart.Line(4f, 5f, 7.5f, 5f),
        GlyphPart.Line(16.5f, 5f, 20f, 5f),
        GlyphPart.Line(20f, 5f, 20f, 8f),
        GlyphPart.Line(20f, 16f, 20f, 19f),
        GlyphPart.Line(20f, 19f, 16.5f, 19f),
        GlyphPart.Line(7.5f, 19f, 4f, 19f),
        GlyphPart.Line(4f, 19f, 4f, 16f),
        // One dash at each edge's middle keeps it a *selection* boundary rather than a crop frame.
        GlyphPart.Line(10.2f, 5f, 13.8f, 5f),
        GlyphPart.Line(20f, 10.2f, 20f, 13.8f),
        GlyphPart.Line(10.2f, 19f, 13.8f, 19f),
        GlyphPart.Line(4f, 10.2f, 4f, 13.8f),
    )

    /**
     * A lasso: an organic loop whose **tail grows out of the rope** and crosses it.
     *
     * A circle with a straight stick under it — the first cut — is a balloon. The loop is drawn as four
     * Béziers so it has no corners at 20 dp, and the tail starts just **inside** the loop so the two
     * strokes read as one rope doubling back.
     */
    private fun lasso(): List<GlyphPart> = listOf(
        // Wider than tall and slightly asymmetric: a perfect circle with anything hanging from it is a
        // magnifying glass, which this window already uses for zoom.
        GlyphPart.Cubic(12.4f, 4.8f, 15.8f, 4.8f, 18.6f, 6.6f, 18.6f, 9.2f),
        GlyphPart.Cubic(18.6f, 9.2f, 18.6f, 11.8f, 15.6f, 14.2f, 11.8f, 14.4f),
        GlyphPart.Cubic(11.8f, 14.4f, 8.6f, 14.6f, 6f, 12.2f, 6f, 9.4f),
        GlyphPart.Cubic(6f, 9.4f, 6f, 6.8f, 9f, 4.8f, 12.4f, 4.8f),
        // The rope leaves the loop's **lower left**, drops and hooks back up: a magnifier's handle is a
        // straight bar leaving at 45°, so a curled tail that turns back on itself cannot be one.
        GlyphPart.Cubic(8.4f, 13.6f, 6.6f, 14.6f, 5.6f, 16f, 5.8f, 17.6f),
        GlyphPart.Cubic(5.8f, 17.6f, 6f, 19.6f, 8f, 20.6f, 8.6f, 22.2f),
        GlyphPart.Cubic(8.6f, 22.2f, 9.8f, 22.8f, 10.6f, 21.6f, 10f, 20.2f),
    )

    /**
     * Select text: an **I-beam**, the cursor every text surface uses. It shares nothing with the
     * marquee's frame, which is the point — the two selections act on different things.
     */
    private fun selectText(): List<GlyphPart> = listOf(
        GlyphPart.Line(12f, 4f, 12f, 20f),
        GlyphPart.Line(8.5f, 4f, 15.5f, 4f),
        GlyphPart.Line(8.5f, 20f, 15.5f, 20f),
    )

    /** Select background: the crop frame, standing in for "take the picture, not the ink". */
    private fun selectBackground(): List<GlyphPart> = listOf(
        GlyphPart.Line(7.5f, 3f, 7.5f, 16.5f),
        GlyphPart.Line(3f, 7.5f, 16.5f, 7.5f),
        GlyphPart.Line(16.5f, 7.5f, 16.5f, 21f),
        GlyphPart.Line(7.5f, 16.5f, 21f, 16.5f),
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

    /** LaTeX: the radical sign, the one mark that reads as mathematics at 20 dp. */
    private fun texImage(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(
                GlyphPoint(3f, 12.5f), GlyphPoint(5.5f, 12.5f), GlyphPoint(7.5f, 19.5f),
                GlyphPoint(11f, 4.5f), GlyphPoint(20f, 4.5f),
            ),
        ),
    )

    /** A played annotation: the transport triangle, outlined like every other glyph. */
    private fun play(): List<GlyphPart> = listOf(
        GlyphPart.Polyline(
            listOf(GlyphPoint(8f, 5f), GlyphPoint(19f, 12f), GlyphPoint(8f, 19f)),
            closed = true,
        ),
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
