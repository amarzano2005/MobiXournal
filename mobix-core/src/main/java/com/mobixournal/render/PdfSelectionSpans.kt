package com.mobixournal.render

/**
 * One line's worth of a PDF text selection, as a **single washed band** in page-local points — the rectangle
 * a shell paints behind the words of one line.
 */
data class PdfSelectionSpan(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
}

/**
 * Turns the selected word boxes into the bands a selection is **drawn** with: one per line, one height per
 * line, the ordinary spaces between contiguous words included.
 *
 * The boxes themselves are the wrong shape for feedback. Painted one per word they read as a row of tiles —
 * a moving block rather than a passage — because every word carries its own ascenders and descenders and its
 * own gap to its neighbour. A line washed as one band, at the union height of the words that line carries,
 * looks like the selection a reader knows from native text: the boundary advances as words are crossed, and
 * nothing about the band's interior moves.
 *
 * Two limits keep it honest, and both are the reason this is a rule rather than a paint call:
 * - **Never across a column.** A gap wider than [SPACE_GAP_RATIO] of the line's height is not a space, so the
 *   band stops at the word and the next one starts a new band on the same line. Two columns on one baseline
 *   are two bands, never one stripe across the gutter.
 * - **Never across unrelated lines.** Words belong to one line while they overlap vertically by at least
 *   [LINE_OVERLAP_RATIO] of the shorter one, so a superscript, a larger first letter or a slightly higher
 *   baseline stays on its line, while the next line down does not.
 *
 * This is presentation only: [PdfTextIndex.rangeText] still joins the words in the extraction's reading order,
 * and no band enters the document, the clipboard or either file format.
 */
object PdfSelectionSpans {

    /** Two words share a line while they overlap vertically by this much of the shorter one. */
    const val LINE_OVERLAP_RATIO = 0.5

    /** A horizontal gap up to this fraction of the line's height reads as an ordinary space. */
    const val SPACE_GAP_RATIO = 1.0

    /** The bands covering [words] (in reading order), one per line segment. */
    fun of(words: List<PdfWord>): List<PdfSelectionSpan> {
        if (words.isEmpty()) return emptyList()
        val spans = ArrayList<PdfSelectionSpan>()
        var line = ArrayList<PdfWord>()
        fun flushLine() {
            if (line.isNotEmpty()) {
                spans += lineSpans(line)
                line = ArrayList()
            }
        }
        for (word in words) {
            if (line.isNotEmpty() && !sharesLine(line.last(), word)) flushLine()
            line.add(word)
        }
        flushLine()
        return spans
    }

    /** True when [word] sits on the same line as [anchor] — vertical overlap, not a top-edge comparison. */
    private fun sharesLine(anchor: PdfWord, word: PdfWord): Boolean {
        val anchorHeight = (anchor.bottom - anchor.top).coerceAtLeast(1.0)
        val wordHeight = (word.bottom - word.top).coerceAtLeast(1.0)
        val overlap = minOf(anchor.bottom, word.bottom) - maxOf(anchor.top, word.top)
        return overlap >= LINE_OVERLAP_RATIO * minOf(anchorHeight, wordHeight)
    }

    /** One line's words as bands: the line's union height, split only where the gap is wider than a space. */
    private fun lineSpans(words: List<PdfWord>): List<PdfSelectionSpan> {
        val top = words.minOf { it.top }
        val bottom = words.maxOf { it.bottom }
        val height = (bottom - top).coerceAtLeast(1.0)
        val spans = ArrayList<PdfSelectionSpan>()
        var left = words.first().left
        var right = words.first().right
        for (i in 1 until words.size) {
            val word = words[i]
            if (word.left - right > SPACE_GAP_RATIO * height) {
                spans += PdfSelectionSpan(left, top, right, bottom)
                left = word.left
            }
            right = maxOf(right, word.right)
        }
        spans += PdfSelectionSpan(left, top, right, bottom)
        return spans
    }
}
