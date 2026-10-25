package com.mobixournal.desktop

import com.mobixournal.format.mobix.SyntheticNotebook
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The page list's lane, opened by the document and not by a default.
 *
 * The window used to open `Untitled 1` with a 240 dp sidebar standing for the one page already filling
 * the rest of the screen. The rule is the document's own page count: **more than one page** earns the
 * lane, one page does not — and a manual `Ctrl+B` is remembered for the document it was made on rather
 * than leaking across tabs.
 */
class DesktopSidebarTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a one-page document opens without the page list`() {
        val state = DesktopShellState()
        state.newDocument()
        assertFalse("a new document has one page and nothing to list", state.sidebarVisible)

        state.open(pages("single.mobix", 1))
        assertFalse("and one page in a file is still nothing to list", state.sidebarVisible)
    }

    @Test
    fun `a notebook opens with the page list beside it`() {
        val state = DesktopShellState()
        state.open(pages("notebook.mobix", 4))
        assertTrue("four pages are a list worth the lane", state.sidebarVisible)
    }

    @Test
    fun `the page list follows the document in front of you`() {
        val state = DesktopShellState()
        state.open(pages("notebook.mobix", 4))
        assertTrue(state.sidebarVisible)

        state.open(pages("single.mobix", 1))
        assertFalse("the one-page tab hides it", state.sidebarVisible)

        state.tabs.activate(0)
        assertTrue("and the notebook brings it back", state.sidebarVisible)
    }

    @Test
    fun `a choice made on one document is that document's`() {
        val state = DesktopShellState()
        state.open(pages("notebook.mobix", 4))
        state.toggleSidebar()
        assertFalse("Ctrl+B hides the notebook's pages", state.sidebarVisible)

        state.open(pages("single.mobix", 2))
        assertTrue("another document starts on its own rule, not on the last choice", state.sidebarVisible)

        state.tabs.activate(0)
        assertFalse("and the notebook still remembers what was asked of it", state.sidebarVisible)

        state.toggleSidebar()
        assertTrue("which is a decision the user can take back", state.sidebarVisible)
    }

    private fun pages(name: String, count: Int): File = temp.newFile(name).also { file ->
        SyntheticNotebook.write(file, SyntheticNotebook.pages(count))
    }
}
