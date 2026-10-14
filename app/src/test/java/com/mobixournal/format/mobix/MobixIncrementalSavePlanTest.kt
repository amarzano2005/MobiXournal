package com.mobixournal.format.mobix

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The **incremental save's plan** ([MobixIncrementalSave]): which entries a save rewrites, and that the
 * archive it produces is the document a full write would have produced — with the untouched bytes
 * streamed out of the file the document was opened from.
 *
 * The fixture must be **deterministic** for any of this to mean anything: a stroke with no id has one
 * minted on every encode, so its page's bytes would differ on every save and nothing would ever stream.
 * Pages and strokes therefore carry their own ids here, as they do in a document the app has saved once.
 */
class MobixIncrementalSavePlanTest {

    private val appVersion = "1.0.0"
    private val now = 1_700_000_000_000L

    private fun stroke(index: Int, x: Double = index * 5.0) = Stroke(
        points = listOf(StrokePoint(x, x, 1.0), StrokePoint(x + 10.0, x + 10.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
        id = "stk_%03d".format(index),
    )

    private fun notebook(pageCount: Int = 10) = Document(
        title = "Incremental notebook",
        pages = (1..pageCount).map { index ->
            Page(
                width = 595.0,
                height = 842.0,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(listOf(stroke(index)), name = "ink")),
                id = "pg_%03d".format(index),
            )
        },
    )

    /** The entries a full write of [document] produces, plus anything the caller wants to preserve. */
    private fun entriesOf(document: Document, vararg extra: MobixContainer.Entry): List<MobixContainer.Entry> =
        MobixDocument.of(document = document, appVersion = appVersion, nowMillis = now)
            .encode(nowMillis = now) + extra

    private fun sourceArchive(entries: List<MobixContainer.Entry>): File {
        val file = File.createTempFile("mobix_plan_source_", ".mobix").apply { deleteOnExit() }
        FileOutputStream(file).use { out -> MobixContainer.save(entries, out) }
        return file
    }

    private fun rawEntries(bytes: ByteArray): List<Triple<String, Int, ByteArray>> {
        val out = mutableListOf<Triple<String, Int, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                out += Triple(entry.name, entry.method, zip.readBytes())
                zip.closeEntry()
            }
        }
        return out
    }

    private fun open(bytes: ByteArray): Document =
        MobixEntrySource.fromMap(rawEntries(bytes).associate { it.first to it.third }).use { MobixDocument.open(it).toDocument() }

    private fun repack(source: File, entries: List<MobixContainer.Entry>): Pair<ByteArray, MobixIncrementalSaveStats> {
        val out = ByteArrayOutputStream()
        val stats = MobixEntrySource.fromFile(source).use { from ->
            MobixIncrementalSave.repack(from, entries, out)
        }
        return out.toByteArray() to stats
    }

    @Test
    fun `editing one page of ten streams the other nine and leaves them byte-identical`() {
        val preserved = MobixContainer.Entry("custom_vendor/telemetry.bin", "unknown blob".toByteArray(Charsets.UTF_8))
        val source = sourceArchive(entriesOf(notebook(), preserved))
        val wasByName = rawEntries(source.readBytes()).associateBy { it.first }

        // Open the archive the way a session does, draw on page 5, and encode it again.
        val document = MobixEntrySource.fromFile(source).use { MobixDocument.open(it).toDocument() }
        val edited = document.copy(
            pages = document.pages.mapIndexed { index, page ->
                if (index != 4) page
                else page.copy(layers = listOf(page.layers[0].copy(elements = page.layers[0].elements + stroke(999, 999.0))))
            },
        )
        val entries = MobixDocument.of(document = edited, appVersion = appVersion, nowMillis = now + 1000)
            .encode(nowMillis = now + 1000)
        val (bytes, stats) = repack(source, entries)
        val byName = rawEntries(bytes).associateBy { it.first }

        val mimetype = byName.getValue(MobixFormat.MIMETYPE_ENTRY)
        assertEquals("the mimetype is still first…", MobixFormat.MIMETYPE_ENTRY, rawEntries(bytes).first().first)
        assertEquals("…and still STORED", ZipEntry.STORED, mimetype.second)

        for (index in 1..10) {
            val name = MobixFormat.pageEntry(index)
            val entry = byName.getValue(name)
            if (index != 5) {
                assertArrayEquals(
                    "untouched page $index is byte-identical to the source",
                    wasByName.getValue(name).third,
                    entry.third,
                )
            } else {
                assertArrayEquals(
                    "the edited page is the page that was written",
                    entries.first { it.name == name }.bytes,
                    entry.third,
                )
            }
        }
        assertArrayEquals(
            "an entry this build does not model survives verbatim",
            preserved.bytes,
            byName.getValue(preserved.name).third,
        )

        // Twelve entries stream (nine untouched pages, the canvas, the bridge and the preserved entry);
        // the manifest and the one edited page are written.
        assertEquals(12, stats.streamedEntries)
        assertEquals(2, stats.rewrittenEntries)
        assertTrue("streamed bytes are measured", stats.streamedRawBytes > 0)
        assertTrue("written bytes are measured", stats.rewrittenRawBytes > 0)

        val full = MobixContainer.toBytes(entries)
        assertEquals("the repack is the document a full write describes", open(full), open(bytes))
        assertTrue(
            "and it is no larger: repacked=${stats.totalOutputBytes}B full=${full.size}B",
            stats.totalOutputBytes <= full.size + 512L,
        )

        // The number this task asked for: what a one-page edit actually writes, against the whole file.
        println(
            "MEASURE ten pages, one dirty: streamed ${stats.streamedEntries} entries / ${stats.streamedRawBytes}B, " +
                "written ${stats.rewrittenEntries} entries / ${stats.rewrittenRawBytes}B, " +
                "out ${stats.totalOutputBytes}B vs full ${full.size}B",
        )
    }

    @Test
    fun `a re-save that changed nothing rewrites only the manifest`() {
        val entries = entriesOf(notebook())
        val source = sourceArchive(entries)

        // The same document, encoded again: every page hashes to what the source's manifest records, so
        // nothing but the envelope moves.
        val again = MobixDocument.of(document = notebook(), appVersion = appVersion, nowMillis = now + 60_000)
            .encode(nowMillis = now + 60_000)
        val (bytes, stats) = repack(source, again)
        val byName = rawEntries(bytes).associateBy { it.first }

        assertEquals("only the manifest is written", 1, stats.rewrittenEntries)
        assertEquals(MobixFormat.MANIFEST_ENTRY, stats.let { byName.getValue(MobixFormat.MANIFEST_ENTRY).first })
        // The ten pages, the canvas and the bridge.
        assertEquals("everything else is streamed", 12, stats.streamedEntries)
        assertEquals(open(MobixContainer.toBytes(again)), open(bytes))
    }

    @Test
    fun `the plan rewrites what changed and nothing else`() {
        val asset = "a picture's bytes".toByteArray(Charsets.UTF_8)
        val newAsset = "new picture".toByteArray(Charsets.UTF_8)
        val assetName = MobixFormat.assetEntry(MobixAssetSink.sha256(asset), MobixFormat.AssetKind.IMAGE, "png")
        val newName = MobixFormat.assetEntry(MobixAssetSink.sha256(newAsset), MobixFormat.AssetKind.IMAGE, "png")
        val pageOne = "page one".toByteArray()
        val source = MobixEntrySource.fromMap(
            mapOf(
                MobixFormat.MANIFEST_ENTRY to "{}".toByteArray(),
                MobixFormat.pageEntry(1) to pageOne,
                assetName to asset,
            ),
        )
        val previous = MobixManifest.forNewDocument("doc_1", CanvasMode.PAGINATED, now, appVersion = appVersion)
            .copy(
                pages = listOf(
                    MobixPart(id = "pg_001", entry = MobixFormat.pageEntry(1), sha256 = MobixAssetSink.sha256(pageOne), modified = now),
                ),
            )
        val entries = listOf(
            MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, "{\"modified\":1}".toByteArray()),
            // Unchanged: the previous manifest records this hash.
            MobixContainer.Entry(MobixFormat.pageEntry(1), pageOne),
            // Changed: the same page name, different bytes.
            MobixContainer.Entry(MobixFormat.pageEntry(2), "page two".toByteArray()),
            // Unchanged, and never read: the name is the hash of its own bytes.
            MobixContainer.Entry(assetName, asset),
            // New: nothing in the source addresses these bytes.
            MobixContainer.Entry(newName, newAsset),
        )

        val dirty = MobixIncrementalSave.dirtyEntries(entries, previous, source)

        assertEquals(
            "the manifest, the changed page and the new payload — nothing else",
            setOf(MobixFormat.MANIFEST_ENTRY, MobixFormat.pageEntry(2), newName),
            dirty.keys,
        )
    }

    @Test
    fun `without a recorded manifest only what the source lacks is written`() {
        val entries = listOf(
            MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, "{}".toByteArray()),
            MobixContainer.Entry(MobixFormat.CANVAS_ENTRY, "{}".toByteArray()),
            MobixContainer.Entry(MobixFormat.pageEntry(1), "page one".toByteArray()),
        )
        // No manifest at all, so there is no record of any part's hash to diff against.
        val source = MobixEntrySource.fromMap(mapOf(MobixFormat.CANVAS_ENTRY to "{}".toByteArray()))

        val dirty = MobixIncrementalSave.dirtyEntries(entries, MobixManifest.read(source), source)

        assertEquals(
            "the manifest always moves; the page the source does not hold is written; the identical" +
                " canvas is streamed",
            setOf(MobixFormat.MANIFEST_ENTRY, MobixFormat.pageEntry(1)),
            dirty.keys,
        )
    }
}
