package com.mobixournal.desktop

import com.mobixournal.format.model.Page
import java.io.File

/**
 * The window's **page clipboard**: whole pages, beside the elements' own ([DesktopClipboard]).
 *
 * One per window rather than per tab, because copying a page out of one document to paste it into
 * another is the whole point — and the pages are immutable model values, so the clipboard shares their
 * structure instead of copying ink.
 *
 * [pdfSource] is the file the copied pages' PDF backgrounds name, or null when none of them sits on a
 * PDF. A page's `Background.Pdf` is a reference into one document's file (`docs/pdf.md`), so a paste
 * into a *different* document has to be refused rather than leave a page pointing at bytes the target
 * does not carry — that is [DesktopPages.lastPageRefusal]'s sentence.
 */
internal class DesktopPageClipboard {

    /** The pages a copy took, in document order. */
    var pages: List<Page> = emptyList()
        private set

    /** The PDF the copied pages came from, when at least one of them sits on one. */
    var pdfSource: File? = null
        private set

    val isEmpty: Boolean get() = pages.isEmpty()

    /** Adopt [pages] as the clipboard's content — the copy command's one write. */
    fun fill(pages: List<Page>, pdfSource: File?) {
        this.pages = pages
        this.pdfSource = pdfSource
    }

    /** Drop the copies: a cut that could not remove its pages must not leave a half-done clipboard. */
    fun clear() {
        pages = emptyList()
        pdfSource = null
    }
}
