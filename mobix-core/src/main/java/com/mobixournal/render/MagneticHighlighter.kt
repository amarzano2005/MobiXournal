package com.mobixournal.render

import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import kotlin.math.abs

/**
 * The **magnetic highlighter**: a highlighter drag that crosses a PDF's text layer is replaced by a
 * flat band sitting on the line it crossed, instead of keeping the wavy freehand path.
 *
 * It works on what the text layer already gives this app — [PdfWord] boxes in page-local points
 * (`PdfTextIndex`) — so no OCR and no font metrics are involved: a word's box *is* its ascenders and
 * descenders, which is exactly the height a highlighter band should have.
 *
 * The rules are deliberately conservative. A drag that is more vertical than horizontal, or that
 * leaves the text it started on, is the user marking a margin rather than a sentence, so it stays
 * freehand: [bands] answers an empty list and the canvas commits the stroke as drawn. Snapping is a
 * convenience, never a correction the user did not ask for.
 *
 * Pure: page points and word boxes in, geometry out. The canvas calls [strokes] at lift-off and appends
 * them in the same gesture, so a magnetic highlight is still one undo step.
 */
object MagneticHighlighter {

    /** Shorter drags are a tap or a flick, not a highlight. */
    const val MIN_LENGTH_PT: Double = 8.0

    /** A drag steeper than this (|dy| / |dx|) is vertical marking and keeps its freehand shape. */
    const val MAX_SLOPE: Double = 0.6

    /** Air above and below a line's glyph box, so the band covers ascenders and descenders. */
    const val BAND_PAD_PT: Double = 1.5

    /** How far (pt) the pen's centre may sit from a word's centre line and still claim it. */
    const val REACH_PAD_PT: Double = 3.0

    /** More lines than this means the drag crossed a paragraph; it stays freehand. */
    const val MAX_LINES: Int = 3

    /** One line's highlight band, in page-local points. */
    data class Band(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        val centerY: Double get() = (top + bottom) / 2.0
        val height: Double get() = bottom - top
        val width: Double get() = right - left
    }

    /**
     * The bands [points] would snap to, or an empty list when the drag is not a horizontal highlight
     * over text.
     *
     * Words are claimed when the stroke's own y at the word's centre lies within the word's height
     * (plus [REACH_PAD_PT]); claimed words are then clustered by line, and each cluster becomes one
     * band covering the union of its glyph boxes.
     */
    fun bands(points: List<StrokePoint>, words: List<PdfWord>): List<Band> {
        if (words.isEmpty()) return emptyList()
        val usable = points.filter { it.x.isFinite() && it.y.isFinite() }
        if (usable.size < 2) return emptyList()
        val dx = usable.last().x - usable.first().x
        val dy = usable.last().y - usable.first().y
        if (abs(dx) < MIN_LENGTH_PT) return emptyList()
        if (abs(dy) > abs(dx) * MAX_SLOPE) return emptyList()
        val minX = usable.minOf { it.x }
        val maxX = usable.maxOf { it.x }

        val claimed = words.filter { word ->
            val centreX = word.centerX
            if (centreX < minX - 1.0 || centreX > maxX + 1.0) return@filter false
            val strokeY = yAt(usable, centreX) ?: return@filter false
            abs(strokeY - word.centerY) <= (word.bottom - word.top) / 2.0 + REACH_PAD_PT
        }
        if (claimed.isEmpty()) return emptyList()

        val lines = mutableListOf<MutableList<PdfWord>>()
        for (word in claimed.sortedBy { it.top }) {
            val line = lines.firstOrNull { group ->
                abs(group.map { it.centerY }.average() - word.centerY) <=
                    maxOf(6.0, (word.bottom - word.top) * 0.75)
            }
            if (line != null) line += word else lines += mutableListOf(word)
        }
        if (lines.size > MAX_LINES) return emptyList()

        return lines.mapNotNull { group ->
            val band = Band(
                left = group.minOf { it.left } - 1.0,
                top = group.minOf { it.top } - BAND_PAD_PT,
                right = group.maxOf { it.right } + 1.0,
                bottom = group.maxOf { it.bottom } + BAND_PAD_PT,
            )
            band.takeIf { it.width >= 4.0 }
        }
    }

    /**
     * [bands] as the strokes the canvas commits: one constant-width, two-point stroke per line, the
     * width the line's own height. The highlighter's multiply rendering and stored alpha then apply
     * exactly as they do to a freehand band, so a snapped highlight round-trips like any other ink.
     */
    fun strokes(
        points: List<StrokePoint>,
        words: List<PdfWord>,
        color: Int,
        tool: Tool = Tool.HIGHLIGHTER,
    ): List<Stroke> = bands(points, words).map { band ->
        Stroke(
            tool = tool,
            color = color,
            capStyle = "round",
            points = listOf(
                StrokePoint(band.left, band.centerY, band.height),
                StrokePoint(band.right, band.centerY, band.height),
            ),
            uniformWidth = true,
        )
    }

    /** The stroke's own y at [x] (linear between its vertices), or null outside its x-range. */
    private fun yAt(points: List<StrokePoint>, x: Double): Double? {
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val lo = minOf(a.x, b.x)
            val hi = maxOf(a.x, b.x)
            if (x !in lo..hi) continue
            val span = b.x - a.x
            if (abs(span) < 1e-6) return (a.y + b.y) / 2.0
            return a.y + (b.y - a.y) * ((x - a.x) / span)
        }
        return null
    }
}
