package com.mobixournal.desktop

import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The tab rules, as a state machine: opening, closing, switching, reordering, and the one that is easy
 * to get wrong and leaks a file handle — opening a file that is already open.
 */
class DesktopTabsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun document(name: String, pages: Int = 3): DesktopDocument {
        val file = temp.newFile(name)
        SyntheticNotebook.write(file, SyntheticNotebook.pages(pages))
        return DocumentLoader.load(file)
    }

    @Test
    fun `opening documents adds tabs and activates the newest`() {
        val tabs = DesktopTabs()
        val first = tabs.open(document("a.mobix"))
        val second = tabs.open(document("b.mobix"))

        assertEquals(2, tabs.size)
        assertEquals(1, tabs.activeIndex)
        assertTrue(tabs.active === second)
        assertEquals("a.mobix", first.title)
        assertEquals(3, first.pageCount)
    }

    @Test
    fun `reopening a file activates its tab instead of duplicating it`() {
        val tabs = DesktopTabs()
        val file = temp.newFile("same.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(4))

        val opened = tabs.open(DocumentLoader.load(file))
        tabs.open(document("other.mobix"))
        val duplicate = DocumentLoader.load(file)
        val again = tabs.open(duplicate)

        assertEquals("still two tabs", 2, tabs.size)
        assertTrue("the same tab object came back", opened === again)
        assertEquals("and it is active", 0, tabs.activeIndex)
        assertNotSame("the tab keeps its own document", duplicate, opened.document)
        assertTrue("the duplicate document was closed instead of leaked", duplicate.isClosed)
    }

    @Test
    fun `closing a tab activates its neighbour`() {
        val tabs = DesktopTabs()
        val a = tabs.open(document("a.mobix"))
        val b = tabs.open(document("b.mobix"))
        val c = tabs.open(document("c.mobix"))

        tabs.activate(1)
        tabs.close(1)
        assertEquals(2, tabs.size)
        assertEquals("the tab after the closed one takes over", 1, tabs.activeIndex)
        assertTrue(tabs.active === c)

        tabs.activate(0)
        tabs.close(0)
        assertEquals("closing the first keeps the neighbour active", 0, tabs.activeIndex)
        assertTrue(tabs.active === c)
        assertFalse(a === b)
    }

    @Test
    fun `closing the active last tab falls back to the previous one`() {
        val tabs = DesktopTabs()
        val a = tabs.open(document("a.mobix"))
        tabs.open(document("b.mobix"))
        tabs.activate(1)
        tabs.close(1)
        assertEquals(0, tabs.activeIndex)
        assertTrue(tabs.active?.document === a.document)
    }

    @Test
    fun `closing the last tab empties the shell`() {
        val tabs = DesktopTabs()
        tabs.open(document("only.mobix"))
        assertFalse("the shell is empty after the last tab", tabs.close(0))
        assertEquals(0, tabs.size)
        assertEquals(-1, tabs.activeIndex)
        assertEquals(null, tabs.active)
    }

    @Test
    fun `reordering keeps the same tab active`() {
        val tabs = DesktopTabs()
        val a = tabs.open(document("a.mobix"))
        tabs.open(document("b.mobix"))
        val c = tabs.open(document("c.mobix"))
        tabs.activate(2)

        tabs.move(2, 0)
        assertEquals(listOf("c.mobix", "a.mobix", "b.mobix"), tabs.all.map { it.title })
        assertEquals("the moved tab is still active", 0, tabs.activeIndex)
        assertTrue(tabs.active?.document === c.document)

        tabs.activate(0)
        tabs.move(0, 2)
        assertEquals(listOf("a.mobix", "b.mobix", "c.mobix"), tabs.all.map { it.title })
        assertEquals("still the same tab after moving down", 2, tabs.activeIndex)
        assertTrue(tabs.active?.document === c.document)
    }

    @Test
    fun `a drag past the end is clamped, not dropped`() {
        val tabs = DesktopTabs()
        tabs.open(document("a.mobix"))
        tabs.open(document("b.mobix"))
        tabs.move(0, 99)
        assertEquals(listOf("b.mobix", "a.mobix"), tabs.all.map { it.title })
        assertEquals("the active tab followed its neighbour to the front", 0, tabs.activeIndex)
    }

    @Test
    fun `switching wraps around and is a no-op on an empty shell`() {
        val tabs = DesktopTabs()
        assertEquals(null, tabs.next())
        assertEquals(null, tabs.previous())

        tabs.open(document("a.mobix"))
        tabs.open(document("b.mobix"))
        tabs.activate(0)
        assertEquals("Ctrl+Tab wraps to the first tab again", 1, tabs.next()?.let { tabs.activeIndex })
        assertEquals(0, tabs.next()?.let { tabs.activeIndex })
        assertEquals(1, tabs.previous()?.let { tabs.activeIndex })
    }

    @Test
    fun `each tab keeps its own page, zoom and fit`() {
        val tabs = DesktopTabs()
        val a = tabs.open(document("a.mobix", pages = 10))
        val b = tabs.open(document("b.mobix", pages = 10))

        a.page = 7
        a.viewport = Viewport(2.5f, -100f, -50f)
        a.fit = FitMode.NONE
        b.page = 1
        b.fit = FitMode.WIDTH

        tabs.activate(0)
        assertEquals(7, (tabs.active!!).page)
        assertEquals(2.5f, tabs.active!!.viewport.zoom, 0f)
        tabs.activate(1)
        assertEquals(1, tabs.active!!.page)
        assertEquals(FitMode.WIDTH, tabs.active!!.fit)
    }

    @Test
    fun `closing a document releases its file handle`() {
        val tabs = DesktopTabs()
        val file = temp.newFile("handles.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
        val document = DocumentLoader.load(file)
        tabs.open(document)
        tabs.close(0)
        // A `.mobix` reader holds the archive open; a closed document must be able to hand the file to
        // another reader — on Windows that is literally a lock, elsewhere it is the invariant.
        val reopened = DocumentLoader.load(file)
        assertEquals(2, reopened.pageCount)
        reopened.close()
    }

    @Test
    fun `a tab's key is the normalised absolute path`() {
        val tabs = DesktopTabs()
        val tab = tabs.open(document("path.mobix"))
        assertEquals(tab.file.canonicalFile.path, tab.key)
        assertEquals("path.mobix", tab.file.name)
    }
}
