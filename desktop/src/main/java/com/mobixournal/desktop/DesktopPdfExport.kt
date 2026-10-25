package com.mobixournal.desktop

import androidx.compose.ui.graphics.toAwtImage
import com.mobixournal.format.model.Page
import com.mobixournal.render.PdfPagePlan
import com.mobixournal.render.PdfPageTransform
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory

/**
 * The window's **PDF export**: the document, as one PDF file.
 *
 * A page is written in the order the tablet writes it — the sheet and its ruling
 * ([PdfPagePlan.paper]), then the picture a `pdf`/`pixmap` background carries, then the ink — and every
 * step is the *shared* one: `PdfPagePlan` compiles the paper and the elements, [DesktopPdfPainter]
 * replays them with this shell's PDFBox. Nothing here knows what a pressure taper or a grid spacing is,
 * which is what stops the PC's print from being a second, slightly different rendering of the same page.
 *
 * Two honest differences from the phone, both stated in `docs/pdf.md`: the picture behind a page is
 * written as a **raster** (`PageBackdrop` has already resolved and cached it, and a flatten is what the
 * window is doing), and no `/Highlight`, `/Ink` or `/Text` annotation dictionaries are added — the ink is
 * flattened, so every viewer shows the page, but a reader's annotation pane stays empty.
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
        val temp = File.createTempFile(".${target.name}.", ".writing", folder)
        try {
            val painter = DesktopPdfPainter()
            PDDocument().use { doc ->
                for (index in 0 until pages) {
                    val page = document.page(index)
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
        }
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
