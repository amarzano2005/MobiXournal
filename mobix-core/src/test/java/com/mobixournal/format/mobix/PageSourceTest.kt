package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * The page surface: counting and id'ing from a `.mobix` manifest without materialising a page, and
 * answering a `.xopp`-shaped document's in-memory pages unchanged (`docs/data-path.md`).
 */
class PageSourceTest {

    private fun sampleStroke(x: Double, y: Double) = Stroke(
        points = listOf(StrokePoint(x, y, 1.0), StrokePoint(x + 10.0, y + 10.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
    )

    private fun samplePage(id: String, x: Double = 0.0): Page = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(listOf(sampleStroke(x, x)))),
        id = id,
    )

    /** Write a [pageCount]-page `.mobix` whose manifest names page `i` as `pg_%03d` of `i + 1`. */
    private fun writeSyntheticArchive(file: File, pageCount: Int) {
        val now = 1700000000000L
        val entries = mutableListOf<MobixContainer.Entry>()
        val parts = mutableListOf<MobixPart>()

        for (i in 0 until pageCount) {
            val pageId = "pg_%03d".format(i + 1)
            val entryName = pageEntryName(i)
            val bytes = JsonWriter.write(MobixPageCodec.encode(samplePage(pageId, i * 2.0), MobixAssetSink()))
                .toByteArray(Charsets.UTF_8)
            entries.add(MobixContainer.Entry(entryName, bytes))
            parts.add(MobixPart(id = pageId, entry = entryName, sha256 = MobixAssetSink.sha256(bytes), modified = now))
        }

        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_page_source",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = now,
            title = "Page Source",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            appVersion = "1.0.0",
        ).copy(pages = parts)

        val allEntries = listOf(
            MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.encode().toByteArray(Charsets.UTF_8)),
            MobixContainer.Entry(
                MobixFormat.CANVAS_ENTRY,
                MobixCanvasFile(CanvasMode.PAGINATED, 595.0, 842.0).encode().toByteArray(Charsets.UTF_8),
            ),
        ) + entries

        FileOutputStream(file).use { out -> MobixContainer.save(allEntries, out) }
    }

    private fun pageEntryName(index: Int) = "pages/page_%03d.json".format(index + 1)

    @Test
    fun aMobixSourceCountsAndIdsFromTheManifestWithoutMaterialisingAPage() {
        val tempFile = File.createTempFile("mobix_page_source_", ".mobix")
        tempFile.deleteOnExit()
        try {
            writeSyntheticArchive(tempFile, pageCount = 200)

            val tracked = TrackedEntrySource(MobixEntrySource.fromFile(tempFile))
            val reader = MobixPagedReader(tracked, cacheBudget = 5)
            val source = PageSource.of(reader)

            // Counting and id'ing are manifest answers: no page entry is inflated for them.
            assertEquals(200, source.pageCount)
            assertEquals("pg_001", source.pageId(0))
            assertEquals("pg_199", source.pageId(198))
            assertEquals("pg_200", source.pageId(199))
            assertNull("an index past the last page has no id", source.pageId(200))

            val index = source.idIndex()
            assertEquals("every declared page is indexed", 200, index.size)
            assertEquals(0, index["pg_001"])
            assertEquals(149, index["pg_150"])
            assertEquals(199, index["pg_200"])
            for (i in 0 until 200) {
                assertEquals(
                    "id'ing page $i must not inflate it",
                    0,
                    tracked.openCount(pageEntryName(i)),
                )
            }

            // Only the page asked for is materialised, and it is the page the manifest named.
            val last = source.page(199)
            assertEquals("pg_200", last.id)
            assertEquals(1, tracked.openCount(pageEntryName(199)))
            assertEquals("no other page is touched", 0, tracked.openCount(pageEntryName(198)))

            assertThrows(IllegalArgumentException::class.java) { source.page(200) }

            source.close()
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun aDocumentBackedSourceAnswersTheInMemoryPagesUnchanged() {
        val pages = listOf(samplePage("pg_001"), samplePage("pg_002", 5.0), samplePage("pg_003", 9.0))
        val source = PageSource.of(Document(pages = pages))

        assertEquals(3, source.pageCount)
        assertEquals("pg_001", source.pageId(0))
        assertEquals("pg_002", source.pageId(1))
        assertEquals("pg_003", source.pageId(2))
        assertNull(source.pageId(3))
        assertEquals(mapOf("pg_001" to 0, "pg_002" to 1, "pg_003" to 2), source.idIndex())

        // The surface is a view: the very instances the document holds come back.
        for (i in pages.indices) {
            assertSame("page $i is the document's own instance", pages[i], source.page(i))
        }
        assertThrows(IllegalArgumentException::class.java) { source.page(3) }

        // It opened nothing, so closing it releases nothing and stays usable.
        source.close()
        assertEquals(3, source.pageCount)
        assertSame(pages[1], source.page(1))
    }

    /**
     * The `List<Page>` a document model holds can be a source's own view: `size` is the manifest's
     * count and `get(i)` inflates page `i`, so a document opened page by page can be handed to code
     * written against `Document.pages` without a page being decoded up front.
     */
    @Test
    fun aSourcePagesAsAListStayLazyAndFindTheirWayBack() {
        val pages = listOf(samplePage("pg_001"), samplePage("pg_002", 5.0))
        val source = PageSource.of(Document(pages = pages))

        val list = source.asPageList()
        assertEquals(2, list.size)
        assertSame(pages[0], list[0])
        assertSame(pages[1], list[1])
        assertThrows(IndexOutOfBoundsException::class.java) { list[2] }

        // A list that is a source's view resolves back to the source it came from …
        assertSame(source, PageSource.of(list))
        // … while a plain list is a view of the pages the caller holds.
        val materialised = PageSource.of(pages)
        assertSame(pages[0], materialised.page(0))
        assertSame(pages[1], materialised.page(1))
    }

    @Test
    fun aFileSourceOwnsTheArchiveItOpened() {
        val tempFile = File.createTempFile("mobix_page_source_file_", ".mobix")
        tempFile.deleteOnExit()
        try {
            writeSyntheticArchive(tempFile, pageCount = 6)

            val source = PageSource.open(tempFile, cacheBudget = 2)
            assertEquals(6, source.pageCount)
            assertEquals("pg_006", source.page(5).id)

            // Closing releases the archive (its entries), while the manifest stays readable.
            source.close()
            assertEquals(6, source.pageCount)
            assertEquals("pg_001", source.pageId(0))

            val reopened = PageSource.open(tempFile, cacheBudget = 2)
            assertNotNull(reopened.page(0))
            reopened.close()
        } finally {
            tempFile.delete()
        }
    }
}
