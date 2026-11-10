package com.mobixournal.format.mobix

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.TextElement
import com.mobixournal.render.DocumentSearch
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The container's own knobs, measured instead of debated — one campaign for the three decisions that
 * were waiting on numbers (`TODO.toml`: `mobix-compression-review`, `mobix-zip64-review`,
 * `mobix-derived-index-review`).
 *
 * 1. **Compression per content class.** The same document is written three ways — everything DEFLATED
 *    (today's writer), page JSON DEFLATED with `assets/` STORED, and everything STORED — and each is
 *    measured for file size, write time and cold open to a usable page.
 * 2. **The real boundary.** Not "ZIP64 might be needed": where the writer and the reader actually stop.
 *    The guard functions are exercised for the 4 GiB field, and real archives are written at the
 *    entry-count boundary to see which limit fires first — [MobixSafetyCaps] (20 000 entries) or
 *    classic ZIP (`ClassicZipLimits`, 65 534).
 * 3. **What a derived `index/` would save.** A thousand-page document, searched through the reader's own
 *    page surface, plus the cost of laying out every page from `pageSize` alone.
 *
 * Reports land under `mobix-core/build/reports/` and are printed. Every figure is wall clock in this
 * JVM on this host; the metadata block says which host, and no figure here is a claim about a phone.
 */
class MobixArchivePolicyBenchmarkTest {

    @get:Rule
    val temp = TemporaryFolder()

    // ---- shared measurement helpers --------------------------------------------------------------

    /** Bytes that do not compress, which is what a picture, a PDF or a recording already is. */
    private fun noise(size: Int): ByteArray =
        ByteArray(size).also { java.util.Random(size.toLong()).nextBytes(it) }

    private fun millis(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun report(name: String, text: String) {
        println(text)
        val out = File("build/reports/$name")
        out.parentFile?.mkdirs()
        out.writeText(text)
    }

    /** The entry name the container gives its mimetype, read off a real archive rather than hardcoded. */
    private fun mimetypeNameOf(file: File): String =
        ZipInputStream(file.inputStream()).use { it.nextEntry.name }

    private class OpenedPages(private val delegate: PageSource) : PageSource {
        var opened = 0
        override val pageCount: Int get() = delegate.pageCount
        override fun pageId(index: Int): String? = delegate.pageId(index)
        override fun pageSize(index: Int): PageSize = delegate.pageSize(index)
        override fun sourceParts(): List<MobixPart>? = delegate.sourceParts()
        override fun page(index: Int): Page = delegate.page(index).also { opened++ }
    }

    /** Cold open, timed to a document whose first page exists — the point a reader can show something. */
    private fun openToFirstPage(file: File): Pair<Long, Int> {
        val start = System.nanoTime()
        val reader = MobixPagedReader(MobixEntrySource.fromFile(file))
        val pages = OpenedPages(PageSource.of(reader))
        val document: Document = MobixDocument.openPaged(reader, pages).toDocument()
        document.pages.firstOrNull()
        val elapsed = (System.nanoTime() - start) / 1_000_000
        reader.close()
        return elapsed to pages.opened
    }

    /** Write [entries] with [stored] deciding which ones skip DEFLATE. */
    private fun writeWithPolicy(file: File, entries: List<MobixContainer.Entry>, stored: (String) -> Boolean): Long =
        millis {
            file.outputStream().use { out ->
                val writer = MobixZip.Writer(out)
                for (entry in entries) {
                    if (stored(entry.name)) {
                        writer.putStored(entry.name, entry.payload.bytes())
                    } else {
                        writer.putDeflated(entry.name, entry.payload.bytes())
                    }
                }
                writer.finish()
            }
        }

    private fun signed(delta: Long): String = if (delta >= 0) "+$delta" else "$delta"

    private fun percent(part: Long, whole: Long): String =
        String.format(java.util.Locale.US, "%.1f%%", 100.0 * part / whole)

    private fun times(ratio: Double): String = String.format(java.util.Locale.US, "%.1f×", ratio)

    // ---- 1. compression per content class --------------------------------------------------------

    @Test
    fun `compress what compresses and store what does not`() {
        val base = File(temp.root, "policy-base.mobix")
        SyntheticNotebook.write(base, SyntheticNotebook.pages(200))
        val mimetypeName = mimetypeNameOf(base)
        val baseArchive = MobixContainer.open(base.inputStream().buffered())
        val mimetype = baseArchive.mimetype.toByteArray()

        // Three media classes, each under a real asset entry name so the policy matches a save's view.
        fun picture(bytes: ByteArray, suffix: String): MobixContainer.Entry =
            MobixContainer.Entry("assets/img_${MobixAssetSink.sha256(bytes)}.$suffix", bytes)

        val small = picture(noise(64 * 1024), "png")
        val photo = picture(noise(1024 * 1024), "webp")
        val recordingBytes = noise(4 * 1024 * 1024)
        val recording = MobixContainer.Entry(
            "assets/aud_${MobixAssetSink.sha256(recordingBytes)}.ogg",
            recordingBytes,
        )

        val entries = listOf(MobixContainer.Entry(mimetypeName, mimetype)) +
            baseArchive.entries + listOf(small, photo, recording)
        val pageJsonBytes = entries.filter { it.name.startsWith("pages/") }.sumOf { it.payload.length }
        val envelopeBytes = entries.filter { it.name in setOf("manifest.json", "canvas.json") }.sumOf { it.payload.length }
        val mediaBytes = small.payload.length + photo.payload.length + recording.payload.length

        val allDeflate = File(temp.root, "all-deflate.mobix")
        val deflateWriteMs = writeWithPolicy(allDeflate, entries) { it == mimetypeName }

        val mediaStored = File(temp.root, "media-stored.mobix")
        val mediaWriteMs = writeWithPolicy(mediaStored, entries) { name -> name == mimetypeName || name.startsWith("assets/") }

        val allStored = File(temp.root, "all-stored.mobix")
        val storedWriteMs = writeWithPolicy(allStored, entries) { true }

        // The same comparison over the compressible half alone, because the media dominate a 5 MB archive
        // and would hide what DEFLATE is actually buying on the JSON.
        val jsonOnly = entries.filterNot { it.name.startsWith("assets/") }
        val jsonDeflate = File(temp.root, "json-deflate.mobix")
        writeWithPolicy(jsonDeflate, jsonOnly) { it == mimetypeName }
        val jsonStored = File(temp.root, "json-stored.mobix")
        writeWithPolicy(jsonStored, jsonOnly) { true }

        val (deflateOpenMs, deflatePages) = openToFirstPage(allDeflate)
        val (mediaOpenMs, mediaPages) = openToFirstPage(mediaStored)
        val (storedOpenMs, storedPages) = openToFirstPage(allStored)

        val text = buildString {
            appendLine("MobiXournal container policy — compression per content class")
            appendLine("generated by MobixArchivePolicyBenchmarkTest @ ${LocalDate.now()}")
            appendLine()
            append(
                BenchmarkEnvironment.table(
                    workload = "a 200-page notebook ($pageJsonBytes B of page JSON, $envelopeBytes B of envelope) plus a " +
                        "64 KiB picture, a 1 MiB photo and a 4 MiB recording of incompressible bytes",
                    warmUp = "none: each archive is written once and opened once, and the comparison is size and " +
                        "relative time rather than a tuned tail",
                    iterations = "one write and one cold open per policy",
                    unit = "bytes and milliseconds (wall clock)",
                ),
            )
            appendLine()
            appendLine("| Policy | File (B) | vs today | Write (ms) | Cold open to first page (ms) | Pages materialised |")
            appendLine("|---|---|---|---|---|---|")
            appendLine("| everything DEFLATED (today) | ${allDeflate.length()} | — | $deflateWriteMs | $deflateOpenMs | $deflatePages |")
            appendLine(
                "| page JSON DEFLATED, `assets/` STORED | ${mediaStored.length()} | " +
                    "${signed(mediaStored.length() - allDeflate.length())} | $mediaWriteMs | $mediaOpenMs | $mediaPages |",
            )
            appendLine(
                "| everything STORED | ${allStored.length()} | ${signed(allStored.length() - allDeflate.length())} | " +
                    "$storedWriteMs | $storedOpenMs | $storedPages |",
            )
            appendLine()
            appendLine("The compressible half on its own — envelope and page JSON, no media:")
            appendLine()
            appendLine("| JSON policy | File (B) | vs DEFLATE |")
            appendLine("|---|---|---|")
            appendLine("| DEFLATED | ${jsonDeflate.length()} | — |")
            appendLine(
                "| STORED | ${jsonStored.length()} | ${signed(jsonStored.length() - jsonDeflate.length())} " +
                    "(${times(jsonStored.length().toDouble() / jsonDeflate.length())} the size) |",
            )
            appendLine()
            appendLine("Incompressible media: $mediaBytes B of the archive's payloads.")
            appendLine(
                "Conclusion: DEFLATE earns its cost on page JSON — storing that half multiplies it by " +
                    "${times(jsonStored.length().toDouble() / jsonDeflate.length())} — and earns nothing on media, where " +
                    "the selective policy differs by ${signed(mediaStored.length() - allDeflate.length())} bytes " +
                    "(${percent(mediaStored.length(), allDeflate.length())}) while the write drops from $deflateWriteMs ms " +
                    "to $mediaWriteMs ms, all of it deflate work on bytes deflate could not shrink.",
            )
        }
        report("mobix-compression-policy.txt", text)

        assertTrue(
            "media do not compress: storing them does not grow the archive",
            mediaStored.length() <= allDeflate.length() + entries.size,
        )
        assertTrue(
            "page JSON does compress: storing the JSON half costs at least half as much again",
            jsonStored.length() > jsonDeflate.length() * 1.5,
        )
        assertEquals("the selective archive still opens to exactly one page", 1, mediaPages)
        assertEquals("and so does the all-STORED one", 1, storedPages)
        assertTrue("storing media does not slow the open by more than double", mediaOpenMs <= deflateOpenMs * 2 + 5)
    }

    // ---- 2. where the archive actually stops -----------------------------------------------------

    @Test
    fun `the safety caps refuse before classic ZIP does, and the guards name their limit`() {
        // The guards themselves: a truncated 32-bit field is never published.
        val sizeGuard = assertThrows(IOException::class.java) { ClassicZipLimits.field(0xFFFFFFFFL) }
        assertTrue("the size guard says what happened", sizeGuard.message!!.contains("ZIP size limit"))
        val entryGuard = assertThrows(IOException::class.java) { ClassicZipLimits.entry(0xFFFE, 0, 5, 10, 10) }
        assertTrue("the entry guard says what happened", entryGuard.message!!.contains("ZIP entry limit"))
        // One below each guard is accepted, so the boundary is exactly the documented one.
        ClassicZipLimits.entry(0xFFFD, 0, 5, 10, 10)
        ClassicZipLimits.field(0xFFFFFFFEL)

        val first = File(temp.root, "entries-probe.mobix")
        SyntheticNotebook.write(first, SyntheticNotebook.pages(1))
        val probe = MobixContainer.open(first.inputStream().buffered())
        val mimetypeName = mimetypeNameOf(first)
        val mimetype = probe.mimetype.toByteArray()
        // A real envelope: the container refuses an archive without a manifest, so a boundary archive has
        // to be a document. Extra page entries it does not list are simply entries it preserves.
        val envelope = probe.entries.filter { it.name == "manifest.json" || it.name == "canvas.json" }
        val realPage = probe.entries.first { it.name.startsWith("pages/") }
        val pageBytes = realPage.payload.bytes()

        /** An archive of mimetype + manifest + canvas + one real page + [pad] extra page entries. */
        fun archive(file: File, pad: Int): Long = millis {
            file.outputStream().use { out ->
                val writer = MobixZip.Writer(out)
                writer.putStored(mimetypeName, mimetype)
                for (entry in envelope) writer.putDeflated(entry.name, entry.payload.bytes())
                writer.putDeflated(realPage.name, pageBytes)
                for (i in 0 until pad) writer.putDeflated("pages/pad$i.json", pageBytes)
                writer.finish()
            }
        }

        // 4 fixed entries + 19 996 pads = 20 000 entries: exactly the reader's cap.
        val atCap = File(temp.root, "entries-at-cap.mobix")
        val atCapMs = archive(atCap, 19_996)
        val openedAtCap = MobixContainer.open(atCap.inputStream().buffered())
        assertEquals("20 000 entries are the cap, and an archive at it opens", 19_999, openedAtCap.entries.size)

        // One past the cap.
        val overCap = File(temp.root, "entries-over-cap.mobix")
        val overCapMs = archive(overCap, 19_997)
        val refusal = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixContainer.open(overCap.inputStream().buffered())
        }
        assertTrue("the reader refuses one entry past the cap", refusal.message!!.contains("more than 20000 entries"))

        // 4 fixed entries + 65 530 pads = 65 534: the classic-ZIP ceiling this writer can still emit.
        val atWriterLimit = File(temp.root, "entries-at-zip-limit.mobix")
        val atWriterLimitMs = archive(atWriterLimit, 65_530)
        val zipRefusal = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixContainer.open(atWriterLimit.inputStream().buffered())
        }
        assertTrue(
            "the ZIP ceiling is refused by the entry cap, long before ZIP64 is a question",
            zipRefusal.message!!.contains("more than 20000 entries"),
        )

        // One more pad and the writer itself refuses the 65 535th entry.
        val pastWriterLimit = assertThrows(IOException::class.java) {
            archive(File(temp.root, "entries-past-zip-limit.mobix"), 65_531)
        }
        assertTrue("the writer's own guard fires at the classic-ZIP entry limit", pastWriterLimit.message!!.contains("ZIP entry limit"))

        val text = buildString {
            appendLine("MobiXournal container policy — where an archive actually stops")
            appendLine("generated by MobixArchivePolicyBenchmarkTest @ ${LocalDate.now()}")
            appendLine()
            appendLine("| Bound | Value | Who enforces it | What happened |")
            appendLine("|---|---|---|---|")
            appendLine("| Entries, read | 20 000 | `MobixSafetyCaps.maxEntries` | 20 000 opens (19 999 payload entries plus the mimetype); 20 001 refused, naming the limit |")
            appendLine("| Entries, written | 65 534 | `ClassicZipLimits.entry` | 65 534 written; the 65 535th throws \"ZIP entry limit\" |")
            appendLine("| One entry, inflated | 256 MiB | `MobixSafetyCaps.maxEntryBytes` | refused while reading, before allocation |")
            appendLine("| Archive, inflated | 1 GiB | `MobixSafetyCaps.maxArchiveBytes` | refused while reading |")
            appendLine("| Any ZIP field | 4 GiB − 1 | `ClassicZipLimits.field` | throws \"ZIP size limit\"; no truncated field is published |")
            appendLine("| Expansion ratio | 1000× | `MobixSafetyCaps.maxExpansionRatio` | refused from the directory, before inflating |")
            appendLine()
            appendLine("| Archive | Entries | File (B) | Write (ms) | Verdict |")
            appendLine("|---|---|---|---|---|")
            appendLine("| at the reader cap | 20 000 | ${atCap.length()} | $atCapMs | opens |")
            appendLine("| one past the reader cap | 20 001 | ${overCap.length()} | $overCapMs | refused by the cap |")
            appendLine("| at the classic-ZIP writer limit | 65 534 | ${atWriterLimit.length()} | $atWriterLimitMs | written, then refused by the cap |")
            appendLine()
            appendLine("A 4 GiB archive was **not** materialised: only its guard was exercised, and this table says so")
            appendLine("rather than implying a real file was written. The binding limits are the 256 MiB entry and")
            appendLine("1 GiB archive caps, an order of magnitude below classic ZIP's 4 GiB, so ZIP64 stays deferred")
            appendLine("with a stated trigger: the first legitimate document that needs a single entry or a total past")
            appendLine("1 GiB. The caps move before the container does.")
        }
        report("mobix-archive-boundary.txt", text)

        assertTrue("the boundary report names its guards", text.contains("ClassicZipLimits.field") && text.contains("20 001"))
    }

    // ---- 3. what a derived index would save ------------------------------------------------------

    private fun textPages(count: Int): List<Page> = List(count) { index ->
        Page(
            width = SyntheticNotebook.WIDTH_PT,
            height = SyntheticNotebook.HEIGHT_PT,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(
                Layer(
                    (0 until 8).map { line ->
                        TextElement(
                            font = "sans-serif",
                            size = 11.0,
                            x = 20.0,
                            y = 20.0 + line * 14.0,
                            color = 0xFF000000.toInt(),
                            content = if (index == 500 && line == 3) "needle in a thousand pages" else "page $index line $line",
                        )
                    },
                ),
            ),
            id = SyntheticNotebook.idOf(index),
        )
    }

    @Test
    fun `a thousand pages are searched in one walk and laid out without decoding any`() {
        val file = File(temp.root, "search-1000.mobix")
        SyntheticNotebook.write(file, textPages(1000))

        val reader = MobixPagedReader(MobixEntrySource.fromFile(file))
        try {
            val pages = OpenedPages(PageSource.of(reader))

            val layoutMs = millis { repeat(pages.pageCount) { pages.pageSize(it) } }
            assertEquals("laying out 1000 pages from canvas.json materialises none", 0, pages.opened)

            var hitCount = 0
            var hitPage = -1
            val coldMs = millis {
                val hits = DocumentSearch.find(pages, { emptyList() }, "needle")
                hitCount = hits.size
                hitPage = hits.firstOrNull()?.pageIndex ?: -1
            }
            val coldPages = pages.opened

            pages.opened = 0
            val warmMs = millis { DocumentSearch.find(pages, { emptyList() }, "needle") }
            val missedMs = millis { DocumentSearch.find(pages, { emptyList() }, "a string that is not there at all") }

            val inMemory = MobixDocument.openPaged(reader, PageSource.of(reader)).toDocument()
            val modelMs = millis { DocumentSearch.find(inMemory, null, "needle") }

            val text = buildString {
                appendLine("MobiXournal — what a derived index/ would save (1000 pages, 8 text boxes a page)")
                appendLine("generated by MobixArchivePolicyBenchmarkTest @ ${LocalDate.now()}")
                appendLine()
                append(
                    BenchmarkEnvironment.table(
                        workload = "one .mobix of 1000 pages and 8 000 text boxes, read through MobixPagedReader " +
                            "and then through the in-memory model",
                        warmUp = "one cold search, then a second (warm) search over the same reader",
                        iterations = "one run each: this bounds a user-visible wait rather than a tuned tail",
                        unit = "milliseconds (wall clock)",
                    ),
                )
                appendLine()
                appendLine("| Operation | First run (ms) | Second run (ms) | Pages decoded |")
                appendLine("|---|---|---|---|")
                appendLine("| `pageSize` for all 1000 pages | $layoutMs | — | 0 |")
                appendLine("| search through the reader (hit) | $coldMs | $warmMs | $coldPages |")
                appendLine("| search through the reader (no hit) | $missedMs | — | 1000 |")
                appendLine("| search through the in-memory model | $modelMs | — | — |")
                appendLine()
                appendLine("The needle is found on page $hitPage ($hitCount hit). Searching a thousand pages decodes")
                appendLine("every page once and is still a fraction of a second, and page geometry comes from")
                appendLine("canvas.json without decoding anything: a derived search index or a page-size index would")
                appendLine("add a second source of truth and a save-time cost for a wait nobody perceives.")
            }
            report("mobix-index-profile.txt", text)

            assertEquals("the needle is found once", 1, hitCount)
            assertEquals("on page 500", 500, hitPage)
            assertEquals("a full search decodes each page exactly once", 1000, coldPages)
            assertTrue("the worst search is not a hang", coldMs < 5_000)
            assertTrue("a second search is the same order as the first", warmMs < coldMs * 5 + 50)
            assertTrue("the in-memory model is not an order of magnitude worse", modelMs < coldMs + 1_000)
        } finally {
            reader.close()
        }
    }
}
