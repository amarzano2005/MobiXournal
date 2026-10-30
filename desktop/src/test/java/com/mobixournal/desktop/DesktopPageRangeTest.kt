package com.mobixournal.desktop

import com.mobixournal.render.PageRangePlan
import com.mobixournal.render.PageRangeRequest
import com.mobixournal.render.PageRanges

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Background
import com.mobixournal.format.mobix.PageSource
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Manage pages** — the advanced half of the page section: a whole range moved or removed at once.
 *
 * The commands are checked against a real tab (the list they change, the undo step they record, the
 * document the next save writes), the refusals against the panel's own rules, and the surface against the
 * material it must wear: the accent on *Move pages* and the document red on *Delete pages*.
 */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopPageRangeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val counters = IntArray(2)

    private fun tabs(pages: Int = 6, name: String = "range"): DesktopTabs {
        val file: File = temp.newFile("$name.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(pages)) }
        return DesktopTabs().also { it.open(DocumentLoader.load(file)) }
    }

    private fun pagesOf(tabs: DesktopTabs) =
        DesktopPages(tabs, { _, _ -> }, { 595.0 to 842.0 })

    private fun DesktopPages.typed(first: String, last: String, target: String) {
        editRange { PageRangeRequest(first, last, target) }
    }

    @Test
    fun `opening proposes the page in view and the front of the document`() {
        val tabs = tabs()
        val tab = tabs.active!!
        tab.page = 2
        val pages = pagesOf(tabs)

        assertTrue(pages.openRange())

        assertEquals(PageRangeRequest(first = "3", last = "3", target = "1"), pages.range)
        pages.closeRange()
        assertEquals(null, pages.range)
    }

    @Test
    fun `deleting a range takes the run out as one undo step`() {
        val tabs = tabs(pages = 6)
        val tab = tabs.active!!
        val pages = pagesOf(tabs)
        assertEquals(6, tab.document.pageCount)

        assertTrue(pages.openRange())
        pages.typed(first = "2", last = "4", target = "1")
        assertTrue("pages 2..4 are removed", pages.deleteRange())

        assertEquals("three pages left", 3, tab.document.pageCount)
        assertEquals("the panel closed on success", null, pages.range)
        assertTrue("one step to undo", tab.undo())
        assertEquals("and the run is back", 6, tab.document.pageCount)
    }

    @Test
    fun `moving a range lands its first page where the target says`() {
        val tabs = tabs(pages = 5)
        val tab = tabs.active!!
        val pages = pagesOf(tabs)
        val labels = (0 until 5).map { tab.document.page(it).width }

        assertTrue(pages.openRange())
        pages.typed(first = "1", last = "2", target = "5")
        assertTrue(pages.moveRange())

        assertEquals("the run moved to the end", listOf(labels[2], labels[3], labels[4], labels[0], labels[1]),
            (0 until 5).map { tab.document.page(it).width })
        assertEquals("the view follows the range", 4, tab.page)
        assertTrue(tab.undo())
        assertEquals(labels, (0 until 5).map { tab.document.page(it).width })
    }

    @Test
    fun `the whole document is never deleted, and a target inside the run does nothing`() {
        val tabs = tabs(pages = 4)
        val tab = tabs.active!!
        val pages = pagesOf(tabs)
        val before = (0 until 4).map { tab.document.page(it).width }

        assertTrue(pages.openRange())
        pages.typed(first = "1", last = "4", target = "1")
        assertFalse("every page selected is refused", pages.deleteRange())
        assertEquals(4, tab.document.pageCount)

        pages.typed(first = "2", last = "3", target = "3")
        assertFalse("a target inside the run changes nothing", pages.moveRange())
        assertEquals(before, (0 until 4).map { tab.document.page(it).width })
    }

    /** The user's own case: pruning the opening pages of a scanned PDF must not take the PDF with them. */
    @Test
    fun `deleting a range that holds the pdf reference keeps the pdf`() {
        val pdf = File(temp.root, "scan.pdf")
        PDDocument().use { doc ->
            repeat(4) { doc.addPage(PDPage(PDRectangle(595f, 842f))) }
            doc.save(pdf)
        }
        val model = DesktopPdfImport.open(pdf).use { DesktopPdfImport.document(it, pdf.absolutePath) }
        val document = DesktopDocument(
            file = File(temp.root, "scan.xopp"),
            source = PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
        )
        val tabs = DesktopTabs().also { it.open(document) }
        val tab = tabs.active!!
        val pages = pagesOf(tabs)

        assertTrue(pages.openRange())
        pages.typed(first = "1", last = "2", target = "1")
        assertTrue(pages.deleteRange())

        assertEquals("two sheets left", 2, document.pageCount)
        assertNotNull("the document still carries its PDF", document.pdfSource())
        assertEquals("and the numbering follows the source", listOf(2, 3),
            document.model.pages.map { (it.background as Background.Pdf).pageNo })
        for (index in 0 until 2) {
            assertNotNull(document.backdrop.image(document.page(index).background))
        }
    }

    /**
     * The surface's material: the accent is the commit (*Move pages*) and the document red the destructive
     * answer (*Delete pages*), on the family's glass, with English words.
     */
    @Test
    fun `move wears the accent and delete the document red`() {
        val scene = ImageComposeScene(460, 420, Density(1f)) {
            Box(Modifier.size(460.dp, 420.dp)) {
                PageRangePanel(
                    request = PageRangeRequest(first = "2", last = "4", target = "1"),
                    pageCount = 6,
                    onEdit = {},
                    onMove = { counters[0]++ },
                    onDelete = { counters[1]++ },
                    onClose = {},
                )
            }
        }
        try {
            Snapshot.sendApplyNotifications()
            // The surface arrives on the family's spring: let it settle before reading its ink.
            var nanos = 0L
            repeat(60) {
                nanos += 16_000_000L
                scene.render(nanos)
            }
            val pixels = scene.render(nanos).toComposeImageBitmap().toPixelMap()
            var accent = 0
            var red = 0
            for (x in 0 until pixels.width) {
                for (y in 0 until pixels.height) {
                    val colour = pixels[x, y]
                    if (near(colour, DesktopTheme.accent)) accent++
                    if (near(colour, DesktopTheme.documentRed)) red++
                }
            }
            assertTrue("the commit is written in the accent ($accent px)", accent > 40)
            assertTrue("the destructive answer in the document red ($red px)", red > 40)
        } finally {
            scene.close()
        }
    }

    private fun near(colour: Color, target: Color): Boolean =
        kotlin.math.abs(colour.red - target.red) < 0.10f &&
            kotlin.math.abs(colour.green - target.green) < 0.10f &&
            kotlin.math.abs(colour.blue - target.blue) < 0.10f &&
            colour.alpha > 0.9f
}
