package com.mobixournal.desktop

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.render.PdfWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The PDF text selection's rules: a press anchors the word under it, a drag extends the range in reading
 * order, and the selected text is the words joined — the tablet's own `PdfTextIndex` answers, not a second
 * hit test.
 *
 * The other half is what the selection is **not**: nothing on the page changes, no undo step is recorded,
 * and a page with no text layer says so instead of selecting silently.
 */
class DesktopPdfTextSelectionTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `a press inside a word anchors that word`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            val begun = DesktopPdfTextSelect.begin(tab, 55.0, 106.0)
            assertEquals(0, begun?.anchor)
            assertEquals(0, begun?.focus)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a drag extends the range in reading order, keeping the anchor`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            val begun = DesktopPdfTextSelect.begin(tab, 55.0, 106.0)!!
            val extended = DesktopPdfTextSelect.extend(tab, begun, 170.0, 106.0)
            assertEquals(0, extended.anchor)
            assertEquals(1, extended.focus)
            assertEquals("Magnetic highlighter", DesktopPdfTextSelect.text(tab, extended))
            assertEquals(2, DesktopPdfTextSelect.boxes(tab, extended).size)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a drag back to the anchor shrinks the selection to one word`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            val begun = DesktopPdfTextSelect.begin(tab, 55.0, 106.0)!!
            val back = DesktopPdfTextSelect.extend(tab, begun.copy(focus = 1), 55.0, 106.0)
            assertEquals("Magnetic", DesktopPdfTextSelect.text(tab, back))
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `the range reads either direction`() {
        val selection = DesktopPdfTextSelection(page = 0, anchor = 1, focus = 0)
        assertEquals(0, selection.first)
        assertEquals(1, selection.last)
    }

    @Test
    fun `selecting writes nothing and records no undo step`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            val before = tab.document.page(0)
            val begun = DesktopPdfTextSelect.begin(tab, 55.0, 106.0)!!
            DesktopPdfTextSelect.extend(tab, begun, 170.0, 106.0)
            assertEquals("the page is untouched", before, tab.document.page(0))
            assertEquals(0, tab.document.edits)
            assertFalse(tab.canUndo)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a page that is not a PDF selects nothing`() {
        val model = com.mobixournal.format.model.Document(
            pages = listOf(
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = Background.Solid(color = 0xFFFFFFFF.toInt(), style = "plain", config = ""),
                    layers = listOf(Layer(emptyList())),
                ),
            ),
        )
        val document = DesktopDocument(
            file = temp.newFile("plain.xopp"),
            source = com.mobixournal.format.mobix.PageSource.of(model),
            model = model,
            container = DesktopContainer.XOPP_GZIP,
            backdrop = PageBackdrop(bundled = { null }, documentDir = temp.root),
        )
        val tab = DesktopTabs().open(document)
        try {
            // An index is present (a PDF tab), but this page names no PDF sheet.
            tab.pdfIndex = DesktopPdfIndex(
                com.mobixournal.render.PdfTextIndex(listOf(DesktopPdfFixture.LINE)),
                com.mobixournal.render.PdfOutline.EMPTY,
            )
            assertNull(DesktopPdfTextSelect.begin(tab, 55.0, 106.0))
            assertEquals(emptyList<PdfWord>(), DesktopPdfTextSelect.wordsOf(tab))
        } finally {
            document.close()
        }
    }
}
