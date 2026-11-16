package com.mobixournal.desktop

import com.mobixournal.desktop.io.DesktopPdfTextHandles
import com.mobixournal.desktop.io.DesktopPdfTextSelect
import com.mobixournal.render.PdfTextHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's endpoint handles on a real tab and a real PDF: the two dots of a live range, the grab a press at
 * one of them performs, the drag that moves only the grabbed end, the counted move a keyboard makes, and the two
 * refusals — a press in whitespace, and a range that belongs to another page.
 *
 * The geometry itself is `PdfTextHandlesTest`'s in `mobix-core`; this pins the wiring the window adds: the page's
 * own sheet, the tab's selection, and no page edit or undo step anywhere.
 */
class DesktopPdfTextHandlesTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `a press on the end dot drags that end and nothing else`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            tab.pdfTextSelection = DesktopPdfTextSelect.begin(tab, 55.0, 106.0)
            val points = DesktopPdfTextHandles.points(tab)
            assertEquals(2, points.size)
            val end = points.first { it.handle == PdfTextHandle.END }
            val grab = DesktopPdfTextHandles.grab(tab, end.x, end.y, density = 1f, zoom = 1f)
            assertEquals(PdfTextHandle.END, grab?.handle)
            assertTrue(DesktopPdfTextHandles.dragTo(tab, grab!!, 170.0, 106.0))
            assertEquals(0, tab.pdfTextSelection?.anchor)
            assertEquals(1, tab.pdfTextSelection?.focus)
            // The passage is view state: the page took no edit and the history has nothing to undo.
            assertFalse(tab.canUndo)
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a press between the words grabs no handle`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            tab.pdfTextSelection = DesktopPdfTextSelect.begin(tab, 55.0, 106.0)
            // Far from either dot, and far from the page's ink.
            assertNull(DesktopPdfTextHandles.grab(tab, 400.0, 400.0, density = 1f, zoom = 1f))
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a counted move extends the end and stops at the sheet's last word`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            tab.pdfTextSelection = DesktopPdfTextSelect.begin(tab, 55.0, 106.0)
            assertEquals(0, tab.pdfTextSelection?.focus)
            assertTrue(DesktopPdfTextHandles.nudge(tab, PdfTextHandle.END, 1))
            assertEquals(1, tab.pdfTextSelection?.focus)
            // Already at the last word: nothing to do, and nothing claimed.
            assertFalse(DesktopPdfTextHandles.nudge(tab, PdfTextHandle.END, 1))
            assertFalse(DesktopPdfTextHandles.nudge(tab, PdfTextHandle.START, -1))
        } finally {
            tab.document.close()
        }
    }

    @Test
    fun `a range left on another page has no handles and is not adjusted`() {
        val tab = DesktopPdfFixture.tab(temp.root)
        try {
            val selection = DesktopPdfTextSelect.begin(tab, 55.0, 106.0)!!
            tab.pdfTextSelection = selection.copy(page = selection.page + 1)
            assertEquals(emptyList<Any>(), DesktopPdfTextHandles.points(tab))
            assertFalse(DesktopPdfTextHandles.nudge(tab, PdfTextHandle.END, 1))
            assertNull(DesktopPdfTextHandles.grab(tab, 55.0, 106.0, density = 1f, zoom = 1f))
        } finally {
            tab.document.close()
        }
    }
}
