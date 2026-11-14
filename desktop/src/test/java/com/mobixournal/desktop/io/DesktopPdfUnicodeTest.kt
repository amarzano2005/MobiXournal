package com.mobixournal.desktop.io

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import com.mobixournal.render.PdfPageTransform
import com.mobixournal.render.PdfUnicodeFixture
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The window's half of *one fixture, both emitters*: a text box holding
 * [PdfUnicodeFixture.MIXED] goes through `PdfPagePlan` and [DesktopPdfPainter] with this shell's PDFBox,
 * is saved, reopened and read back with a text stripper — and must give the same characters the app's
 * `PdfUnicodeExportTest` gets from the same constant.
 *
 * The faces load off the classpath (`desktop/src/main/resources/fonts`, the same five DejaVu files the
 * app ships), which is also what a packaged window reads, so this runs in the real resource layout rather
 * than against a test-only copy.
 */
class DesktopPdfUnicodeTest {

    private val width = 595.2756f
    private val height = 841.8898f
    private val black = 0xFF000000.toInt()

    private fun page(content: String) = Page(
        width = width.toDouble(),
        height = height.toDouble(),
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(
            Layer(
                listOf(TextElement(font = "Sans", size = 12.0, x = 72.0, y = 96.0, color = black, content = content)),
            ),
        ),
    )

    /** Paint [content], then save and reload: the file a reader opens, with every font written. */
    private fun painted(content: String): PDDocument {
        val built = PDDocument()
        built.addPage(PDPage(PDRectangle(0f, 0f, width, height)))
        val cs = PDPageContentStream(built, built.getPage(0), PDPageContentStream.AppendMode.APPEND, false)
        DesktopPdfPainter().paint(built, cs, page(content), PdfPageTransform(0f, 0f, height))
        cs.close()
        val bytes = ByteArrayOutputStream().also { built.save(it) }.toByteArray()
        built.close()
        return PDDocument.load(ByteArrayInputStream(bytes))
    }

    private fun extracted(doc: PDDocument): String = PDFTextStripper().getText(doc).trim()

    private fun fonts(doc: PDDocument): List<PDFont> {
        val resources = doc.getPage(0).resources
        return resources.fontNames.map { resources.getFont(it) }
    }

    @Test
    fun `a run WinAnsi cannot carry comes out of the window as the same characters the app gets`() {
        painted(PdfUnicodeFixture.MIXED).use { doc ->
            val out = extracted(doc)
            assertEquals(PdfUnicodeFixture.MIXED, out)
            assertTrue("no character is left as a question mark", '?' !in out)
        }
    }

    @Test
    fun `the window embeds the same faces, subset, with a ToUnicode map`() {
        painted(PdfUnicodeFixture.MIXED).use { doc ->
            val embedded = fonts(doc)
            assertTrue("the page names at least one font", embedded.isNotEmpty())
            for (font in embedded) {
                if (font !is PDType0Font) fail("a bundled run must be an embedded Type0 font, got $font")
                assertTrue(
                    "without a ToUnicode map the text draws but cannot be copied",
                    font.cosObject.containsKey(COSName.TO_UNICODE),
                )
            }
        }
    }

    @Test
    fun `a pure-winansi run still keeps the base-14 face`() {
        painted(PdfUnicodeFixture.WIN_ANSI).use { doc ->
            val used = fonts(doc)
            assertTrue("the page names a font", used.isNotEmpty())
            for (font in used) {
                if (font !is PDType1Font) fail("a WinAnsi run must stay base-14, got $font")
            }
            assertEquals(PdfUnicodeFixture.WIN_ANSI, extracted(doc))
        }
    }
}
