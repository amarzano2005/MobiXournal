package com.mobixournal.desktop.shell
import com.mobixournal.desktop.settings.DesktopTheme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform

/**
 * The **current search match** on the page: a wash and a hairline over the boxes the engine found.
 *
 * Chrome, in the selection's own accent and at the same translucency the PDF text selection uses — the
 * boxes describe where words are, they are not words, and a highlight that hid the text under it would
 * make the search useless exactly where it worked. Nothing here is written to the document: a search is
 * a reading, so `Esc` (or another query) drops the paint and the file is untouched.
 *
 * [pxPerPt] is the sheet's own scale (`zoom × density`), so the boxes follow a zoom while the hairline
 * stays one screen pixel — the rule the marquee's handles and the text selection follow.
 */
internal fun DrawScope.drawSearchHit(
    tab: DesktopTab,
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    density: Float,
) {
    // A match on another page is not drawn: the paint belongs to the sheet in view, and a page turn is
    // what `Enter` does next anyway.
    val hit = tab.search.hit?.takeIf { it.pageIndex == tab.page } ?: return
    if (hit.boxes.isEmpty()) return
    val pxPerPt = zoom * density
    withTransform({ translate(offsetX * density, offsetY * density) }) {
        for (box in hit.boxes) {
            val topLeft = Offset((box.left * pxPerPt).toFloat(), (box.top * pxPerPt).toFloat())
            val size = Size(
                ((box.right - box.left) * pxPerPt).toFloat(),
                ((box.bottom - box.top) * pxPerPt).toFloat(),
            )
            if (size.width <= 0f || size.height <= 0f) continue
            drawRect(color = DesktopTheme.accent.copy(alpha = 0.20f), topLeft = topLeft, size = size)
            drawRect(
                color = DesktopTheme.accent.copy(alpha = 0.75f),
                topLeft = topLeft,
                size = size,
                style = Stroke(width = 1f),
            )
        }
    }
}
