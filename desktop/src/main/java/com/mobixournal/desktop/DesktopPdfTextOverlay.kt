package com.mobixournal.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.mobixournal.render.MagneticHighlighter
import com.mobixournal.render.PdfWord

/**
 * A PDF text selection drawn **over the page**: a wash and a hairline per word box.
 *
 * It is chrome, in the selection's own colour (`DesktopTheme.accent`, the one the marquee's outline
 * wears), and never the page's ink: nothing here is in the document, so `<Esc>` simply drops it and a
 * save writes the file it read. The wash is translucent because a selection must not hide the words it
 * selected.
 *
 * [pxPerPt] is the sheet's own scale (`zoom × density`), so the boxes follow a zoom while the hairline
 * stays one screen pixel — the rule the marquee's handles follow.
 */
internal fun DrawScope.drawPdfTextSelection(words: List<PdfWord>, pxPerPt: Float) {
    for (word in words) {
        val topLeft = Offset((word.left * pxPerPt).toFloat(), (word.top * pxPerPt).toFloat())
        val size = Size(
            ((word.right - word.left) * pxPerPt).toFloat(),
            ((word.bottom - word.top) * pxPerPt).toFloat(),
        )
        if (size.width <= 0f || size.height <= 0f) continue
        drawRect(color = DesktopTheme.accent.copy(alpha = 0.20f), topLeft = topLeft, size = size)
        drawRect(
            color = DesktopTheme.accent.copy(alpha = 0.55f),
            topLeft = topLeft,
            size = size,
            style = Stroke(width = 1f),
        )
    }
}

/**
 * The tab's own text selection, drawn in the page's mapping — the one call a canvas makes.
 *
 * The transform is the sheet's (`SheetLayout`'s viewport and zoom), so the wash follows a zoom exactly as
 * the sheet does; nothing is drawn for another page's selection or for a page with no words.
 */
internal fun DrawScope.drawSelectedPdfText(
    tab: DesktopTab,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    density: Float,
) {
    val picked = tab.pdfTextSelection?.takeIf { it.page == tab.page } ?: return
    val words = DesktopPdfTextSelect.boxes(tab, picked)
    if (words.isEmpty()) return
    withTransform({ translate(offsetX * density, offsetY * density) }) {
        drawPdfTextSelection(words, zoom * density)
    }
}

/**
 * The magnetic highlighter's bands while a drag snaps, at the highlighter's own translucency — a preview
 * of the ink, not chrome.
 */
internal fun DrawScope.drawPdfPreviewBands(bands: List<MagneticHighlighter.Band>, colour: Int, pxPerPt: Float) {
    for (band in bands) {
        drawRect(
            color = Color(colour).copy(alpha = 0.35f),
            topLeft = Offset((band.left * pxPerPt).toFloat(), (band.top * pxPerPt).toFloat()),
            size = Size((band.width * pxPerPt).toFloat(), (band.height * pxPerPt).toFloat()),
        )
    }
}
