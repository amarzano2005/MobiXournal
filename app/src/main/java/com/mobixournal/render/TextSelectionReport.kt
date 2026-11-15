package com.mobixournal.render

/**
 * What the chrome knows about the **PDF text** selection on one pane, all from one paint pass.
 *
 * The bar needs three things the old single box could not answer: the **bands** to hang from (one per line,
 * the same [PdfSelectionSpans] the page is washed with), the band the **endpoint** is on (the line the reader
 * is actually looking at), and the selection's **phase** (whether the range is still being made, which is when
 * the bar stays hidden). Rectangles are in this view's own pixels, like every other report to the chrome.
 *
 * `null` instead of an instance means "nothing is selected": the bar is not mounted at all, rather than
 * mounted over an empty range.
 */
data class TextSelectionReport(
    /** The union of [spans] — kept for readers that only need the passage's overall box. */
    val union: android.graphics.RectF,
    /** The washed line bands, in reading order, as [PdfSelectionSpans] drew them. */
    val spans: List<android.graphics.RectF>,
    /** The band under the focus end of the range, or null when that word cannot be mapped. */
    val active: android.graphics.RectF?,
    val phase: PdfTextActionPhase,
)
