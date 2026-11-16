package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopContainer
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.DesktopPdfExport
import com.mobixournal.desktop.io.DesktopSaves
import com.mobixournal.desktop.sheet.PageBackdrop

import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.OutlineNode
import com.mobixournal.format.model.OutlineTarget
import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.io.DocumentEncoder
import com.mobixournal.render.BackgroundGrid
import com.mobixournal.render.BackgroundRulings
import com.mobixournal.render.CanvasGeometry
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import org.apache.pdfbox.pdmodel.graphics.color.PDColor
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **PDF export**, as a file a PDF reader opens.
 *
 * Every case re-opens the produced bytes with PDFBox instead of inspecting the exporter's arguments,
 * because the claim being made is about the document the user gets: two pages of the sheet's size, the
 * words of both, a ruled sheet that really carries its ruling, and a picture behind a page that is
 * there. A test of the calls would pass while the ruling was drawn white on white — which is exactly
 * the failure this suite exists to catch, since the paper used to live in the app's own painter.
 */
class DesktopPdfExportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val black = 0xFF000000.toInt()

    private fun text(content: String, y: Double = 100.0) = TextElement(
        font = "Sans",
        size = 18.0,
        x = 72.0,
        y = y,
        color = black,
        content = content,
    )

    private fun stroke() = Stroke(
        tool = Tool.PEN,
        color = black,
        capStyle = "round",
        points = listOf(StrokePoint(100.0, 200.0, 1.0), StrokePoint(200.0, 260.0, 1.0)),
        uniformWidth = true,
    )

    private fun sheet(style: String = "graph", vararg elements: com.mobixournal.format.model.Element) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), style),
        layers = listOf(Layer(elements.toList())),
    )

    /** A document over [pages], with the window's own page window and a backdrop over [temp]. */
    private fun document(
        vararg pages: Page,
        backdrop: PageBackdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
        shape: DocumentEncoder.SaveShape = DocumentEncoder.SaveShape(),
        outline: OutlineTree = OutlineTree.EMPTY,
    ): DesktopDocument {
        val model = Document(pages = pages.toList(), outline = outline)
        return DesktopDocument(
            file = File(temp.root, "notes.xopp"),
            source = PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = backdrop,
            shape = shape,
        )
    }

    private fun export(document: DesktopDocument, name: String = "export.pdf"): Pair<File, PDDocument> {
        val target = File(temp.root, name)
        val pages = DesktopPdfExport.write(document, target, document.backdrop)
        val doc = PDDocument.load(target)
        assertEquals("the exporter reports what it wrote", doc.numberOfPages, pages)
        return target to doc
    }

    @Test
    fun onlyTheChosenPagesAreWrittenInTheNotebooksOwnOrder() {
        val doc = document(
            sheet("graph", text("Page one"), stroke()),
            sheet("plain", text("Page two")),
            sheet("graph", text("Page three"), stroke()),
            sheet("plain", text("Page four")),
        )
        val target = File(temp.root, "picked.pdf")

        // Notebook pages 2 and 4, named last first: the file holds them as the notebook reads them.
        val written = DesktopPdfExport.write(doc, target, doc.backdrop, only = listOf(3, 1))

        PDDocument.load(target).use { pdf ->
            assertEquals("the exporter reports what it wrote", 2, written)
            assertEquals("only the two pages the answer named", 2, pdf.numberOfPages)
            assertEquals("the second sheet comes first", "Page two", pageText(pdf, 1))
            assertEquals("and the fourth after it", "Page four", pageText(pdf, 2))
            listOf(0, 1).forEach { index ->
                assertTrue(
                    "each page carries its ink",
                    pdf.getPage(index).contents.use { it.readBytes().isNotEmpty() },
                )
            }
        }
    }

    @Test
    fun theIndexIsWrittenAsBookmarksOnTheExportedPages() {
        val one = sheet("plain", text("Page one")).copy(id = "pg_one")
        val two = sheet("plain", text("Page two")).copy(id = "pg_two")
        val three = sheet("plain", text("Page three")).copy(id = "pg_three")
        val outline = OutlineTree(
            nodes = listOf(
                OutlineNode(
                    "bkm_1", "Chapter", OutlineTarget.Page("pg_three"),
                    listOf(OutlineNode("bkm_2", "Section", OutlineTarget.Page("pg_one"))),
                ),
                OutlineNode("bkm_3", "Not exported", OutlineTarget.Page("pg_two")),
            ),
        )
        val doc = document(one, two, three, outline = outline)
        val target = File(temp.root, "index.pdf")

        // Pages 1 and 3: written in the notebook's order, so page 3 is the file's second sheet.
        DesktopPdfExport.write(doc, target, doc.backdrop, only = listOf(2, 0))

        PDDocument.load(target).use { pdf ->
            val root = pdf.documentCatalog.documentOutline ?: error("no outline was written")
            val chapter = root.firstChild
            assertEquals("the reader's title", "Chapter", chapter.title)
            assertEquals(
                "it opens the sheet notebook page 3 was written as",
                1,
                pdf.pages.indexOf((chapter.destination as PDPageFitDestination).page),
            )
            val section = chapter.firstChild
            assertEquals("Section", section.title)
            assertEquals(0, pdf.pages.indexOf((section.destination as PDPageFitDestination).page))
            assertNull("a row whose page was not exported is left out", chapter.nextSibling)
        }
    }

    @Test
    fun anAnswerThatNamesNoPageIsRefusedAndWritesNothing() {
        val doc = document(sheet("plain", text("Only page")))
        val target = File(temp.root, "none.pdf")
        target.writeText("keep me")

        val refused = runCatching {
            DesktopPdfExport.write(doc, target, doc.backdrop, only = emptyList())
        }.exceptionOrNull()

        assertTrue("an empty answer is refused in words: $refused", refused is DesktopSaves.SaveRefused)
        assertEquals("and the file that was there keeps its bytes", "keep me", target.readText())
    }

    /** The words of page [one] (1-based), as a reader's PDF viewer would copy them. */
    private fun pageText(pdf: PDDocument, one: Int): String =
        PDFTextStripper().apply { startPage = one; endPage = one }.getText(pdf).trim()

    @Test
    fun everyPageOfTheDocumentIsAPageOfThePdfAtItsOwnSize() {
        val (_, pdf) = export(
            document(
                sheet("graph", text("First page"), stroke()),
                sheet("plain", text("Second page")),
            ),
        )

        pdf.use {
            assertEquals("one sheet per page", 2, it.numberOfPages)
            assertEquals("the first sheet's width", 595.0, it.getPage(0).mediaBox.width.toDouble(), 0.5)
            assertEquals("and its height", 842.0, it.getPage(0).mediaBox.height.toDouble(), 0.5)
            val words = PDFTextStripper().apply { startPage = 1; endPage = 2 }.getText(it)
            assertTrue("the first page's words: $words", words.contains("First page"))
            assertTrue("and the second's", words.contains("Second page"))
        }
    }

    /**
     * The paper is the shell's half of the page that used to be missing entirely. A rule is drawn at the
     * sheet's own spacing, so this reads the pixel that should hold one (and the same pixel on a plain
     * sheet, which must stay paper-white).
     */
    @Test
    fun aRuledPagePrintsItsRulingAndAPlainPageStaysWhite() {
        val ruleY = BackgroundGrid.lines(842.0, BackgroundRulings.defaultSpacingPt("graph")).first().toInt()

        // Two files, because a PDF still open for reading holds its name on Windows.
        val (_, ruled) = export(document(sheet("graph")), "ruled.pdf")
        val (_, plain) = export(document(sheet("plain")), "plain.pdf")
        ruled.use { pdf ->
            val rendered = PDFRenderer(pdf).renderImageWithDPI(0, 72f)
            assertFalse(
                "a rule is not paper: ${rendered.rgbAt(5, ruleY).toString(16)}",
                isWhite(rendered.rgbAt(5, ruleY)),
            )
        }
        plain.use { pdf ->
            val rendered = PDFRenderer(pdf).renderImageWithDPI(0, 72f)
            assertTrue(
                "a plain sheet carries no rule at all: ${rendered.rgbAt(5, ruleY).toString(16)}",
                isWhite(rendered.rgbAt(5, ruleY)),
            )
        }
    }

    /**
     * A page written over an annotated PDF is pixels the model cannot hold, so the exporter hands it to
     * `PageBackdrop` and flattens the result. The check is the colour: the source page is a grey sheet, so
     * the exported page must be grey where the source is — not the white paper the ruling alone would leave.
     */
    @Test
    fun thePictureBehindAPdfPageIsFlattenedUnderTheInk() {
        val source = greyPdf()
        val page = sheet("plain").copy(
            background = Background.Pdf(
                filename = source.absolutePath,
                pageNo = 0,
                domain = "absolute",
            ),
        )

        val (_, pdf) = export(document(page))

        pdf.use {
            val rendered = PDFRenderer(it).renderImageWithDPI(0, 72f)
            val pixel = rendered.rgbAt(300, 400)
            assertEquals("the source page's grey is the export's", 128, (pixel shr 16) and 0xFF)
            assertEquals("and it covers the whole sheet", 128, (pixel shr 8) and 0xFF)
        }
    }

    /**
     * A page whose PDF source is gone prints blank paper today, which is the one output nobody notices
     * before handing the file on. It is refused instead, by sheet number, and the PDF that was already
     * at the destination keeps its bytes so the reader can relink and export again.
     */
    @Test
    fun aPageWhosePdfSourceIsGoneIsRefusedAndTheFileThatWasThereKeepsItsBytes() {
        val source = greyPdf()
        val page = sheet("plain").copy(
            background = Background.Pdf(filename = source.absolutePath, pageNo = 0, domain = "absolute"),
        )
        val document = document(page)
        source.delete()
        val target = File(temp.root, "kept.pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val refusal = runCatching { DesktopPdfExport.write(document, target, document.backdrop) }.exceptionOrNull()

        assertTrue("a missing sheet is a refusal: $refusal", refusal is DesktopSaves.SaveRefused)
        val message = refusal?.message.orEmpty()
        assertTrue(
            "that names the sheet and what to do: $message",
            message.contains("Page 1") && message.contains("Import the PDF again"),
        )
        assertEquals("and the destination is exactly what it was", listOf<Byte>(1, 2, 3), target.readBytes().toList())
    }

    /** A page number the source does not have is the same lie as a missing file, so it is reported too. */
    @Test
    fun aPageNumberTheSourceDoesNotHaveIsRefusedRatherThanClamped() {
        val source = greyPdf()
        val page = sheet("plain").copy(
            background = Background.Pdf(filename = source.absolutePath, pageNo = 9, domain = "absolute"),
        )
        val document = document(page)

        val refusal = runCatching {
            DesktopPdfExport.write(document, File(temp.root, "past-the-end.pdf"), document.backdrop)
        }.exceptionOrNull()

        assertTrue("nothing truthful can be printed: $refusal", refusal is DesktopSaves.SaveRefused)
        assertTrue("and the sheet is named: ${refusal?.message}", refusal?.message?.contains("Page 1") == true)
    }

    /** The raster fallback is untouched: a page that sits on a picture still prints that picture. */
    @Test
    fun aPageOnAPictureStillPrintsThePicture() {
        val picture = greyPng()
        val page = sheet("plain").copy(
            background = Background.Pixmap(domain = "absolute", filename = picture.name, bundledAsset = null),
        )
        val document = document(page)

        val (_, pdf) = export(document, "picture.pdf")

        pdf.use {
            val pixel = PDFRenderer(it).renderImageWithDPI(0, 72f).rgbAt(300, 400)
            assertEquals("the picture is under the ink", 128, (pixel shr 16) and 0xFF)
        }
    }

    /**
     * The import and the export agree about a cropped page: the sheet an import makes is the box the
     * export imports the source page by, so a mark drawn on the sheet lands on the same point of the
     * picture after the export.
     */
    @Test
    fun anImportedCroppedSheetExportsByTheSameBoxItIsDrawnBy() {
        val file = File(temp.root, "cropped.pdf")
        PDDocument().use { pdf ->
            val page = PDPage(PDRectangle(400f, 600f))
            page.cropBox = PDRectangle(300f, 500f).apply { lowerLeftX = 50f; lowerLeftY = 40f; upperRightX = 350f; upperRightY = 540f }
            pdf.addPage(page)
            pdf.save(file)
        }
        val sheet = com.mobixournal.desktop.io.DesktopPdfImport.open(file).use { pdf ->
            com.mobixournal.desktop.io.DesktopPdfImport.pages(pdf, file.absolutePath).single()
        }
        val document = document(sheet)

        val (_, exported) = export(document, "cropped-export.pdf")

        exported.use {
            val box = it.getPage(0).cropBox
            assertEquals("the printed page is the sheet's width", sheet.width, box.width.toDouble(), 0.01)
            assertEquals("and its height", sheet.height, box.height.toDouble(), 0.01)
        }
    }

    /** A board has no pages to print, and saying so is better than printing one tile of it. */
    @Test
    fun aBoardIsRefusedInWords() {
        val board = document(
            sheet("plain"),
            shape = DocumentEncoder.SaveShape(
                mode = CanvasMode.INFINITE_2D,
                geometry = CanvasGeometry(
                    mode = CanvasMode.INFINITE_2D,
                    widthPt = 595.0,
                    heightPt = 842.0,
                    chunkSizePt = 500.0,
                    chunkCoords = listOf(0 to 0),
                    originOffsetPt = 0.0,
                ),
            ),
        )

        val refusal = runCatching {
            DesktopPdfExport.write(board, File(temp.root, "board.pdf"), board.backdrop)
        }.exceptionOrNull()

        assertTrue("the refusal says what happened: $refusal", refusal is DesktopSaves.SaveRefused)
        assertTrue(
            "in a sentence a person can act on: ${refusal?.message}",
            refusal?.message?.contains("board", ignoreCase = true) == true,
        )
        assertFalse("and nothing was written", File(temp.root, "board.pdf").exists())
    }

    /** A document with nothing in it is refused rather than turned into an empty file. */
    @Test
    fun aDocumentWithNoPagesIsRefused() {
        val empty = document()

        val refusal = runCatching {
            DesktopPdfExport.write(empty, File(temp.root, "empty.pdf"), empty.backdrop)
        }.exceptionOrNull()

        assertTrue("the refusal says what happened: $refusal", refusal is DesktopSaves.SaveRefused)
        assertFalse("and nothing was written", File(temp.root, "empty.pdf").exists())
    }

    // ---- fixtures ----------------------------------------------------------------------------

    /** A one-page PDF filled with mid-grey, the stand-in for an annotated source document. */
    private fun greyPdf(): File {
        val file = File(temp.root, "source.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(595f, 842f))
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.setNonStrokingColor(PDColor(floatArrayOf(0.5f, 0.5f, 0.5f), PDDeviceRGB.INSTANCE))
                cs.addRect(0f, 0f, 595f, 842f)
                cs.fill()
            }
            doc.save(file)
        }
        return file
    }

    /** A mid-grey PNG beside the document — a picture background the window can resolve by name. */
    private fun greyPng(): File {
        val image = BufferedImage(595, 842, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until image.width) for (y in 0 until image.height) image.setRGB(x, y, 0x808080)
        val file = File(temp.root, "picture.png")
        ImageIO.write(image, "png", file)
        return file
    }

    private fun BufferedImage.rgbAt(x: Int, y: Int): Int = getRGB(x, y) and 0xFFFFFF

    private fun isWhite(rgb: Int): Boolean = rgb == 0xFFFFFF
}
