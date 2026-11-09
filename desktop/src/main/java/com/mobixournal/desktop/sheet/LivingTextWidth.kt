package com.mobixournal.desktop.sheet

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.mobixournal.desktop.tools.textFace
import com.mobixournal.render.InkTokens
import com.mobixournal.render.TextWidth

/**
 * The window's font engine, handed to a living table (`TextWidth`).
 *
 * A cell's text is compiled into ink that carries its own `x`, and centring it needs the string's
 * real advance — the per-character estimate `ElementBounds` keeps runs a few points wide, which a
 * narrow column shows as text sitting left of its midline. The tablet has handed its `Paint` to
 * `TableInk` since the tables were fixed there; this is the window's half of the same answer, so a
 * table on the sheet is placed identically on both shells.
 *
 * It measures with the very style [PageView]'s `drawTextBox` draws a cell with: the same face
 * resolved from [InkTokens.FIGURE_LABEL_FONT] (`textFace`), the same pt→px size, and the same
 * density-folding `toSp`. The width comes back in **page points**, because that is the unit a compiled
 * cell is placed in; a multi-line cell measures its widest line. Exports pass no measurer at all
 * (`.xopp`, PDF and SVG have no glyphs to ask), which is why they keep the estimate.
 */
internal fun livingTextWidth(measurer: TextMeasurer, pxPerPt: Float, density: Density): TextWidth {
    val face = textFace(InkTokens.FIGURE_LABEL_FONT)
    fun styleFor(sizePt: Double, bold: Boolean): TextStyle = with(density) {
        TextStyle(
            fontSize = (sizePt * pxPerPt).toFloat().toSp(),
            fontFamily = face.family,
            fontWeight = if (bold) FontWeight.Bold else face.weight,
            fontStyle = face.style,
        )
    }
    return object : TextWidth {
        override fun of(text: String, sizePt: Double, bold: Boolean): Double =
            measurer.measure(text = text, style = styleFor(sizePt, bold)).multiParagraph.width.toDouble() / pxPerPt

        /** Two lines of the same style, so their baselines state the leading the cell is drawn with. */
        override fun lineHeightOf(sizePt: Double, bold: Boolean): Double {
            val layout = measurer.measure(text = "Ag\nAg", style = styleFor(sizePt, bold))
            if (layout.lineCount < 2) return sizePt * InkTokens.FIGURE_LABEL_LINE_H
            return (layout.getLineBaseline(1) - layout.getLineBaseline(0)).toDouble() / pxPerPt
        }
    }
}
