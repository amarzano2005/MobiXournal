package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.render.LatexLayout
import com.mobixournal.render.LatexParser

/**
 * A `<teximage>` — and a living math block, which compiles to exactly one — painted as **typeset math**
 * rather than skipped.
 *
 * The window drew every other living entity through `LivingInk` and then had no arm for the ink a formula
 * compiles to (`LivingInk` returns a `TexImageElement` carrying the source), so a formula on the page was
 * invisible: present in the file, selectable, and blank on screen. The layout is `mobix-core`'s
 * [LatexLayout] — the same arithmetic the PDF export typesets with — so a formula on the PC and the same
 * formula in a PDF are positioned by one implementation, not two.
 *
 * The element's box is fitted from its **own** source every time: a formula is re-laid-out as the block is
 * resized, and a long one shrinks rather than spilling out of the box it was given. Glyphs are drawn at the
 * baseline the layout asks for, measured by the same [TextMeasurer] the text boxes use.
 */
internal fun DrawScope.drawTexImage(tex: TexImageElement, scale: Float, measurer: TextMeasurer) {
    if (tex.latex.isBlank()) return
    val boxW = (tex.right - tex.left).coerceAtLeast(1.0)
    val boxH = (tex.bottom - tex.top).coerceAtLeast(1.0)
    val layout = LatexLayout.layout(LatexParser.parse(tex.latex), boxH, boxW)
    val ink = Color(tex.color)
    for (glyph in layout.glyphs) {
        if (glyph.text.isBlank()) continue
        val measured = measurer.measure(
            text = glyph.text,
            // [scale] is pixels per point, so the glyph's size is already in px: `.sp` would multiply the
            // display density in again and paint a formula [density] times too large beside its own box
            // (the text boxes had the same defect — see `drawTextBox`).
            style = TextStyle(color = ink, fontSize = (glyph.size * scale).toFloat().toSp()),
        )
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                ((tex.left + glyph.x) * scale).toFloat(),
                ((tex.top + glyph.y) * scale).toFloat() - measured.firstBaseline,
            ),
        )
    }
    for (rule in layout.rules) {
        drawLine(
            color = ink,
            start = Offset(((tex.left + rule.x1) * scale).toFloat(), ((tex.top + rule.y1) * scale).toFloat()),
            end = Offset(((tex.left + rule.x2) * scale).toFloat(), ((tex.top + rule.y2) * scale).toFloat()),
            strokeWidth = (rule.widthPt * scale).toFloat().coerceIn(0.8f, 6f),
        )
    }
}
