package com.mobixournal.desktop.io
import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.settings.DesktopTheme
import com.mobixournal.desktop.sheet.SheetLayout
import com.mobixournal.desktop.shell.DesktopTab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.mobixournal.design.DesignTokens
import com.mobixournal.render.MagneticHighlighter
import com.mobixournal.render.PdfSelectionSpans
import com.mobixournal.render.PdfWord

/**
 * A PDF text selection drawn **over the page**: one washed band per line, the spaces between its words inside
 * it and no border per word.
 *
 * The geometry is `PdfSelectionSpans`' — shared with the tablet — because the boxes a text layer reports are
 * the wrong shape for feedback: painted one per word they read as a row of tiles, a moving block rather than a
 * passage. A line at its union height looks like the selection a reader knows from native text, and nothing in
 * the band's interior moves as the pointer crosses words: only the boundary advances, with no spring, fade or
 * scale anywhere in this function.
 *
 * The colour is the theme's own selection accent at `DesignTokens.TEXT_SELECTION_WASH_ALPHA`, so both shells
 * wash a passage with one material. It is content feedback, not a floating surface, and never the page's ink:
 * nothing here is in the document, so `<Esc>` simply drops it and a save writes the file it read.
 *
 * [pxPerPt] is the sheet's own scale (`zoom × density`), so the bands follow a zoom exactly as the sheet does.
 */
internal fun DrawScope.drawPdfTextSelection(words: List<PdfWord>, pxPerPt: Float) {
    for (span in PdfSelectionSpans.of(words)) {
        val size = Size((span.width * pxPerPt).toFloat(), (span.height * pxPerPt).toFloat())
        if (size.width <= 0f || size.height <= 0f) continue
        drawRect(
            color = DesktopTheme.accent.copy(alpha = DesignTokens.TEXT_SELECTION_WASH_ALPHA),
            topLeft = Offset((span.left * pxPerPt).toFloat(), (span.top * pxPerPt).toFloat()),
            size = size,
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
