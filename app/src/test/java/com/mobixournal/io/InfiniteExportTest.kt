package com.mobixournal.io

import android.content.ContentResolver
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixChunk
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A board leaves the app as pages: the `.xopp` a Save As writes is the document sliced through the
 * auto-slicer, laid out at the coordinates its chunks sit at (`docs/infinite-canvas.md` →
 * *Auto-pagination and export slicing*).
 *
 * What the export has to get right is that the file it writes is an **ordinary** one — a reader that
 * knows nothing about chunks opens it — while the `.mobix` it came from keeps its chunks, untouched.
 * The cutting itself is [com.mobixournal.render.BoardSlicerTest]'s subject; this pins the file that
 * reaches the disk: gzip + XML, re-parsed through [Xopp.parseXml], with the sheets' own geometry.
 */
class InfiniteExportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val cacheDir by lazy { temp.newFolder("cache") }
    private val filesDir by lazy { temp.newFolder("files") }

    /** The board's paper, in pt — the sheet every sliced page has to come out as. */
    private val sheetWidth = 500.0
    private val sheetHeight = 800.0

    /** The tile size `canvas.json` declares: 1000 pt, so a cell is a major step down the board. */
    private val tile = 1000.0

    private fun createIo(): DocumentIo = DocumentIo(
        resolver = object : ContentResolver(null) {},
        cacheDir = cacheDir,
        filesDir = filesDir,
        appVersion = "1.3.0",
        textPdf = null,
        entrySources = MobixEntrySource::fromFile,
    )

    /**
     * One stroke at cell-local [at], running **along the mode's axis** — x for a horizontal strip, y for
     * a vertical one — so which sheet its ink lands on is told by the coordinate under test.
     */
    private fun strokeAt(at: Double, mode: CanvasMode, id: String): Stroke = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = if (mode == CanvasMode.INFINITE_HORIZONTAL) {
            listOf(StrokePoint(at, 10.0, 2.0), StrokePoint(at + 80.0, 10.0, 2.0))
        } else {
            listOf(StrokePoint(10.0, at, 2.0), StrokePoint(10.0, at + 80.0, 2.0))
        },
        uniformWidth = true,
        id = id,
    )

    /**
     * A strip board written as a real chunked `.mobix`: one chunk per cell of [positions], each holding
     * one stroke at that cell-local coordinate, at the coordinates its index names.
     */
    private fun writeStrip(
        file: File,
        positions: List<Double>,
        mode: CanvasMode = CanvasMode.INFINITE_VERTICAL,
    ): File {
        val coords = positions.indices.map { index -> if (mode == CanvasMode.INFINITE_HORIZONTAL) index to 0 else 0 to index }
        val document = MobixDocument.forInfinite(
            documentId = "doc_strip",
            canvasMode = mode,
            widthPt = sheetWidth,
            heightPt = sheetHeight,
            chunkSizePt = tile,
            chunks = coords.mapIndexed { index, (cx, cy) ->
                MobixChunk(
                    cx = cx,
                    cy = cy,
                    layers = listOf(Layer(listOf(strokeAt(positions[index], mode, "stk_$index")))),
                    id = "ch_${cx}_$cy",
                )
            },
            title = "Strip",
        )
        FileOutputStream(file).use { out -> MobixContainer.save(document.encode(nowMillis = 1L).entries, out) }
        return file
    }

    /** The decompressed XML of the `.xopp` a write staged — what a reader actually parses. */
    private fun xmlOf(file: File): String =
        GZIPInputStream(file.inputStream()).reader(Charsets.UTF_8).use { it.readText() }

    private fun exportOf(file: File): com.mobixournal.format.model.Document {
        // One session: the geometry a board's export slices by is not derivable from its pages, so it
        // comes from the file the *same* session opened (`MobixOpenSessions`, `docs/data-path.md`).
        val io = createIo()
        val loaded = io.read(file) as LoadedFile.Doc
        val encoded = io.encode(
            loaded.document,
            pdf = null,
            format = SaveFormat.ORIGINAL,
            canvasMode = loaded.canvasMode,
        )
        assertTrue("a board cooks into plain ink, with nothing left undrawn", encoded.uncompiled.isEmpty())
        return Xopp.parseXml(xmlOf(encoded.file))
    }

    private fun strokesOf(page: com.mobixournal.format.model.Page): List<Stroke> =
        page.layers.flatMap { it.elements }.filterIsInstance<Stroke>()

    /** Three cells down the board, each holding a stroke, export as three sheets of the paper. */
    @Test
    fun `a board exports as the sheets its cells add up to`() {
        val file = writeStrip(temp.newFile("strip.mobix"), positions = listOf(100.0, 20.0, 20.0))

        val parsed = exportOf(file)

        assertEquals("one sheet per cell's worth of board", 3, parsed.pages.size)
        for (page in parsed.pages) {
            assertEquals("the sheet is the board's paper", sheetWidth, page.width, 0.001)
            assertEquals(sheetHeight, page.height, 0.001)
        }
        // The ink is on the sheet its coordinate names, placed as the cell-local y moved onto the board:
        // the second cell starts 1000 pt down, which is the second sheet's band at 200 pt.
        assertEquals(
            listOf(100.0, 220.0, 420.0),
            parsed.pages.map { strokesOf(it).single().points.first().y },
        )
        assertEquals(
            "and every stroke arrives whole",
            listOf(2, 2, 2),
            parsed.pages.map { strokesOf(it).single().points.size },
        )
    }

    /** A horizontal strip exports the same way, cut along x. */
    @Test
    fun `a horizontal strip exports along its own axis`() {
        val file = writeStrip(
            temp.newFile("sideways.mobix"),
            positions = listOf(100.0, 20.0),
            mode = CanvasMode.INFINITE_HORIZONTAL,
        )

        val parsed = exportOf(file)

        assertEquals(3, parsed.pages.size)
        for (page in parsed.pages) assertEquals(sheetHeight, page.height, 0.001)
        assertEquals(
            "the ink follows its cell along x",
            listOf(100.0, 20.0),
            parsed.pages.filter { strokesOf(it).isNotEmpty() }.map { strokesOf(it).single().points.first().x },
        )
    }

    /** The document it came from is not the file being written: the `.mobix` keeps its chunks. */
    @Test
    fun `the board itself is left chunked`() {
        val file = writeStrip(temp.newFile("keeps.mobix"), positions = listOf(100.0, 20.0))
        val before = file.readBytes()
        val io = createIo()
        val loaded = io.read(file) as LoadedFile.Doc

        io.encode(loaded.document, pdf = null, format = SaveFormat.ORIGINAL, canvasMode = loaded.canvasMode)

        assertEquals("the pages the export cut are not the document's", 2, loaded.document.pages.size)
        assertTrue("and the .mobix on disk is untouched", before.contentEquals(file.readBytes()))
        val entries = file.inputStream().use { MobixContainer.open(it.buffered()).names.toList() }
        assertTrue("its chunks are still chunks", entries.any { it.startsWith("chunks/") })
        assertTrue("and no page entry appeared", entries.none { it.startsWith("pages/") })
    }
}
