/**
 * The `<background … config="…">` attribute: desktop Xournal++'s paper parameters, and this app's
 * one home for **custom ruling spacing, margins and line widths**.
 *
 * The attribute is a comma-separated `key=value` list (`BackgroundConfig.cpp` in desktop splits on
 * `,` and each entry at its *last* `=`), so the parameters live beside anything else the desktop (or
 * a newer version) has put there. That is why this is an **ordered map** rather than a struct: an
 * unknown key — a foreground colour (`f1`), a page-template parameter we don't model, anything a
 * future desktop writes — must survive load → save untouched, exactly like an element's `extraAttrs`.
 *
 * The keys we interpret are desktop's own (`BackgroundConfig.h`):
 * `r1` spacing (line spacing for lined/ruled, square size for graph/dotted, **triangle side for the
 * isometric styles**), `m1` margin, `lw` line width, `bli`/`blw` bold-line interval and width. Distances
 * are in **pt**; an absent key means "desktop's default for this style", which is what the app has
 * always drawn ([BackgroundGrid]'s constants).
 *
 * Kept Android-free, so parsing, formatting and the default fallbacks are unit-tested on the JVM.
 */
package com.mobixournal.render

import java.util.Locale

/** An ordered `key=value` view of a background's `config` attribute (unknown keys preserved). */
class BackgroundRuling private constructor(private val entries: List<Pair<String, String>>) {

    /** The raw value bound to [key], or null when the attribute doesn't name it. */
    fun value(key: String): String? = entries.firstOrNull { it.first == key }?.second

    /** [key] read as a number in pt, or null when absent or unparsable. */
    fun number(key: String): Double? = value(key)?.toDoubleOrNull()

    /** [key] read as an integer, or null when absent or unparsable. */
    fun int(key: String): Int? = value(key)?.toDoubleOrNull()?.toInt()

    /**
     * This ruling with [key] set to [pt], or **removed** when [pt] is null. A set key is updated in
     * place so the attribute's existing order (and the keys around it) is preserved.
     */
    fun withPt(key: String, pt: Double?): BackgroundRuling {
        val kept = entries.filterNot { it.first == key }
        if (pt == null) return BackgroundRuling(kept)
        val text = BackgroundRuling.format(pt)
        val at = entries.indexOfFirst { it.first == key }
        val out = kept.toMutableList()
        out.add(if (at < 0) kept.size else at.coerceAtMost(out.size), key to text)
        return BackgroundRuling(out)
    }

    /** This ruling with an integer [key] set to [n], or removed when [n] is null. */
    fun withInt(key: String, n: Int?): BackgroundRuling = withPt(key, n?.toDouble())

    /** The attribute value, or null when nothing is left to write (so the attribute is dropped). */
    fun text(): String? =
        if (entries.isEmpty()) null else entries.joinToString(",") { (k, v) -> "$k=$v" }

    override fun toString(): String = text() ?: ""

    companion object {
        /** `r1`: line spacing / square size, in pt. */
        const val KEY_SPACING = "r1"

        /** `m1`: margin, in pt. */
        const val KEY_MARGIN = "m1"

        /** `lw`: ruling line width, in pt. */
        const val KEY_LINE_WIDTH = "lw"

        /** `bli`: draw every Nth line bold. */
        const val KEY_BOLD_INTERVAL = "bli"

        /** `blw`: bold line width, in pt. */
        const val KEY_BOLD_WIDTH = "blw"

        /** The empty ruling (no parameters at all — desktop defaults apply). */
        val EMPTY = BackgroundRuling(emptyList())

        /**
         * Parse a `config` attribute. Unparsable fragments (no `=`) and empty keys are dropped, as
         * desktop drops them; every other entry is kept **in the order it was written**.
         */
        fun parse(config: String?): BackgroundRuling {
            if (config.isNullOrBlank()) return EMPTY
            val out = ArrayList<Pair<String, String>>()
            for (part in config.split(',')) {
                val at = part.lastIndexOf('=')
                if (at <= 0) continue
                val key = part.substring(0, at).trim()
                if (key.isEmpty() || key.any { it.isWhitespace() || it == '=' }) continue
                out += key to part.substring(at + 1).trim()
            }
            return BackgroundRuling(out)
        }

        /**
         * A number as desktop parses it back (`std::istringstream >> double`): at most three decimals,
         * with trailing zeros and a trailing dot trimmed, so a 5 mm grid writes `r1=14.173` rather
         * than `r1=14.173228346456693` and a round value stays readable.
         */
        fun format(pt: Double): String {
            val rounded = String.format(Locale.US, "%.3f", pt)
            val trimmed = rounded.trimEnd('0').trimEnd('.')
            return if (trimmed.isEmpty() || trimmed == "-") "0" else trimmed
        }
    }
}

/**
 * The defaults and the derived numbers a background style draws with, so the renderer, the SVG
 * writer and the PDF flatten all resolve a page's ruling the same way.
 */
object BackgroundRulings {

    /**
     * Spacing in pt for [style] when the page names none: the desktop-parity constants the app has
     * always ruled with — a 24 pt line spacing, a 5 mm (14.17 pt) grid.
     */
    fun defaultSpacingPt(style: String): Double = when (style) {
        "graph", "dotted" -> BackgroundGrid.GRID_SPACING_PT
        "isograph", "isodotted" -> BackgroundGrid.ISO_SPACING_PT
        else -> BackgroundGrid.RULE_SPACING_PT
    }

    /** Spacing in pt for [style]: the page's own `r1`, else the style's default. */
    fun spacingPt(style: String, ruling: BackgroundRuling): Double =
        ruling.number(BackgroundRuling.KEY_SPACING)?.takeIf { it > 0.0 } ?: defaultSpacingPt(style)

    /** The page's `m1` margin in pt, or null when it names none. */
    fun marginPt(ruling: BackgroundRuling): Double? =
        ruling.number(BackgroundRuling.KEY_MARGIN)?.takeIf { it > 0.0 }

    /** Ruling stroke width in pt: the page's `lw`, else 1 pt — and 1.5 pt for the ruled margin line. */
    fun lineWidthPt(ruling: BackgroundRuling, base: Double = 1.0): Double =
        ruling.number(BackgroundRuling.KEY_LINE_WIDTH)?.takeIf { it > 0.0 } ?: base

    /** Every how many lines the ruling goes bold (desktop's `bli`), or null for a uniform ruling. */
    fun boldInterval(ruling: BackgroundRuling): Int? =
        ruling.int(BackgroundRuling.KEY_BOLD_INTERVAL)?.takeIf { it > 0 }

    /** The bold line's width in pt: the page's `blw`, else 1.5× the normal line width. */
    fun boldWidthPt(ruling: BackgroundRuling, lineWidthPt: Double): Double =
        ruling.number(BackgroundRuling.KEY_BOLD_WIDTH)?.takeIf { it > 0.0 } ?: lineWidthPt * 1.5

    /** True when [index] (0-based line number) is a bold one under [interval]. */
    fun isBold(index: Int, interval: Int?): Boolean = interval != null && index % interval == 0
}
