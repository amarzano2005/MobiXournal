package com.mobixournal.render

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The tablet's exported PDF is the **same shape the window writes**: the marks are page content and
 * nothing else. No `/Highlight`, `/Ink` or `/Text` annotation dictionaries are added, so the two shells
 * agree page for page and a reader's annotation pane stays empty (see `render/PdfExporter.kt`).
 *
 * A test rather than a comment because the difference is invisible on screen: dictionaries would draw
 * nothing new, so only the file itself can say whether they are there.
 */
class PdfExportTest {

    private val width = 595.0
    private val height = 842.0
    private val black = 0xFF000000.toInt()

    private fun pen() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(StrokePoint(20.0, 20.0, 2.0), StrokePoint(120.0, 40.0, 2.0)),
        uniformWidth = true,
    )

    private fun highlighter() = Stroke(
        tool = Tool.HIGHLIGHTER,
        color = 0x7FFFFF00.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(45.0, 106.0, 16.0), StrokePoint(95.0, 106.0, 16.0)),
        uniformWidth = true,
    )

    private fun page(vararg elements: Element) = Page(
        width = width,
        height = height,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
    )

    private fun export(vararg pages: Page): ByteArray {
        val out = ByteArrayOutputStream()
        PdfExporter(null, null).export(Document(pages = pages.toList()), out)
        return out.toByteArray()
    }

    @Test
    fun theExportedPageCarriesItsPaintAndNoAnnotationDictionaries() {
        val bytes = export(page(pen(), TextElement("Sans", 12.0, 300.0, 300.0, black, "Remember this"), highlighter()))

        PDDocument.load(bytes).use { exported ->
            assertEquals(1, exported.numberOfPages)
            val page = exported.getPage(0)
            assertEquals(width.toFloat(), page.mediaBox.width, 0.01f)
            assertEquals(height.toFloat(), page.mediaBox.height, 0.01f)
            assertTrue("the page is painted", page.contents.use { it.readBytes().isNotEmpty() })
            assertNull(
                "no /Annots array on the page",
                page.cosObject.getDictionaryObject(COSName.ANNOTS),
            )
            assertTrue("and PDFBox lists no annotation for it either", page.annotations.isEmpty())
        }

        // The page dictionary is written uncompressed, so a dictionary of any kind would be visible here.
        val raw = String(bytes, Charsets.ISO_8859_1)
        assertFalse("no annotation array is in the file", raw.contains("/Annots"))
        listOf("/Highlight", "/Ink", "/Popup").forEach { subtype ->
            assertFalse("nor a $subtype dictionary", raw.contains("/$subtype"))
        }
    }

    @Test
    fun everyPageIsWrittenAndNoneOfThemIsAnnotated() {
        val bytes = export(page(pen()), page(highlighter()))

        PDDocument.load(bytes).use { exported ->
            assertEquals(2, exported.numberOfPages)
            exported.pages.forEach { page ->
                assertTrue(page.annotations.isEmpty())
                assertNull(page.cosObject.getDictionaryObject(COSName.ANNOTS))
            }
        }
    }
}
