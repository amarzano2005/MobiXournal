package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.mobix.PageSource
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's two **export commands**: `Ctrl+E`, the menu's rows, and what a person sees afterwards.
 *
 * The exporters are tested where they live (`DesktopPdfExportTest`, `DesktopSvgExportTest`); this suite is
 * about the command — that the key means the command, that the command asks the window where to write,
 * that it does nothing without a document, and that its outcome reaches either the status bar's line or
 * the failure card. Those are the three ways a working exporter can still be a feature nobody can use.
 */
class DesktopExportCommandTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun clearNotice() {
        DesktopExportNotice.clear()
    }

    private fun settings() = DesktopSettings(File(temp.root, "settings.json"))

    private fun text(content: String) = TextElement(
        font = "Sans",
        size = 18.0,
        x = 72.0,
        y = 100.0,
        color = 0xFF000000.toInt(),
        content = content,
    )

    private fun page(content: String) = Page(
        width = 595.0,
        height = 842.0,
        background = com.mobixournal.format.model.Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(listOf(text(content)))),
    )

    /** A two-page document named `notes.xopp`, with the window's own page window and a backdrop. */
    private fun document(): DesktopDocument {
        val model = Document(pages = listOf(page("First page"), page("Second page")))
        return DesktopDocument(
            file = File(temp.root, "notes.xopp"),
            source = PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
        )
    }

    private fun state(document: DesktopDocument? = document()): DesktopShellState {
        val state = DesktopShellState(settings = settings())
        document?.let { state.tabs.open(it) }
        return state
    }

    @Test
    fun ctrlEIsThePdfExportAndCtrlShiftEISNot() {
        assertEquals(ShellCommand.EXPORT_PDF, shellCommand(Key.E, ctrl = true, shift = false))
        assertNull("the shifted key is not a second binding", shellCommand(Key.E, ctrl = true, shift = true))
        assertNull("and a bare E is the eraser's neighbour, not an export", shellCommand(Key.E, ctrl = false, shift = false))
    }

    @Test
    fun theCommandAsksTheWindowWhereToWriteAndIsUnavailableWithNoDocument() {
        val state = state()
        try {
            var asked = 0
            assertTrue("the command is handled", state.apply(ShellCommand.EXPORT_PDF) { asked++ })
            assertEquals("and it asked the window for a destination", 1, asked)

            val empty = state(document = null)
            try {
                assertFalse("with nothing open there is nothing to export", empty.apply(ShellCommand.EXPORT_PDF) { asked++ })
                assertEquals("so the dialog never opened", 1, asked)
            } finally {
                empty.tabs.closeAll()
            }
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun exportingAPdfWritesTheDocumentAndSaysWhatItWrote() {
        val state = state()
        try {
            val target = File(temp.root, "notes.pdf")
            val outcome = state.exportPdf(target)

            assertTrue("the export reported success: $outcome", outcome is ExportOutcome.Written)
            PDDocument.load(target).use { pdf ->
                assertEquals("both pages are in the file", 2, pdf.numberOfPages)
                val words = PDFTextStripper().getText(pdf)
                assertTrue("with the second page's words: $words", words.contains("Second page"))
            }
            val notice = DesktopExportNotice.text
            assertNotNull("the status bar says what happened", notice)
            assertTrue("naming the file: $notice", notice!!.contains("notes.pdf"))
            assertTrue("and how many pages: $notice", notice.contains("2 pages"))
            assertNull("and there is no failure card", state.failure)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun exportingSvgWritesOneFilePerPageBesideTheDocument() {
        val state = state()
        try {
            val outcome = state.exportSvg()

            assertTrue("the export reported success: $outcome", outcome is ExportOutcome.Written)
            val pages = temp.root.listFiles().orEmpty().filter { it.name.endsWith(".svg") }
            assertEquals("one file per page", 2, pages.size)
            assertTrue(
                "named after the document: ${pages.map { it.name }}",
                pages.all { it.name.startsWith("notes-p") },
            )
            assertTrue("and the status bar says so: ${DesktopExportNotice.text}", DesktopExportNotice.text!!.contains("SVG"))
        } finally {
            state.tabs.closeAll()
        }
    }

    /**
     * **Export PNG**: the same pages as pictures, one file each, at twice the page's own units — and
     * painted, not blank: the page's words are ink in the bitmap, which is what separates a rendered page
     * from a white rectangle with a file name.
     */
    @Test
    fun exportingPngWritesOneImagePerPageBesideTheDocument() {
        val state = state()
        try {
            val outcome = state.exportPng()

            assertTrue("the export reported success: $outcome", outcome is ExportOutcome.Written)
            val pages = temp.root.listFiles().orEmpty().filter { it.name.endsWith(".png") }.sortedBy { it.name }
            assertEquals("one image per page", 2, pages.size)
            assertEquals(
                "named after the document, in reading order",
                listOf("notes-p001.png", "notes-p002.png"),
                pages.map { it.name },
            )
            val first = ImageIO.read(pages[0])
            assertEquals("the sheet at two pixels per point", (595.0 * 2).roundToInt(), first.width)
            assertEquals((842.0 * 2).roundToInt(), first.height)
            var ink = 0
            for (y in 180 until 240) {
                for (x in 140 until 600) {
                    if ((first.getRGB(x, y) and 0xFF) < 0x90) ink++
                }
            }
            assertTrue("the page's words are painted into the image, not left white", ink > 40)
            assertTrue("and the status bar says so: ${DesktopExportNotice.text}", DesktopExportNotice.text!!.contains("PNG"))
            assertNull("with no failure card", state.failure)
        } finally {
            state.tabs.closeAll()
        }
    }

    /** A destination that cannot hold the export reports itself in words, and writes nothing. */
    @Test
    fun aRefusedDestinationBecomesTheFailureCard() {
        val state = state()
        try {
            val notAFolder = File(temp.root, "blocked").apply { writeText("a file, not a folder") }
            val target = File(notAFolder, "notes.pdf")

            val outcome = state.exportPdf(target)

            assertTrue("the outcome is a failure: $outcome", outcome is ExportOutcome.Failed)
            val failure = state.failure
            assertNotNull("the window says so", failure)
            assertTrue(
                "in a sentence about the export: ${failure!!.title}",
                failure.title.contains("export", ignoreCase = true),
            )
            assertFalse("and nothing was written", target.exists())
            assertNull("with no success line to contradict it", DesktopExportNotice.text)
        } finally {
            state.tabs.closeAll()
        }
    }
}
