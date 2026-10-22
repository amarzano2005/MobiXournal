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
import com.mobixournal.format.mobix.MobixIncrementalSave
import com.mobixournal.format.mobix.MobixManifest
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.MobixPart
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.mobix.CountingEntrySource
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.withPage
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A chunked `.mobix` opens, and a save keeps it chunked — at its own coordinates.
 *
 * The format half of the infinite canvas was complete and unreachable: `MobixDocument.open` reads
 * chunks, `toDocument` maps them to pages, `of`/`forInfinite` write them — while the open path refused
 * the mode outright, and a save never asked for anything but `PAGINATED`. What this pins is the part a
 * round trip is actually worth: the parts come back at the **coordinates they were at**, because a
 * chunk is named after them and a chunk's place in the manifest is not its place on the board. A save
 * that derived them from the index would move every chunk of a 2D board to the first column, which is
 * data loss dressed as a format choice.
 */
class ChunkedMobixRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val cacheDir by lazy { temp.newFolder("cache") }
    private val filesDir by lazy { temp.newFolder("files") }

    private class Counted {
        val counted = mutableListOf<CountingEntrySource>()

        val open: (File) -> MobixEntrySource = { file ->
            CountingEntrySource(MobixEntrySource.fromFile(file)).also { counted += it }
        }

        fun count(name: String): Int = counted.sumOf { it.openCount(name) }

        fun inflates(name: String): Int = counted.sumOf { it.openCount(name) }

        /** How many times [name] was copied out verbatim instead of being inflated. */
        fun copies(name: String): Int = counted.sumOf { it.copyCount(name) }

        /** Every entry of [names] that was opened at all. */
        fun read(names: Collection<String>): List<String> = names.filter { count(it) > 0 }
    }

    private fun createIo(entrySources: (File) -> MobixEntrySource = MobixEntrySource::fromFile): DocumentIo =
        DocumentIo(
            resolver = object : ContentResolver(null) {},
            cacheDir = cacheDir,
            filesDir = filesDir,
            appVersion = "1.3.0",
            textPdf = null,
            entrySources = entrySources,
        )

    /** The coordinates the fixture's parts sit at — two of them negative, so a signed name is tested. */
    private val coords = listOf(-3 to 2, 0 to 0, 2 to -1)

    /** The entry names an archive holds, in order. */
    private fun entriesOf(file: File): List<String> =
        file.inputStream().use { MobixContainer.open(it.buffered()).names.toList() }

    private fun chunkNames() = coords.map { (cx, cy) -> MobixFormat.chunkEntry(cx, cy) }

    /** The bytes of one entry of the archive at [file], as a comparable list. */
    private fun bytesOf(file: File, name: String): List<Byte> =
        file.inputStream().use { MobixContainer.open(it.buffered()).require(name).bytes.toList() }

    /** The board geometry `canvas.json` of the archive at [file] declares. */
    private fun canvasOf(file: File): MobixCanvasFile =
        file.inputStream().use { MobixContainer.open(it.buffered()) }
            .let { archive -> MobixCanvasFile.decode(archive.require(MobixFormat.CANVAS_ENTRY).bytes.toString(Charsets.UTF_8)) }

    /** The `chunks[]` parts a `.mobix`'s manifest records — id, entry, hash and stamp. */
    private fun chunkPartsOf(file: File): List<MobixPart> =
        file.inputStream().use { MobixContainer.open(it.buffered()) }
            .let { archive -> MobixManifest.decode(archive.require(MobixFormat.MANIFEST_ENTRY).bytes.toString(Charsets.UTF_8)) }
            .chunks

    /** The envelope entries a chunked open is allowed to read: no part of it is a page. */
    private fun envelope() = listOf(
        MobixFormat.MIMETYPE_ENTRY,
        MobixFormat.MANIFEST_ENTRY,
        MobixFormat.CANVAS_ENTRY,
        MobixFormat.BRIDGE_ENTRY,
    )

    /** A chunked `.mobix`: one chunk per coordinate, each holding one stroke. */
    private fun writeBoard(file: File): File {
        val chunks = coords.mapIndexed { index, (cx, cy) ->
            MobixChunk(
                cx = cx,
                cy = cy,
                layers = listOf(Layer(listOf(SyntheticNotebook.stroke(10.0 + index, id = "stk_$cx$cy")))),
                id = "ch_${cx}_$cy",
            )
        }
        val document = MobixDocument.forInfinite(
            documentId = "doc_board",
            canvasMode = CanvasMode.INFINITE_2D,
            widthPt = 1024.0,
            heightPt = 1024.0,
            chunks = chunks,
            title = "Board",
        )
        FileOutputStream(file).use { out -> MobixContainer.save(document.encode(nowMillis = 1L).entries, out) }
        return file
    }

    @Test
    fun aChunkedDocumentOpensAsPagesWithNoChunkInflatedAtOpen() {
        val file = writeBoard(temp.newFile("board.mobix"))
        val counter = Counted()

        val loaded = createIo(counter.open).read(file) as LoadedFile.Doc

        assertEquals(SaveFormat.MOBIX, loaded.format)
        assertEquals("the mode travels with the loaded document", CanvasMode.INFINITE_2D, loaded.canvasMode)
        assertEquals("a chunk is a page, in manifest order", 3, loaded.document.pages.size)
        assertEquals(
            "the open reads the envelope and no part of the document",
            emptyList<String>(),
            counter.read(chunkNames()),
        )

        // …and a page read is the chunk read, with the id the manifest gave that part.
        assertEquals(1, loaded.document.pages[0].layers.single().elements.size)
        assertEquals(1, counter.count(MobixFormat.chunkEntry(-3, 2)))
        assertEquals(0, counter.count(MobixFormat.chunkEntry(0, 0)))
        assertEquals(0, counter.count(MobixFormat.chunkEntry(2, -1)))
        assertEquals(
            "nothing but the chunk that page needed",
            chunkNames().take(1),
            counter.read(chunkNames()),
        )

        // Reading a page's *id* goes through the model's page, so the order is what reading the whole
        // board costs — the manifest's own order, one part at a time.
        assertEquals(
            listOf("ch_-3_2", "ch_0_0", "ch_2_-1"),
            (0 until 3).map { loaded.document.pages[it].id },
        )
    }

    @Test
    fun aSaveWithNoSourceWritesTheChunksBackAtTheirOwnCoordinates() {
        val file = writeBoard(temp.newFile("board.mobix"))
        val io = createIo()
        val loaded = io.read(file) as LoadedFile.Doc

        val encoded = io.encode(loaded.document, pdf = null, format = SaveFormat.MOBIX, target = null)
        val names = entriesOf(encoded.file)

        for (name in chunkNames()) assertTrue("$name must be written back where it was", name in names)
        assertTrue(
            "a chunked document is never rewritten as a paginated one",
            names.none { it.startsWith("pages/") },
        )
        assertTrue("the chunk half of the envelope travels too", MobixFormat.CANVAS_ENTRY in names)
        // The board's own geometry is not derivable from its pages: a save that rebuilt `canvas.json`
        // would change the locked dimension and drop the origin and the fields this build does not model.
        assertEquals(
            "canvas.json comes back exactly as the file wrote it",
            bytesOf(file, MobixFormat.CANVAS_ENTRY),
            bytesOf(encoded.file, MobixFormat.CANVAS_ENTRY),
        )
    }

    @Test
    fun anEditedChunkKeepsTheCoordinatesItWasAt() {
        val file = writeBoard(temp.newFile("board.mobix"))
        val io = createIo()
        val loaded = io.read(file) as LoadedFile.Doc

        // An edit lands on one page; the two around it are untouched. The edited one still belongs at
        // (-3, 2), which is where the file said it was.
        val edited = loaded.document.withPage(
            0,
            loaded.document.pages[0].copy(
                layers = listOf(Layer(listOf(SyntheticNotebook.stroke(99.0, id = "stk_edited")))),
            ),
        )

        val encoded = io.encode(edited, pdf = null, format = SaveFormat.MOBIX, target = null)
        val names = entriesOf(encoded.file)

        for (name in chunkNames()) assertTrue("$name must survive the edit", name in names)
        assertTrue("and nothing may be written at a deduced coordinate", "chunks/chunk_0_0.json" in names)
        assertFalse("nor any paginated entry", names.any { it.startsWith("pages/") })

        // The edit is in the file that comes back, at the coordinate the original had it at.
        val reopened = createIo().read(encoded.file) as LoadedFile.Doc
        assertEquals(CanvasMode.INFINITE_2D, reopened.canvasMode)
        assertEquals(3, reopened.document.pages.size)
        val stroke = reopened.document.pages[0].layers.single().elements.single() as Stroke
        assertEquals(99.0, stroke.points.first().x, 0.001)
    }

    @Test
    fun aSaveOverItsOwnFileKeepsItChunkedAndCopiesTheChunksItDidNotTouch() {
        val file = writeBoard(temp.newFile("board.mobix"))
        val loaded = createIo().read(file) as LoadedFile.Doc

        // The app's own save path, over the file the document was opened from: the shape the session
        // opened it with (document I/O reads it out of `MobixOpenSessions` and passes it on).
        val out = temp.newFile("repacked.mobix")
        FileOutputStream(out).use { output ->
            MobixEntrySource.fromFile(file).use { source ->
                MobixIncrementalSave.saveOver(
                    source = source,
                    document = loaded.document,
                    appVersion = "1.3.0",
                    nowMillis = 2L,
                    output = output,
                    canvasMode = CanvasMode.INFINITE_2D,
                    chunkCoords = coords,
                    canvas = canvasOf(file),
                )
            }
        }

        val names = entriesOf(out)
        for (name in chunkNames()) assertTrue("$name keeps its place in the repacked file", name in names)
        assertTrue("no page entry appears", names.none { it.startsWith("pages/") })

        // …and the file that comes out is still a chunked document when it is reopened.
        val reopened = createIo().read(out) as LoadedFile.Doc
        assertEquals(CanvasMode.INFINITE_2D, reopened.canvasMode)
        assertEquals(3, reopened.document.pages.size)
        assertNotNull(reopened.document.pages[0].layers.single().elements.single())
    }

    /**
     * The session remembers the shape of the `.mobix` it opened, by document id: the mode, the board
     * geometry, and the coordinates each part sits at — read out of the manifest's own entry names,
     * which is why remembering them costs no page read. That is what lets the app's save path hand the
     * writer the shape back ([MobixOpenSessions], `docs/data-path.md`).
     */
    @Test
    fun theOpenedShapeIsRememberedByDocumentAndReleasedWithIt() {
        val file = writeBoard(temp.newFile("board.mobix"))
        val loaded = createIo().read(file) as LoadedFile.Doc
        val sessions = MobixOpenSessions()
        val opened = MobixPagedReader(MobixEntrySource.fromFile(file))
        opened.use { reader ->
            sessions.remember("doc_board", file, null)
            sessions.shape(MobixDocument.openPaged(reader))
        }

        val document = loaded.document
        assertEquals(CanvasMode.INFINITE_2D, sessions.canvasModeOf(document))
        assertEquals(coords, sessions.chunkCoordsOf(document))
        assertEquals(canvasOf(file), sessions.canvasOf(document))

        // Nothing is remembered for a document the session never opened, and a document the session
        // has let go is forgotten — its next save would build the archive afresh rather than repack.
        assertEquals(CanvasMode.PAGINATED, sessions.canvasModeOf(document.copy(documentId = "other")))
        assertEquals(null, sessions.chunkCoordsOf(document.copy(documentId = "other")))
        sessions.prune(listOf(document.documentId))
        assertEquals(coords, sessions.chunkCoordsOf(document))
        sessions.prune(emptyList())
        assertEquals(null, sessions.chunkCoordsOf(document))
        assertEquals(null, sessions.canvasOf(document))
    }

    /**
     * A save that changed nothing rewrites the manifest and nothing else: every part of the board is
     * streamed out of the file it was read from, keeping its own `sha256` and `modified` stamp, which is
     * what a merge and a sync client read to see that nothing moved (`docs/mobix-format.md`).
     */
    @Test
    fun aSaveWithNothingChangedRebuildsNoChunk() {
        val file = writeBoard(temp.newFile("board.mobix"))
        val counter = Counted()
        val loaded = createIo(counter.open).read(file) as LoadedFile.Doc

        val out = temp.newFile("repacked.mobix")
        FileOutputStream(out).use { output ->
            MobixEntrySource.fromFile(file).use { source ->
                MobixIncrementalSave.saveOver(
                    source = source,
                    document = loaded.document,
                    appVersion = "1.3.0",
                    nowMillis = 2L,
                    output = output,
                    canvasMode = CanvasMode.INFINITE_2D,
                    chunkCoords = coords,
                    canvas = canvasOf(file),
                )
            }
        }
        // A part the save does not write is never inflated: its bytes are copied out of the file. The
        // only page the write reads is the first, which carries the board's `defaultPaper` into the
        // manifest — one page however long the board is.
        for (index in listOf(1, 2)) {
            val name = MobixFormat.chunkEntry(coords[index].first, coords[index].second)
            assertEquals("$name is never inflated", 0, counter.inflates(name))
        }

        val before = entriesOf(file)
        val after = entriesOf(out)
        assertTrue("no part of the board is dropped", before.all { it in after })
        // The manifest carries this save's own `modified` stamp and the bridge record names the file the
        // save went to; every *part* of the document is the source's own bytes, untouched.
        for (name in chunkNames()) {
            assertEquals("$name is the source's own bytes", bytesOf(file, name), bytesOf(out, name))
        }
        assertEquals(
            "and so is the board geometry",
            bytesOf(file, MobixFormat.CANVAS_ENTRY),
            bytesOf(out, MobixFormat.CANVAS_ENTRY),
        )
        assertEquals(
            "every chunk part is the manifest's own record of it",
            chunkPartsOf(file),
            chunkPartsOf(out),
        )
    }

    /**
     * A chunk the session never touched is **copied** out of the source archive rather than rebuilt —
     * the chunk half of [MobixPageReuse], which is what makes saving a long board cost the chunk that
     * moved instead of the board.
     */
    @Test
    fun aSaveOverItsOwnFileRebuildsOnlyTheChunkThatMoved() {
        val file = writeBoard(temp.newFile("board.mobix"))
        val loaded = createIo().read(file) as LoadedFile.Doc
        val edited = loaded.document.withPage(
            1,
            loaded.document.pages[1].copy(
                layers = listOf(Layer(listOf(SyntheticNotebook.stroke(50.0, id = "stk_moved")))),
            ),
        )
        val out = temp.newFile("repacked.mobix")
        val source = CountingEntrySource(MobixEntrySource.fromFile(file))
        val stats = FileOutputStream(out).use { output ->
            source.use { from ->
                MobixIncrementalSave.saveOver(
                    source = from,
                    document = edited,
                    appVersion = "1.3.0",
                    nowMillis = 2L,
                    output = output,
                    canvasMode = CanvasMode.INFINITE_2D,
                    chunkCoords = coords,
                )
            }
        }

        // An untouched chunk is copied out of the archive as the bytes it already is — opened for the
        // copy, never inflated, so its JSON is not decoded, re-encoded or re-deflated on the way
        // through — and the bytes that land in the new file are its own.
        for (index in listOf(0, 2)) {
            val name = MobixFormat.chunkEntry(coords[index].first, coords[index].second)
            assertTrue("$name is copied out of the source", source.copyCount(name) >= 1)
            assertEquals("$name is copied, never inflated", 0, source.openCount(name))
            assertEquals("$name lands in the new file byte for byte", bytesOf(file, name), bytesOf(out, name))
        }
        // …and its manifest part is the archive's own record of it, unchanged: what a merge and a sync
        // client read to see that this part did not move.
        val partsBefore = chunkPartsOf(file).associateBy { it.entry }
        for (index in listOf(0, 2)) {
            val name = MobixFormat.chunkEntry(coords[index].first, coords[index].second)
            assertEquals("$name's manifest part is the source's own", partsBefore[name], chunkPartsOf(out).associateBy { it.entry }[name])
        }
        val moved = MobixFormat.chunkEntry(coords[1].first, coords[1].second)
        assertTrue("the chunk that moved is rewritten", bytesOf(file, moved) != bytesOf(out, moved))
        assertTrue("and the repack says so", stats.rewrittenEntries >= 1 && stats.streamedEntries >= 2)

        // The board reads back with the edit at the coordinate it happened at, and the rest untouched.
        val reopened = createIo().read(out) as LoadedFile.Doc
        assertEquals(CanvasMode.INFINITE_2D, reopened.canvasMode)
        val xs = reopened.document.pages.map { page ->
            ((page.layers.single().elements.single()) as Stroke).points.first().x
        }
        assertEquals(listOf(10.0, 50.0, 12.0), xs)
    }
}
