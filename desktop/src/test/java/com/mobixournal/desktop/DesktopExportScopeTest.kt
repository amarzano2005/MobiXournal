package com.mobixournal.desktop

import com.mobixournal.desktop.io.DesktopContainer
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.DesktopExportNotice
import com.mobixournal.desktop.io.DesktopExportQueue
import com.mobixournal.desktop.io.askExportPdfPages
import com.mobixournal.desktop.io.exportPdf
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.sheet.PageBackdrop
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import com.mobixournal.render.ExportPageScope
import com.mobixournal.render.PageRangePlan
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Export PDF's page question** in the window: which pages the command asks about, what an answer comes to,
 * and what a refusal or a `Cancel` leaves alone.
 *
 * The exporter is tested where it lives (`DesktopPdfExportTest`); this suite is about the question in front of
 * it — that `Export PDF` asks *which pages* before it asks *where*, that the answer reaches the file, and that
 * a question put away writes nothing. Those are the three ways a working exporter can still hand the reader the
 * wrong file.
 */
class DesktopExportScopeTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Before
    fun reset() {
        DesktopExportQueue.cancelScope()
        DesktopExportNotice.clear()
    }

    @After
    fun clear() {
        DesktopExportQueue.cancelScope()
        DesktopExportQueue.pickPdfDestination = null
        DesktopExportNotice.clear()
    }

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
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(listOf(text(content)))),
    )

    private fun document(): DesktopDocument {
        val model = Document(pages = listOf(page("First page"), page("Second page"), page("Third page")))
        return DesktopDocument(
            file = File(temp.root, "notes.xopp"),
            source = PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
        )
    }

    private fun state(document: DesktopDocument = document()): DesktopShellState {
        val state = DesktopShellState(settings = DesktopSettings(File(temp.root, "settings.json")))
        state.tabs.open(document)
        return state
    }

    @Test
    fun theCommandAsksWhichPagesAndOpensOnEveryPage() {
        val state = state()
        try {
            state.askExportPdfPages()

            val request = DesktopExportQueue.scopeRequest
            assertNotNull("the question is up", request)
            assertEquals("and it counts the document's pages", 3, request!!.pageCount)
            assertEquals("opening on every page, which is what an export always was", ExportPageScope.ALL, request.scope)
            assertEquals("so the card's first answer is the whole notebook", listOf(0, 1, 2), request.chosen)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun aTypedAnswerExportsThosePagesAndOnlyThem() {
        val state = state()
        try {
            val target = File(temp.root, "picked.pdf")
            state.askExportPdfPages()
            // Third page first, as a reader might type it: the file keeps the notebook's order.
            DesktopExportQueue.editScope { it.copy(scope = ExportPageScope.SELECTED, pages = "3,1") }
            var asked = 0
            DesktopExportQueue.pickPdfDestination = { asked++ }
            DesktopExportQueue.confirmScope()

            assertEquals("the answer is taken, and the window is asked where", 1, asked)
            assertNull("the question is put away", DesktopExportQueue.scopeRequest)

            state.exportPdf(target)

            PDDocument.load(target).use { pdf ->
                assertEquals("only the two pages the answer named", 2, pdf.numberOfPages)
                assertEquals("the first page first", "First page", words(pdf, 1))
                assertEquals("the third after it", "Third page", words(pdf, 2))
            }
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun thePageInViewIsOneAnswer() {
        val state = state()
        try {
            state.askExportPdfPages()
            DesktopExportQueue.editScope { it.copy(scope = ExportPageScope.CURRENT) }

            assertEquals("the page the window is showing", listOf(state.tabs.active!!.page), DesktopExportQueue.scopeRequest!!.chosen)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun aRefusedFieldKeepsTheQuestionUpAndAsksNothing() {
        val state = state()
        try {
            state.askExportPdfPages()
            DesktopExportQueue.editScope { it.copy(scope = ExportPageScope.SELECTED, pages = "9") }
            var asked = 0
            DesktopExportQueue.pickPdfDestination = { asked++ }

            val plan = DesktopExportQueue.scopeRequest!!.plan
            assertTrue("a page past the end is refused", plan is PageRangePlan.Invalid)
            DesktopExportQueue.confirmScope()

            assertNotNull("so the card stays up", DesktopExportQueue.scopeRequest)
            assertEquals("and no destination was asked for", 0, asked)
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun cancelPutsTheQuestionAwayAndWritesNothing() {
        val state = state()
        try {
            state.askExportPdfPages()
            DesktopExportQueue.cancelScope()

            assertNull("the question is gone", DesktopExportQueue.scopeRequest)
            assertNull("and nothing is waiting to be written", DesktopExportQueue.takePdfPages())
        } finally {
            state.tabs.closeAll()
        }
    }

    /** The words of page [one] (1-based), as a reader's viewer would copy them. */
    private fun words(pdf: PDDocument, one: Int): String =
        PDFTextStripper().apply { startPage = one; endPage = one }.getText(pdf).trim()
}
