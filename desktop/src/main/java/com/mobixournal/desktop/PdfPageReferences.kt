package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page

/**
 * The document's **one PDF reference**, and the pages that name it only by number.
 *
 * A document this window imports — and any `.xopp` desktop Xournal++ writes — records the PDF on **one**
 * page (`filename` with `domain="absolute"`, or a bundled `asset` in a `.mobix`) and every other pdf page
 * carries `pageno` alone; that shape is `docs/pdf.md`'s, and desktop reads it by inheritance. The window
 * used to read each page on its own, which is why a three-page import painted its first sheet and left the
 * rest blank: pages 2..N name no file at all.
 *
 * Kept out of [PageBackdrop] so the rule is testable by itself, and holds three answers:
 *
 *  - which page carries a reference ([of]),
 *  - what a page that only names a number resolves to ([resolve]),
 *  - what a **delete** must do so removing the page that holds the reference does not take the PDF out of
 *    the document ([keep]).
 */
internal object PdfPageReferences {

    /** Whether this background names the PDF itself, rather than one page of one. */
    fun Background.Pdf.carriesSource(): Boolean = bundledAsset != null || !filename.isNullOrBlank()

    /**
     * [background] as it should be drawn, with the document's reference folded in — null when the page
     * names no PDF of its own and the document has none to lend it.
     *
     * The page's own `pageNo` is kept: the reference says *which file*, the page says *which sheet of it*.
     */
    fun resolve(background: Background.Pdf, reference: Background.Pdf?): Background.Pdf? = when {
        background.carriesSource() -> background
        reference == null -> null
        else -> reference.copy(pageNo = background.pageNo)
    }

    /**
     * The background that carries the reference of [pages], or null for a document with no PDF background.
     *
     * A `.mobix`'s page list is a lazy view, so this stops at the first page that answers — which for the
     * shape an import writes is the first page — instead of walking the document.
     */
    fun of(pages: List<Page>): Background.Pdf? = pages.firstNotNullOfOrNull { page ->
        (page.background as? Background.Pdf)?.takeIf { it.carriesSource() }
    }

    /**
     * [after] with [before]'s reference carried over, when a structural edit dropped the page that held it.
     *
     * The reference moves to the first pdf page that survives. A document that has no pdf page left, or
     * that already carries a reference, comes back untouched — this never invents a PDF and never
     * overwrites one with another.
     */
    fun keep(before: List<Page>, after: List<Page>): List<Page> {
        val reference = of(before) ?: return after
        if (of(after) != null) return after
        val heir = after.indexOfFirst { it.background is Background.Pdf }
        if (heir < 0) return after
        return after.mapIndexed { index, page ->
            if (index != heir) {
                page
            } else {
                val background = page.background as Background.Pdf
                page.copy(
                    background = background.copy(
                        filename = reference.filename,
                        domain = reference.domain,
                        bundledAsset = reference.bundledAsset,
                    ),
                )
            }
        }
    }
}
