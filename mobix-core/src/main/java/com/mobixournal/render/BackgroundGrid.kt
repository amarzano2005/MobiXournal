package com.mobixournal.render

/**
 * Pure geometry for page-background rulings — the evenly-spaced line/dot offsets a background
 * style needs, in the page's own units (pt). Kept free of Android types so it is unit-testable on
 * the JVM; [BackgroundRenderer] turns these offsets into canvas draws.
 */
object BackgroundGrid {

    /** pt spacings, approximating desktop Xournal++ paper. Every renderer and [Snapping] uses these. */
    const val RULE_SPACING_PT: Double = 24.0
    const val GRID_SPACING_PT: Double = 14.17 // ~0.5 cm

    /** Default triangle side of an **isometric** sheet (`r1`), 1 cm — the side the app rules with. */
    const val ISO_SPACING_PT: Double = 28.3465

    /** The "ruled" red margin's offset from the left edge, 1 inch in. */
    const val MARGIN_PT: Double = 72.0

    // RGB colours for the background ruling patterns, hex for hex with desktop Xournal++'s paper
    // (red, green, blue components; BackgroundRenderer/PdfBackgroundPainter add the opaque alpha).

    /** Horizontal rule of a **lined/ruled** sheet — desktop's `Colors::xopp_dodgerblue` (`#40A0FF`). */
    const val LINED_RGB: Int = 0x40A0FF

    /** Rule of a **graph** sheet — desktop's `Colors::xopp_silver` (`#BDBDBD`). */
    const val GRAPH_RGB: Int = 0xBDBDBD

    /** Dot of a **dotted** sheet — desktop's `Colors::xopp_silver` (`#BDBDBD`). */
    const val DOT_RGB: Int = 0xBDBDBD

    /**
     * The red margin a **ruled** sheet rules down the page. The app's own addition — desktop's ruled
     * background rules no margin line — so it is the one ruling colour with no upstream hex to match;
     * left as the app's muted red.
     */
    const val MARGIN_RGB: Int = 0xE79B9B

    /**
     * The isometric grid for a page of [width] × [height] pt and a triangle side of [sizePt] — the
     * one geometry desktop Xournal++'s `isograph`/`isodotted` paper uses (`r1` is that side length).
     *
     * It is the two families of lines at ±30° that pass through the lattice of `√3·side/2` by
     * `side/2` cells — desktop draws the diagonals of every one of those cells, which is exactly the
     * union of these lines. Each family's lines differ by `side/2` in their y-intercept, and every
     * line is clipped to the sheet here so nothing outside it is ever drawn.
     *
     * @param width Page width in pt.
     * @param height Page height in pt.
     * @param sizePt Triangle side length (`r1`) in pt.
     * @return The clipped segments, as (x1, y1, x2, y2) quads, ready to stroke.
     */
    fun isometric(width: Double, height: Double, sizePt: Double): List<DoubleArray> {
        if (sizePt <= 0.0 || width <= 0.0 || height <= 0.0) return emptyList()
        val step = sizePt / 2.0
        val root3 = kotlin.math.sqrt(3.0)
        val out = ArrayList<DoubleArray>()
        // Family A: y = x/√3 + c, clipped to the sheet, c stepping by half a triangle.
        var c = -width / root3 - step
        while (c <= height + step) {
            val x0 = maxOf(0.0, -c * root3)
            val x1 = minOf(width, (height - c) * root3)
            if (x1 > x0) out += doubleArrayOf(x0, x0 / root3 + c, x1, x1 / root3 + c)
            c += step
        }
        // Family B: y = -x/√3 + c (the mirror of A).
        c = -step
        while (c <= height + width / root3 + step) {
            val x0 = maxOf(0.0, (c - height) * root3)
            val x1 = minOf(width, c * root3)
            if (x1 > x0) out += doubleArrayOf(x0, -x0 / root3 + c, x1, -x1 / root3 + c)
            c += step
        }
        return out
    }

    /** Interior gridline offsets: `spacing, 2·spacing, …` strictly inside `(0, extent)`. */
    fun lines(extent: Double, spacing: Double): List<Double> {
        if (spacing <= 0.0 || extent <= 0.0) return emptyList()
        val out = ArrayList<Double>(((extent / spacing).toInt()).coerceAtLeast(0))
        var v = spacing
        while (v < extent) {
            out += v
            v += spacing
        }
        return out
    }
}
