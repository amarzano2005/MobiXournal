package com.mobixournal.render

import com.mobixournal.format.model.Page
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream

/**
 * Draws a fresh (non-PDF) page background — the sheet fill plus its ruling — onto a PDFBox content
 * stream, as the replay of [PdfPagePlan.paper].
 *
 * The geometry used to live here, which was the last piece of a printed page that did not: the app's
 * export drew the paper with its own arms while the window had none, so a ruled sheet could only ever
 * be ruled on one of them. It is a plan now, beside the page's ink, and this object is one line of
 * delegation — kept because [PdfExporter] names it and because "the paper" is a real step of a page, not
 * because it has an opinion left to hold.
 */
object PdfBackgroundPainter {

    /**
     * Write the sheet and its ruling for [page]. Used only when no source PDF page is preserved behind
     * it; a PDF-backed page keeps its original vector content instead.
     */
    fun draw(doc: PDDocument, cs: PDPageContentStream, page: Page, t: PdfPageTransform) {
        PdfVectorPainter().paint(doc, cs, PdfPagePlan.paper(page, t))
    }
}
