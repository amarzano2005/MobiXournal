package com.mobixournal.render

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import java.io.OutputStream

/**
 * Writes a [Document] to a PDF as **vector** content, the shape the window writes: every page is
 * compiled by `PdfPagePlan` into an ordered list of ops and replayed by [PdfVectorPainter] through the
 * Android port of PDFBox, so the two shells cannot print different pages. A `pdf`-backed page whose
 * source is available ([pdfSource].source) has the untouched source page imported verbatim and only the
 * vector overlay written over it, which keeps its own vectors and its text layer. Everything else is a
 * fresh sheet whose ruling is vector ([PdfBackgroundPainter]), with a `pixmap` background's picture
 * embedded over it ([pixmapBackground]). Nothing is rasterised except bitmaps that were already raster
 * — user-placed images and image backgrounds.
 *
 * Rotated source pages (`/Rotate` 90/180/270) are handled: the overlay is authored in the page's
 * visual space, so a [PdfOverlayMatrix] pre-transforms the content stream into the page's unrotated
 * space and the viewer's `/Rotate` cancels back to the drawn position (see [preservedPage]).
 *
 * The ink is **page content only**: no `/Highlight`, `/Ink` or `/Text` annotation dictionaries are
 * written, exactly as the window writes it, so both shells produce the same file. The marks are still
 * visible in every viewer because they are paint; a reader's annotation pane stays empty.
 */
internal class PdfExporter(
    private val pdfSource: PdfPageCache?,
    private val imageSource: ImageBackgroundCache? = null,
) {

    private val painter = PdfVectorPainter()

    fun export(doc: Document, out: OutputStream) {
        val source = pdfSource?.source?.let { runCatching { PDDocument.load(it) }.getOrNull() }
        val outDoc = PDDocument()
        try {
            // A chunked document is written as the pages its parts stand for: a PDF reader can only
            // open pages, and the sheet slicing a board would need is not this exporter's step.
            doc.pages.forEach { page -> writePage(outDoc, source, page) }
            outDoc.save(out)
        } finally {
            outDoc.close()
            source?.close()
        }
    }

    private fun writePage(outDoc: PDDocument, source: PDDocument?, page: Page) {
        val bg = page.background
        if (bg is Background.Pdf && source != null && bg.pageNo in 0 until source.numberOfPages) {
            preservedPage(outDoc, source, bg.pageNo, page)
        } else {
            freshPage(outDoc, page)
        }
    }

    /** Import the original page's vector content unchanged, then append the ink overlay. */
    private fun preservedPage(outDoc: PDDocument, source: PDDocument, pageNo: Int, page: Page) {
        val pdPage = outDoc.importPage(source.getPage(pageNo))
        val crop = pdPage.cropBox
        // importPage copies the source /Rotate; map the visual-space overlay into unrotated content
        // space so the viewer's rotation lands it where it was drawn. For /Rotate 0 this is just the
        // crop-origin shift, so the per-point transform runs at the origin.
        val m = PdfOverlayMatrix.forPage(crop.lowerLeftX, crop.lowerLeftY, crop.width, crop.height, pdPage.rotation)
        val transform = PdfPageTransform(0f, 0f, m.visualHeight)
        PDPageContentStream(outDoc, pdPage, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.transform(Matrix(m.a, m.b, m.c, m.d, m.e, m.f))
            painter.paint(outDoc, cs, page, transform)
        }
    }

    /** No source PDF page: a fresh sheet with a vector background plus the ink overlay. */
    private fun freshPage(outDoc: PDDocument, page: Page) {
        val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
        outDoc.addPage(pdPage)
        val transform = PdfPageTransform(0f, 0f, page.height.toFloat())
        PDPageContentStream(outDoc, pdPage).use { cs ->
            PdfBackgroundPainter.draw(outDoc, cs, page, transform)
            pixmapBackground(outDoc, cs, page, transform)
            painter.paint(outDoc, cs, page, transform)
        }
    }

    /**
     * An image-backed page: embed the decoded picture over the sheet fill, stretched to the page box
     * exactly as the editor blits it. Decoded at [EXPORT_SCALE]× the page's point size so the flatten
     * keeps print-usable detail; a picture that can't be decoded leaves the bare sheet behind.
     */
    private fun pixmapBackground(
        outDoc: PDDocument,
        cs: PDPageContentStream,
        page: Page,
        t: PdfPageTransform,
    ) {
        val bg = page.background as? Background.Pixmap ?: return
        val width = (page.width * EXPORT_SCALE).toInt()
        val bmp = imageSource?.render(bg.filename, width) ?: return
        runCatching {
            val xobj = LosslessFactory.createFromImage(outDoc, bmp)
            cs.drawImage(xobj, t.x(0.0), t.y(page.height), page.width.toFloat(), page.height.toFloat())
        }
    }

    private companion object {
        /** Points→pixels for the exported background: ~150 dpi, enough to print without bloating. */
        const val EXPORT_SCALE = 2.0
    }
}
