package com.mobixournal.desktop

import com.mobixournal.desktop.io.*
import com.mobixournal.desktop.settings.DesktopSettings
import com.mobixournal.desktop.shell.*
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.SaveFormat
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.*

class DesktopSaveWorkerTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun saveOverItsOwnFileNeverQueuesBehindAReader() {
        val file = temp.newFile("save.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(3))
        val original = DocumentLoader.load(file)
        val tab = DesktopTab(original)
        val pool = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        try {
            // A reader that holds the lease for as long as it likes: an export, a page read, the crash snapshot.
            val reader = pool.submit { original.withReader { entered.countDown(); release.await(10, TimeUnit.SECONDS) } }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val request = SaveRequest(tab, file, SaveFormat.MOBIX) {}
            // The rule the window's save is built on: the bytes come from an immutable snapshot and land as one
            // rename of a staged sibling, so a save over the tab's **own** file does not queue for the document
            // at all. Waiting here (the old rule) is what put a save and the canvas in front of each other: the
            // writer starved behind long readers and the window showed a save that never finished.
            val outcome = pool.submit<Saved> { commitSaveRequest(request, stageSaveRequest(request)) }
                .get(10, TimeUnit.SECONDS)
            assertNull(outcome.failure)
            assertTrue("the save landed with the reader still open", outcome.kept)
            assertFalse("so the canvas keeps its pages", original.isClosed)
            assertNull("and nothing had to be reopened", outcome.document)
            assertEquals("the file holds the pages", 3, DocumentLoader.load(file).use { it.pageCount })
            // The proof that nothing queued: the save is already back and its reader is still inside the lease.
            assertFalse("the reader was never waited for", reader.isDone)
            release.countDown(); reader.get(5, TimeUnit.SECONDS)
            assertEquals("and it still reads its own copy", 3, original.pageCount)
        } finally { release.countDown(); pool.shutdownNow(); original.close() }
    }
    @Test fun publishingTheSameDocumentKeepsTheUndoHistory() {
        val file = temp.newFile("undo.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(2))
        val tab = DesktopTab(DocumentLoader.load(file))
        try {
            tab.record(tab.document.snapshot())
            tab.record(tab.document.snapshot())
            assertTrue("two commands are undoable", tab.canUndo)
            assertTrue("and unpublished", tab.isDirty)
            // What an autosave over the tab's own file does once the bytes landed: the document on screen is
            // the one the steps describe, so only the **save point** moves.
            tab.notePublished(dirty = false)
            assertFalse("the file holds them, so the tab is clean", tab.isDirty)
            assertTrue("but Ctrl+Z still works across a save", tab.canUndo)
            assertTrue("and takes the step back", tab.undo())
            assertTrue("which makes the tab dirty again", tab.isDirty)
        } finally { tab.document.close() }
    }

    @Test fun sameFileMobixSaveRepacksOverItsReadCopy() {
        val file = temp.newFile("repack.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(3))
        val document = DocumentLoader.load(file)
        try {
            // A `.mobix` reads a **copy** in the cache, so the repack source can never equal the target path:
            // asking for that equality disabled the incremental write on every save, and each autosave rebuilt
            // and rewrote the whole notebook into a synchronised folder.
            val own = document.saveShapeFor(file)
            assertNotNull("a save over its own file repacks", own.repackOver)
            assertEquals("over the archive the pages were read from", document.pageSource, own.repackOver)
            assertNull("another destination is a whole write", document.saveShapeFor(temp.newFile("elsewhere.mobix")).repackOver)
        } finally { document.close() }
    }

    @Test fun autoSaveWritesADirtyDocumentAndStopsAskingOnceItLands() {
        val file = temp.newFile("automatic.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
        val state = DesktopShellState(
            settings = DesktopSettings(temp.newFile("automatic.properties")),
            recovery = DesktopRecovery(temp.newFolder()),
        )
        try {
            state.tabs.open(DocumentLoader.load(file))
            val tab = state.active!!
            tab.record(tab.document.snapshot())
            // The switch turned on over a document that is already dirty is itself a reason to write: the
            // quiet job is keyed on it, so the reader does not have to type again to be saved.
            state.prefs.pickAutoSave(true)
            assertTrue("the quiet job has something to write", state.autoSaveWanted())
            state.autoSaveNow()
            assertNull("and it wrote the file", state.failure)
            assertFalse("so the tab is clean", tab.isDirty)
            assertFalse("and the job stops asking", state.autoSaveWanted())

            // Stopping a recording after auto-save finished must dirty the tab and re-arm auto-save.
            tab.markAudioFinalized()
            assertTrue("finalizing audio marks the tab dirty", tab.isDirty)
            assertTrue("Save is enabled", tab.canSave)
            assertTrue("auto-save wants to write again", state.autoSaveWanted())
            state.autoSaveNow()
            assertNull("and the follow-up auto-save succeeds", state.failure)
            assertFalse("so the tab is clean again", tab.isDirty)
        } finally { state.tabs.closeAll() }
    }

    @Test fun aRecoveredTabNeverReadsTheSnapshotInPlace() {
        val recovery = temp.newFolder("recovery")
        val snapshot = File(recovery, "926ea51b.mobix")
        SyntheticNotebook.write(snapshot, SyntheticNotebook.pages(2))
        val origin = temp.newFile("note.mobix")
        SyntheticNotebook.write(origin, SyntheticNotebook.pages(2))
        val document = DocumentLoader.loadRecovered(snapshot, origin)
        try {
            assertEquals("the save target is the file the reader knows", origin.canonicalFile, document.file)
            // `recovery/` is rewritten after every quiet burst and deleted once the edits land: a tab that
            // read it in place made its save repack from a file another lane was replacing, which Windows
            // answered with "access denied" naming a `%APPDATA%` path the reader never chose.
            assertNotEquals("the pages come from a copy, not from the recovery folder", snapshot, document.pageSource)
            assertEquals("and that copy is the tab's to delete", document.pageSource, document.aside)
            assertEquals(
                "so a save to the recovered file repacks over the copy",
                document.pageSource,
                document.saveShapeFor(document.file).repackOver,
            )
        } finally { document.close() }
    }

    @Test fun aCloseAskedDuringASaveIsAnsweredWhenItLands() {
        val file = temp.newFile("close.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
        val state = DesktopShellState()
        try {
            state.tabs.open(DocumentLoader.load(file))
            val tab = state.active!!
            tab.record(tab.document.snapshot())
            state.requestSave()
            assertTrue("a write owns the document", state.isSaving)
            // The glyph and the X used to answer nothing at all until the bytes landed, which reads as a
            // hung window: the close is kept and asked again by the worker when its lane is free.
            assertFalse("the close waits rather than refusing", state.requestCloseTab(0))
            assertSame(tab, state.saveState.closeAfterSave)
            state.saveState.request = null
            state.flushPendingClose()
            assertSame("and it is the question asked now", tab, state.closeRequest?.tab)
        } finally { state.tabs.closeAll() }
    }

    @Test fun closeSaveCapturesTheRequestedTabEvenWhenAnotherTabIsActive() {
        val firstFile = temp.newFile("first.mobix")
        val secondFile = temp.newFile("second.mobix")
        SyntheticNotebook.write(firstFile, SyntheticNotebook.pages(1))
        SyntheticNotebook.write(secondFile, SyntheticNotebook.pages(1))
        val state = DesktopShellState()
        try {
            state.tabs.open(DocumentLoader.load(firstFile))
            val first = state.active!!
            state.tabs.open(DocumentLoader.load(secondFile))
            state.closeRequest = CloseRequest(first, false)
            state.requestCloseSave()
            assertSame(first, state.saveState.request!!.tab)
            assertFalse(state.requestExit())
        } finally { state.tabs.closeAll() }
    }
    @Test fun rawXmlImportUsesTheStreamingReaderAndClosesItsFile() {
        val file = temp.newFile("raw.xopp")
        file.writeText(com.mobixournal.format.Xopp.toXml(com.mobixournal.format.model.Document(pages = SyntheticNotebook.pages(2))))
        DocumentLoader.load(file).use { assertEquals(2, it.pageCount) }
        val replacement = temp.newFile("replacement.xopp").also { it.writeText("replacement") }
        java.nio.file.Files.move(replacement.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        assertEquals("replacement", file.readText())
    }
    @Test fun ordinarySaveUsesTheRememberedContainerRegardlessOfTheFileSuffix() {
        val file = temp.newFile("native-with-a-different-suffix.xopp")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(1))
        val state = DesktopShellState()
        try {
            state.tabs.open(DocumentLoader.load(file))
            state.requestSave()
            assertEquals(SaveFormat.MOBIX, state.saveState.request!!.format)
        } finally { state.tabs.closeAll() }
    }
}
