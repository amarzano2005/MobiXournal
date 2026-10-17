package com.mobixournal.io

import android.content.ContentResolver
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixChunk
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.render.blankPage
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The board geometry the canvas is handed — and the two ways it is answered.
 *
 * A board's shape cannot be read off its pages: a page says how big its content is, not which cell of
 * the board it sits in, and a `.mobix` opened page by page never reads a page at open. So the answer
 * comes from the file the session opened (`canvas.json` plus the manifest's own entry names), and only
 * a document that has never been a file — one created here, born on a geometry in Settings — falls back
 * to the strip its mode runs in, the way the writer lays it out.
 */
class CanvasGeometryTest {

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

    /** A chunked `.mobix` on `canvas.json`'s own numbers, with one chunk per coordinate given. */
    private fun writeBoard(file: File, coords: List<Pair<Int, Int>>, chunkSizePt: Double = 1024.0): File {
        val document = MobixDocument.forInfinite(
            documentId = "doc_board",
            canvasMode = CanvasMode.INFINITE_2D,
            widthPt = 4096.0,
            heightPt = 2048.0,
            chunkSizePt = chunkSizePt,
            chunks = coords.mapIndexed { index, (cx, cy) ->
                MobixChunk(
                    cx = cx,
                    cy = cy,
                    layers = listOf(Layer(listOf(SyntheticNotebook.stroke(10.0 + index, id = "stk_$cx$cy")))),
                    id = "ch_${cx}_$cy",
                )
            },
            title = "Board",
        )
        FileOutputStream(file).use { out -> MobixContainer.save(document.encode(nowMillis = 1L).entries, out) }
        return file
    }

    @Test
    fun `an opened board is laid out at the cells its file names`() {
        val file = writeBoard(temp.newFile("board.mobix"), coords = listOf(2 to -1, 0 to 0))
        val io = createIo()
        val loaded = io.read(file) as LoadedFile.Doc

        val geometry = io.canvasGeometryOf(loaded.document, loaded.canvasMode)

        assertNotNull("a chunked document is a board", geometry)
        assertEquals(CanvasMode.INFINITE_2D, geometry!!.mode)
        assertEquals("the cells are the manifest's own, in document order", listOf(2 to -1, 0 to 0), geometry.chunkCoords)
        assertEquals("the padding of a 2D board is not derived from the index", 2, geometry.chunkCoords[0].first)
        assertEquals(4096.0, geometry.widthPt, 0.001)
        assertEquals(2048.0, geometry.heightPt, 0.001)
        assertEquals(1024.0, geometry.chunkSizePt, 0.001)
    }

    @Test
    fun `a board written with another tile size keeps its own grid`() {
        val file = writeBoard(temp.newFile("smalltiles.mobix"), coords = listOf(0 to 0), chunkSizePt = 256.0)
        val io = createIo()
        val loaded = io.read(file) as LoadedFile.Doc

        val geometry = io.canvasGeometryOf(loaded.document, loaded.canvasMode)

        assertEquals(
            "the document's tile size, not this build's default",
            256.0,
            geometry!!.chunkSizePt,
            0.001,
        )
    }

    @Test
    fun `a board this session never opened is laid out as its mode runs`() {
        val io = createIo()
        val created = Document(pages = SyntheticNotebook.pages(3))

        val down = io.canvasGeometryOf(created, CanvasMode.INFINITE_VERTICAL)
        assertEquals(CanvasMode.INFINITE_VERTICAL, down!!.mode)
        assertEquals("a vertical board's parts run down, one cell each", listOf(0 to 0, 0 to 1, 0 to 2), down.chunkCoords)
        assertEquals("its sheet is the page it was born with", 595.0, down.widthPt, 0.001)
        assertEquals(842.0, down.heightPt, 0.001)
        assertEquals(MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT, down.chunkSizePt, 0.001)

        val right = io.canvasGeometryOf(created, CanvasMode.INFINITE_HORIZONTAL)
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0), right!!.chunkCoords)
    }

    @Test
    fun `a strip locks one axis and a board can only move that one`() {
        val io = createIo()
        val created = Document(pages = SyntheticNotebook.pages(1))

        val down = io.canvasGeometryOf(created, CanvasMode.INFINITE_VERTICAL)!!
        assertEquals("a vertical strip's bound is its width", 595.0, down.fixedExtentPt!!, 0.001)
        assertTrue(down.fixedExtentIsWidth)
        val wider = down.withFixedExtent(1200.0)
        assertEquals(1200.0, wider.widthPt, 0.001)
        assertEquals("the running axis is untouched", 842.0, wider.heightPt, 0.001)

        val right = io.canvasGeometryOf(created, CanvasMode.INFINITE_HORIZONTAL)!!
        assertEquals("a horizontal strip's bound is its height", 842.0, right.fixedExtentPt!!, 0.001)
        val taller = right.withFixedExtent(1200.0)
        assertEquals(1200.0, taller.heightPt, 0.001)
        assertEquals("the running axis is untouched", 595.0, taller.widthPt, 0.001)

        val board2d = io.canvasGeometryOf(created, CanvasMode.INFINITE_2D)!!
        assertNull("a 2D board locks nothing, so there is no bound to apply", board2d.fixedExtentPt)
        assertEquals("and none is invented for it", board2d, board2d.withFixedExtent(1200.0))
    }

    @Test
    fun `a strip born at the pane's size takes that width as its bound`() {
        // What `MainActivity.blankTab` does for a strip: the one locked side is the pane's own extent
        // in pt, so the sheet covers the canvas from the first frame and the bound is that number.
        val io = createIo()
        val born = Document(pages = listOf(blankPage(2400.0, 841.89)))

        val down = io.canvasGeometryOf(born, CanvasMode.INFINITE_VERTICAL)!!
        assertEquals(2400.0, down.fixedExtentPt!!, 0.001)
        assertEquals("the running axis keeps the default paper", 841.89, down.heightPt, 0.001)

        val right = io.canvasGeometryOf(
            Document(pages = listOf(blankPage(595.276, 800.0))),
            CanvasMode.INFINITE_HORIZONTAL,
        )!!
        assertEquals(800.0, right.fixedExtentPt!!, 0.001)
        assertEquals(595.276, right.widthPt, 0.001)
    }

    @Test
    fun `a paginated document has no board`() {
        val plain = temp.newFile("pages.mobix")
        SyntheticNotebook.write(plain, SyntheticNotebook.pages(2))
        val io = createIo()
        val loaded = io.read(plain) as LoadedFile.Doc

        assertNull("a paginated .mobix is a page stack, not a board", io.canvasGeometryOf(loaded.document, loaded.canvasMode))

        val xoppish = Document(pages = SyntheticNotebook.pages(1))
        assertNull(io.canvasGeometryOf(xoppish))
    }
}
