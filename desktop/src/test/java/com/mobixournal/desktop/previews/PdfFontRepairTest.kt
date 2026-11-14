package com.mobixournal.desktop.previews

import java.awt.image.BufferedImage
import org.apache.pdfbox.cos.COSArray
import org.apache.pdfbox.cos.COSDictionary
import org.apache.pdfbox.cos.COSFloat
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.cos.COSStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDResources
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.rendering.PDFRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A page whose font was announced and not delivered — the shape a broken export has — must still read: the
 * repair rebuilds the font from its own ToUnicode, and the render goes from substituted or missing glyphs to
 * the letters the document says.
 */
class PdfFontRepairTest {

    @Test
    fun aFontWithoutItsProgramIsRebuiltAndDrawsItsLetters() {
        PDDocument().use { document ->
            val page = pageWith(code1 = 'H', code2 = 'i', toUnicode = true)
            document.addPage(page)

            val before = ink(document)
            assertEquals(1, PdfFontRepair.repair(page))
            val after = ink(document)

            assertTrue("the damaged page drew $before ink, the repaired one $after", after > before)
        }
    }

    @Test
    fun theRebuiltFontKeepsItsWeightSlantAndSpacing() {
        PDDocument().use { document ->
            val bold = pageWith('B', '!', toUnicode = true, baseFont = "QAAAAA+NotoSans-Bold")
            val mono = pageWith('m', 'o', toUnicode = true, baseFont = "RAAAAA+DejaVuSansMono")
            val italic = pageWith('i', 't', toUnicode = true, baseFont = "SAAAAA+NotoSans-Italic")
            listOf(bold, mono, italic).forEach {
                document.addPage(it)
                assertEquals(1, PdfFontRepair.repair(it))
            }

            assertEquals("Helvetica-Bold", standInOf(bold))
            assertEquals("Courier", standInOf(mono))
            assertEquals("Helvetica-Oblique", standInOf(italic))
        }
    }

    @Test
    fun aFontWithNoToUnicodeIsLeftAlone() {
        PDDocument().use { document ->
            val page = pageWith(code1 = 'H', code2 = 'i', toUnicode = false)
            document.addPage(page)

            assertEquals(0, PdfFontRepair.repair(page))
        }
    }

    /** A 200×100 sheet with one TrueType font that names a program and carries none, and `<code1 code2>`. */
    private fun pageWith(
        code1: Char,
        code2: Char,
        toUnicode: Boolean,
        baseFont: String = "ABCDEF+NotoSans-Regular",
    ): PDPage {
        val page = PDPage(PDRectangle(200f, 100f))
        val fonts = COSDictionary()
        val font = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setName(COSName.SUBTYPE, "TrueType")
            setName(COSName.BASE_FONT, baseFont)
            setInt(COSName.FIRST_CHAR, 0)
            setInt(COSName.LAST_CHAR, 2)
            setItem(
                COSName.WIDTHS,
                COSArray().apply { repeat(3) { add(COSFloat(600f)) } },
            )
            setItem(
                COSName.FONT_DESC,
                COSDictionary().apply {
                    setName(COSName.TYPE, "FontDescriptor")
                    setName(COSName.FONT_NAME, "ABCDEF+NotoSans-Regular")
                    setInt(COSName.FLAGS, 4)
                },
            )
            if (toUnicode) setItem(COSName.TO_UNICODE, cmap(code1, code2))
        }
        fonts.setItem(COSName.getPDFName("F1"), font)
        page.resources = PDResources().apply {
            cosObject.setItem(COSName.FONT, fonts)
        }
        // `BT /F1 24 Tf 20 40 Td <0102> Tj ET`, written raw because the font it names has no object.
        page.cosObject.setItem(COSName.CONTENTS, COSStream().apply {
            createOutputStream().use {
                it.write("BT /F1 24 Tf 20 40 Td <0102> Tj ET".toByteArray(Charsets.ISO_8859_1))
            }
        })
        return page
    }

    /** A ToUnicode CMap naming codes 1 and 2. */
    private fun cmap(code1: Char, code2: Char): COSStream = COSStream().apply {
        val text = """
            /CIDInit /ProcSet findresource begin
            12 dict begin
            begincmap
            1 begincodespacerange
            <00> <FF>
            endcodespacerange
            2 beginbfchar
            <01> <${code1.code.toString(16).padStart(4, '0').uppercase()}>
            <02> <${code2.code.toString(16).padStart(4, '0').uppercase()}>
            endbfchar
            endcmap
            CMapName currentdict /CMap defineresource pop
            end
            end
        """.trimIndent()
        createOutputStream().use { it.write(text.toByteArray(Charsets.ISO_8859_1)) }
    }

    /** The base-14 face the page's one font was rebuilt as. */
    private fun standInOf(page: PDPage): String? =
        ((page.resources.cosObject.getDictionaryObject(COSName.FONT) as COSDictionary)
            .getDictionaryObject(COSName.getPDFName("F1")) as COSDictionary)
            .getNameAsString(COSName.BASE_FONT)

    /** Dark pixels of the page's raster: the cheapest measure of "there is writing on it". */
    private fun ink(document: PDDocument): Int {
        val image: BufferedImage = PDFRenderer(document).renderImage(0, 2f)
        var dark = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val luminance = (((rgb shr 16) and 0xFF) + ((rgb shr 8) and 0xFF) + (rgb and 0xFF)) / 3
                if (luminance < 128) dark++
            }
        }
        return dark
    }
}
