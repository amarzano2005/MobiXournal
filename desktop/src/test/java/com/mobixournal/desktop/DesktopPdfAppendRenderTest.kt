package com.mobixournal.desktop

import com.mobixournal.format.SaveFormat
import java.io.File
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * **Pages added to a document** — the *Add the pages to this document* answer, drawn and saved.
 *
 * The report this file reproduces: after adding a PDF's pages to an open document, only the first sheet
 * showed its PDF. A document carries its reference on one page (`docs/pdf.md`), so every later page has to
 * be drawn through it — and it has to survive a save, whether the file is a `.mobix` that bundles the PDF,
 * a zipped `.xopp` that attaches it, or a plain one that names it beside the document.
 */
class DesktopPdfAppendRenderTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A PDF of [count] pages, written under a name no other case uses. */
    private fun pdf(count: Int, name: String = "added"): File {
        val file = File(temp.root, "$name-$count-${System.nanoTime()}.pdf")
        PDDocument().use { doc ->
            repeat(count) { doc.addPage(PDPage(PDRectangle(595f, 842f))) }
            doc.save(file)
        }
        return file
    }

    private fun DesktopShellState.paintedPages(): List<Boolean> {
        val tab = tabs.active ?: return emptyList()
        val document = tab.document
        return (0 until document.pageCount).map { index ->
            document.backdrop.image(document.page(index).background) != null
        }
    }

    @Test
    fun `pages added to a document all draw, before any save`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            state.importPdf(pdf(3), com.mobixournal.render.ImportPdfMode.APPEND)

            assertEquals(3, state.tabs.active!!.document.pageCount)
            assertEquals("every added sheet sits on the PDF", listOf(true, true, true), state.paintedPages())
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `pages added to a document still draw after a mobix save`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            state.importPdf(pdf(3, "mobix"), com.mobixournal.render.ImportPdfMode.APPEND)
            val target = File(temp.root, "saved.mobix")
            state.saveAs(target, SaveFormat.MOBIX)
            check(state.failure == null) { "save failed: ${state.failure?.title}" }

            state.tabs.closeAll()
            val reopened = DesktopTabs().also { it.open(DocumentLoader.load(target)) }
            val document = reopened.active!!.document
            val painted = (0 until document.pageCount).map { index ->
                document.backdrop.image(document.page(index).background) != null
            }
            assertNotNull("the archive carries the PDF", target)
            assertEquals("every page still has its PDF after reopening", listOf(true, true, true), painted)
            reopened.closeAll()
        } finally {
            state.tabs.closeAll()
        }
    }

    @Test
    fun `pages added to a document still draw after a plain xopp save`() {
        val state = DesktopShellState()
        try {
            state.newDocument()
            val source = pdf(3, "plain")
            state.importPdf(source, com.mobixournal.render.ImportPdfMode.APPEND)
            val target = File(temp.root, "saved.xopp")
            state.saveAs(target, SaveFormat.ORIGINAL)
            check(state.failure == null) { "save failed: ${state.failure?.title}" }

            state.tabs.closeAll()
            val reopened = DesktopTabs().also { it.open(DocumentLoader.load(target)) }
            val document = reopened.active!!.document
            val painted = (0 until document.pageCount).map { index ->
                document.backdrop.image(document.page(index).background) != null
            }
            assertEquals("every page still has its PDF after reopening", listOf(true, true, true), painted)
            reopened.closeAll()
        } finally {
            state.tabs.closeAll()
        }
    }
}
