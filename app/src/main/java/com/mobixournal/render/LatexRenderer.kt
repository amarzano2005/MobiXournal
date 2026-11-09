package com.mobixournal.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a parsed [LatexNode] tree onto an Android [Canvas] — as the **same layout** the window and the
 * PDF export typeset, [LatexLayout]'s.
 *
 * The tablet used to own a second layout engine here: it measured every run with its own [Paint] and
 * stacked fractions, radicals and scripts by its own gaps, so one formula could sit differently on the
 * tablet, on the PC and in an exported PDF. The geometry is now `mobix-core`'s, fitted to the target
 * rectangle there; this class only paints it — a glyph per [MathGlyph], a line per [MathRule]. It is also
 * why a formula's advances are now the measured ones ([TextAdvance]): there is one layout, and both
 * shells read it.
 *
 * [maxFontSize] stays the caller's ceiling, applied as a **scale** rather than as a per-glyph clamp, so a
 * script keeps being a script when a formula is capped.
 */
class LatexRenderer {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private companion object {
        /** The smallest glyph the painter will draw, in px — a layout shrunk past it is not readable. */
        const val MIN_FONT = 1f

        /** A rule's width as a share of the formula's own size, when the layout states none. */
        const val RULE_SHARE = 0.05f
    }

    /**
     * Paint [node] centred inside [bounds] (px), tinted [color], its type capped at [maxFontSize]. Draws
     * nothing for an empty tree or an empty box.
     */
    fun draw(canvas: Canvas, node: LatexNode, bounds: RectF, color: Int, maxFontSize: Float) {
        if (bounds.width() <= 0f || bounds.height() <= 0f) return
        // The layout's units are the box's: the box's px are handed over as they are, so every distance
        // comes back in px and the two shells cannot disagree about a proportion.
        val layout = LatexLayout.layout(node, bounds.height().toDouble(), bounds.width().toDouble())
        val largest = layout.glyphs.maxOfOrNull { it.size } ?: return
        val scale = min(1.0, maxFontSize / largest).toFloat()
        if (scale <= 0f) return

        textPaint.color = color
        rulePaint.color = color
        rulePaint.strokeWidth = max(1f, layout.rules.firstOrNull()?.widthPt?.toFloat() ?: (largest * RULE_SHARE).toFloat())
        // The layout already sits the formula down the box's height; it starts at the box's left edge and
        // is centred here, which is what the page and the PDF do around the same frame.
        val left = bounds.left + (bounds.width() - layout.widthPt.toFloat() * scale) / 2f
        for (glyph in layout.glyphs) {
            if (glyph.text.isBlank()) continue
            textPaint.textSize = (glyph.size * scale).toFloat().coerceAtLeast(MIN_FONT)
            canvas.drawText(glyph.text, left + glyph.x.toFloat() * scale, bounds.top + glyph.y.toFloat() * scale, textPaint)
        }
        for (rule in layout.rules) {
            canvas.drawLine(
                left + rule.x1.toFloat() * scale,
                bounds.top + rule.y1.toFloat() * scale,
                left + rule.x2.toFloat() * scale,
                bounds.top + rule.y2.toFloat() * scale,
                rulePaint,
            )
        }
    }
}
