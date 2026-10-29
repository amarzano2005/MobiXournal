package com.mobixournal.render

import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixManifest
import com.mobixournal.format.mobix.MobixPageCodec
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.MobixPart
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.CountingEntrySource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * The canvas's page window: it lays out and draws **through a [PageSource]**, so a `.mobix` whose
 * envelope declares its page size is stacked without opening a single page, and a page is
 * materialised only when a box that shows it is asked for its model (`docs/data-path.md` →
 * *The page window*).
 *
 * This is the JVM half of `materialise-only-the-visible-pages-in-the-canvas`; the emulator pass that
 * scrolls a real 200-page notebook is the other half.
 */
class CanvasPageWindowTest {

    private val pageCount = 200

    private fun sampleStroke(x: Double) = Stroke(
        points = listOf(StrokePoint(x, x, 1.0), StrokePoint(x + 10.0, x + 10.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
    )

    private fun page(index: Int) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(listOf(sampleStroke(index.toDouble())))),
        id = "pg_%03d".format(index + 1),
    )

    /** A synthetic `pageCount`-page `.mobix` whose `canvas.json` declares the page size. */
    private fun writeSyntheticNotebook(file: File, pages: List<Page>) {
        val now = 1700000000000L
        val entries = mutableListOf<MobixContainer.Entry>()
        val parts = mutableListOf<MobixPart>()
        pages.forEachIndexed { index, page ->
            val entryName = "pages/page_%03d.json".format(index + 1)
            val sink = MobixAssetSink()
            val json = JsonWriter.write(MobixPageCodec.encode(page, sink)).toByteArray(Charsets.UTF_8)
            entries += MobixContainer.Entry(entryName, json)
            parts += MobixPart(id = page.id!!, entry = entryName, sha256 = MobixAssetSink.sha256(json), modified = now)
        }
        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_canvas_window",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = now,
            title = "Canvas window",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            appVersion = "1.0.0",
        ).copy(pages = parts)
        val canvas = MobixCanvasFile(CanvasMode.PAGINATED, 595.0, 842.0)
        val envelope = listOf(
            MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.encode().toByteArray(Charsets.UTF_8)),
            MobixContainer.Entry(MobixFormat.CANVAS_ENTRY, canvas.encode().toByteArray(Charsets.UTF_8)),
        )
        FileOutputStream(file).use { MobixContainer.save(envelope + entries, it) }
    }

    private fun pageEntry(index: Int) = "pages/page_%03d.json".format(index + 1)

    @Test
    fun stackingAPagedMobixOpensNoPageAndDrawsTheSameGeometryAsTheInMemoryList() {
        val file = File.createTempFile("canvas_window_", ".mobix").apply { deleteOnExit() }
        val pages = List(pageCount) { page(it) }
        writeSyntheticNotebook(file, pages)

        val tracked = CountingEntrySource(MobixEntrySource.fromFile(file))
        val source = PageSource.of(MobixPagedReader(tracked, cacheBudget = 5))
        try {
            val layout = PageStacker.stack(source, viewWidthPx = 600, gapPx = 10f)

            assertEquals(pageCount, layout.boxes.size)
            // The sizes come from the envelope, so laying out a 200-page notebook opens no page at all.
            for (i in 0 until pageCount) {
                assertEquals("layout must not open ${pageEntry(i)}", 0, tracked.openCount(pageEntry(i)))
            }

            // The same pixels as stacking the pages already in memory: identical geometry, and each box
            // resolves to the page at its index.
            val inMemory = PageStacker.stack(pages, viewWidthPx = 600, gapPx = 10f)
            assertEquals(inMemory.totalHeightPx, layout.totalHeightPx, 1e-3f)
            assertEquals(inMemory.contentWidthPx, layout.contentWidthPx, 1e-3f)
            layout.boxes.forEachIndexed { i, box ->
                assertEquals(inMemory.boxes[i].topPx, box.topPx, 1e-3f)
                assertEquals(inMemory.boxes[i].leftPx, box.leftPx, 1e-3f)
                assertEquals(inMemory.boxes[i].scale, box.scale, 1e-4f)
            }
        } finally {
            source.close()
        }
    }

    @Test
    fun drawingOnePageMaterialisesExactlyThatPage() {
        val file = File.createTempFile("canvas_window_", ".mobix").apply { deleteOnExit() }
        writeSyntheticNotebook(file, List(pageCount) { page(it) })

        val tracked = CountingEntrySource(MobixEntrySource.fromFile(file))
        val source = PageSource.of(MobixPagedReader(tracked, cacheBudget = 5))
        try {
            val layout = PageStacker.stack(source, viewWidthPx = 600, gapPx = 10f)

            // Drawing the box at page 151 reads its page — and only it.
            val drawn = layout.boxes[150].page
            assertEquals("pg_151", drawn.id)
            assertEquals(1, tracked.openCount(pageEntry(150)))
            for (i in 0 until pageCount) {
                if (i != 150) assertEquals("${pageEntry(i)} must stay unopened", 0, tracked.openCount(pageEntry(i)))
            }

            // A second read of the same box is served from the reader's window, not from the archive.
            assertSame(drawn, layout.boxes[150].page)
            assertEquals(1, tracked.openCount(pageEntry(150)))
        } finally {
            source.close()
        }
    }

    @Test
    fun rendersFromTheInMemoryListUnchanged() {
        // A `.xopp` or a new document lays out and draws through the in-memory source: the boxes hand
        // back the very page objects the document holds, so nothing about those documents changes.
        val pages = List(pageCount) { page(it) }
        val source = PageSource.of(Document(pages = pages))
        try {
            val layout = PageStacker.stack(source, viewWidthPx = 600, gapPx = 10f, zoom = 1.5f)
            assertEquals(pageCount, layout.boxes.size)
            assertSame(pages[0], layout.boxes[0].page)
            assertSame(pages[1], layout.boxes[1].page)
            // The page is fitted to the 600 px view, scaled by the 1.5 zoom.
            assertEquals(600f * 1.5f, layout.boxes[0].widthPx, 1e-3f)
        } finally {
            source.close()
        }
    }
}
