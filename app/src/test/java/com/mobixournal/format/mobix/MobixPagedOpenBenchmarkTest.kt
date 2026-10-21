package com.mobixournal.format.mobix

import android.content.ContentResolver
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.io.DocumentIo
import com.mobixournal.io.LoadedFile
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The measurement behind `docs/mobix-format.md`'s streaming claim and the scope
 * `open-a-mobix-page-by-page-not-whole`: how many entries a synthetic 200-page `.mobix` actually
 * inflates, and how many milliseconds opening it takes — today's whole-archive path against the
 * paged reader.
 *
 * Run it with:
 *
 * ```
 * ./gradlew testDebugUnitTest --tests com.mobixournal.format.mobix.MobixPagedOpenBenchmarkTest
 * ```
 *
 * and read `build/reports/mobix-open.txt` (also printed, visible with `--info`).
 *
 * The archive is 200 small JSON pages plus one 1 MiB `assets/` blob, so the asset deferral is
 * visible in both the entry count and the bytes.
 */
class MobixPagedOpenBenchmarkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pageCount = 200
    private val assetBytes = 1 shl 20 // 1 MiB

    private fun sampleStroke(x: Double, y: Double) = Stroke(
        points = listOf(StrokePoint(x, y, 1.0), StrokePoint(x + 10.0, y + 10.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
    )

    private fun createArchive(file: File) {
        val entries = mutableListOf<MobixContainer.Entry>()
        val parts = mutableListOf<MobixPart>()
        val now = 1_700_000_000_000L

        for (i in 0 until pageCount) {
            val pageId = "pg_%03d".format(i + 1)
            val entryName = MobixFormat.pageEntry(i + 1)
            val page = Page(
                width = 595.0,
                height = 842.0,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(listOf(sampleStroke(i * 2.0, i * 3.0)))),
                id = pageId,
            )
            val json = JsonWriter.write(MobixPageCodec.encode(page, MobixAssetSink()))
            val bytes = json.toByteArray(Charsets.UTF_8)
            entries += MobixContainer.Entry(entryName, bytes)
            parts += MobixPart(id = pageId, entry = entryName, sha256 = MobixAssetSink.sha256(bytes), modified = now)
        }

        // One large bundled asset, so deferring asset payloads can be seen.
        entries += MobixContainer.Entry("${MobixFormat.ASSETS_DIR}img_big.bin", ByteArray(assetBytes) { it.toByte() })

        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_open_benchmark",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = now,
            title = "Open benchmark",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            appVersion = "1.3.0",
        ).copy(pages = parts)

        val envelope = listOf(
            MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.encode().toByteArray(Charsets.UTF_8)),
            MobixContainer.Entry(
                MobixFormat.CANVAS_ENTRY,
                MobixCanvasFile(CanvasMode.PAGINATED, 595.0, 842.0).encode().toByteArray(Charsets.UTF_8),
            ),
        )
        FileOutputStream(file).use { out -> MobixContainer.save(envelope + entries, out) }
    }

    private inline fun timed(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]

    /** The eager path, kept as the baseline: every entry, every page, in memory at once. */
    private fun openWhole(file: File) {
        val archive = file.inputStream().use { MobixContainer.open(it) }
        val doc = MobixDocument.open(archive)
        doc.toDocument()
    }

    /** The streaming open (`MobixDocument.open`), which defers the `assets/` payloads. */
    private fun openStreaming(file: File, tracked: CountingEntrySource? = null) {
        val source = tracked ?: MobixEntrySource.fromFile(file)
        try {
            MobixDocument.open(source).toDocument()
        } finally {
            if (tracked == null) source.close()
        }
    }

    /**
     * The open the app **ships**: `io/DocumentIo.kt`'s `.mobix` branch, which reads the envelope and
     * the manifest, hands the editor a document whose pages are read through the reader, and adopts
     * a page's backgrounds when that page is read. It is measured through the real entry point so the
     * claim in `docs/mobix-format.md` is about the code that runs, not about a path a test keeps alive.
     */
    private fun openShipped(file: File, tracked: CountingEntrySource): Boolean {
        val io = DocumentIo(
            resolver = object : ContentResolver(null) {},
            cacheDir = temp.newFolder(),
            filesDir = temp.newFolder(),
            appVersion = "1.3.0",
            textPdf = null,
            entrySources = { tracked },
        )
        val loaded = io.read(file) as? LoadedFile.Doc ?: return false
        // The count and the names come from the manifest, so reading one is what an open costs.
        val pages = loaded.document.pages.size
        io.prunePagedOpens(emptyList())
        return pages == pageCount
    }

    /** The paged path: the reader plus the first five pages. */
    private fun openPaged(file: File, tracked: CountingEntrySource) {
        val reader = MobixPagedReader(tracked, cacheBudget = 5)
        reader.pageCount
        (0 until 5).forEach { reader.getPage(it) }
        reader.close()
    }

    @Test
    fun reportEagerOpenAgainstPagedOpen() {
        val file = temp.newFile("open_benchmark.mobix")
        createArchive(file)

        // Warm the JIT so the first measured run is not the slowest one.
        openWhole(file)
        openPaged(file, CountingEntrySource(MobixEntrySource.fromFile(file)))

        val wholeMs = median((1..5).map { timed { openWhole(file) } })

        val pagedMs = median((1..5).map {
            val fresh = CountingEntrySource(MobixEntrySource.fromFile(file))
            timed { openPaged(file, fresh) }
        })
        val tracked = CountingEntrySource(MobixEntrySource.fromFile(file))
        openPaged(file, tracked)
        val totalEntries = ZipFile(file).use { zip -> zip.entries().asSequence().count() }
        val entriesOpenedPaged = tracked.entryNames.count { tracked.openCount(it) > 0 }

        val streamingMs = median((1..5).map {
            val fresh = CountingEntrySource(MobixEntrySource.fromFile(file))
            timed { openStreaming(file, fresh) }
        })
        val streaming = CountingEntrySource(MobixEntrySource.fromFile(file))
        openStreaming(file, streaming)
        val entriesOpenedStreaming = streaming.entryNames.count { streaming.openCount(it) > 0 }

        val shippedMs = median((1..5).map {
            val fresh = CountingEntrySource(MobixEntrySource.fromFile(file))
            timed { openShipped(file, fresh) }
        })
        val shipped = CountingEntrySource(MobixEntrySource.fromFile(file))
        val shippedOk = openShipped(file, shipped)
        val entriesOpenedShipped = shipped.entryNames.count { shipped.openCount(it) > 0 }

        val report = buildString {
            appendLine("MobiXournal .mobix open — the eager baseline, the reader, and the shipped open")
            appendLine(
                "generated by com.mobixournal.format.mobix.MobixPagedOpenBenchmarkTest @ " +
                    java.time.LocalDate.now(),
            )
            appendLine("archive: $pageCount pages + one 1 MiB asset, ${file.length()} B on disk, $totalEntries entries")
            appendLine("times: median of 5 runs, ms, wall clock on the harness host")
            appendLine()
            appendLine("path                             open ms    entries inflated")
            appendLine("eager (MobixContainer + open)    ${wholeMs.toString().padStart(5)}    ${totalEntries} (every one)")
            appendLine("streaming (MobixDocument.open)    ${streamingMs.toString().padStart(5)}    $entriesOpenedStreaming")
            appendLine("paged reader + 5 pages           ${pagedMs.toString().padStart(5)}    $entriesOpenedPaged")
            appendLine("shipped (DocumentIo.read)        ${shippedMs.toString().padStart(5)}    $entriesOpenedShipped")
        }
        println(report)
        val out = File("build/reports/mobix-open.txt")
        out.parentFile?.mkdirs()
        out.writeText(report)

        assertTrue("the paged open must not inflate the whole archive", entriesOpenedPaged < totalEntries)
        assertTrue("the paged open must not inflate the big asset", tracked.openCount("${MobixFormat.ASSETS_DIR}img_big.bin") == 0)
        assertTrue("the streaming open must not inflate the big asset", streaming.openCount("${MobixFormat.ASSETS_DIR}img_big.bin") == 0)
        assertTrue("the streaming open must not inflate the whole archive", entriesOpenedStreaming < totalEntries)
        assertTrue("the shipped open must read the archive it claims to", shippedOk)
        assertTrue("the shipped open must not inflate the whole archive", entriesOpenedShipped < totalEntries)
        assertTrue("the shipped open must not inflate the big asset", shipped.openCount("${MobixFormat.ASSETS_DIR}img_big.bin") == 0)
        assertTrue(
            "the shipped counter must come from the manifest, not from the pages",
            shipped.entryNames.none { it.startsWith(MobixFormat.PAGES_DIR) && shipped.openCount(it) > 0 },
        )
        val reader = MobixPagedReader(MobixEntrySource.fromFile(file))
        try {
            assertEquals(pageCount, reader.pageCount)
        } finally {
            reader.close()
        }
    }
}
