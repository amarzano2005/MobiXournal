package com.mobixournal.desktop

import androidx.compose.ui.input.key.Key
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.withPage
import com.mobixournal.render.ATTACH_DOMAIN
import com.mobixournal.render.documentWithPdfDomain
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The window's **write** half, exercised headlessly: an edit, a save, then the file re-opened by the
 * same loader the window uses.
 *
 * Every case is a round trip through the real container writers, because the claim being tested is the
 * one a save makes — *the file on disk holds the edit, in the container it already had*. A test that
 * inspected the encoder's arguments would pass while the tab wrote the wrong document; a test that
 * re-opens the file cannot.
 *
 * The three rules this suite pins (`docs/data-path.md`):
 *  - **sticky format**: a `.mobix` saves as a `.mobix` and a gzip `.xopp` as a gzip `.xopp`;
 *  - **Save As is the only switch**: the container follows the name the user gives it;
 *  - **a refused or failed write leaves the original untouched**, byte for byte — the save is staged
 *    in a temp file and moved over the target, so there is no moment at which the document is partial.
 */
class DesktopSaveTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ---- the version written into a .mobix ----------------------------------------------------

    /**
     * [DesktopSaves.APP_VERSION] is a `.mobix`'s `minAppVersion`, so a wrong one either locks a file to
     * a version that never shipped or lets an older build open one it cannot read. The window has no
     * `BuildConfig`, so the number is duplicated here and this test is what keeps the copy honest.
     */
    @Test
    fun appVersionFollowsTheModulesVersionName() {
        val buildFile = repoFile("app/build.gradle.kts")
        val declared = Regex("""versionName\s*=\s*"([^"]+)"""")
            .find(buildFile.readText())
            ?.groupValues
            ?.get(1)
        assertEquals(
            "DesktopSaves.APP_VERSION must be app/build.gradle.kts's versionName",
            declared,
            DesktopSaves.APP_VERSION,
        )
    }

    // ---- sticky format -----------------------------------------------------------------------

    /** A gzip `.xopp` saved after an edit is still a gzip `.xopp`, and the edit is in it. */
    @Test
    fun savesAGzipXoppBackInTheContainerItWasOpenedAs() {
        val file = temp.newFile("notes.xopp").apply { delete(); repoFile("udiff.xopp").copyTo(this) }
        val before = digest(file)
        val loaded = DocumentLoader.load(file)
        val edited = withExtraStroke(loaded, x = 137.0, id = "stk_desktop_edit")
        try {
            val uncompiled = DesktopSaves.save(edited, file)
            assertTrue("an ink-only .xopp draws everything it has: $uncompiled", uncompiled.isEmpty())

            assertNotSame("the file was actually rewritten", before, digest(file))
            assertNoStagedLeftovers(file.parentFile)

            val reopened = DocumentLoader.load(file)
            try {
                assertEquals("the sticky format kept it a gzip .xopp", DesktopContainer.XOPP_GZIP, reopened.container)
                assertEquals("no page was lost", loaded.pageCount, reopened.pageCount)
                assertEquals(
                    "the edit is on page 1 of the file",
                    strokesOf(loaded.model, 0) + 1,
                    strokesOf(reopened.model, 0),
                )
                assertTrue(
                    "and it is the stroke that was added, at its own position",
                    reopened.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()
                        .any { stroke -> stroke.points.first().x == 137.0 },
                )
            } finally {
                reopened.close()
            }
        } finally {
            edited.close()
        }
    }

    /**
     * A `.mobix` saved back onto itself repacks over its own archive (`SaveShape.repackOver`): the
     * edited page is rebuilt, every untouched page streams out of the file. What the test can see from
     * outside is that the page count, the container and the untouched ink all survive the edit.
     */
    @Test
    fun savesAMobixBackInTheContainerItWasOpenedAs() {
        val file = temp.newFile("notebook.mobix").apply { delete() }
        SyntheticNotebook.write(file, SyntheticNotebook.pages(40))
        // The tab's own path: a `.mobix` holds its archive open, so the write is staged, the handle
        // released and the file moved into place before it is opened again (Windows refuses otherwise).
        val loaded = DocumentLoader.load(file)
        val tab = DesktopTab(withExtraStroke(loaded, x = 210.0, id = "stk_desktop_edit"))
        try {
            tab.saveBack()
            assertNoStagedLeftovers(file.parentFile)

            val reopened = tab.document
            try {
                assertEquals("the sticky format kept it a .mobix", DesktopContainer.MOBIX, reopened.container)
                assertEquals("every page is still there", 40, reopened.pageCount)
                assertEquals("the edit is on page 1", 2, strokesOf(reopened.model, 0))
                assertTrue(
                    "the added stroke kept its position",
                    reopened.page(0).layers.flatMap { it.elements }.filterIsInstance<Stroke>()
                        .any { stroke -> stroke.points.first().x == 210.0 },
                )
                assertEquals("an untouched page still holds its own ink", 1, strokesOf(reopened.model, 3))
                assertEquals(
                    "and the fixture's stroke at x=3, not the edit",
                    3.0,
                    (reopened.page(3).layers.single().elements.single() as Stroke).points.first().x,
                    0.0,
                )
            } finally {
                reopened.close()
            }
        } finally {
            tab.document.close()
        }
    }

    /**
     * Save As is the one place the format changes: a gzip `.xopp` written to a name ending in `.mobix`
     * becomes a native archive — the whole-archive arm, not a repack — and the source file is left as
     * it was.
     */
    @Test
    fun writesANativeArchiveWhenSaveAsAsksForThatName() {
        val source = temp.newFile("notes.xopp").apply { delete(); repoFile("udiff.xopp").copyTo(this) }
        val sourceBytes = digest(source)
        val loaded = DocumentLoader.load(source)
        val edited = withExtraStroke(loaded, x = 42.0, id = "stk_desktop_edit")
        val target = File(temp.root, "notes.mobix")
        try {
            DesktopSaves.save(edited, target, SaveFormat.MOBIX)

            assertEquals("the original is untouched by a Save As", sourceBytes, digest(source))
            val reopened = DocumentLoader.load(target)
            try {
                assertEquals(DesktopContainer.MOBIX, reopened.container)
                assertEquals("the whole document travelled", loaded.pageCount, reopened.pageCount)
                assertEquals("with the edit in it", strokesOf(loaded.model, 0) + 1, strokesOf(reopened.model, 0))
            } finally {
                reopened.close()
            }
        } finally {
            edited.close()
            loaded.close()
        }
    }

    // ---- refusals and failures ----------------------------------------------------------------

    /**
     * A packaged `.xopp` carries its background *inside* the package, and the window has no store to
     * bundle a PDF back out of. It is refused **before** anything is written, so what the user had is
     * still on disk exactly as it was.
     */
    @Test
    fun refusesAPackagedXoppWithAnAttachmentAndLeavesItByteIdentical() {
        val file = temp.newFile("packaged.xopp").apply { delete() }
        val background = repoFile("bhm_prior.pdf")
        file.outputStream().use { out ->
            XoppZip.save(
                documentWithPdfDomain(pdfDocument(background), ATTACH_DOMAIN),
                background,
                out,
            )
        }
        val loaded = DocumentLoader.load(file)
        try {
            assertEquals(DesktopContainer.XOPP_ZIP, loaded.container)
            val before = digest(file)
            val refused = runCatching { DesktopSaves.save(loaded, file) }.exceptionOrNull()
            assertTrue("a package with attachments is refused: $refused", refused is DesktopSaves.SaveRefused)
            assertTrue(
                "and the refusal says what to do instead: ${refused?.message}",
                refused?.message?.contains(".mobix") == true,
            )
            assertEquals("the original is byte-identical", before, digest(file))
            assertNoStagedLeftovers(file.parentFile)
        } finally {
            loaded.close()
        }
    }

    /** The refusal reaches the user as a sentence in the shell's own notice, not as an exception. */
    @Test
    fun reportsARefusedSaveInTheShellsNotice() {
        val file = temp.newFile("packaged.xopp").apply { delete() }
        val background = repoFile("bhm_prior.pdf")
        file.outputStream().use { out ->
            XoppZip.save(
                documentWithPdfDomain(pdfDocument(background), ATTACH_DOMAIN),
                background,
                out,
            )
        }
        val state = DesktopShellState()
        try {
            state.open(file)
            assertNotNull("the package opened", state.tabs.active)
            state.save()
            val reported = state.failure
            assertNotNull("the failed save is reported", reported)
            assertEquals("This document cannot be saved there", reported!!.title)
            assertTrue("and the notice names the way out: ${reported.detail}", reported.detail.contains(".mobix"))
        } finally {
            state.tabs.closeAll()
        }
    }

    // ---- the shell's Save As ------------------------------------------------------------------

    /**
     * `Ctrl+Shift+S` reaches the picker and the write that follows re-homes the tab: the next `Ctrl+S`
     * writes back to the new file, in the container the user chose. The page and viewport stay put — a
     * Save As is not a new document.
     */
    @Test
    fun saveAsRehomesTheTabOnTheFileItWrote() {
        val source = temp.newFile("notebook.mobix").apply { delete() }
        SyntheticNotebook.write(source, SyntheticNotebook.pages(5))
        val target = File(temp.root, "renamed.xopp")
        val state = DesktopShellState()
        try {
            state.open(source)
            val tab = state.tabs.active!!
            tab.page = 2
            var pickerOpened = false
            val handled = state.apply(ShellCommand.SAVE_AS, onSavePicker = { pickerOpened = true })
            assertTrue("Save As is handled", handled)
            assertTrue("and it reaches the platform picker", pickerOpened)

            // The picker is the platform's; what it hands back is the destination, and that is the write.
            state.saveAs(target)
            assertTrue("the write succeeded", state.failure == null)

            assertEquals("the tab follows the file it wrote", target.absoluteFile, tab.file.absoluteFile)
            assertEquals("and the container it chose", DesktopContainer.XOPP_GZIP, tab.document.container)
            assertEquals("the page it was on is kept", 2, tab.page)
            assertEquals("the label follows the name", "renamed.xopp", tab.title)

            // The next Save writes back there: the file it was re-homed onto stays the destination.
            state.save()
            assertTrue(state.failure == null)
            assertTrue("the new file is the one that grows", target.exists())
            assertEquals("and the source is still a .mobix of its own", DesktopContainer.MOBIX, DocumentLoader.load(source).use { it.container })
        } finally {
            state.tabs.closeAll()
        }
    }

    /**
     * The format is the Save As **question's** answer, not a guess from the file's name: a reader who types
     * over the suffix still gets the container they picked (`SaveAsDialog.kt`).
     */
    @Test
    fun saveAsWritesTheChosenContainerWhateverTheName() {
        val source = temp.newFile("picked.mobix").apply { delete() }
        SyntheticNotebook.write(source, SyntheticNotebook.pages(2))
        // A name with no recognised suffix: reading the name would have kept the document's own container.
        val target = File(temp.root, "chosen.txt")
        val state = DesktopShellState()
        try {
            state.open(source)
            val tab = state.tabs.active!!
            assertEquals(DesktopContainer.MOBIX, tab.document.container)

            state.saveAs(target, SaveFormat.ZIPPED)
            assertTrue("the write succeeded", state.failure == null)
            assertEquals(
                "the file on disk is the container the question answered",
                DesktopContainer.XOPP_ZIP,
                DocumentLoader.load(target).use { it.container },
            )
            assertEquals("and the tab follows it", DesktopContainer.XOPP_ZIP, tab.document.container)
        } finally {
            state.tabs.closeAll()
        }
    }

    /** The two shortcuts the save hangs on, as the table in `docs/ui-modernization.md` states them. */
    @Test
    fun saveKeysMeaning() {
        assertEquals(ShellCommand.SAVE, shellCommand(Key.S, ctrl = true, shift = false))
        assertEquals(ShellCommand.SAVE_AS, shellCommand(Key.S, ctrl = true, shift = true))
        assertEquals(
            "a bare S is the selection tool, not a save",
            null,
            shellCommand(Key.S, ctrl = false, shift = false),
        )
    }

    // ---- fixtures ----------------------------------------------------------------------------

    /** [document] with one more stroke on its first page — an edit, as the page window will make one. */
    private fun withExtraStroke(document: DesktopDocument, x: Double, id: String): DesktopDocument {
        val model = document.model
        val page = model.pages[0]
        val layer = page.layers.first()
        val editedPage = page.copy(
            layers = listOf(layer.copy(elements = layer.elements + SyntheticNotebook.stroke(x, id))) +
                page.layers.drop(1),
        )
        return DesktopDocument(
            file = document.file,
            source = document.source,
            model = model.withPage(0, editedPage),
            container = document.container,
            backdrop = document.backdrop,
            shape = document.shape,
        )
    }

    private fun strokesOf(document: Document, index: Int): Int =
        document.pages[index].layers.sumOf { layer -> layer.elements.count { it is Stroke } }

    /** A one-page document whose background is [pdf], for the packaged-`.xopp` fixture. */
    private fun pdfDocument(pdf: File): Document = Document(
        pages = listOf(
            Page(
                595.0,
                842.0,
                com.mobixournal.format.model.Background.Pdf(filename = pdf.name, pageNo = 0, domain = "absolute"),
                listOf(Layer(listOf(SyntheticNotebook.stroke(1.0, id = "stk_pdf")))),
            ),
        ),
    )

    private fun digest(file: File): String =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    /** The staged file a save writes through must be gone, whatever the outcome. */
    private fun assertNoStagedLeftovers(folder: File?) {
        assertTrue("the fixture has a folder to inspect", folder != null)
        val leftovers = folder!!.listFiles()?.filter { it.name.endsWith(".writing") }.orEmpty()
        assertTrue("a staged temp file was left behind: $leftovers", leftovers.isEmpty())
    }

    /** The module's working directory is `desktop/`, so walk up to the repository root. */
    private fun repoFile(name: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, name)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("$name not found above ${System.getProperty("user.dir")}")
    }
}
