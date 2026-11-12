package com.mobixournal.desktop

import com.mobixournal.desktop.io.DesktopDocument
import com.mobixournal.desktop.io.DocumentLoader
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.DesktopTabs
import com.mobixournal.format.mobix.SyntheticNotebook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A tab's **own** steps, across a switch of the active tab.
 *
 * The window keeps every document open and gives each tab its own `EditHistory` (`DesktopTabs`), so
 * changing tabs — the strip, `Ctrl+Tab`, the menu — moves only which tab is active. This is the rule the
 * tablet had to learn (there the canvas is one surface, so the stack has to travel with the document);
 * here it already held, and these two tests are what stops a future `activate`/`close` from clearing it
 * the way a save once did.
 */
class DesktopTabSwitchingHistoryTest {

    @get:Rule val temp = TemporaryFolder()

    private fun document(name: String): DesktopDocument {
        val file = temp.newFile(name)
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
        return DocumentLoader.load(file)
    }

    @Test
    fun aTabKeepsItsStepsWhileAnotherOneIsActive() {
        val tabs = DesktopTabs()
        val first = tabs.open(document("one.mobix"))
        val width = first.document.page(0).width
        first.record(first.document.snapshot())
        first.document.editPage(0) { it.copy(width = 700.0) }
        assertTrue("the edit is undoable", first.canUndo)

        val second = tabs.open(document("two.mobix"))
        assertTrue("the other tab is active", tabs.active === second)

        // Back the way the strip and Ctrl+Tab do it, without touching either document.
        tabs.activate(first)
        assertTrue("the tab came back with its step", first.canUndo)
        assertTrue("and still unpublished", first.isDirty)
        assertTrue("which it can take back", first.undo())
        assertEquals("restoring the page it had before the edit", width, first.document.page(0).width, 0.0)
        assertFalse("and the other tab never saw it", second.canUndo)
    }

    @Test
    fun eachTabHasItsOwnStepsAndClosingOneDoesNotTouchTheOther() {
        val tabs = DesktopTabs()
        val first = tabs.open(document("a.mobix"))
        val second = tabs.open(document("b.mobix"))
        val firstWidth = first.document.page(0).width
        val secondHeight = second.document.page(0).height
        first.record(first.document.snapshot())
        first.document.editPage(0) { it.copy(width = 640.0) }
        second.record(second.document.snapshot())
        second.document.editPage(0) { it.copy(height = 900.0) }

        tabs.activate(second)
        assertTrue("the second tab has its step", second.canUndo)
        assertTrue("takes it back", second.undo())
        assertEquals("restoring its own page", secondHeight, second.document.page(0).height, 0.0)

        tabs.close(tabs.all.indexOf(second))
        assertTrue("the first tab's step outlives the closed one", first.canUndo)
        assertTrue("and is still its own", first.undo())
        assertEquals("restoring its page", firstWidth, first.document.page(0).width, 0.0)
    }
}
