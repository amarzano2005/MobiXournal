package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class MobixMergeEngineTest {

    private fun sampleStroke(id: String, x: Double, y: Double) = Stroke(
        points = listOf(StrokePoint(x, y, 1.0), StrokePoint(x + 5.0, y + 5.0, 1.0)),
        color = 0xFF000000.toInt(),
        tool = Tool.PEN,
        capStyle = "round",
        uniformWidth = false,
        id = id,
    )

    private fun sampleText(id: String, text: String) = TextElement(
        content = text,
        font = "Sans",
        size = 12.0,
        x = 50.0,
        y = 50.0,
        color = 0xFF000000.toInt(),
        id = id,
    )

    private fun buildDoc(pages: List<Page>, docId: String = "doc_test"): MobixDocument {
        val parts = pages.mapIndexed { idx, page ->
            val json = JsonWriter.write(MobixPageCodec.encode(page, MobixAssetSink())).toByteArray(Charsets.UTF_8)
            MobixPart(
                id = page.id ?: "pg_%03d".format(idx + 1),
                entry = MobixFormat.pageEntry(idx + 1),
                sha256 = MobixAssetSink.sha256(json),
                modified = 1000L,
            )
        }
        val manifest = MobixManifest.forNewDocument(
            documentId = docId,
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = 1000L,
            title = "Test Doc",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        ).copy(pages = parts)

        return MobixDocument(
            manifest = manifest,
            canvas = MobixCanvasFile(CanvasMode.PAGINATED, 595.0, 842.0),
            pages = pages,
        )
    }

    private fun pageOf(id: String, vararg elements: com.mobixournal.format.model.Element): Page = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
        id = id,
    )

    @Test
    fun disjointPageEditsAreAdoptedWithoutConflict() {
        // Base document: 2 pages
        val baseP1 = pageOf("pg_1", sampleStroke("stk_1", 10.0, 10.0))
        val baseP2 = pageOf("pg_2", sampleStroke("stk_2", 20.0, 20.0))
        val base = buildDoc(listOf(baseP1, baseP2))

        // Local (tablet) modifies page 1
        val localP1 = pageOf("pg_1", sampleStroke("stk_1", 10.0, 10.0), sampleStroke("stk_local", 15.0, 15.0))
        val local = buildDoc(listOf(localP1, baseP2))

        // Remote (laptop) modifies page 2
        val remoteP2 = pageOf("pg_2", sampleStroke("stk_2", 20.0, 20.0), sampleStroke("stk_remote", 25.0, 25.0))
        val remote = buildDoc(listOf(baseP1, remoteP2))

        val result = MobixMergeEngine.merge(local = local, remote = remote, base = base)

        assertTrue(result.hasRemoteChanges)
        assertEquals("Adopted remote page 2", 1, result.adoptedRemotePages)
        assertEquals("Adopted local page 1", 1, result.adoptedLocalPages)
        assertEquals("No same-page element collisions", 0, result.elementMergedPages)

        val merged = result.mergedDocument
        assertEquals(2, merged.pages.size)
        // Page 1 has local edit
        assertEquals(2, merged.pages[0].layers[0].elements.size)
        assertEquals("stk_local", ElementId.idOf(merged.pages[0].layers[0].elements[1]))
        // Page 2 has remote edit
        assertEquals(2, merged.pages[1].layers[0].elements.size)
        assertEquals("stk_remote", ElementId.idOf(merged.pages[1].layers[0].elements[1]))
    }

    @Test
    fun samePageConcurrentEditsMergeDistinctElementsViaCrdt() {
        val baseP1 = pageOf("pg_1", sampleStroke("stk_shared", 0.0, 0.0))
        val base = buildDoc(listOf(baseP1))

        // Local added a stroke
        val localP1 = pageOf("pg_1", sampleStroke("stk_shared", 0.0, 0.0), sampleStroke("stk_local_pen", 10.0, 10.0))
        val local = buildDoc(listOf(localP1))

        // Remote added a text element on the same page
        val remoteP1 = pageOf("pg_1", sampleStroke("stk_shared", 0.0, 0.0), sampleText("txt_remote", "Hello from Laptop"))
        val remote = buildDoc(listOf(remoteP1))

        val result = MobixMergeEngine.merge(local = local, remote = remote, base = base)

        assertTrue(result.hasRemoteChanges)
        assertEquals(1, result.elementMergedPages)
        assertEquals(3, result.mergedElementCount) // stk_shared, stk_local_pen, txt_remote

        val merged = result.mergedDocument
        assertEquals(1, merged.pages.size)
        val elements = merged.pages[0].layers[0].elements
        assertEquals(3, elements.size)
        assertEquals("stk_shared", ElementId.idOf(elements[0]))
        assertEquals("stk_local_pen", ElementId.idOf(elements[1]))
        assertEquals("txt_remote", ElementId.idOf(elements[2]))
    }

    @Test
    fun remoteAppendedPageIsAddedToDocument() {
        val p1 = pageOf("pg_1", sampleStroke("stk_1", 10.0, 10.0))
        val local = buildDoc(listOf(p1))

        val p2Remote = pageOf("pg_2", sampleStroke("stk_remote_new", 20.0, 20.0))
        val remote = buildDoc(listOf(p1, p2Remote))

        val result = MobixMergeEngine.merge(local = local, remote = remote, base = null)

        assertTrue(result.hasRemoteChanges)
        assertEquals(1, result.adoptedRemotePages)
        assertEquals(1, result.unchangedPages)
        assertEquals(2, result.mergedDocument.pages.size)
        assertEquals("pg_2", result.mergedDocument.pages[1].id)
    }
}
