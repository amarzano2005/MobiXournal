package com.mobixournal.design

/**
 * The pen palette and the three width slots, as **plain Kotlin** — no Compose types and no Android
 * types, so the tablet, the desktop window and any test can read the same rules.
 *
 * This object is the one home of the *values*: the eight factory swatches (desktop Xournal++'s own
 * hexes, in its published order), the palette ceiling, the three default widths and the bounds a width
 * may live in. The *UI* stays with each shell — the tablet's `ColorPaletteRows` / `CustomColorPickerDialog`
 * and the desktop's flyouts — and `AppSettings.penColors` remains the live palette; what both shells
 * must agree on is what a palette *is*, and that is here:
 *
 *  - every entry is opaque ARGB, because a translucent swatch is a colour nobody chose;
 *  - a colour appears once (adding one that is already there selects it, it does not make a twin);
 *  - the palette never exceeds [MAX_COLORS] — past that a swatch grid stops being glanceable;
 *  - an empty palette falls back to the factory list rather than leaving the pickers blank.
 *
 * `AppSettings.sanitized`, `ColorPaletteState.addColor` and the desktop's palette flyout all call
 * [sanitised] / [add] instead of re-spelling the rules, so the two shells cannot drift.
 */
object PenPalette {

    /** Xournal++ palette colour `#000000` (black) — the pen a fresh document starts with. */
    val BLACK: Int = 0xFF000000.toInt()

    /** `#FF0000` (red) — what the "red pen" shortcut switches to. */
    val RED: Int = 0xFFFF0000.toInt()

    /** `#008000` (green). */
    val GREEN: Int = 0xFF008000.toInt()

    /** `#3333CC` (blue). */
    val BLUE: Int = 0xFF3333CC.toInt()

    /** `#FF8000` (orange). */
    val ORANGE: Int = 0xFFFF8000.toInt()

    /** `#FFFF00` (yellow) — the factory highlighter colour. */
    val YELLOW: Int = 0xFFFFFF00.toInt()

    /** `#FF00FF` (magenta) — desktop Xournal++'s own name for this swatch. */
    val MAGENTA: Int = 0xFFFF00FF.toInt()

    /** `#FFFFFF` (white). */
    val WHITE: Int = 0xFFFFFFFF.toInt()

    /**
     * The factory palette: **Black, Red, Green, Blue, Orange, Yellow, Magenta, White**, in that order,
     * each hex transcribed from upstream `palettes/xournal.gpl` so a colour picked on either shell writes
     * the same ARGB as desktop Xournal++. A fresh install and **Restore default** both start from it.
     */
    val COLORS: List<Int> = listOf(BLACK, RED, GREEN, BLUE, ORANGE, YELLOW, MAGENTA, WHITE)

    /**
     * The most colours a palette may hold: room for every desktop swatch plus a working set of one's
     * own, while a picker's swatch grid still fits two or three rows.
     */
    const val MAX_COLORS: Int = 24

    /** Factory defaults for the three width slots, in points — the old fixed S/M/L values. */
    val DEFAULT_WIDTHS: List<Float> = listOf(0.85f, 1.5f, 2.6f)

    /** The labels of the three width slots, smallest first. */
    val WIDTH_LABELS: List<String> = listOf("S", "M", "L")

    /** The thinnest pen width a document may carry, in points. */
    const val WIDTH_MIN: Float = 0.5f

    /** The thickest pen width a document may carry, in points. */
    const val WIDTH_MAX: Float = 15f

    /** Every palette entry is opaque: this is the promotion the rules apply, exposed for a picker. */
    fun opaque(color: Int): Int = color or 0xFF000000.toInt()

    /**
     * Append [color] to [colors]: opaque, de-duplicated (a colour already present is not added twice) and
     * capped at [MAX_COLORS]. An empty input is **not** replaced by the factory list — this is the
     * "user added a colour" path, not a repair.
     */
    fun add(colors: List<Int>, color: Int): List<Int> =
        (colors + opaque(color)).distinct().take(MAX_COLORS)

    /**
     * The palette as it may be stored: opaque, de-duplicated, capped, and the factory list when nothing
     * is left — a picker with no swatch at all would be a picker that cannot pick.
     */
    fun sanitised(colors: List<Int>): List<Int> =
        colors.map(::opaque).distinct().take(MAX_COLORS).ifEmpty { COLORS }

    /** [width] brought into [WIDTH_MIN]..[WIDTH_MAX]. */
    fun clampWidth(width: Float): Float = width.coerceIn(WIDTH_MIN, WIDTH_MAX)

    /**
     * The slot of [widths] that [width] belongs to: the nearest value, the lower slot on a tie.
     *
     * Used to light the S/M/L slot a width is on, and to step from a width that no longer equals any
     * slot exactly (a document written by another build, say).
     */
    fun widthSlot(width: Float, widths: List<Float> = DEFAULT_WIDTHS): Int {
        if (widths.isEmpty()) return 0
        val target = clampWidth(width)
        return widths.indices.minByOrNull { index ->
            val distance = kotlin.math.abs(widths[index] - target)
            // A tie must go to the lower slot, so the index is the tie-breaker rather than an accident
            // of which side `minByOrNull` happened to test first.
            distance * 1000f + index * 0.001f
        } ?: 0
    }
}
