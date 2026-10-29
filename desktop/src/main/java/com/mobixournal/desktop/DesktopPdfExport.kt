package com.mobixournal.desktop

import androidx.compose.ui.graphics.toAwtImage
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.render.PdfOverlayMatrix
import com.mobixournal.render.PdfPagePlan
import com.mobixournal.render.PdfPageTransform
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.util.Matrix

/**
 * The window's **PDF export**: the document, as one PDF file.
 *
 * A page is written in the order the tablet writes it — the sheet and its ruling
 * ([PdfPagePlan.paper]), then the picture a `pdf`/`pixmap` background carries, then the ink — and every
 * step is the *shared* one: `PdfPagePlan` compiles the paper and the elements, [DesktopPdfPainter]
 * replays them with this shell's PDFBox. Nothing here knows what a pressure taper or a grid spacing is,
 * which is what stops the PC's print from being a second, slightly different rendering of the same page.
 *
 * A page written over a source PDF keeps that PDF's **own vectors**, as the tablet's exporter does: the
 * page is imported verbatim ([preservedPage]) and only the annotation overlay is written on top, so a
 * scanned notebook does not become a pile of bitmaps and its text stays selectable. The raster picture
 * (`PageBackdrop`) is the fallback for a source this window cannot open. The source document stays open
 * for as long as the export writes into its pages — an imported page borrows its streams — and is closed
 * once the target file has been saved.
 *
 * The ink is page content only, exactly as the tablet writes it (`docs/pdf.md`): no `/Highlight`,
 * `/Ink` or `/Text` annotation dictionaries are added, so the two shells produce the same file — every
 * viewer shows the marks because they are paint, and a reader's annotation pane stays empty.
 */
internal object DesktopPdfExport {

    /**
     * Write every page of [document] as [target], and answer how many were written.
     *
     * The pages are read **through the window** ([DesktopDocument.pageCount]/[DesktopDocument.page]), so a
     * paged `.mobix` inflates one sheet at a time and an edit still sitting in the window's overlay is in
     * the export — the same rule the SVG driver and the painter follow.
     *
     * The bytes are staged in a temp file beside [target] and moved over it ([DesktopSaves.replace]), so a
     * crash or a full disk leaves the PDF that was there instead of a truncated one.
     *
     * Two destinations are refused in words rather than half-honoured: a **board** (a chunked infinite
     * canvas needs the sheet slicing the tablet's exporter does, and writing one sheet of it as the whole
     * document would be a lie), and a document with no pages.
     */
    fun write(document: DesktopDocument, target: File, backdropPage: PageBackdrop): Int {
        if (document.shape.geometry != null) {
            throw DesktopSaves.SaveRefused(
                "This document is an infinite board, and a PDF needs pages. Open it as pages, or export " +
                    "it to .xopp from Save As — the tablet slices a board into sheets, and this window has no " +
                    "sheet slicer yet.",
            )
        }
        val pages = document.pageCount
        if (pages <= 0) {
            throw DesktopSaves.SaveRefused("This document has no pages, so there is nothing to export.")
        }
        val folder = target.absoluteFile.parentFile
            ?: throw DesktopSaves.SaveRefused("That destination has no folder to write into.")
        if (!folder.exists() && !folder.mkdirs()) {
            throw DesktopSaves.SaveRefused("Could not create the folder for the PDF: ${folder.path}.")
        }
        val temp = DesktopSaves.stagingFile(folder)
        // An imported page is imported **by reference**: its content stream and its resources are the
        // source document's own COSStreams, so a source closed before the target is written leaves the
        // save with nothing to read — PDFBox refuses with "COSStream has been closed", and a scanned
        // page (an image XObject) is exactly the case that shows it. The sources therefore stay open
        // until the file has been written, and each one is opened once however many pages name it.
        val sources = ArrayList<PDDocument>()
        val opened = HashMap<String, PDDocument?>()
        try {
            val painter = DesktopPdfPainter()
            PDDocument().use { doc ->
                for (index in 0 until pages) {
                    val page = document.page(index)
                    // The tablet's first arm: a page over a PDF whose source opens keeps its vectors.
                    val source = page.background as? Background.Pdf
                    val pdf = source?.let { openSource(backdropPage, it, opened, sources) }
                    val preserved = pdf != null && source != null &&
                        preservedPage(doc, pdf, source.pageNo, page, painter)
                    if (preserved) continue
                    val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                    doc.addPage(pdPage)
                    val t = PdfPageTransform(0f, 0f, page.height.toFloat())
                    PDPageContentStream(doc, pdPage).use { cs ->
                        // The paper, then the picture over it, then the ink: the order a page is painted in.
                        painter.paint(doc, cs, PdfPagePlan.paper(page, t))
                        picture(doc, cs, backdropPage, page, t)
                        painter.paint(doc, cs, page, t)
                    }
                }
                temp.outputStream().use { out -> doc.save(out) }
            }
            DesktopSaves.replace(temp, target)
            return pages
        } catch (failure: Throwable) {
            temp.delete()
            throw failure
        } finally {
            // After the save, never before it: the pages just written point into these documents.
            sources.forEach { runCatching { it.close() } }
        }
    }

    /**
     * The source document behind [background], opened at most once per document.
     *
     * [opened] holds the answer **including a null one**, so an unreadable source is tried once instead of
     * once per page; every document actually opened is added to [sources], which the caller closes after
     * the target has been saved.
     */
    private fun openSource(
        backdropPage: PageBackdrop,
        background: Background.Pdf,
        opened: MutableMap<String, PDDocument?>,
        sources: MutableList<PDDocument>,
    ): PDDocument? {
        // The resolved reference, not the page's background: pages 2..N name only a page number and
        // borrow the document's one reference, and they must land on the same open document.
        val reference = backdropPage.reference(background) ?: return null
        val key = "${reference.bundledAsset}|${reference.domain}|${reference.filename}"
        if (opened.containsKey(key)) return opened[key]
        val pdf = backdropPage.sourcePdf(reference)
        if (pdf != null) sources += pdf
        opened[key] = pdf
        return pdf
    }

    /**
     * Import the source PDF's page [pageNo] verbatim and append the annotation overlay over it — the arm
     * the tablet's `PdfExporter.preservedPage` takes, with the same rotation handling: annotations are
     * authored in the page's **visual** space, so the content stream is pre-transformed by
     * [PdfOverlayMatrix] and the viewer's `/Rotate` cancels it back to the drawn position. For `/Rotate 0`
     * the matrix is the crop-origin shift.
     *
     * Answers false when the source has no such page, so the caller writes the fresh sheet instead of
     * failing the export over one unreadable page.
     */
    private fun preservedPage(
        doc: PDDocument,
        source: PDDocument,
        pageNo: Int,
        page: Page,
        painter: DesktopPdfPainter,
    ): Boolean {
        if (pageNo !in 0 until source.numberOfPages) return false
        val pdPage = doc.importPage(source.getPage(pageNo))
        val crop = pdPage.cropBox
        val m = PdfOverlayMatrix.forPage(
            cropX = crop.lowerLeftX,
            cropY = crop.lowerLeftY,
            cropW = crop.width,
            cropH = crop.height,
            rotation = pdPage.rotation,
        )
        PDPageContentStream(doc, pdPage, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.transform(Matrix(m.a, m.b, m.c, m.d, m.e, m.f))
            painter.paint(doc, cs, page, PdfPageTransform(0f, 0f, m.visualHeight))
        }
        return true
    }

    /**
     * The picture a page sits on, if it has one — a `pdf` page or a `pixmap` photograph, stretched to the
     * page box as the canvas and the tablet's exporter stretch it.
     *
     * [PageBackdrop] has already resolved it (bundle first, then the file the reference names) and cached
     * the bitmap; a background it cannot resolve leaves the sheet behind rather than failing the export,
     * exactly as a missing picture leaves paper on screen.
     */
    private fun picture(
        doc: PDDocument,
        cs: PDPageContentStream,
        backdropPage: PageBackdrop,
        page: Page,
        t: PdfPageTransform,
    ) {
        val bitmap = backdropPage.image(page.background) ?: return
        runCatching {
            val xobj = LosslessFactory.createFromImage(doc, bitmap.toAwtImage())
            cs.drawImage(xobj, t.x(0.0), t.y(page.height), page.width.toFloat(), page.height.toFloat())
        }
    }
}
