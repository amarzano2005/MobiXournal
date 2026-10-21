package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class MobixPagedReaderTest {

    private fun sampleStroke(x: Double, y: Double) = Stroke(
        points = listOf(StrokePoint(x, y, 1.0), StrokePoint(x + 10.0, y + 10.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = com.mobixournal.format.model.Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
    )

    private fun createSyntheticNotebookArchive(file: File, pageCount: Int) {
        val entries = mutableListOf<MobixContainer.Entry>()
        val parts = mutableListOf<MobixPart>()
        val now = 1700000000000L

        for (i in 0 until pageCount) {
            val pageId = "pg_%03d".format(i + 1)
            val entryName = "pages/page_%03d.json".format(i + 1)
            val page = Page(
                width = 595.0,
                height = 842.0,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(listOf(sampleStroke(i * 2.0, i * 3.0)))),
                id = pageId,
            )
            val sink = MobixAssetSink()
            val json = JsonWriter.write(MobixPageCodec.encode(page, sink))
            val bytes = json.toByteArray(Charsets.UTF_8)
            val digest = MobixAssetSink.sha256(bytes)

            entries.add(MobixContainer.Entry(entryName, bytes))
            parts.add(MobixPart(id = pageId, entry = entryName, sha256 = digest, modified = now))
        }

        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_synthetic_200",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = now,
            title = "Synthetic 200 Pages",
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

    @Test
    fun synthetic200PageNotebookOpensWithoutInflatingUntouchedPages() {
        val tempFile = File.createTempFile("mobix_synth_200_", ".mobix")
        tempFile.deleteOnExit()
        try {
            createSyntheticNotebookArchive(tempFile, 200)

            val rawSource = MobixEntrySource.fromFile(tempFile)
            val tracked = TrackedEntrySource(rawSource)
            val reader = MobixPagedReader(tracked, cacheBudget = 5)

            // Opening verifies metadata: mimetype, manifest.json, canvas.json
            assertEquals(200, reader.pageCount)
            assertEquals("Synthetic 200 Pages", reader.manifest.title)
            assertEquals("pg_001", reader.pageId(0))
            assertEquals("pg_200", reader.pageId(199))

            // None of the 200 pages should be inflated at open time
            for (i in 0 until 200) {
                assertFalse("Page $i must not be materialized on open", reader.isPageMaterialized(i))
                val entryName = "pages/page_%03d.json".format(i + 1)
                assertEquals("Entry $entryName must not be opened on container init", 0, tracked.openCount(entryName))
            }

            // Materialize window around page 0 (pages 0, 1, 2)
            val window = reader.ensureWindow(centerIndex = 0, radius = 2)
            assertEquals(3, window.size) // 0, 1, 2 (boundary at 0)
            assertTrue(reader.isPageMaterialized(0))
            assertTrue(reader.isPageMaterialized(1))
            assertTrue(reader.isPageMaterialized(2))

            assertEquals(1, tracked.openCount("pages/page_001.json"))
            assertEquals(1, tracked.openCount("pages/page_002.json"))
            assertEquals(1, tracked.openCount("pages/page_003.json"))

            // Pages 3..199 were never touched
            for (i in 3 until 200) {
                val entryName = "pages/page_%03d.json".format(i + 1)
                assertEquals("Page entry $entryName must still be untouched", 0, tracked.openCount(entryName))
            }

            // Navigating directly to page 199 (page 200) inflates only page 200
            val p200 = reader.getPage(199)
            assertEquals("pg_200", p200.id)
            assertEquals(1, tracked.openCount("pages/page_200.json"))

            // Pages in the middle (e.g. page 50..150) remain untouched with 0 opens
            for (i in 50..150) {
                val entryName = "pages/page_%03d.json".format(i + 1)
                assertEquals("Intermediate page $entryName must never have been inflated", 0, tracked.openCount(entryName))
            }

            reader.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun lruCacheEvictsAndReReadsPageWithoutCorruption() {
        val tempFile = File.createTempFile("mobix_lru_test_", ".mobix")
        tempFile.deleteOnExit()
        try {
            createSyntheticNotebookArchive(tempFile, 10)

            val rawSource = MobixEntrySource.fromFile(tempFile)
            val tracked = TrackedEntrySource(rawSource)
            // Cache budget of 3 pages
            val reader = MobixPagedReader(tracked, cacheBudget = 3)

            // Read page 0, 1, 2
            val p0 = reader.getPage(0)
            val p1 = reader.getPage(1)
            val p2 = reader.getPage(2)
            assertEquals(setOf(0, 1, 2), reader.materializedIndices())
            assertEquals(1, tracked.openCount("pages/page_001.json"))

            // Access page 3 -> page 0 is evicted from LRU
            val p3 = reader.getPage(3)
            assertEquals(3, reader.materializedIndices().size)
            assertFalse("Page 0 should be evicted from LRU cache", reader.isPageMaterialized(0))
            assertTrue(reader.isPageMaterialized(1))
            assertTrue(reader.isPageMaterialized(2))
            assertTrue(reader.isPageMaterialized(3))

            // Re-read page 0: must inflate again without error and match original content
            val p0Reloaded = reader.getPage(0)
            assertEquals(2, tracked.openCount("pages/page_001.json"))
            assertEquals(p0.id, p0Reloaded.id)
            assertEquals(p0.width, p0Reloaded.width, 1e-9)
            assertEquals(p0.height, p0Reloaded.height, 1e-9)
            assertEquals(p0.layers.size, p0Reloaded.layers.size)

            val sOriginal = p0.layers[0].elements[0] as Stroke
            val sReloaded = p0Reloaded.layers[0].elements[0] as Stroke
            assertEquals(sOriginal.points.size, sReloaded.points.size)
            assertEquals(sOriginal.points[0].x, sReloaded.points[0].x, 1e-9)

            reader.close()
        } finally {
            tempFile.delete()
        }
    }
}
