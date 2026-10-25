package com.mobixournal.desktop

import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.render.PageSvgWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.w3c.dom.Element as XmlElement

/**
 * The window's SVG export, exercised headlessly: the files it names, the geometry it carries and the
 * picture it puts under the ink.
 *
 * The claim worth pinning is **agreement with the tablet**, not "a file appeared". `PageSvgWriter` is
 * the shared writer, so the strongest assertion available is that a page with no picture is byte for
 * byte what `PageSvgWriter.write(page)` produces — a window that grows its own arm for the sheet, the
 * ruling or a living entity would fail that even while it still looked plausible.
 *
 * The second claim is the one the window alone can make: an annotated-PDF page is pixels, and its
 * exported file carries that picture as one `<image>` over the page box, while a ruled page carries no
 * picture and still carries its ruling.
 */
class DesktopSvgExportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val black = 0xFF000000.toInt()

    private val svgNamespace = "http://www.w3.org/2000/svg"

    private fun ink() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(StrokePoint(10.0, 10.0, 1.0), StrokePoint(40.0, 60.0, 1.0)),
        uniformWidth = true,
    )

    private fun words() = TextElement(
        font = "Sans",
        size = 12.0,
        x = 72.0,
        y = 96.0,
        color = black,
        content = "Exported",
    )

    private fun ruledPage() = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "ruled"),
        layers = listOf(Layer(listOf(ink(), words()))),
    )

    private fun pdfPage() = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Pdf(filename = "scan.pdf", pageNo = 0, domain = "absolute"),
        layers = listOf(Layer(listOf(ink()))),
    )

    /** A two-page document named `notes.xopp` — two different backgrounds, so both arms are covered. */
    private fun document(vararg pages: Page = arrayOf(ruledPage(), pdfPage())): DesktopDocument {
        val file = File(temp.root, "notes.xopp")
        val model = Document(pages = pages.toList())
        return DesktopDocument(
            file = file,
            source = PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
        )
    }

    /** A 4×4 PNG, the shape [PageBackdrop] would hand over for an annotated PDF page. */
    private fun picture(): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private fun parse(file: File): XmlElement =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
            .documentElement

    private fun count(root: XmlElement, tag: String): Int = root.getElementsByTagNameNS(svgNamespace, tag).length

    @Test
    fun oneFilePerPageNamedInReadingOrder() {
        val folder = File(temp.root, "export")
        val files = DesktopSvgExport.write(document(), folder) { null }

        assertEquals("one file per page", 2, files.size)
        assertEquals("padded page number for the first", "notes-p001.svg", files[0].name)
        assertEquals("and for the second", "notes-p002.svg", files[1].name)
        assertTrue("and they are where the caller asked", files.all { it.parentFile == folder })
        assertFalse(
            "no staging file outlives the export",
            folder.listFiles().orEmpty().any { it.name.endsWith(".writing") },
        )
        for (file in files) {
            assertTrue("$file is truncated", file.length() > 0)
        }
    }

    @Test
    fun everyPageIsAWellFormedDocumentOfItsOwnSize() {
        val files = DesktopSvgExport.write(document()) { null }

        for (file in files) {
            val root = parse(file)
            assertEquals("the root is the SVG element", "svg", root.localName)
            assertEquals("the exported sheet is the page's width", 595.0, root.getAttribute("width").toDouble(), 1e-6)
            assertEquals("and its height", 842.0, root.getAttribute("height").toDouble(), 1e-6)
            assertEquals("with its own ink", 1, count(root, "polyline"))
        }
    }

    @Test
    fun thePictureBehindAPdfPageIsOneImageOverTheSheet() {
        val files = DesktopSvgExport.write(document()) { background ->
            if (background is Background.Pdf) picture() else null
        }

        val pdfRoot = parse(files[1])
        val ruledRoot = parse(files[0])
        assertEquals("the annotated page carries its picture once", 1, count(pdfRoot, "image"))
        val image = pdfRoot.getElementsByTagNameNS(svgNamespace, "image").item(0) as XmlElement
        assertEquals("over the page's full width", 595.0, image.getAttribute("width").toDouble(), 1e-6)
        assertEquals("and full height", 842.0, image.getAttribute("height").toDouble(), 1e-6)
        assertTrue(
            "as an inline data URL, so the file stands alone",
            image.getAttributeNS("http://www.w3.org/1999/xlink", "href").startsWith("data:image/png;base64,"),
        )
        assertEquals("a ruled page has no picture", 0, count(ruledRoot, "image"))
        assertTrue("but keeps its ruling", count(ruledRoot, "line") > 0)
    }

    @Test
    fun aPageWithNoPictureIsExactlyTheSharedWritersPage() {
        val files = DesktopSvgExport.write(document()) { null }

        // The drift guard: the window must not keep an opinion of its own about a page. If someone adds
        // an arm here that the tablet does not have, this comparison is what fails.
        assertEquals(
            "the window's page is the shared writer's page",
            PageSvgWriter.write(ruledPage()),
            files[0].readText(),
        )
        assertEquals(
            "and passing no picture changes nothing",
            PageSvgWriter.write(pdfPage()),
            files[1].readText(),
        )
    }

    @Test
    fun aDestinationThatCannotHoldThePagesIsRefusedInWords() {
        val notAFolder = File(temp.root, "notes.txt").apply { writeText("not a folder") }

        val refusal = runCatching { DesktopSvgExport.write(document(), notAFolder) { null } }.exceptionOrNull()

        assertTrue("the refusal says what happened: $refusal", refusal is DesktopSaves.SaveRefused)
        assertTrue(
            "in a sentence a person can act on: ${refusal?.message}",
            refusal?.message?.contains("folder", ignoreCase = true) == true,
        )
        assertFalse(
            "and nothing was written beside it",
            temp.root.listFiles().orEmpty().any { it.name.endsWith(".svg") },
        )
    }
}
