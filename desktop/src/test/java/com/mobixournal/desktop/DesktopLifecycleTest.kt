package com.mobixournal.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **lifecycle**: when a document is dirty, what closing one asks, and what survives a
 * crash — headless, because none of it needs a window.
 *
 * The rule the tests are built around is the save point: dirty is not a flag somebody sets, it is the
 * question "is the history still at the state the file holds?". So the assertions walk the document —
 * edit, save, edit, undo back to the save point, redo away from it — instead of trusting a boolean the
 * commands forgot to clear. Closing is the second half: a dirty tab becomes a **question**, an answer
 * writes the file or drops the snapshot, and the window's own X asks the same way the tab's glyph does.
 *
 * The last one is a crash: one shell writes a snapshot, dies without closing its tabs, and the next shell
 * starts on the recovered one — with the edits, the right file behind it, and the snapshot gone once it
 * is saved.
 */
class DesktopLifecycleTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun notebook(name: String = "lifecycle.mobix", pages: Int = 6): File =
        temp.newFile(name).also { file -> SyntheticNotebook.write(file, SyntheticNotebook.pages(pages)) }

    private var settingsCount = 0

    private fun store(): DesktopRecovery = DesktopRecovery(temp.newFolder())

    /** A shell of its own per call: each one gets a fresh settings file, and the snapshot folder is shared. */
    private fun state(store: DesktopRecovery = store()): DesktopShellState = DesktopShellState(
        settings = DesktopSettings(temp.newFile("desktop-${settingsCount++}.properties")),
        recovery = store,
    )

    private fun strokeCount(page: Page): Int =
        page.layers.sumOf { layer -> layer.elements.count { element -> element is Stroke } }

    /** One committed edit, recorded the way a tool does it: the pre-edit snapshot, then the page change. */
    private fun DesktopTab.edit(id: String = "stk_edit") {
        record(document.snapshot())
        document.editPage(0) { page -> page.withElement(drawnBar(id)) }
    }

    private fun drawnBar(id: String): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(60.0, 200.0, 8.0), StrokePoint(520.0, 200.0, 8.0)),
        uniformWidth = true,
        id = id,
    )

    /**
     * The save point, walked: a fresh tab is clean, an edit makes it dirty, a save and an undo back to the
     * saved state both make it clean again, and a redo away from the save point makes it dirty.
     */
    @Test
    fun theSavePointSurvivesAnUndoAndSavingIsUnavailableWhenNothingChanged() {
        val state = state()
        try {
            val file = notebook()
            state.open(file)
            val tab = state.active!!
            assertFalse("an opened file is what the file holds", tab.isDirty)
            assertFalse("and there is nothing to save", state.apply(ShellCommand.SAVE))

            tab.edit()
            assertTrue("the edit is in the document, not the file", tab.isDirty)
            assertTrue("so Ctrl+S is available", state.apply(ShellCommand.SAVE))
            assertNull(state.failure)
            assertFalse("the file is the baseline again", tab.isDirty)
            DocumentLoader.load(file).use { saved ->
                assertEquals("and it holds the stroke", 2, strokeCount(saved.page(0)))
            }

            tab.edit("stk_second")
            assertTrue(tab.isDirty)
            assertTrue("undo returns to the saved state", state.apply(ShellCommand.UNDO))
            assertFalse("a document back at its save point is clean, not merely 'probably clean'", tab.isDirty)
            assertFalse("so Save is unavailable again", state.apply(ShellCommand.SAVE))

            assertTrue("and redo leaves the save point", state.apply(ShellCommand.REDO))
            assertTrue(tab.isDirty)
        } finally {
            state.tabs.closeAll()
        }
    }

    /** A dirty tab is not closed by its glyph: it becomes a question, and Cancel leaves it exactly as it was. */
    @Test
    fun closingADirtyTabAsksBeforeItGoes() {
        val state = state()
        try {
            state.open(notebook())
            val tab = state.active!!
            tab.edit()

            assertFalse("the close glyph asks instead of closing", state.requestCloseTab(0))
            assertSame(tab, state.closeRequest?.tab)
            assertFalse("one tab's close is not the window's exit", state.closeRequest!!.exit)
            assertEquals("and the tab is still open", 1, state.tabs.size)

            state.cancelClose()
            assertNull("Cancel answers the question", state.closeRequest)
            assertEquals(1, state.tabs.size)
            assertTrue("and the edits are untouched", tab.isDirty)

            assertTrue("Ctrl+W is handled — it opened the question", state.apply(ShellCommand.CLOSE_TAB))
            assertNotNull(state.closeRequest)
            state.confirmCloseDiscard()
            assertNull("Discard settles the question", state.closeRequest)
            assertEquals("and closes the tab", 0, state.tabs.size)
        } finally {
            state.tabs.closeAll()
        }
    }

    /** **Save** in the question writes the tab's own file and then closes it. */
    @Test
    fun savingFromTheQuestionWritesTheFileAndClosesTheTab() {
        val state = state()
        try {
            val file = notebook()
            state.open(file)
            state.active!!.edit()

            assertFalse(state.requestCloseTab(0))
            state.confirmCloseSave()
            assertNull("the save reported nothing", state.failure)
            assertNull("the question is answered", state.closeRequest)
            assertEquals("and the tab is gone", 0, state.tabs.size)

            DocumentLoader.load(file).use { saved ->
                assertEquals("the file holds the edit the tab was about to lose", 2, strokeCount(saved.page(0)))
            }
        } finally {
            state.tabs.closeAll()
        }
    }

    /** The window's X asks about every dirty tab, one at a time, and goes only when there is none left. */
    @Test
    fun theWindowAsksForEachDirtyTabBeforeItGoes() {
        val state = state()
        try {
            state.open(notebook("uno.mobix"))
            state.open(notebook("due.mobix"))
            state.tabs.all.forEach { it.edit() }

            assertFalse("the X cannot bypass the question", state.requestExit())
            assertEquals("it asks about a tab that is on screen", "uno.mobix", state.closeRequest?.tab?.title)
            assertTrue("and it is the window that is closing", state.closeRequest!!.exit)

            state.confirmCloseDiscard()
            assertEquals("the next dirty tab is asked about", "due.mobix", state.closeRequest?.tab?.title)
            assertFalse("the window still has not gone", state.exitAllowed)

            state.confirmCloseDiscard()
            assertNull("no dirty tab left, no question", state.closeRequest)
            assertTrue("the window may close now", state.exitAllowed)
            assertEquals(0, state.tabs.size)

            assertTrue("an empty window closes without asking", state().requestExit())
        } finally {
            state.tabs.closeAll()
        }
    }

    /**
     * The crash path: a shell writes a snapshot and dies without closing its tabs; the next shell starts on
     * a recovered tab that knows its file, is dirty, and lets go of the snapshot once the file holds it.
     */
    @Test
    fun aSnapshotFromOneWindowIsOfferedByTheNext() {
        val file = notebook()
        val store = DesktopRecovery(temp.newFolder("recovery"))
        val crashed = state(store)
        try {
            crashed.open(file)
            crashed.active!!.edit()
            assertEquals("one dirty tab, one snapshot", 1, crashed.flushRecovery())
            assertEquals(1, store.entries().size)
        } finally {
            // A crash does not settle anything: the documents are released and the snapshot stays.
            crashed.tabs.closeAll()
        }

        val next = state(store)
        try {
            assertEquals("the next run adopts the snapshot", 1, next.recover())
            val recovered = next.active!!
            assertTrue("a recovered tab starts with work the file does not have", recovered.isDirty)
            assertEquals("it stands for the file it came from", file.absoluteFile, recovered.file.absoluteFile)
            assertTrue("and says so in its label", recovered.title.endsWith("(recovered)"))
            assertEquals("with the edit that was never saved", 2, strokeCount(recovered.document.page(0)))

            assertTrue("saving it writes the tab's own file", next.apply(ShellCommand.SAVE))
            assertFalse("the file is the baseline again", recovered.isDirty)
            assertEquals("and the snapshot is gone", 0, store.entries().size)
            DocumentLoader.load(file).use { saved ->
                assertEquals(2, strokeCount(saved.page(0)))
            }
        } finally {
            next.tabs.closeAll()
        }
    }

    /** The accent dot is the strip's dirty readout — a token colour, and only where there are unsaved edits. */
    @Test
    @OptIn(ExperimentalComposeUiApi::class)
    fun theStripDotsADirtyTabInTheAccent() {
        val tabs = DesktopTabs()
        try {
            tabs.open(DocumentLoader.load(notebook()))
            val scene = ImageComposeScene(420, 34, Density(1f)) {
                TabStrip(tabs = tabs, onActivate = {}, onClose = {}, onMove = { _, _ -> }, onNew = {})
            }
            try {
                val clean = accents(scene.render().encodeToData()?.bytes!!)
                assertEquals("a clean tab carries no accent mark", 0, clean)

                tabs.active!!.edit()
                Snapshot.sendApplyNotifications()
                val dirty = accents(scene.render().encodeToData()?.bytes!!)
                assertTrue("the unsaved edit is drawn in the accent ($dirty px)", dirty > 10)
            } finally {
                scene.close()
            }
        } finally {
            tabs.closeAll()
        }
    }

    /** The question is a floating surface over the page, never a silent close and never a system dialog. */
    @Test
    @OptIn(ExperimentalComposeUiApi::class)
    fun theQuestionIsDrawnOverThePage() {
        val state = state()
        try {
            state.open(notebook())
            state.active!!.edit()
            val scene = ImageComposeScene(1000, 700, Density(1f)) {
                DesktopShell(state = state, onOpenPicker = {})
            }
            try {
                // The page's centre in the canvas lane: paper before the question, the card after it.
                val centreX = 648
                val centreY = 358
                val before = png(advance(scene, to = 0))
                assertEquals("the page under the question is paper", 0xFFFFFF, before.getRGB(centreX, centreY) and 0xFFFFFF)

                assertFalse(state.requestCloseTab(0))
                Snapshot.sendApplyNotifications()
                // The card arrives on the family's spring, so the frame that shows it is the settled
                // one: the animation has to be walked at the scene's frame clock, not skipped.
                val settled = advance(scene, to = 700)
                File("build/desktop-render/lifecycle-question.png").apply { parentFile?.mkdirs() }
                    .writeBytes(requireNotNull(settled.encodeToData()?.bytes))
                val after = png(settled)
                assertNotEquals(
                    "the card is drawn where the page was",
                    0xFFFFFF,
                    after.getRGB(centreX, centreY) and 0xFFFFFF,
                )
            } finally {
                scene.close()
            }
        } finally {
            state.tabs.closeAll()
        }
    }

    private var now = 0L

    /** Render frames, 16 ms apart, up to [to] — how a running window would see the spring. */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun advance(scene: ImageComposeScene, to: Long): org.jetbrains.skia.Image {
        var last = scene.render(now * 1_000_000L)
        while (now < to) {
            now = minOf(now + 16, to)
            last = scene.render(now * 1_000_000L)
        }
        return last
    }

    private fun png(bitmap: org.jetbrains.skia.Image): java.awt.image.BufferedImage =
        ImageIO.read(ByteArrayInputStream(requireNotNull(bitmap.encodeToData()?.bytes)))

    /** How many rendered pixels carry the theme's accent — the dot's colour, read from the token. */
    private fun accents(png: ByteArray): Int {
        val accent = DesktopTheme.accent.toArgb() and 0xFFFFFF
        val image = ImageIO.read(ByteArrayInputStream(png))
        var found = 0
        for (x in 0 until image.width) {
            for (y in 0 until image.height) if (image.getRGB(x, y) and 0xFFFFFF == accent) found++
        }
        return found
    }
}
