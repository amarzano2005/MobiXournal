package com.mobixournal.io

import android.content.ContentResolver
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixChunk
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.render.blankDocument
import com.mobixournal.ui.NewDocumentCanvas
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A document **created** on an infinite canvas is written as chunks by its first save.
 *
 * The format could hold a board and the app could open one, but nothing could *make* one: every new
 * document was a paginated `.mobix` (or a `.xopp`) whatever the user wanted, so the only way to a
 * chunked file was to author it elsewhere. What this pins is the birth: the geometry setting reaches
 * the writer, a board is written on `chunks/` from the very first save rather than converted later,
 * and a `.xopp` — which has no chunk container — is paged whatever the setting says.
 *
 * The other half of the same rule is here too: a document the session **opened** keeps the geometry its
 * own file declares, so a caller that believes otherwise (a tab restored from a record written before
 * the setting existed) can never turn a board into a paginated rewrite.
 */
class NewDocumentCanvasTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val cacheDir by lazy { temp.newFolder("cache") }
    private val filesDir by lazy { temp.newFolder("files") }

    private fun createIo(): DocumentIo = DocumentIo(
        resolver = object : ContentResolver(null) {},
        cacheDir = cacheDir,
        filesDir = filesDir,
        appVersion = "1.3.0",
        textPdf = null,
        entrySources = MobixEntrySource::fromFile,
    )

    private fun entriesOf(file: File): List<String> =
        file.inputStream().use { MobixContainer.open(it.buffered()).names.toList() }

    /** The `canvas.json` mode of the archive at [file] — what a reopen reports and a save must keep. */
    private fun modeOf(file: File): CanvasMode =
        file.inputStream().use { input ->
            MobixContainer.open(input.buffered()).let { archive ->
                MobixCanvasFile
                    .decode(archive.require(MobixFormat.CANVAS_ENTRY).bytes.toString(Charsets.UTF_8))
                    .mode
            }
        }

    /** Save [document] as a `.mobix` with no target, as a first save of a new document does. */
    private fun save(document: Document, canvasMode: CanvasMode): File =
        createIo().encode(
            document = document,
            pdf = null,
            format = SaveFormat.MOBIX,
            target = null,
            canvasMode = canvasMode,
        ).file

    /** A chunked `.mobix` to open: one chunk, at a coordinate no strip layout would derive. */
    private fun writeBoard(file: File): File {
        val document = MobixDocument.forInfinite(
            documentId = "doc_board",
            canvasMode = CanvasMode.INFINITE_2D,
            widthPt = 1024.0,
            heightPt = 1024.0,
            chunks = listOf(
                MobixChunk(
                    cx = 2,
                    cy = -1,
                    layers = listOf(Layer(listOf(SyntheticNotebook.stroke(5.0, id = "stk_board")))),
                    id = "ch_2_-1",
                ),
            ),
            title = "Board",
        )
        FileOutputStream(file).use { out -> MobixContainer.save(document.encode(nowMillis = 1L).entries, out) }
        return file
    }

    @Test
    fun aNewDocumentBornOnABoardIsWrittenAsChunks() {
        val document = blankDocument()
        assertNull("a brand-new document has no identity yet", document.documentId)

        val saved = save(document, NewDocumentCanvas.INFINITE_VERTICAL.modeFor(SaveFormat.MOBIX))
        val entries = entriesOf(saved)

        assertTrue("the write lands on chunks/", entries.contains(MobixFormat.chunkEntry(0, 0)))
        assertFalse("and never on pages/", entries.any { MobixFormat.isPageEntry(it) })
        assertEquals(CanvasMode.INFINITE_VERTICAL, modeOf(saved))

        val reopened = createIo().read(saved) as LoadedFile.Doc
        assertEquals("a reopen reports the geometry it was written on", CanvasMode.INFINITE_VERTICAL, reopened.canvasMode)
        assertEquals("the blank page came back as the board's first chunk", 1, reopened.document.pages.size)
    }

    @Test
    fun aStripExtendsInTheDirectionItsModeRuns() {
        val document = Document(pages = SyntheticNotebook.pages(2))

        val downwards = entriesOf(save(document, CanvasMode.INFINITE_VERTICAL))
        assertTrue(
            "a vertical board runs down: chunk (0, 0) then (0, 1)",
            downwards.containsAll(listOf(MobixFormat.chunkEntry(0, 0), MobixFormat.chunkEntry(0, 1))),
        )

        val rightwards = entriesOf(save(document, CanvasMode.INFINITE_HORIZONTAL))
        assertTrue(
            "a horizontal one runs right: chunk (0, 0) then (1, 0)",
            rightwards.containsAll(listOf(MobixFormat.chunkEntry(0, 0), MobixFormat.chunkEntry(1, 0))),
        )
    }

    @Test
    fun theDefaultGeometryIsStillWrittenAsPages() {
        val entries = entriesOf(save(blankDocument(), CanvasMode.PAGINATED))

        assertTrue("a new document on the default geometry is paged", entries.any { MobixFormat.isPageEntry(it) })
        assertFalse("and carries no chunks", entries.any { MobixFormat.isChunkEntry(it) })
    }

    @Test
    fun aXoppIsBornPagedWhateverTheGeometrySettingSays() {
        assertEquals(CanvasMode.PAGINATED, NewDocumentCanvas.INFINITE_2D.modeFor(SaveFormat.ORIGINAL))
        assertEquals(CanvasMode.PAGINATED, NewDocumentCanvas.INFINITE_2D.modeFor(SaveFormat.ZIPPED))
        assertEquals(CanvasMode.INFINITE_2D, NewDocumentCanvas.INFINITE_2D.modeFor(SaveFormat.MOBIX))
        assertEquals(CanvasMode.PAGINATED, NewDocumentCanvas.PAGINATED.modeFor(SaveFormat.MOBIX))
    }

    @Test
    fun everyModeRoundTripsThroughThePageFormatDialogsChoice() {
        // The dialog shows the geometry the canvas is in: each mode names exactly one choice, and the
        // choice it names gives the mode back (which is what keeps the dialog from opening on "Paged"
        // over a board and offering to "change" it to what it already is).
        for (mode in CanvasMode.entries) {
            assertEquals(mode, NewDocumentCanvas.of(mode).mode)
        }
    }

    @Test
    fun anOpenedBoardKeepsTheModeItsFileDeclares() {
        val file = writeBoard(temp.newFile("board.mobix"))
        val io = createIo()
        val loaded = io.read(file) as LoadedFile.Doc
        assertEquals(CanvasMode.INFINITE_2D, loaded.canvasMode)

        // A Save As to another name, by a caller that claims the document is paged — a tab restored
        // from a record written before the geometry existed. The file wins: a chunked document is never
        // rewritten as a paginated one.
        val encoded = io.encode(
            document = loaded.document,
            pdf = null,
            format = SaveFormat.MOBIX,
            target = null,
            canvasMode = CanvasMode.PAGINATED,
        )

        assertEquals(CanvasMode.INFINITE_2D, modeOf(encoded.file))
        assertTrue("the board's own coordinate survives", entriesOf(encoded.file).contains(MobixFormat.chunkEntry(2, -1)))
        assertFalse(entriesOf(encoded.file).any { MobixFormat.isPageEntry(it) })
    }
}
