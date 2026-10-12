package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class MobixIncrementalSaveTest {

    private fun sampleStroke(x: Double, y: Double) = Stroke(
        points = listOf(StrokePoint(x, y, 1.0), StrokePoint(x + 10.0, y + 10.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
    )

    private fun createNotebookArchive(file: File, pageCount: Int, extraPreservedEntry: Pair<String, ByteArray>? = null) {
        val entries = mutableListOf<MobixContainer.Entry>()
        val parts = mutableListOf<MobixPart>()
        val now = 1700000000000L

        for (i in 0 until pageCount) {
            val pageId = "pg_%03d".format(i + 1)
            val entryName = MobixFormat.pageEntry(i + 1)
            val page = Page(
                width = 595.0,
                height = 842.0,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(listOf(sampleStroke(i * 5.0, i * 5.0)))),
                id = pageId,
            )
            val sink = MobixAssetSink()
            val json = JsonWriter.write(MobixPageCodec.encode(page, sink))
            val bytes = json.toByteArray(Charsets.UTF_8)
            val digest = MobixAssetSink.sha256(bytes)

            entries.add(MobixContainer.Entry(entryName, bytes))
            parts.add(MobixPart(id = pageId, entry = entryName, sha256 = digest, modified = now))
        }

        if (extraPreservedEntry != null) {
            entries.add(MobixContainer.Entry(extraPreservedEntry.first, extraPreservedEntry.second))
        }

        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_incremental_test",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = now,
            title = "Incremental Notebook",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            appVersion = "1.0.0",
        ).copy(pages = parts)

        val manifestBytes = manifest.encode().toByteArray(Charsets.UTF_8)
        val canvasBytes = MobixCanvasFile(CanvasMode.PAGINATED, 595.0, 842.0).encode().toByteArray(Charsets.UTF_8)

        val allEntries = listOf(
            MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifestBytes),
            MobixContainer.Entry(MobixFormat.CANVAS_ENTRY, canvasBytes),
        ) + entries

        FileOutputStream(file).use { out ->
            MobixContainer.save(allEntries, out)
        }
    }

    private fun rawEntries(bytes: ByteArray): List<Triple<String, Int, ByteArray>> {
        val out = mutableListOf<Triple<String, Int, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e: ZipEntry = zip.nextEntry ?: break
                out += Triple(e.name, e.method, zip.readBytes())
                zip.closeEntry()
            }
        }
        return out
    }

    @Test
    fun editingOnePageOfTenPagesRepacksWithUntouchedEntriesByteIdentical() {
        val tempSource = File.createTempFile("mobix_incr_source_", ".mobix")
        val tempDest = File.createTempFile("mobix_incr_dest_", ".mobix")
        tempSource.deleteOnExit()
        tempDest.deleteOnExit()

        try {
            val preservedPayload = "unknown_vendor_data_blob".toByteArray(Charsets.UTF_8)
            val preservedName = "custom_vendor/telemetry.bin"
            createNotebookArchive(tempSource, pageCount = 10, extraPreservedEntry = preservedName to preservedPayload)

            val initialBytes = tempSource.readBytes()
            val initialRawEntries = rawEntries(initialBytes).associateBy { it.first }

            // Open source via MobixEntrySource
            val source = MobixEntrySource.fromFile(tempSource)
            val trackedSource = TrackedEntrySource(source)

            // Let's modify Page 3 (pages/page-3.json)
            val modifiedPage = Page(
                width = 595.0,
                height = 842.0,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(
                    Layer(listOf(sampleStroke(999.0, 999.0))),
                ),
                id = "pg_003",
            )
            val sink = MobixAssetSink()
            val newPage3Bytes = JsonWriter.write(MobixPageCodec.encode(modifiedPage, sink)).toByteArray(Charsets.UTF_8)
            val newPage3Digest = MobixAssetSink.sha256(newPage3Bytes)
            val now = 1700000060000L

            // Read original manifest and update page 3 entry
            val origManifest = MobixManifest.decode(trackedSource.readBytes(MobixFormat.MANIFEST_ENTRY)!!.toString(Charsets.UTF_8))
            val updatedParts = origManifest.pages.map { part ->
                if (part.id == "pg_003") {
                    part.copy(sha256 = newPage3Digest, modified = now)
                } else {
                    part
                }
            }
            val newManifest = origManifest.copy(
                pages = updatedParts,
                modified = now,
            )
            val newManifestBytes = newManifest.encode().toByteArray(Charsets.UTF_8)

            val dirtyMap = mapOf(
                MobixFormat.MANIFEST_ENTRY to newManifestBytes,
                MobixFormat.pageEntry(3) to newPage3Bytes,
            )

            val stats = FileOutputStream(tempDest).use { destOut ->
                MobixIncrementalSaver.repack(
                    source = trackedSource,
                    dirtyEntries = dirtyMap,
                    output = destOut,
                )
            }

            source.close()

            // Verify stats
            // Total entries: 10 pages + manifest + canvas + preserved = 13 entries (mimetype is separate)
            assertEquals("Total entries count", 13, stats.totalEntries)
            assertEquals("Rewritten entries count (manifest + page 3)", 2, stats.rewrittenEntries)
            assertEquals("Streamed entries count (9 pages + canvas + preserved)", 11, stats.streamedEntries)
            assertTrue("Total repacked bytes measured > 0", stats.totalOutputBytes > 0)
            assertTrue("Streamed raw bytes > 0", stats.streamedRawBytes > 0)
            assertTrue("Rewritten raw bytes > 0", stats.rewrittenRawBytes > 0)

            // Read repacked archive and check byte identity
            val repackedBytes = tempDest.readBytes()
            val repackedEntries = rawEntries(repackedBytes)

            // 1. Rule 1: Mimetype is first and STORED
            assertEquals("mimetype is first entry", MobixFormat.MIMETYPE_ENTRY, repackedEntries.first().first)
            assertEquals("mimetype is STORED", ZipEntry.STORED, repackedEntries.first().second)

            val repackedMap = repackedEntries.associateBy { it.first }

            // 2. Untouched pages (1, 2, 4, 5, 6, 7, 8, 9, 10) are byte-identical
            for (p in 1..10) {
                val entryName = MobixFormat.pageEntry(p)
                val repackedEntry = repackedMap[entryName]
                assertNotNull("Entry $entryName must exist in repacked archive", repackedEntry)
                if (p == 3) {
                    assertArrayEquals("Page 3 bytes must match modified content", newPage3Bytes, repackedEntry!!.third)
                } else {
                    val originalEntry = initialRawEntries[entryName]
                    assertNotNull(originalEntry)
                    assertArrayEquals(
                        "Untouched page $p bytes must be identical to original",
                        originalEntry!!.third,
                        repackedEntry!!.third,
                    )
                }
            }

            // 3. Canvas entry is untouched and byte-identical
            val origCanvas = initialRawEntries[MobixFormat.CANVAS_ENTRY]
            val repCanvas = repackedMap[MobixFormat.CANVAS_ENTRY]
            assertArrayEquals("Canvas must be byte-identical", origCanvas!!.third, repCanvas!!.third)

            // 4. Preserved unknown entry is untouched and byte-identical (Container Rule 4)
            val origPreserved = initialRawEntries[preservedName]
            val repPreserved = repackedMap[preservedName]
            assertNotNull("Preserved entry must be present", repPreserved)
            assertArrayEquals("Preserved entry must be byte-identical", origPreserved!!.third, repPreserved!!.third)

            // Verify with MobixPagedReader that repacked file opens cleanly
            MobixPagedReader.open(tempDest).use { reader ->
                assertEquals(10, reader.pageCount)
                assertEquals("pg_001", reader.pageId(0))
                assertEquals("pg_003", reader.pageId(2))
                val readP3 = reader.getPage(2)
                assertEquals("pg_003", readP3.id)
                assertEquals(1, readP3.layers.size)
                val stroke = readP3.layers[0].elements[0] as Stroke
                assertEquals(999.0, stroke.points[0].x, 0.001)
            }
        } finally {
            tempSource.delete()
            tempDest.delete()
        }
    }
}
