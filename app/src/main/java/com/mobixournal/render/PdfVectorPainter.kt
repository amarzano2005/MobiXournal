package com.mobixournal.render

import android.graphics.BitmapFactory
import com.mobixournal.format.model.Page
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.blend.BlendMode
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState

/**
 * Draws a [Page]'s annotation layers onto a PDFBox [PDPageContentStream] as **vector** content, so
 * they overlay the preserved PDF page (or a fresh sheet) without rasterising.
 *
 * The geometry is not here: [PdfPagePlan] compiles the page into ops in `mobix-core`, and this class
 * only **replays** them through the Android port of PDFBox — one arm per op, no page walking, no
 * arithmetic. That split is what lets the desktop emit the very same page through `org.apache.pdfbox`,
 * instead of keeping a second painter with its own opinion of a pressure taper or a highlighter band.
 *
 * What stays shell-side is what only this platform can do: mapping a [PdfFontFace] to a base-14
 * `PDType1Font`, decoding an image's bytes with `BitmapFactory`, and the graphics-state calls. An op
 * that throws is skipped rather than aborting the export, as before.
 */
class PdfVectorPainter {

    fun paint(doc: PDDocument, cs: PDPageContentStream, page: Page, t: PdfPageTransform) {
        for (op in PdfPagePlan.of(page, t)) {
            runCatching {
                when (op) {
                    is PdfFill -> fill(cs, op)
                    is PdfPath -> path(cs, op)
                    is PdfText -> text(cs, op)
                    is PdfImage -> image(doc, cs, op)
                }
            }
        }
    }

    /** Flood a closed outline: non-stroking colour at the fill's alpha, under the stroke that follows. */
    private fun fill(cs: PDPageContentStream, op: PdfFill) {
        cs.saveGraphicsState()
        cs.setGraphicsStateParameters(alphaState(op.alpha, op.multiply))
        cs.setNonStrokingArgb(op.colour)
        polyline(cs, op.points, close = true)
        cs.fill()
        cs.restoreGraphicsState()
    }

    private fun path(cs: PDPageContentStream, op: PdfPath) {
        cs.saveGraphicsState()
        cs.setGraphicsStateParameters(alphaState(op.alpha, op.multiply))
        cs.setStrokingArgb(op.colour)
        cs.setLineCapStyle(1) // round
        cs.setLineJoinStyle(1) // round
        op.dash?.let { cs.setLineDashPattern(it.toFloatArray(), 0f) }
        if (op.widths.isEmpty()) {
            // One path, one stroke: a translucent band must not bead where it crosses itself.
            cs.setLineWidth(op.width.toFloat())
            polyline(cs, op.points, op.close)
            cs.stroke()
        } else {
            // The pen: one stroke per segment, so the width can follow the pressure between vertices.
            for (i in op.widths.indices) {
                cs.setLineWidth(op.widths[i].toFloat())
                cs.moveTo(op.points[i].x, op.points[i].y)
                cs.lineTo(op.points[i + 1].x, op.points[i + 1].y)
                cs.stroke()
            }
        }
        cs.restoreGraphicsState()
    }

    private fun text(cs: PDPageContentStream, op: PdfText) {
        if (op.lines.isEmpty()) return
        cs.beginText()
        cs.setNonStrokingArgb(op.colour)
        cs.setFont(fontFor(op.face), op.size.toFloat())
        cs.newLineAtOffset(op.x, op.y)
        for (i in op.lines.indices) {
            cs.showText(op.lines[i])
            if (i < op.lines.lastIndex) cs.newLineAtOffset(0f, -op.lineHeight.toFloat())
        }
        cs.endText()
    }

    private fun image(doc: PDDocument, cs: PDPageContentStream, op: PdfImage) {
        val bmp = BitmapFactory.decodeByteArray(op.data, 0, op.data.size) ?: return
        val xobj = LosslessFactory.createFromImage(doc, bmp)
        cs.drawImage(xobj, op.left, op.bottom, op.width, op.height)
    }

    /** Build a polyline on [cs] from already-transformed points. Closes if [close] is true. */
    private fun polyline(cs: PDPageContentStream, points: List<PdfPoint>, close: Boolean) {
        cs.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) cs.lineTo(points[i].x, points[i].y)
        if (close) cs.closePath()
    }

    private fun fontFor(face: PdfFontFace): PDFont = when (face.family) {
        PdfFamily.SERIF -> when {
            face.bold && face.italic -> PDType1Font.TIMES_BOLD_ITALIC
            face.bold -> PDType1Font.TIMES_BOLD
            face.italic -> PDType1Font.TIMES_ITALIC
            else -> PDType1Font.TIMES_ROMAN
        }
        PdfFamily.MONO -> when {
            face.bold && face.italic -> PDType1Font.COURIER_BOLD_OBLIQUE
            face.bold -> PDType1Font.COURIER_BOLD
            face.italic -> PDType1Font.COURIER_OBLIQUE
            else -> PDType1Font.COURIER
        }
        PdfFamily.SANS -> when {
            face.bold && face.italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            face.bold -> PDType1Font.HELVETICA_BOLD
            face.italic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }
    }

    private companion object {
        fun alphaState(a: Float, multiply: Boolean = false) = PDExtendedGraphicsState().apply {
            strokingAlphaConstant = a
            nonStrokingAlphaConstant = a
            if (multiply) setBlendMode(BlendMode.MULTIPLY)
        }
    }
}
