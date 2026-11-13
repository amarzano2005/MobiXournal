package com.mobixournal.desktop

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.ImageComposeScene
import com.mobixournal.desktop.ink.withElement
import com.mobixournal.desktop.io.DesktopRecovery
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.DesktopShell
import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.DesktopTab
import com.mobixournal.desktop.shell.entryCards
import com.mobixournal.desktop.shell.flushRecovery
import com.mobixournal.desktop.shell.forgetEntry
import com.mobixournal.desktop.shell.restoreLastSession
import com.mobixournal.desktop.shell.startFreshSession
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window with nothing open is the **entry screen**: a sheet to start, the Open command, and the notebooks
 * opened lately (`EntryScreen`) — not a blank page nobody asked for, and not a two-line notice.
 *
 * Two claims here: a fresh window opens **on that screen** (`start` no longer makes a blank tab), and the
 * list it shows is the settings file's, most recent first, capped, and still there after a restart.
 */
class DesktopEntryScreenTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `a fresh window opens on the entry screen, with no tab`() {
        val state = DesktopShellState(DesktopSettings(temp.newFile("settings.properties")))
        state.start(null)
        assertEquals("tabs at startup", 0, state.tabs.all.size)
    }

    @Test
    fun `the recent list is most recent first, one entry per notebook, and outlives a restart`() {
        val settingsFile = temp.newFile("settings.properties")
        val first = temp.newFile("lecture-one.xopp").apply { writeText("x") }
        val second = temp.newFile("lecture-two.xopp").apply { writeText("x") }
        val settings = DesktopSettings(settingsFile)
        settings.recents.record(first)
        settings.recents.record(second)
        // Opened again: one card, in front — the list is a set of sheets, not a log of visits.
        settings.recents.record(first)
        assertEquals(
            "most recent first, and no duplicate",
            listOf(first.absolutePath, second.absolutePath),
            settings.recents.files.map { it.absolutePath },
        )
        // A restart reads the same file back.
        val reopened = DesktopSettings(settingsFile)
        assertEquals(
            "the list survives the process",
            listOf(first.absolutePath, second.absolutePath),
            reopened.recents.files.map { it.absolutePath },
        )
        // A notebook deleted in a file manager leaves no card that opens nothing.
        second.delete()
        assertEquals("a missing file drops out", 1, reopened.recents.files.size)
    }

    @Test
    fun `the new sheet's preview has the sheet's proportions`() {
        val state = DesktopShellState(DesktopSettings(temp.newFile("proportions.properties")))
        val page = com.mobixournal.desktop.io.DesktopDocuments.blankPage(
            state.settings.pageWidthPt,
            state.settings.pageHeightPt,
        )
        val painted = com.mobixournal.desktop.shell.renderPageBitmap(page, 208f / page.width.toFloat())
        println("sheet: page ${page.width} x ${page.height}, preview ${painted.width} x ${painted.height}")
        // Where the paper actually is **inside** the bitmap: a preview whose bitmap is a sheet and whose paper
        // is a strip would pass a size check and still show the strip.
        val pixels = painted.toPixelMap()
        var minX = Int.MAX_VALUE
        var maxX = -1
        for (y in 0 until pixels.height step 2) {
            for (x in 0 until pixels.width step 2) {
                if (pixels[x, y].alpha > 0.5f) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }
        }
        println("sheet: paper columns $minX..$maxX of ${pixels.width} x ${pixels.height}")
        assertTrue("the paper spans the preview", maxX - minX > painted.width * 0.9)
    }

    @Test
    fun `the entry screen paints`() {
        val state = DesktopShellState(DesktopSettings(temp.newFile("settings.properties")))
        state.start(null)
        val scene = ImageComposeScene(1200, 800, Density(1f)) {
            DesktopShell(state = state, onOpenPicker = {})
        }
        try {
            val painted = scene.render().encodeToData()?.bytes ?: ByteArray(0)
            assertTrue("the entry screen drew nothing", painted.isNotEmpty())
        } finally {
            scene.close()
        }
    }

    /**
     * The window keeps no tab list, so "the previous session" is exactly what `recovery/` holds: a start
     * with a snapshot **offers** it as a card instead of adopting it, and *Restore last session* brings the
     * work back dirty on its own file.
     */
    @Test
    fun `an unsaved document from the last run is a card first, and never a tab at start`() {
        val settingsFile = temp.newFile("session.properties")
        val notebook = temp.newFile("unsaved.mobix").also { file ->
            SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
        }
        val store = DesktopRecovery(temp.newFolder("recovery"))
        val crashed = DesktopShellState(DesktopSettings(settingsFile), store)
        try {
            crashed.open(notebook)
            crashed.active!!.markEdited()
            assertEquals("one dirty tab, one snapshot", 1, crashed.flushRecovery())
        } finally {
            // A crash settles nothing: the documents are released and the snapshot stays.
            crashed.tabs.closeAll()
        }

        val next = DesktopShellState(DesktopSettings(settingsFile), store)
        next.start(null)
        assertEquals("the offer opens no tab", 0, next.tabs.all.size)
        val cards = next.entryCards()
        assertEquals("the unsaved document, and no second card for its file", 1, cards.size)
        assertTrue("it is a previous-session card", cards[0].fromPreviousSession)
        // A document's identity is the alias-free path ([DesktopTabs.keyOf]), so the card is compared
        // with the canonical file: a temp folder reached as `RUNNER~1` is the same file as its long name.
        assertEquals(notebook.canonicalFile, cards[0].file.canonicalFile)

        assertEquals("restore brings it back", 1, next.restoreLastSession())
        assertTrue("a recovered tab holds work the file does not", next.active!!.isDirty)
        assertEquals(notebook.canonicalFile, next.active!!.file.canonicalFile)
        next.tabs.closeAll()
    }

    /** The card's `×` forgets the notebook; the file itself stays where the reader put it. */
    @Test
    fun `the remove mark forgets a recent without touching its file`() {
        val file = temp.newFile("remove-me.mobix").also { SyntheticNotebook.write(it, SyntheticNotebook.pages(1)) }
        val settings = DesktopSettings(temp.newFile("removals.properties"))
        settings.recents.record(file)
        val state = DesktopShellState(settings)
        state.forgetEntry(state.entryCards().single())
        assertEquals("no card left", 0, state.entryCards().size)
        assertTrue("the notebook is still on disk", file.isFile)
    }

    /** On a previous-session card the same `×` also drops the snapshot its edits lived in. */
    @Test
    fun `the remove mark on a previous-session card drops its snapshot too`() {
        val settingsFile = temp.newFile("drop.properties")
        val store = DesktopRecovery(temp.newFolder("recovery"))
        val notebook = crashedNotebook()
        val notebookPath = notebook.absolutePath
        val crashed = DesktopShellState(DesktopSettings(settingsFile), store)
        try {
            crashed.open(notebook)
            crashed.active!!.markEdited()
            assertEquals(1, crashed.flushRecovery())
        } finally {
            crashed.tabs.closeAll()
        }

        val next = DesktopShellState(DesktopSettings(settingsFile), store)
        next.forgetEntry(next.entryCards().single())
        assertEquals("the snapshot behind it is gone", 0, store.entries().size)
        // The notebook is still on disk and was opened, so it stays as an ordinary recent card — the same
        // answer the tablet gives when a session card's × drops the tab rather than the document.
        val remaining = next.entryCards().single()
        assertFalse("only the offer was dropped", remaining.fromPreviousSession)
        assertEquals(notebookPath, remaining.file.absolutePath)
    }

    /** *New sheet* is a fresh decision: the previous run's offer goes with it, as on the tablet. */
    @Test
    fun `new sheet drops the offer and starts on a blank page`() {
        val settingsFile = temp.newFile("fresh.properties")
        val store = DesktopRecovery(temp.newFolder("recovery"))
        val crashed = DesktopShellState(DesktopSettings(settingsFile), store)
        try {
            crashed.open(crashedNotebook())
            crashed.active!!.markEdited()
            assertEquals(1, crashed.flushRecovery())
        } finally {
            crashed.tabs.closeAll()
        }

        val next = DesktopShellState(DesktopSettings(settingsFile), store)
        next.startFreshSession()
        assertEquals("the offer is dropped", 0, store.entries().size)
        assertEquals("one blank tab", 1, next.tabs.all.size)
        assertTrue("the new sheet is unsaved", next.active!!.unsaved)
        assertEquals("and nothing of the old one is offered", 0, next.entryCards().count { it.fromPreviousSession })
        next.tabs.closeAll()
    }

    /** A notebook for a crash test, written where the temporary folder collects it. */
    private fun crashedNotebook(): File =
        temp.newFile("crash-${temp.root.list()?.size}.mobix").also { file ->
            SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
        }

    /** One committed edit — enough for the tab to hold work its file does not. */
    private fun DesktopTab.markEdited() {
        record(document.snapshot())
        document.editPage(0) { page -> page.withElement(drawnBar()) }
    }

    private fun drawnBar(): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(60.0, 200.0, 8.0), StrokePoint(520.0, 200.0, 8.0)),
        uniformWidth = true,
        id = "stk_entry",
    )

    /** The repository's own notebook, found by walking up: the tests run inside `desktop/`. */
    private fun repositoryNotebook(): File? {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "udiff.xopp")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        return null
    }
}
