package com.mobixournal.desktop

import com.mobixournal.format.model.Page
import com.mobixournal.render.PdfFamily
import com.mobixournal.render.PdfFill
import com.mobixournal.render.PdfFontFace
import com.mobixournal.render.PdfImage
import com.mobixournal.render.PdfOp
import com.mobixournal.render.PdfPagePlan
import com.mobixournal.render.PdfPageTransform
import com.mobixournal.render.PdfPath
import com.mobixournal.render.PdfPoint
import com.mobixournal.render.PdfText
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState

/**
 * One page's annotation layers written into a PDF page as **vector** content, by the **desktop's**
 * PDFBox (`org.apache.pdfbox`, declared in `desktop/build.gradle.kts`).
 *
 * This is the second consumer that `PdfVectorPainter`'s own doc names. The geometry is not here: it is
 * not here *twice*, which is the whole point. `PdfPagePlan` in `mobix-core` compiles a page — strokes with
 * their pressure taper, the highlighter's multiply band, text, images, and the living entities through
 * `LivingInk` — into an ordered list of ops, and both shells replay that list with one arm per op. The PC
 * therefore cannot print a different page from the one the tablet prints, and a change to how a stroke is
 * built is a change in one place.
 *
 * What stays shell-side is what only this platform can do: mapping a [PdfFontFace] to a base-14
 * `PDType1Font`, decoding an image's bytes with `ImageIO`, and the graphics-state calls. An op that throws
 * is skipped rather than aborting the page, exactly as the app's emitter does.
 *
 * The library difference that costs the most care is colour: the Android port's
 * `setStrokingArgb`/`setNonStrokingArgb` helpers do **not** exist in Apache PDFBox, so the ARGB int is
 * split into three 0..255 components here and the alpha stays where it belongs — in the graphics state.
 * The base-14 constants ([PDType1Font.HELVETICA] and its family) still exist in 2.0.31; a future move to
 * PDFBox 3 replaces them with `Standard14Fonts`, and [fontFor] is the single place to change.
 */
internal class DesktopPdfPainter {

    fun paint(doc: PDDocument, cs: PDPageContentStream, page: Page, t: PdfPageTransform) =
        paint(doc, cs, PdfPagePlan.of(page, t))

    /**
     * Replay [ops] — the sheet ([PdfPagePlan.paper]) or the page's ink, in the order given. The window
     * writes the paper with the same replay as the ink, so a ruling line is one rule on both shells.
     */
    fun paint(doc: PDDocument, cs: PDPageContentStream, ops: List<PdfOp>) {
        for (op in ops) {
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
        cs.setNonStrokingColor(op.colour.red, op.colour.green, op.colour.blue)
        polyline(cs, op.points, close = true)
        cs.fill()
        cs.restoreGraphicsState()
    }

    private fun path(cs: PDPageContentStream, op: PdfPath) {
        cs.saveGraphicsState()
        cs.setGraphicsStateParameters(alphaState(op.alpha, op.multiply))
        cs.setStrokingColor(op.colour.red, op.colour.green, op.colour.blue)
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
        cs.setNonStrokingColor(op.colour.red, op.colour.green, op.colour.blue)
        cs.setFont(fontFor(op.face), op.size.toFloat())
        cs.newLineAtOffset(op.x, op.y)
        for (i in op.lines.indices) {
            cs.showText(op.lines[i])
            if (i < op.lines.lastIndex) cs.newLineAtOffset(0f, -op.lineHeight.toFloat())
        }
        cs.endText()
    }

    /**
     * An image op carries the document's own encoded bytes, because decoding is the one step with no
     * platform-free implementation: the app decodes with `BitmapFactory`, the window with `ImageIO`. A
     * picture that will not decode is skipped, leaving the rest of the page intact.
     */
    private fun image(doc: PDDocument, cs: PDPageContentStream, op: PdfImage) {
        val decoded: BufferedImage = ImageIO.read(ByteArrayInputStream(op.data)) ?: return
        val xobj = LosslessFactory.createFromImage(doc, decoded)
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

/**
 * An ARGB colour's three channels — what the Apache PDFBox colour calls take, where the Android port
 * offers a single `setStrokingArgb`. Alpha is not one of them: it lives in the graphics state, so a
 * translucent band is one alpha constant on a solid colour rather than a colour with a transparency.
 */
private val Int.red: Int get() = (this shr 16) and 0xFF
private val Int.green: Int get() = (this shr 8) and 0xFF
private val Int.blue: Int get() = this and 0xFF
