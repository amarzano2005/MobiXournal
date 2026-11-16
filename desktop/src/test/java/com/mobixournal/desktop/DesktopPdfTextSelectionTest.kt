package com.mobixournal.desktop

import com.mobixournal.desktop.ink.of
import com.mobixournal.desktop.io.DesktopContainer
import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.DesktopPdfIndex
import com.mobixournal.desktop.io.DesktopPdfTextHighlight
import com.mobixournal.desktop.io.DesktopPdfTextSelect
import com.mobixournal.desktop.io.DesktopPdfTextSelection
import com.mobixournal.desktop.io.DesktopPdfTextUnderline
import com.mobixournal.desktop.sheet.PageBackdrop
import com.mobixournal.desktop.shell.DesktopTabs

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.Tool
import com.mobixournal.render.PdfWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `Highlight writes ordinary highlighter bands, and one undo takes them all back`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            tab.pdfTextSelection = DesktopPdfTextSelection(page = 0, anchor = 0, focus = 1)
            assertTrue(DesktopPdfTextHighlight.highlight(tab, 0xFFFFFF00.toInt()))
            val bands = tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()
            assertEquals("one band over the one selected line", 1, bands.size)
            assertEquals(Tool.HIGHLIGHTER, bands.single().tool)
            assertTrue("a highlight is a band, not a growing stroke", bands.single().uniformWidth)
            assertEquals(2, bands.single().points.size)
            assertEquals(1, tab.document.edits)
            assertTrue("the whole highlight is one undo step", tab.canUndo)
            assertTrue(tab.undo())
            assertTrue(
                "undo takes every band of the selection back",
                tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>().isEmpty(),
            )
            assertTrue("and redo puts them back", tab.redo())
            assertEquals(1, tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>().size)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `Highlight without a selection writes nothing`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            assertFalse(DesktopPdfTextHighlight.highlight(tab, 0xFFFFFF00.toInt()))
            assertFalse(tab.canUndo)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `Underline writes one thin pen rule under the line, and one undo takes it back`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            tab.pdfTextSelection = DesktopPdfTextSelection(page = 0, anchor = 0, focus = 1)
            assertTrue(DesktopPdfTextUnderline.underline(tab, 0xFF1A237E.toInt(), 1.2))
            val rules = tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()
            assertEquals("one rule over the one selected line", 1, rules.size)
            val rule = rules.single()
            assertEquals("ordinary pen ink, not a highlighter band", Tool.PEN, rule.tool)
            assertTrue("a constant width, never a pressure profile", rule.uniformWidth)
            assertEquals(2, rule.points.size)
            assertEquals("the rule is level", rule.points[0].y, rule.points[1].y, 1e-9)
            assertEquals("in the pen's own colour", 0xFF1A237E.toInt(), rule.color)
            assertEquals(1.2, rule.points[0].width, 1e-9)
            assertEquals(1, tab.document.edits)
            assertTrue("the whole underline is one undo step", tab.canUndo)
            assertTrue(tab.undo())
            assertTrue(
                "undo takes the rule back",
                tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>().isEmpty(),
            )
            assertTrue("and redo puts it back", tab.redo())
            assertEquals(1, tab.document.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>().size)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `Underline without a selection writes nothing`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            assertFalse(DesktopPdfTextUnderline.underline(tab, 0xFF000000.toInt(), 1.2))
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
