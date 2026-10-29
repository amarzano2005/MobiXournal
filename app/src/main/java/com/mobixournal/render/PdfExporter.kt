package com.mobixournal.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.OutputStream

/**
 * Writes a [Document] to a **normal** PDF: one page per page, each painted exactly as the canvas shows
 * it — the sheet, its ruling or its PDF/image background, and every layer of ink — into one image at
 * [EXPORT_SCALE]× the page's point size (~150 dpi), embedded in a page of the page's own size.
 *
 * This is the export shape the app settled on (2026-10-29, recorded in `docs/pdf.md`): what leaves is
 * what the reader sees, so a page can never disagree with itself between the tablet and the file. The
 * vector path it replaces — `PdfPagePlan` replayed by `PdfVectorPainter`, with an imported PDF page
 * kept as its original content stream — is gone from the app; the plan itself stays in `mobix-core`
 * because the window still emits it.
 *
 * The PDF dictionaries beside the picture are unchanged: `/Highlight` over a PDF's words, `/Ink` for
 * handwriting and `/Text` notes are real annotations over the flattened sheet, so a viewer that lists
 * annotations still finds them. A page with no PDF behind it gets none.
 */
internal class PdfExporter(
    private val pdfSource: PdfPageCache?,
    private val imageSource: ImageBackgroundCache? = null,
    /**
     * The imported PDFs' positioned text layer, when the editor holds one. It is what turns a
     * highlighter over text into a `/Highlight` annotation instead of a generic ink mark; without it
     * the export is unchanged apart from the `/Ink` and `/Text` dictionaries.
     */
    private val textIndex: PdfTextIndex? = null,
    /**
     * The page→picture step, defaulted to the canvas's own rasteriser below. Injectable so a JVM suite
     * can write a document without a device bitmap: what a page *looks* like is proven on a device, and
     * what the file *is* (a page per page, its size, its annotations) here.
     */
    rasterise: ((Page) -> Bitmap?)? = null,
) {

    private val pictureOf: (Page) -> Bitmap? = rasterise ?: ::paint

    fun export(doc: Document, out: OutputStream) {
        val outDoc = PDDocument()
        try {
            doc.pages.forEach { page -> writePage(outDoc, page) }
            outDoc.save(out)
        } finally {
            outDoc.close()
        }
    }

    /** One page of the file: the flattened sheet, then the annotations that sit over it. */
    private fun writePage(outDoc: PDDocument, page: Page) {
        if (page.width <= 0.0 || page.height <= 0.0) return
        val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
        outDoc.addPage(pdPage)
        val sheet = pictureOf(page)
        PDPageContentStream(outDoc, pdPage).use { cs ->
            if (sheet != null) {
                cs.drawImage(
                    LosslessFactory.createFromImage(outDoc, sheet),
                    0f,
                    0f,
                    page.width.toFloat(),
                    page.height.toFloat(),
                )
            }
        }
        // The picture is already in the page's visual space (the rasteriser draws what is on screen),
        // so the annotations need no rotation matrix: the page carries no /Rotate of its own.
        val background = page.background as? Background.Pdf
        PdfAnnotations.add(
            pdPage,
            page,
            background?.pageNo,
            textIndex,
            PdfPageTransform(0f, 0f, page.height.toFloat()),
            null,
        )
    }

    /**
     * The page as the canvas paints it, at [EXPORT_SCALE]× its point size. The background comes from
     * the same caches the editor blits from — [PdfPageCache.render] for a PDF page,
     * [ImageBackgroundCache.render] for a picture — so a `pdf` or `pixmap` page exports as what was
     * under the ink rather than as a bare sheet.
     */
    private fun paint(page: Page): Bitmap? {
        val width = (page.width * EXPORT_SCALE).toInt().coerceAtLeast(1)
        val height = (page.height * EXPORT_SCALE).toInt().coerceAtLeast(1)
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return null
        }
        val canvas = Canvas(bitmap)
        val scale = (width / page.width).toFloat()
        val box = PageBox(
            index = 0,
            topPx = 0f,
            leftPx = 0f,
            widthPx = width.toFloat(),
            heightPx = height.toFloat(),
            scale = scale,
            source = PageSource.of(listOf(page)),
        )
        val pageImage = when (val background = page.background) {
            is Background.Pdf -> pdfSource?.render(background.pageNo, width)
            is Background.Pixmap -> imageSource?.render(background.filename, width)
            is Background.Solid -> null
        }
        BackgroundRenderer.draw(canvas, box, scrollX = 0f, scrollY = 0f, pageImage = pageImage)
        // One-shot renderer: its decoded pictures must not sit in the shared bitmap budget after the
        // page is written.
        val elements = ElementRenderer()
        try {
            PageRenderer.drawElements(canvas, page, scale, offsetX = 0f, offsetY = 0f, StrokePainter(), elements)
        } finally {
            elements.close()
        }
        return bitmap
    }

    private companion object {

        /** Points→pixels for the flattened page: ~150 dpi, enough to print without bloating the file. */
        const val EXPORT_SCALE = 2.0
    }
}
