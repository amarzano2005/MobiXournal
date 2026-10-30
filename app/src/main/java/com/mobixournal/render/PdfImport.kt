package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page

/**
 * Builds a fresh [Document] whose pages are backed by the pages of an imported PDF — the same shape
 * desktop Xournal++ produces when you "annotate a PDF": one `.xopp` page per PDF page, each with a
 * `<background type="pdf">` and an empty layer to draw on. Only the first page carries `filename`
 * and `domain`; the rest reference the same PDF by `pageno` alone, matching the on-disk convention
 * (see `docs/pdf.md`). The actual rasterisation is done by [PdfPageCache].
 *
 * [reference] is the string stored as the background's `filename` — on Android that's the source
 * PDF's `content://` URI (domain="absolute"), which the open path resolves back to reload the PDF.
 */
object PdfImport {

    fun documentFor(cache: PdfPageCache, reference: String, indices: IntRange? = null): Document =
        Document(pages = pagesFor(cache, reference, indices = indices))

    /**
     * One `.xopp` page per page of [cache], ready to become a whole document ([documentFor]) or to be
     * appended after an existing document's pages ([PageOps.appendPages]).
     *
     * [reference] is carried by the *first* produced page (with `domain="absolute"`), so these pages
     * normally land as the document's only PDF-backed run. Pass `null` when appending onto a document
     * that already carries the reference — the case where the incoming PDF has been merged into the
     * existing background PDF ([PdfMerger]) and the existing reference is re-pointed at the joined
     * file instead. [pageNoOffset] is then where these pages start inside that joined PDF.
     *
     * [indices] is the run of **source** pages to bring, 0-based; null brings the whole PDF. A `pageno`
     * is always the source page's own number plus [pageNoOffset], never the run's position, so importing
     * pages 5–20 of a scan still draws pages 5–20 of that scan — the same rule the window's import
     * keeps, because it is the page numbering of the file that has to be right, not the run's.
     */
    fun pagesFor(
        cache: PdfPageCache,
        reference: String?,
        pageNoOffset: Int = 0,
        indices: IntRange? = null,
    ): List<Page> {
        val wanted = (indices?.toList() ?: (0 until cache.pageCount).toList())
            .filter { it in 0 until cache.pageCount }
        return wanted.mapIndexed { run, index ->
            val (w, h) = cache.pageSizePt(index)
            Page(
                width = w,
                height = h,
                background = Background.Pdf(
                    filename = if (run == 0) reference else null,
                    pageNo = index + pageNoOffset,
                    domain = if (run == 0 && reference != null) ABSOLUTE_DOMAIN else null,
                ),
                layers = listOf(Layer(emptyList())),
            )
        }
    }
}
