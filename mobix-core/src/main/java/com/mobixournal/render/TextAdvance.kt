package com.mobixournal.render

/**
 * How wide a string is, in em, **per glyph** — the estimate the format's own layer has to use
 * because it holds no glyphs.
 *
 * It replaces the flat "every character is [InkTokens.FIGURE_LABEL_CHAR_W] of the size" guess: a
 * string of `i`s is a quarter the width of the same number of `M`s, and on a table column that one
 * guess put a centred cell several points off its midline in the exports — which are the shells with
 * no font engine to measure with (`[TextWidth]`). A cell of `iii` was estimated 1.86 em against a real
 * 0.76, a formula `x² + 1` 3.72 against 2.74.
 *
 * The numbers are **measured**, not chosen: each glyph's advance was read from the fonts the shells
 * actually draw with — Android's `Sans` (Roboto Regular), the desktop's Inter Regular, and the
 * DejaVu Sans this app bundles and the PDF writer embeds — as the `hmtx` advance over the font's own
 * units per em, and then averaged across the three faces. So a document's cells land in the same
 * place whether it is read on the tablet, in the window, or in a PDF.
 *
 * Text the table does not know is not thereby narrow: an ideograph (CJK, kana, hangul, fullwidth forms)
 * is drawn a full em square, so it is estimated as one, and anything else falls back to
 * [InkTokens.FIGURE_LABEL_CHAR_W].
 */
object TextAdvance {

    /** A glyph the table carries none of: an ideograph fills its em square. */
    private const val IDEOGRAPH_EM = 1.0

    /** From here up, a rune is drawn in a full-width ideograph block rather than in Latin metrics. */
    private const val IDEOGRAPH_START = 0x2E80

    /**
     * Advance per glyph, in em: the mean of Roboto Regular, Inter Regular and DejaVu Sans at the
     * same size, read from each font's own horizontal metrics.
     */
    private val EM: Map<Char, Double> = mapOf(
        ' ' to 0.282, '!' to 0.315, '"' to 0.415, '#' to 0.696, '$' to 0.613, '%' to 0.888,
        '&' to 0.682, '\'' to 0.250, '(' to 0.366, ')' to 0.368, '*' to 0.477, '+' to 0.689,
        ',' to 0.267, '-' to 0.366, '.' to 0.290, '/' to 0.370,
        '0' to 0.610, '1' to 0.535, '2' to 0.603, '3' to 0.605, '4' to 0.615,
        '5' to 0.597, '6' to 0.606, '7' to 0.588, '8' to 0.605, '9' to 0.606,
        ':' to 0.289, ';' to 0.283, '<' to 0.669, '=' to 0.683, '>' to 0.674, '?' to 0.505,
        '@' to 0.955,
        'A' to 0.675, 'B' to 0.654, 'C' to 0.693, 'D' to 0.716, 'E' to 0.600, 'F' to 0.573,
        'G' to 0.734, 'H' to 0.736, 'I' to 0.278, 'J' to 0.472, 'K' to 0.652, 'L' to 0.554,
        'M' to 0.880, 'N' to 0.738, 'O' to 0.746, 'P' to 0.624, 'Q' to 0.746, 'R' to 0.651,
        'S' to 0.623, 'T' to 0.618, 'U' to 0.708, 'V' to 0.670, 'W' to 0.954, 'X' to 0.665,
        'Y' to 0.630, 'Z' to 0.638,
        '[' to 0.340, '\\' to 0.369, ']' to 0.340, '^' to 0.576, '_' to 0.469, '`' to 0.377,
        'a' to 0.573, 'b' to 0.603, 'c' to 0.548, 'd' to 0.604, 'e' to 0.576, 'f' to 0.356,
        'g' to 0.603, 'h' to 0.592, 'i' to 0.254, 'j' to 0.253, 'k' to 0.545, 'l' to 0.254,
        'm' to 0.909, 'n' to 0.592, 'o' to 0.594, 'p' to 0.603, 'q' to 0.605, 'r' to 0.375,
        's' to 0.521, 't' to 0.349, 'u' to 0.592, 'v' to 0.546, 'w' to 0.796, 'x' to 0.544,
        'y' to 0.542, 'z' to 0.524,
        '{' to 0.467, '|' to 0.304, '}' to 0.467, '~' to 0.727,
        // The units and symbols a notebook's cells are full of.
        '°' to 0.443, 'µ' to 0.596, '±' to 0.678, '²' to 0.403, '³' to 0.404,
        '×' to 0.678, '÷' to 0.690, '≈' to 0.688, '≤' to 0.669, '≥' to 0.674,
        '√' to 0.621, '∞' to 0.932, '→' to 0.745, '←' to 0.745, '…' to 0.844,
        '–' to 0.552, '—' to 0.927, 'Ω' to 0.733, 'π' to 0.601, 'α' to 0.629,
        'β' to 0.609, 'γ' to 0.552, 'δ' to 0.587, 'Δ' to 0.690, 'Σ' to 0.631,
    )

    /** The width of [text] in em: the sum of its glyphs' measured advances. */
    fun em(text: String): Double {
        var total = 0.0
        for (ch in text) total += emOf(ch)
        return total
    }

    /** The width of [text] at [sizePt], in the same points its box is measured in. */
    fun pt(text: String, sizePt: Double): Double = em(text) * sizePt

    /** One glyph's advance in em: measured, or the full em of an ideograph, or the flat fallback. */
    fun emOf(ch: Char): Double = EM[ch]
        ?: if (ch.code >= IDEOGRAPH_START) IDEOGRAPH_EM else InkTokens.FIGURE_LABEL_CHAR_W
}
