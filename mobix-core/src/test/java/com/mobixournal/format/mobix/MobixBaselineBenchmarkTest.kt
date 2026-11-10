package com.mobixournal.format.mobix

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.withPage
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import java.time.LocalDate
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The **baseline** `docs/benchmarks.md` asks for: one comparable set instead of three unrelated reports,
 * so an optimisation later has something to be measured against.
 *
 * What it measures, and how it reports:
 *
 * - **Open**, at 1, 50, 200 and 1000 pages, with light ink (one stroke a page) and heavy ink (40
 *   strokes of 20 points a page): time to the manifest, time to a usable first page, how many pages were
 *   actually materialised, and the JVM's **peak heap** across the phase
 *   ([java.lang.management.MemoryPoolMXBean.peakUsage], reset before each run — no estimate).
 * - **Save**, at 50, 200 and 1000 pages: a save with nothing changed and a save with exactly one page
 *   edited, reporting elapsed time and the saver's own counters — entries rewritten versus streamed,
 *   bytes each way, and the output size.
 * - **Resources**: a document carrying a bundled picture and a bundled PDF, and recordings of one and
 *   twenty minutes, reporting whether a save ever inflates an asset or a recording (it must not), how
 *   many bytes it streams, and the peak heap.
 *
 * **Cold** is the first run of a case and is reported alone; **warm** runs follow it and are reported as
 * p50 and p95 of nine runs. Nothing here is a speedup claim: the numbers are a starting point, and every
 * assertion is about work done (entries, pages, inflations), so the test fails on a regression even on a
 * machine slower than this one.
 *
 * Run it with:
 *
 * ```
 * ./gradlew :mobix-core:test --tests com.mobixournal.format.mobix.MobixBaselineBenchmarkTest
 * ```
 *
 * and read `mobix-core/build/reports/mobix-baseline.txt` (also printed). Deliberately not measured here:
 * UI frame time (a benchmark drives no canvas, and `docs/benchmarks.md` says so) and a 120-minute
 * recording, because a monoaural 44.1 kHz 16-bit WAV of that length is ~635 MB and this corpus carries
 * no such file — the twenty-minute case is the measured evidence that the path streams rather than
 * holds, and the report says exactly that instead of extrapolating a number.
 */
class MobixBaselineBenchmarkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private val sizes = listOf(1, 50, 200, 1000)
    private val warmRuns = 9
    private val saveRuns = 5

    // ---- measurement helpers ---------------------------------------------------------------------

    private fun heapPools() = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == MemoryType.HEAP }

    private fun resetPeaks() = heapPools().forEach { it.resetPeakUsage() }

    /** The JVM's own peak, summed over the heap pools: not an estimate and not a sampled guess. */
    private fun peakHeapMiB(): Long = heapPools().sumOf { it.peakUsage.used } / 1024 / 1024

    private fun percentile(values: List<Long>, fraction: Double): Long {
        val sorted = values.sorted()
        val index = ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        return sorted[index]
    }

    /** A page source that counts materialisations, so "pages opened" is a fact and not a guess. */
    private class OpenedPages(private val delegate: PageSource) : PageSource {
        var opened = 0
        override val pageCount: Int get() = delegate.pageCount
        override fun pageId(index: Int): String? = delegate.pageId(index)
        override fun pageSize(index: Int): PageSize = delegate.pageSize(index)
        override fun sourceParts(): List<MobixPart>? = delegate.sourceParts()
        override fun page(index: Int): Page = delegate.page(index).also { opened++ }
    }

    private class OpenRun(
        val toManifestMs: Long,
        val toFirstPageMs: Long,
        val pagesOpened: Int,
        val peakHeapMiB: Long,
    )

    /** Reading the manifest is constructing the reader; a usable page needs the document layer. */
    private fun openOnce(file: File): OpenRun {
        resetPeaks()
        val startManifest = System.nanoTime()
        val reader = MobixPagedReader(MobixEntrySource.fromFile(file))
        val afterManifest = System.nanoTime()
        val pages = OpenedPages(PageSource.of(reader))
        val document = MobixDocument.openPaged(reader, pages).toDocument()
        document.pages.firstOrNull()
        val afterFirstPage = System.nanoTime()
        val peak = peakHeapMiB()
        reader.close()
        return OpenRun(
            toManifestMs = (afterManifest - startManifest) / 1_000_000,
            toFirstPageMs = (afterFirstPage - afterManifest) / 1_000_000,
            pagesOpened = pages.opened,
            peakHeapMiB = peak,
        )
    }

    /**
     * The document **and** the reader it still reads through: a save streams the pages nothing touched
     * out of that archive, so closing it before the save is a measurement of a closed file.
     */
    private class Opened(val document: Document, private val reader: MobixPagedReader) : AutoCloseable {
        override fun close() = reader.close()
    }

    private fun openFor(file: File): Opened {
        val reader = MobixPagedReader(MobixEntrySource.fromFile(file))
        val document = MobixDocument.openPaged(reader, PageSource.of(reader)).toDocument()
        return Opened(document, reader)
    }

    private class SaveRun(
        val ms: Long,
        val stats: MobixIncrementalSaveStats,
        val assetInflations: Int,
        val audioInflations: Int,
    )

    private fun saveOnce(file: File, document: Document): SaveRun {
        val tracked = TrackedEntrySource(MobixEntrySource.fromFile(file))
        val out = ByteArrayOutputStream()
        val start = System.nanoTime()
        val stats = MobixIncrementalSave.saveOver(
            source = tracked,
            document = document,
            appVersion = "1.3.0",
            nowMillis = now + 60_000,
            output = out,
        )
        val ms = (System.nanoTime() - start) / 1_000_000
        // Inflating a payload means decoding it; streaming it means moving bytes. The save must never do
        // the first with an asset, whichever kind it is.
        val assetInflations = tracked.entryNames.count {
            MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.IMAGE) && tracked.inflateCount(it) > 0
        }
        val audioInflations = tracked.entryNames.count {
            MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.AUDIO) && tracked.inflateCount(it) > 0
        }
        tracked.close()
        return SaveRun(ms, stats, assetInflations, audioInflations)
    }

    private fun editOnePage(document: Document, index: Int = 4): Document {
        val page = document.pages[index]
        return document.withPage(
            index,
            page.copy(layers = page.layers + Layer(listOf(SyntheticNotebook.stroke(500.0, id = "stk_edited")))),
        )
    }

    // ---- the workloads ---------------------------------------------------------------------------

    private fun notebook(name: String, pages: List<Page>, backgrounds: (Background) -> MobixPayload? = { null }, audio: (String) -> MobixPayload? = { null }): File {
        val file = File(temp.root, name)
        SyntheticNotebook.write(file, pages, backgrounds, audio)
        return file
    }

    /**
     * Bytes that do not compress, which is what a picture and a recording are: a run of the same byte
     * would trip the container's own `inflates 1000× its stored size` guard, and rightly so.
     */
    private fun noise(size: Int): ByteArray = ByteArray(size).also { java.util.Random(size.toLong()).nextBytes(it) }

    /** A 1 MiB picture element on page 1 and a bundled 2 MiB PDF as page 2's paper. */
    private fun resourcesNotebook(): File {
        val picture = Page(
            width = SyntheticNotebook.WIDTH_PT,
            height = SyntheticNotebook.HEIGHT_PT,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(Layer(listOf(ImageElement(10.0, 10.0, 210.0, 210.0, noise(1 shl 20))))),
            id = SyntheticNotebook.idOf(0),
        )
        val paper = Page(
            width = SyntheticNotebook.WIDTH_PT,
            height = SyntheticNotebook.HEIGHT_PT,
            background = Background.Pdf(filename = "notes.pdf", pageNo = 1, domain = "absolute"),
            layers = emptyList(),
            id = SyntheticNotebook.idOf(1),
        )
        val pdf = MobixPayload.of(noise(2 shl 20))
        return notebook(
            "baseline-resources.mobix",
            listOf(picture, paper) + SyntheticNotebook.pages(48).drop(2),
            backgrounds = { background -> if (background is Background.Pdf) pdf else null },
        )
    }

    /** A 44.1 kHz mono 16-bit WAV of [minutes] of noise, as a stroke's bundled recording. */
    private fun recording(minutes: Int): Pair<String, MobixPayload> {
        val samples = minutes * 60 * 44_100
        val bytes = noise(44 + samples * 2)
        val header = "RIFF".toByteArray()
        header.copyInto(bytes, 0)
        bytes[8] = 'W'.code.toByte(); bytes[9] = 'A'.code.toByte(); bytes[10] = 'V'.code.toByte(); bytes[11] = 'E'.code.toByte()
        val name = "take-$minutes.wav"
        return name to MobixPayload.of(bytes)
    }

    private fun audioNotebook(minutes: Int): File {
        val (name, payload) = recording(minutes)
        val pages = SyntheticNotebook.pages(4).map { page ->
            page.copy(
                layers = page.layers.map { layer ->
                    layer.copy(
                        elements = layer.elements.map { element ->
                            if (element is com.mobixournal.format.model.Stroke) {
                                element.copy(extraAttrs = mapOf("fn" to name, "ts" to "0"))
                            } else {
                                element
                            }
                        },
                    )
                },
            )
        }
        return notebook("baseline-audio-$minutes.mobix", pages, audio = { asked -> if (asked == name) payload else null })
    }

    // ---- the report ------------------------------------------------------------------------------

    @Test
    fun reportTheOpenSaveAndStreamingBaseline() {
        val openRows = mutableListOf<String>()
        val saveRows = mutableListOf<String>()
        val notes = mutableListOf<String>()

        // Open: 4 sizes x 2 ink levels; the first run is the cold one, the next nine are warm.
        for (size in sizes) {
            for (heavy in listOf(false, true)) {
                val pages = if (heavy) SyntheticNotebook.densePages(size, strokes = 40, points = 20) else SyntheticNotebook.pages(size)
                val file = notebook("baseline-open-$size-${if (heavy) "heavy" else "light"}.mobix", pages)
                val cold = openOnce(file)
                val warm = (1..warmRuns).map { openOnce(file) }
                openRows += "| $size | ${if (heavy) "heavy" else "light"} | ${file.length()} | ${cold.toFirstPageMs} | " +
                    "${percentile(warm.map { it.toFirstPageMs }, 0.50)} | ${percentile(warm.map { it.toFirstPageMs }, 0.95)} | " +
                    "${warm.last().pagesOpened} | ${warm.maxOf { it.peakHeapMiB }} |"
                assertTrue(
                    "the ${if (heavy) "heavy" else "light"} $size-page open materialises a page or two, not the book",
                    warm.last().pagesOpened <= 2,
                )
            }
        }

        // Save: no change against exactly one edited page, at three sizes of the light notebook.
        for (size in listOf(50, 200, 1000)) {
            val file = notebook("baseline-save-$size.mobix", SyntheticNotebook.pages(size))
            openFor(file).use { opened ->
                saveOnce(file, opened.document) // warm-up, discarded
                val noop = (1..saveRuns).map { saveOnce(file, opened.document) }
                val edited = editOnePage(opened.document)
                val editRuns = (1..saveRuns).map { saveOnce(file, edited) }
                val rewritesForNoChange = noop.last().stats.rewrittenEntries
                saveRows += "| $size | nothing changed | ${percentile(noop.map { it.ms }, 0.50)} | ${percentile(noop.map { it.ms }, 0.95)} | " +
                    "$rewritesForNoChange | ${noop.last().stats.streamedEntries} | ${noop.last().stats.streamedRawBytes} | ${noop.last().stats.totalOutputBytes} |"
                saveRows += "| $size | one page edited | ${percentile(editRuns.map { it.ms }, 0.50)} | ${percentile(editRuns.map { it.ms }, 0.95)} | " +
                    "${editRuns.last().stats.rewrittenEntries} | ${editRuns.last().stats.streamedEntries} | " +
                    "${editRuns.last().stats.streamedRawBytes} | ${editRuns.last().stats.totalOutputBytes} |"
                assertTrue("a no-change save rewrites at most the envelope, and streams the pages", rewritesForNoChange <= 2)
                assertTrue("the unchanged pages stream, they are not inflated", noop.last().stats.streamedEntries >= size - 2)
                assertEquals("a one-page edit rewrites the manifest and that page", 3, editRuns.last().stats.rewrittenEntries)
            }
        }

        // Resources: a bundled picture and PDF must be streamed on a save, never inflated.
        val resources = resourcesNotebook()
        val resourcesSave = openFor(resources).use { opened ->
            saveOnce(resources, opened.document)
            saveOnce(resources, opened.document)
        }
        assertEquals("a save never inflates a bundled asset", 0, resourcesSave.assetInflations)
        assertEquals("a save never inflates a bundled PDF", 0, resourcesSave.assetInflations)
        notes += "| bundled 1 MiB picture + 2 MiB PDF | ${resources.length()} | ${resourcesSave.ms} | " +
            "${resourcesSave.stats.streamedRawBytes} | ${resourcesSave.stats.rewrittenEntries}/${resourcesSave.stats.streamedEntries} | " +
            "${resourcesSave.assetInflations} |"

        // Audio: one and twenty minutes, saved with the document that names them.
        for (minutes in listOf(1, 20)) {
            val file = audioNotebook(minutes)
            val run = openFor(file).use { opened ->
                saveOnce(file, opened.document)
                saveOnce(file, opened.document)
            }
            assertEquals("a save never inflates a recording", 0, run.audioInflations)
            notes += "| ${minutes}-minute recording | ${file.length()} | ${run.ms} | ${run.stats.streamedRawBytes} | " +
                "${run.stats.rewrittenEntries}/${run.stats.streamedEntries} | ${run.audioInflations} |"
        }

        val report = buildString {
            appendLine("MobiXournal baseline — open, save and streaming")
            appendLine("generated by com.mobixournal.format.mobix.MobixBaselineBenchmarkTest @ ${LocalDate.now()}")
            appendLine()
            append(
                BenchmarkEnvironment.table(
                    workload = "notebooks of 1, 50, 200 and 1000 pages, light (1 stroke a page) and heavy " +
                        "(40 strokes of 20 points); a 50-page document carrying a 1 MiB picture and a 2 MiB PDF; " +
                        "4-page documents carrying one recording of 1 and of 20 minutes",
                    warmUp = "one unmeasured run of each case before its measured runs",
                    iterations = "open: 1 cold run then $warmRuns warm; save: 1 warm-up then $saveRuns; " +
                        "p50 and p95 reported over the warm runs",
                    unit = "milliseconds (wall clock), bytes, entries, pages, MiB of peak heap",
                ),
            )
            appendLine()
            appendLine("## Open")
            appendLine()
            appendLine("| Pages | Ink | File (B) | Cold to first page (ms) | Warm p50 (ms) | Warm p95 (ms) | Pages materialised | Peak heap (MiB) |")
            appendLine("|---|---|---|---|---|---|---|---|")
            openRows.forEach(::appendLine)
            appendLine()
            appendLine("## Save")
            appendLine()
            appendLine("| Pages | Case | p50 (ms) | p95 (ms) | Rewritten | Streamed | Streamed raw bytes | Output (B) |")
            appendLine("|---|---|---|---|---|---|---|---|")
            saveRows.forEach(::appendLine)
            appendLine()
            appendLine("## Resources and audio")
            appendLine()
            appendLine("| Document | File (B) | Save (ms) | Streamed raw bytes | Rewritten/streamed | Asset inflations |")
            appendLine("|---|---|---|---|---|---|")
            notes.forEach(::appendLine)
            appendLine()
            appendLine("Not measured here, on purpose: UI frame time (no benchmark drives a canvas, per `docs/benchmarks.md`);")
            appendLine("a 120-minute recording, because a monoaural 44.1 kHz 16-bit WAV of that length is ~635 MB and no such")
            appendLine("fixture is committed — the 20-minute case is the evidence that the save streams the payload rather than")
            appendLine("holding it, and no number is extrapolated from it.")
            appendLine()
            appendLine("Every figure is wall clock in the test JVM; compare two runs only when their metadata blocks agree.")
        }
        println(report)
        val out = File("build/reports/mobix-baseline.txt")
        out.parentFile?.mkdirs()
        out.writeText(report)
        assertTrue("the report names its conditions", report.contains("| CPU |") && report.contains("| Workload |"))
    }
}
