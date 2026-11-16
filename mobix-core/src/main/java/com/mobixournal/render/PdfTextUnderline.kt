package com.mobixournal.render

import com.mobixournal.format.model.LineStyle
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool

/**
 * The **underline** a selected PDF passage becomes: one ordinary pen rule under each line of the words the
 * reader chose.
 *
 * Underline is the second thing a reader wants to do with a passage after highlighting it, and — like the
 * highlight — it is not a PDF annotation object but **ink**. The rule is a two-point [Stroke] of [Tool.PEN] with
 * a constant width, a plain line style and round caps, written on the page's active layer by the shell, so it
 * saves in `.mobix` and in `.xopp`, reopens as the stroke it is, and reaches an exported PDF through the
 * ordinary stroke path (`PdfVectorPainter`). Nothing here adds an element, an attribute or a wire format.
 *
 * The geometry is derived from what the text layer already reports, never from font metrics:
 * - **One rule per line segment.** [PdfSelectionSpans.of] — the same grouping the selection's wash is painted
 *   from — supplies the bands, so a five-line paragraph gets five rules, two columns on one baseline get two,
 *   and nothing is ever drawn across a gutter. That grouping is the *selection's* approach; unlike a magnetic
 *   highlighter drag there is no three-line cap, because a selection may legitimately run down a page.
 * - **Below the words, not through them.** Each rule sits [GAP_PT] under its own band's bottom edge, so a
 *   descender is not struck through and a cropped or rotated sheet needs no extra transform: the word boxes are
 *   already in the visual page frame the ink lives in, and the rule is placed from them.
 * - **A documented pen policy.** Colour and width are the **pen's** — the palette colour and the S/M/L slot the
 *   user has in hand (`AppSettings.penColors`/`penWidths`, `DesktopInks.pen`) — not the highlighter's, and a
 *   width below [MIN_PEN_WIDTH_PT] is clamped rather than written as a hairline the format cannot keep.
 *
 * Pure: word boxes in, geometry out. A shell commits the strokes as one page edit and one undo step.
 */
object PdfTextUnderline {

    /** How far under a line's glyph box the rule is drawn, in page points. */
    const val GAP_PT: Double = 1.0

    /** A band narrower than this is not worth a rule — a speck of a selection still has to be visible. */
    const val MIN_WIDTH_PT: Double = 3.0

    /** The thinnest width a rule is written at, in page points — the pen palette's own floor. */
    const val MIN_PEN_WIDTH_PT: Double = 0.5

    /** One line segment's rule: from [left] to [right] at [y], all in page-local points. */
    data class Rule(val left: Double, val right: Double, val y: Double) {
        val width: Double get() = right - left
    }

    /**
     * The rules [words] form, in reading order: one per line segment, just under that segment's own bottom edge.
     *
     * Two columns on one baseline are two rules, because [PdfSelectionSpans] refuses to bridge a gap wider than
     * a space; a superscript stays on its line for the same reason it stays in the band's union height.
     */
    fun rules(words: List<PdfWord>): List<Rule> = PdfSelectionSpans.of(words).mapNotNull { span ->
        Rule(span.left, span.right, span.bottom + GAP_PT).takeIf { it.width >= MIN_WIDTH_PT }
    }

    /**
     * [rules] as the strokes a shell commits: one constant-width [Tool.PEN] stroke per line, [color] and
     * [widthPt] the pen's own, [widthPt] clamped at [MIN_PEN_WIDTH_PT].
     */
    fun strokes(
        words: List<PdfWord>,
        color: Int,
        widthPt: Double,
        tool: Tool = Tool.PEN,
    ): List<Stroke> {
        val width = widthPt.coerceAtLeast(MIN_PEN_WIDTH_PT)
        return rules(words).map { rule ->
            Stroke(
                tool = tool,
                color = color,
                capStyle = "round",
                points = listOf(
                    StrokePoint(rule.left, rule.y, width),
                    StrokePoint(rule.right, rule.y, width),
                ),
                uniformWidth = true,
                lineStyle = LineStyle.PLAIN,
            )
        }
    }
}
