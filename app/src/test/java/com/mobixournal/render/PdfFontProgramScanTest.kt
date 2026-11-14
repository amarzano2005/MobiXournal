package com.mobixournal.render

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The cheap question asked before the expensive repair: does this file name a font program it does not carry?
 * A file whose programs are all there must answer no — that answer is what keeps PDFBox off the first open of
 * every healthy PDF — and a truncated one must answer yes.
 */
class PdfFontProgramScanTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a file whose programs are all there is not suspected`() {
        val file = folder.newFile("healthy.pdf")
        file.writeText(raw(program = true))

        assertFalse(PdfFontProgramScan.suspectsMissingProgram(file))
    }

    @Test
    fun `a reference to an object the file does not carry is suspected`() {
        val file = folder.newFile("truncated.pdf")
        file.writeText(raw(program = false))

        assertTrue(PdfFontProgramScan.suspectsMissingProgram(file))
    }

    @Test
    fun `a file that embeds no font program at all is not suspected`() {
        val file = folder.newFile("standard-fonts.pdf")
        file.writeText("%PDF-1.7\n1 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n%%EOF\n")

        assertFalse(PdfFontProgramScan.suspectsMissingProgram(file))
    }

    @Test
    fun `a real export with a bundled face is not suspected`() {
        val file = folder.newFile("export.pdf")
        PDDocument().use { document ->
            val page = PDPage(PDRectangle(200f, 100f))
            document.addPage(page)
            val font = AssetFonts.file(PdfBundledFace.PROPORTIONAL).inputStream()
                .use { PDType0Font.load(document, it, true) }
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(font, 12f)
                stream.newLineAtOffset(20f, 40f)
                stream.showText("Hello")
                stream.endText()
            }
            document.save(file)
        }

        assertFalse(PdfFontProgramScan.suspectsMissingProgram(file))
    }

    /**
     * A minimal PDF body: a font dictionary naming a program object. With [program] the object is written,
     * without it the reference dangles — the shape a truncated export has.
     */
    private fun raw(program: Boolean): String {
        val fontFile = if (program) {
            "3 0 obj\n<< /Length1 8 /Filter /FlateDecode >>\nstream\nx\nendstream\nendobj\n"
        } else {
            ""
        }
        return "%PDF-1.7\n" +
            "1 0 obj\n<< /Type /Font /Subtype /TrueType /BaseFont /ABCDEF+NotoSans /FontDescriptor 2 0 R >> endobj\n" +
            "2 0 obj\n<< /Type /FontDescriptor /FontName /ABCDEF+NotoSans /FontFile2 3 0 R >> endobj\n" +
            fontFile +
            "%%EOF\n"
    }
}
