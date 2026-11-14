package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The **round trip** of an exported text run: what a page holding Greek, Cyrillic, a subscript, a
 * combining accent, an astral emoji and the CP1252 punctuation set becomes in a real PDF — drawn, and
 * copied back out with the same characters.
 *
 * `PdfTextEncodingAuditTest` (mobix-core) pins the plan's decision; this paints that plan through the
 * app's emitter and reads the file back with PDFBox's own stripper, which is the half a plan cannot
 * promise: text that draws but cannot be copied is not text. The bundled DejaVu faces are registered
 * exactly as the activity registers its own ([useExportFonts]), and loaded off disk by [AssetFonts]
 * because a JVM test has no `AssetManager`.
 */
class PdfUnicodeExportTest {

    private val width = 595.2756f
    private val height = 841.8898f
    private val black = 0xFF000000.toInt()

    /** The run both shells are measured against ([PdfUnicodeFixture]), not a second copy of it. */
    private val mixed = PdfUnicodeFixture.MIXED

    @Before
    fun registerFonts() {
        exportFonts = AssetFonts.loader()
    }

    @After
    fun clearFonts() {
        exportFonts = null
    }

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

    /**
     * Paint [content] into a fresh page, then **save and reload** it: the file a reader gets.
     *
     * Saving is when PDFBox subsets an embedded font and writes its `ToUnicode` map, so a document checked
     * before that save has neither — and the stripper would be reading glyph ids, not text.
     */
    private fun painted(content: String): PDDocument {
        val built = PDDocument()
        built.addPage(PDPage(PDRectangle(0f, 0f, width, height)))
        val cs = PDPageContentStream(built, built.getPage(0), PDPageContentStream.AppendMode.APPEND, false)
        PdfVectorPainter().paint(built, cs, page(content), PdfPageTransform(0f, 0f, height))
        cs.close()
        val bytes = ByteArrayOutputStream().also { built.save(it) }.toByteArray()
        built.close()
        return PDDocument.load(ByteArrayInputStream(bytes))
    }

    private fun extracted(doc: PDDocument): String = PDFTextStripper().getText(doc).trim()

    /** Every font the page names, as the file actually carries them. */
    private fun fonts(doc: PDDocument): List<PDFont> {
        val resources = doc.getPage(0).resources
        return resources.fontNames.map { resources.getFont(it) }
    }

    @Test
    fun `a run WinAnsi cannot carry is drawn, and copied back out character for character`() {
        painted(mixed).use { doc ->
            val out = extracted(doc)
            // Every character of the fixture survives the round trip — Greek, Cyrillic, the subscript, the
            // combining accent, the astral pair and the CP1252 punctuation set — and nothing degrades to
            // the `?` the old code-range guard produced.
            assertEquals(mixed, out)
            assertTrue("no character is left as a question mark", '?' !in out)
            assertTrue("and no surrogate half is lost", out.codePointCount(0, out.length) == mixed.codePointCount(0, mixed.length))
        }
    }

    @Test
    fun `the bundle is embedded as a subset with a ToUnicode map`() {
        painted(mixed).use { doc ->
            val embedded = fonts(doc)
            assertTrue("the page names at least one font", embedded.isNotEmpty())
            for (font in embedded) {
                if (font !is PDType0Font) fail("a bundled run must be typeset in an embedded Type0 font, got $font")
                assertTrue(
                    "without a ToUnicode map the text draws but cannot be copied",
                    font.cosObject.containsKey(COSName.TO_UNICODE),
                )
            }
        }
    }

    @Test
    fun `a character WinAnsi encodes above U+00FF is drawn, not dropped`() {
        painted(PdfUnicodeFixture.CP1252_ABOVE_LATIN1).use { doc ->
            assertEquals(PdfUnicodeFixture.CP1252_ABOVE_LATIN1, extracted(doc))
        }
    }

    @Test
    fun `a C1 control becomes a substitute glyph, never the character WinAnsi maps it to`() {
        // WinAnsi's 0x85 is …, so the old range test printed an ellipsis where the text held a control
        // character. It is not text at all: it must not survive as a printable character.
        painted(PdfUnicodeFixture.CONTROL).use { doc ->
            val out = extracted(doc)
            assertEquals("one substitute, not the ellipsis", 1, out.codePointCount(0, out.length))
            assertTrue("and not a question mark", out != "?")
            assertTrue("and not WinAnsi's ellipsis", out != "…")
        }
    }

    @Test
    fun `a surrogate pair survives as one codepoint`() {
        painted(PdfUnicodeFixture.ASTRAL).use { doc ->
            val out = extracted(doc)
            assertEquals("one codepoint, not two surrogate halves", 1, out.codePointCount(0, out.length))
            assertTrue("and never a question mark", '?' !in out)
        }
    }

    @Test
    fun `a combining mark keeps both codepoints`() {
        painted(PdfUnicodeFixture.COMBINING).use { doc ->
            val out = extracted(doc)
            assertEquals(PdfUnicodeFixture.COMBINING, out)
            assertEquals("the mark is its own codepoint after the base letter", 2, out.codePointCount(0, out.length))
        }
    }

    @Test
    fun `the embedded face is a subset, not the whole three megabyte family`() {
        val built = PDDocument()
        built.addPage(PDPage(PDRectangle(0f, 0f, width, height)))
        val cs = PDPageContentStream(built, built.getPage(0), PDPageContentStream.AppendMode.APPEND, false)
        PdfVectorPainter().paint(built, cs, page(mixed), PdfPageTransform(0f, 0f, height))
        cs.close()
        val bytes = ByteArrayOutputStream().also { built.save(it) }.toByteArray()
        built.close()
        // DejaVu Sans is 759 kB on its own; a page of text that ships the face whole is a file nobody can
        // mail. `subset = true` is the difference, and this is what it looks like.
        assertTrue("the export is ${bytes.size} bytes, which suggests a face was embedded whole", bytes.size < 200_000)
    }

    @Test
    fun `a pure-winansi run keeps the base-14 face, and no font file comes with it`() {
        painted(PdfUnicodeFixture.WIN_ANSI).use { doc ->
            val used = fonts(doc)
            assertTrue("the page names a font", used.isNotEmpty())
            for (font in used) {
                // A standard-14 font is referenced by name only: the export of ordinary Latin text is the
                // file it always was, byte for byte.
                if (font !is PDType1Font) fail("a WinAnsi run must stay base-14, got $font")
            }
            assertEquals(PdfUnicodeFixture.WIN_ANSI, extracted(doc))
        }
    }
}
