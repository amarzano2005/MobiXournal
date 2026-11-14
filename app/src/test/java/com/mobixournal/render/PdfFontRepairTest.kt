package com.mobixournal.render

import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tablet's repair of a page whose font was announced and not delivered: the dictionary becomes a face the
 * app carries, the page keeps its own operators and widths, and what a reader extracts is still the text.
 */
class PdfFontRepairTest {

    @Test
    fun aFontWithoutItsProgramIsRebuiltWithTheFaceTheNameAsksFor() {
        PDDocument().use { document ->
            val page = pageWith(code1 = 'H', code2 = 'i', baseFont = "QAAAAA+DejaVuSans-Bold")
            document.addPage(page)
            assertTrue("the page was not recognised as damaged", PdfFontRepair.isDamaged(page))

            assertEquals(1, PdfFontRepair.repair(document, page, ::bundled))

            assertFalse("still damaged after the repair", PdfFontRepair.isDamaged(page))
            assertEquals("DejaVuSans-Bold", fontOf(page).getNameAsString(COSName.BASE_FONT))
            assertTrue("the bundled face was not embedded", hasProgram(page))
            assertEquals("Hi", extracted(document))
        }
    }

    @Test
    fun aFamilyTheAppDoesNotCarryGetsTheBase14FaceOfItsStyle() {
        PDDocument().use { document ->
            val page = pageWith(code1 = 's', code2 = 'f', baseFont = "TAAAAA+NotoSerif-Regular")
            document.addPage(page)

            assertEquals(1, PdfFontRepair.repair(document, page, ::bundled))

            assertEquals("Times-Roman", fontOf(page).getNameAsString(COSName.BASE_FONT))
            assertEquals("sf", extracted(document))
        }
    }

    @Test
    fun aPageWhoseFontsAreAllThereIsLeftAlone() {
        PDDocument().use { document ->
            val page = pageWith(code1 = 'H', code2 = 'i', baseFont = "AAAAAA+Arial", withProgram = true)
            document.addPage(page)

            assertEquals(0, PdfFontRepair.repair(document, page, ::bundled))
            assertEquals("AAAAAA+Arial", fontOf(page).getNameAsString(COSName.BASE_FONT))
        }
    }

    /** The faces the app ships, read off disk the way [AssetFonts] reads them for a JVM test. */
    private fun bundled(face: PdfBundledFace): ByteArray = AssetFonts.file(face).readBytes()

    /** A 200×100 sheet with one font, and `<code1 code2>` drawn in it at 24 pt. */
    private fun pageWith(
        code1: Char,
        code2: Char,
        baseFont: String,
        withProgram: Boolean = false,
    ): PDPage {
        val page = PDPage(PDRectangle(200f, 100f))
        val fonts = COSDictionary()
        val font = COSDictionary().apply {
            setItem(COSName.TYPE, COSName.FONT)
            setName(COSName.SUBTYPE, "TrueType")
            setName(COSName.BASE_FONT, baseFont)
            setInt(COSName.FIRST_CHAR, 0)
            setInt(COSName.LAST_CHAR, 2)
            setItem(COSName.WIDTHS, COSArray().apply { repeat(3) { add(COSFloat(600f)) } })
            setItem(
                COSName.FONT_DESC,
                COSDictionary().apply {
                    setName(COSName.TYPE, "FontDescriptor")
                    setName(COSName.FONT_NAME, baseFont)
                    setInt(COSName.FLAGS, 4)
                    if (withProgram) {
                        val bytes = bundled(PdfBundledFace.PROPORTIONAL)
                        setItem(
                            COSName.FONT_FILE2,
                            COSStream().apply {
                                setInt(COSName.LENGTH1, bytes.size)
                                createOutputStream(COSName.FLATE_DECODE).use { it.write(bytes) }
                            },
                        )
                    }
                },
            )
            setItem(COSName.TO_UNICODE, cmap(code1, code2))
        }
        fonts.setItem(COSName.getPDFName("F1"), font)
        page.resources = PDResources().apply { cosObject.setItem(COSName.FONT, fonts) }
        page.cosObject.setItem(
            COSName.CONTENTS,
            COSStream().apply {
                createOutputStream().use {
                    it.write("BT /F1 24 Tf 20 40 Td <0102> Tj ET".toByteArray(Charsets.ISO_8859_1))
                }
            },
        )
        return page
    }

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

    private fun fontOf(page: PDPage): COSDictionary =
        (page.resources.cosObject.getDictionaryObject(COSName.FONT) as COSDictionary)
            .getDictionaryObject(COSName.getPDFName("F1")) as COSDictionary

    private fun hasProgram(page: PDPage): Boolean {
        val descriptor = fontOf(page).getDictionaryObject(COSName.FONT_DESC) as COSDictionary
        return descriptor.getDictionaryObject(COSName.FONT_FILE2) != null
    }

    /** The text a reader gets back, through a save and a reload — the file, not the in-memory page. */
    private fun extracted(document: PDDocument): String {
        val bytes = ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        return PDDocument.load(ByteArrayInputStream(bytes)).use { PDFTextStripper().getText(it).trim() }
    }
}
