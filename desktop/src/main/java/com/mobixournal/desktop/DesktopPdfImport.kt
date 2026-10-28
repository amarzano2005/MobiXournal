package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.render.ABSOLUTE_DOMAIN
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument

/**
 * **A PDF as pages to write on** — the PC's half of the tablet's `render/PdfImport`.
 *
 * Importing a PDF is not opening it: a PDF is not a document in this app, it is the **background** a page
 * sits on. So an import produces the shape desktop Xournal++ itself produces for an annotated PDF — one
 * page per PDF page, each a `<background type="pdf">`, the sheet the size of the PDF page. Only the first
 * page carries the reference (`filename` with `domain="absolute"`); the rest name it by `pageno` alone,
 * which is the on-disk convention `docs/pdf.md` owns and the reason a `.xopp` can hold exactly one PDF.
 *
 * The tablet's twin asks a `PdfPageCache` for the page count and the page sizes, and that cache is the
 * only Android-shaped thing in it. Here the numbers come from the desktop's own PDFBox, so this file is a
 * plain reader over a `PDDocument` and nothing else: no raster, no cache, no window. What paints the
 * pages afterwards is [PageBackdrop], which already resolves an absolute reference.
 */
internal object DesktopPdfImport {

    /** Open [file] as a PDF, or refuse it in words a reader can act on. */
    fun open(file: File): PDDocument = try {
        PDDocument.load(file).also {
            if (it.numberOfPages == 0) {
                it.close()
                throw PdfImportRefused(
                    "“${file.name}” has no pages, so there is nothing to write on. Pick another PDF.",
                )
            }
        }
    } catch (refused: PdfImportRefused) {
        throw refused
    } catch (failure: Throwable) {
        throw PdfImportRefused(
            "“${file.name}” could not be read as a PDF. A protected or damaged file has to be " +
                "unlocked or re-exported before it can be imported.",
            failure,
        )
    }

    /**
     * One page per PDF page of [pdf], ready to become a whole document ([document]) or to be appended
     * after an existing one ([com.mobixournal.render.PageOps.appendPages]).
     *
     * [reference] is what the background records as its `filename` — the PDF's own path — and only the
     * first page **of the run** carries it. [pageNoOffset] is where these pages start inside that file: a
     * merge that joined an arriving PDF after the document's own passes the base's page count, so every
     * `pageno` still points at the page it was made from.
     *
     * [indices] is the run of **source** pages to bring, 0-based; null brings the whole PDF. A `pageno` is
     * always the source page's own number plus [pageNoOffset], never the run's position, so importing pages
     * 5–20 of a scan still draws pages 5–20 of that scan.
     */
    fun pages(
        pdf: PDDocument,
        reference: String?,
        pageNoOffset: Int = 0,
        indices: IntRange? = null,
    ): List<Page> {
        val wanted = (indices?.toList() ?: (0 until pdf.numberOfPages).toList())
            .filter { it in 0 until pdf.numberOfPages }
        return wanted.mapIndexed { run, index ->
            val page = pdf.getPage(index)
            val box = page.mediaBox
            // A rotated page is shown, and drawn on, the way the reader turns it: the sheet follows the
            // rotation rather than standing the writing on its side.
            val quarterTurn = page.rotation % 180 != 0
            Page(
                width = (if (quarterTurn) box.height else box.width).toDouble(),
                height = (if (quarterTurn) box.width else box.height).toDouble(),
                background = Background.Pdf(
                    filename = if (run == 0) reference else null,
                    pageNo = index + pageNoOffset,
                    domain = if (run == 0 && reference != null) ABSOLUTE_DOMAIN else null,
                ),
                layers = listOf(Layer(emptyList())),
            )
        }
    }

    /** The whole document an import makes: the PDF's pages ([indices], or all), and nothing else. */
    fun document(pdf: PDDocument, reference: String, indices: IntRange? = null): Document =
        Document(pages = pages(pdf, reference, indices = indices))

    /** How many pages [file] has, without building anything — the question card's readout. */
    fun pageCount(file: File): Int = open(file).use { it.numberOfPages }

    /** A PDF this window cannot turn into pages, said in a sentence the window can show. */
    class PdfImportRefused(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * What a merge adds to the first page: the reference re-pointed at the joined PDF.
     *
     * The document keeps one PDF reference (`docs/pdf.md`), so after joining, the page that carries it
     * names the joined file — `domain="absolute"`, since that file lives wherever the window left it.
     */
    fun retarget(pages: List<Page>, joined: File): List<Page> = pages.mapIndexed { index, page ->
        val background = page.background
        if (index == 0 && background is Background.Pdf) {
            page.copy(
                background = background.copy(filename = joined.absolutePath, domain = ABSOLUTE_DOMAIN),
            )
        } else {
            page
        }
    }
}
