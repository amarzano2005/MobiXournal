/**
 * What the tablet's Select-text slot can say about the page in view, assembled from the two halves that know
 * it: the **process** ([PdfTextStatus], per file) and the **result** the canvas holds (`pdfTextIndex`, per
 * sheet). Kept out of `DrawingSurfaceView.kt` because that file has a recorded length ceiling and this is one
 * question, not the canvas's business.
 */
package com.mobixournal.render

import com.mobixournal.format.model.Background

/**
 * The PDF sheet number the page in view shows, or null when that page shows no PDF at all.
 *
 * It is the page index a PDF's outline names, not the document's own index: a merged import renumbers the
 * pages it appends, so "where am I, in the file's own words?" has to be asked of the background.
 */
fun DrawingSurfaceView.pdfSheetInView(): Int? =
    (doc.pages.getOrNull(currentPageIndex())?.background as? Background.Pdf)?.pageNo

/** The words the extracted layer holds for the sheet in view — 0 on a scan, and on a page with no PDF. */
fun DrawingSurfaceView.pdfWordsOnPageInView(): Int {
    val index = pdfTextIndex ?: return 0
    val sheet = pdfSheetInView() ?: return 0
    return index.words(sheet).size
}

/**
 * The slot's whole answer: the file's phase, and — once the layer is there — how many words **this** sheet
 * holds, which is what separates a digital page from a scan in the same document.
 */
fun DrawingSurfaceView.pdfTextAvailability(): PdfTextAvailability {
    val stored = PdfTextStatus.of(pdfSourceFile())
    return if (stored.phase == PdfTextPhase.READY) stored.copy(wordsOnPage = pdfWordsOnPageInView()) else stored
}
