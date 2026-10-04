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
