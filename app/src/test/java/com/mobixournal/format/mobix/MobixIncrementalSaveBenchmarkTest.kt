package com.mobixournal.format.mobix

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.withPage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The measurement behind the claim that a `.mobix` save costs the pages that moved: **one edited page of
 * a two-hundred-page notebook**, saved the way the app saves it ([MobixIncrementalSave.saveOver], which
 * repacks over the file the document was opened from) against the path it replaced (build the whole
 * archive, then diff it).
 *
 * Run it with:
 *
 * ```
 * ./gradlew testDebugUnitTest --tests com.mobixournal.format.mobix.MobixIncrementalSaveBenchmarkTest
 * ```
 *
 * and read `app/build/reports/mobix-incremental-save.txt` (also printed).
 *
 * What is counted matters more than the milliseconds: **pages opened** is how many page models the save
 * had to inflate and decode, and an entry **inflated** is a read that parsed or built its JSON. The point
 * of the reuse is that both stay constant for a save that edits one page, however long the notebook is —
 * a copied page is never opened, never parsed, never rebuilt and never hashed.
 */
class MobixIncrementalSaveBenchmarkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val pageCount = 200
    private val now = 1_700_000_000_000L

    /** A `.mobix` of [pageCount] pages, optionally carrying a 1 MiB `assets/` blob nothing references. */
    private fun archive(file: File, asset: Boolean = false) {
        SyntheticNotebook.write(file, SyntheticNotebook.pages(pageCount))
        if (!asset) return
        // Re-write through the container with the payload added: the container owns the entry order, so
        // the archive stays an ordinary one.
        val entries = MobixContainer.open(file.inputStream()).entries +
            MobixContainer.Entry("${MobixFormat.ASSETS_DIR}img_big.bin", ByteArray(1 shl 20) { it.toByte() })
        FileOutputStream(file).use { out -> MobixContainer.save(entries, out) }
    }

    private fun editOnePage(document: Document, index: Int = 4): Document {
        val page = document.pages[index]
        return document.withPage(
            index,
            page.copy(layers = page.layers + Layer(listOf(SyntheticNotebook.stroke(500.0, id = "stk_edited")))),
        )
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

    private class Opened(val document: Document, val pages: OpenedPages, val reader: MobixPagedReader) : AutoCloseable {
        override fun close() = reader.close()
    }

    private fun open(file: File, edit: Boolean = true): Opened {
        val reader = MobixPagedReader(MobixEntrySource.fromFile(file))
        val pages = OpenedPages(PageSource.of(reader))
        val document = MobixDocument.openPaged(reader, pages).toDocument()
        return Opened(if (edit) editOnePage(document) else document, pages, reader)
    }

    private class Measured(
        val ms: Long,
        val pagesOpened: Int,
        val entriesInflated: Int,
        val stats: MobixIncrementalSaveStats,
        val bytes: ByteArray,
        /** How many times the archive's own `assets/` blob was read, when it carries one. */
        val assetReads: Int = 0,
    )

    /** The save the app does: repack over the file, reusing the pages nothing touched. */
    private fun saveWithReuse(file: File, opened: Opened): Measured {
        val tracked = TrackedEntrySource(MobixEntrySource.fromFile(file))
        val out = ByteArrayOutputStream()
        opened.pages.opened = 0
        val start = System.nanoTime()
        val stats = MobixIncrementalSave.saveOver(
            source = tracked,
            document = opened.document,
            appVersion = "1.3.0",
            nowMillis = now + 60_000,
            output = out,
        )
        val ms = (System.nanoTime() - start) / 1_000_000
        val inflated = tracked.entryNames.count { tracked.inflateCount(it) > 0 }
        val assetReads = tracked.inflateCount("${MobixFormat.ASSETS_DIR}img_big.bin")
        tracked.close()
        return Measured(ms, opened.pages.opened, inflated, stats, out.toByteArray(), assetReads)
    }

    /** What a save used to do: build every page's JSON, hash it, and only then decide what to write. */
    private fun saveByRebuilding(file: File, opened: Opened): Measured {
        val tracked = TrackedEntrySource(MobixEntrySource.fromFile(file))
        val out = ByteArrayOutputStream()
        opened.pages.opened = 0
        val start = System.nanoTime()
        val mobix = MobixDocument.of(opened.document, appVersion = "1.3.0", nowMillis = now + 60_000)
        val plan = mobix.encode(nowMillis = now + 60_000)
        val stats = MobixIncrementalSave.repack(tracked, plan, out)
        val ms = (System.nanoTime() - start) / 1_000_000
        val inflated = tracked.entryNames.count { tracked.inflateCount(it) > 0 }
        tracked.close()
        return Measured(ms, opened.pages.opened, inflated, stats, out.toByteArray())
    }

    private fun median(runs: List<Measured>): Long = runs.map { it.ms }.sorted()[runs.size / 2]

    private fun describe(runs: List<Measured>): String = runs.last().let { m ->
        "time ${median(runs)} ms   pages opened ${m.pagesOpened}   entries inflated ${m.entriesInflated}" +
            "   written ${m.stats.rewrittenEntries} (${m.stats.rewrittenRawBytes} B)" +
            "   streamed ${m.stats.streamedEntries} (${m.stats.streamedRawBytes} B)" +
            "   out ${m.stats.totalOutputBytes} B"
    }

    private fun pagesOf(bytes: ByteArray): List<List<com.mobixournal.format.model.Element>> =
        MobixDocument.open(MobixContainer.open(ByteArrayInputStream(bytes))).toDocument()
            .pages.map { page -> page.layers.flatMap { it.elements } }

    @Test
    fun reportOneEditedPageOfTwoHundred() {
        val file = temp.newFile("save_benchmark.mobix")
        archive(file)
        val opened = open(file)
        opened.use {
            // Warm the JIT: the first run of either path is the slowest one.
            saveWithReuse(file, opened)
            saveByRebuilding(file, opened)

            val reuse = (1..5).map { saveWithReuse(file, opened) }
            val rebuild = (1..5).map { saveByRebuilding(file, opened) }

            val assetFile = temp.newFile("save_benchmark_asset.mobix")
            archive(assetFile, asset = true)
            val withAsset = open(assetFile)
            withAsset.use {
                val assetRun = saveWithReuse(assetFile, withAsset)

                val report = buildString {
                    appendLine("MobiXournal .mobix save — one edited page of $pageCount")
                    appendLine(
                        "generated by com.mobixournal.format.mobix.MobixIncrementalSaveBenchmarkTest @ " +
                            java.time.LocalDate.now(),
                    )
                    appendLine("archive: $pageCount pages, one stroke each, ${file.length()} B on disk")
                    appendLine("times: median of 5 runs, ms, wall clock on the harness host")
                    appendLine()
                    appendLine("reuse (what the app saves now)    " + describe(reuse))
                    appendLine("rebuild every page (what it was)  " + describe(rebuild))
                    appendLine()
                    appendLine(
                        "  the same document either way; the repacked file is " +
                            "${reuse.last().stats.totalOutputBytes} B against " +
                            "${rebuild.last().stats.totalOutputBytes} B (the few bytes are the per-page" +
                            " \"modified\" stamps the reuse keeps, which the rebuild resets)",
                    )
                    appendLine(
                        "with a 1 MiB bundled asset nothing references: " + describe(listOf(assetRun)) +
                            "  — the asset is never read: the repack streams it (it is only dropped by a save" +
                            " with no source to stream from, `supply-a-deferred-payload-on-a-save-that-has-no-source`)",
                    )
                }
                println(report)
                val out = File("build/reports/mobix-incremental-save.txt")
                out.parentFile?.mkdirs()
                out.writeText(report)

                // The claim, as a fact about work rather than about a stopwatch: a one-page save of a
                // two-hundred-page notebook opens one page — the first, which states the document's own
                // paper and page size — while rebuilding every page opens the document twice (once to
                // adopt identity, once to encode it).
                assertEquals("the reuse path opens one page", 1, reuse.last().pagesOpened)
                assertTrue("the rebuild path opens every page", rebuild.last().pagesOpened >= pageCount)
                assertEquals("the manifest and the source record are written, the edited page with them", 3, reuse.last().stats.rewrittenEntries)
                assertEquals("the canvas and the ${pageCount - 1} copied pages stream", pageCount, reuse.last().stats.streamedEntries)
                assertEquals("the bundled asset is streamed, never read", 0, assetRun.assetReads)
                assertEquals(
                    "and both paths describe the same document",
                    pagesOf(rebuild.last().bytes),
                    pagesOf(reuse.last().bytes),
                )
            }
        }
    }
}
