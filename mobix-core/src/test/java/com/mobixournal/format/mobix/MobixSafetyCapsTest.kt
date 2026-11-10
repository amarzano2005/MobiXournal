package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The cap policy (`docs/mobix-format.md`, *What a reader must refuse*): every threshold has a document
 * just under it — which must read exactly as it always did — and one just past it, which must be refused
 * by name.
 *
 * The thresholds themselves are megabytes, so the policy is exercised as a **value** with small numbers:
 * the mechanism is the same one production runs, and the point of the test is the boundary, not the size.
 * The asset case builds a real archive because the cumulative budget only exists inside a document.
 */
class MobixSafetyCapsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val manifest = """{"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED"}""".toByteArray()

    private val page = "pages/page-0001.json" to """{"layers":[]}""".toByteArray()

    private val noAssets = MobixAssetSource { null }

    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                fun put(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                put(MobixFormat.MIMETYPE_ENTRY, MobixFormat.MIMETYPE.toByteArray())
                for ((name, bytes) in entries) put(name, bytes)
            }
        }.toByteArray()

    private fun opening(caps: MobixSafetyCaps, vararg entries: Pair<String, ByteArray>): MobixContainer.Archive =
        MobixContainer.open(ByteArrayInputStream(archive(*entries)), caps)

    private fun refusal(caps: MobixSafetyCaps, vararg entries: Pair<String, ByteArray>): MobixContainer.MalformedMobixException =
        assertThrows(MobixContainer.MalformedMobixException::class.java) { opening(caps, *entries) }

    // ---- the container --------------------------------------------------------------------------

    @Test
    fun anArchiveAtTheEntryCountOpensAndOnePastItIsRefused() {
        // mimetype + manifest + page = three names.
        val opened = opening(MobixSafetyCaps(maxEntries = 3), MobixFormat.MANIFEST_ENTRY to manifest, page)
        assertTrue("the page is there", page.first in opened.names)

        val failure = refusal(MobixSafetyCaps(maxEntries = 2), MobixFormat.MANIFEST_ENTRY to manifest, page)
        assertTrue("the refusal says what was exceeded: ${failure.message}", "entries" in failure.message.orEmpty())
    }

    @Test
    fun anEntryAtTheByteCapOpensAndOneBytePastItIsRefused() {
        opening(MobixSafetyCaps(maxEntryBytes = manifest.size.toLong()), MobixFormat.MANIFEST_ENTRY to manifest)

        val failure = refusal(
            MobixSafetyCaps(maxEntryBytes = manifest.size - 1L),
            MobixFormat.MANIFEST_ENTRY to manifest,
        )
        assertTrue(
            "the refusal names the entry: ${failure.message}",
            MobixFormat.MANIFEST_ENTRY in failure.message.orEmpty(),
        )
    }

    @Test
    fun anArchiveAtTheTotalCapOpensAndOneBytePastItIsRefused() {
        // The mimetype is the container's own entry and is not part of the payload budget.
        opening(MobixSafetyCaps(maxArchiveBytes = manifest.size.toLong()), MobixFormat.MANIFEST_ENTRY to manifest)

        val failure = refusal(
            MobixSafetyCaps(maxArchiveBytes = manifest.size - 1L),
            MobixFormat.MANIFEST_ENTRY to manifest,
        )
        assertTrue("the refusal states the total: ${failure.message}", "expands" in failure.message.orEmpty())
    }

    /** A source that knows how the bytes were stored, which is what an expansion ratio needs. */
    private class StoredSource(
        private val entries: Map<String, ByteArray>,
        private val stored: Map<String, Long>,
    ) : MobixEntrySource {
        override val entryNames: Set<String> get() = entries.keys

        override fun openEntry(name: String): InputStream? = entries[name]?.let(::ByteArrayInputStream)

        override fun storedSizes(name: String): MobixStoredSize? {
            val size = entries[name]?.size?.toLong() ?: return null
            return MobixStoredSize(inflated = size, compressed = stored[name] ?: size)
        }

        override fun close() = Unit
    }

    private fun notebookSource(): MobixEntrySource = StoredSource(
        entries = mapOf(
            MobixFormat.MIMETYPE_ENTRY to MobixFormat.MIMETYPE.toByteArray(),
            MobixFormat.MANIFEST_ENTRY to manifest,
        ),
        // The manifest claims to have been stored in a quarter of its size.
        stored = mapOf(MobixFormat.MANIFEST_ENTRY to (manifest.size / 4L).coerceAtLeast(1L)),
    )

    @Test
    fun anExpansionAtTheRatioOpensAndOnePastItIsRefused() {
        val ratio = manifest.size / (manifest.size / 4L).coerceAtLeast(1L)
        MobixPagedReader(notebookSource(), caps = MobixSafetyCaps(maxExpansionRatio = ratio.toInt())).close()

        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixPagedReader(notebookSource(), caps = MobixSafetyCaps(maxExpansionRatio = ratio.toInt() - 1))
        }
        assertTrue(
            "the refusal names the entry and its ratio: ${failure.message}",
            MobixFormat.MANIFEST_ENTRY in failure.message.orEmpty() && "inflates" in failure.message.orEmpty(),
        )
    }

    // ---- the model ------------------------------------------------------------------------------

    private fun pageOf(elements: Int): com.mobixournal.format.json.JsonNode.Obj {
        val items = (1..elements).joinToString(",") { """{"type":"text","content":"a"}""" }
        return MobixJson.parseJson("""{"layers":[{"elements":[$items]}]}""", "a page")
            as com.mobixournal.format.json.JsonNode.Obj
    }

    @Test
    fun aPageAtTheElementCapDecodesAndOnePastItIsRefused() {
        val decoded = MobixPageCodec.decode(pageOf(2), noAssets, MobixSafetyCaps(maxElementsPerPage = 2))
        assertEquals(2, decoded.layers.single().elements.size)

        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixPageCodec.decode(pageOf(3), noAssets, MobixSafetyCaps(maxElementsPerPage = 2))
        }
        assertTrue("the refusal states the count: ${failure.message}", "elements" in failure.message.orEmpty())
    }

    private fun strokeOf(points: Int): com.mobixournal.format.json.JsonNode.Obj {
        val pts = (1..points).joinToString(",") { """{"x":$it,"y":$it}""" }
        return MobixJson.parseJson("""{"type":"stroke","tool":"PEN","points":[$pts]}""", "a stroke")
            as com.mobixournal.format.json.JsonNode.Obj
    }

    @Test
    fun aStrokeAtThePointCapDecodesAndOnePastItIsRefused() {
        val decoded = MobixElementCodec.decode(strokeOf(3), noAssets, caps = MobixSafetyCaps(maxPointsPerStroke = 3))
        assertTrue("a pen stroke is read as one", decoded is Stroke)
        assertEquals(3, (decoded as Stroke).points.size)

        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixElementCodec.decode(strokeOf(4), noAssets, caps = MobixSafetyCaps(maxPointsPerStroke = 3))
        }
        assertTrue("the refusal states the count: ${failure.message}", "points" in failure.message.orEmpty())
    }

    // ---- the assets of a real document ----------------------------------------------------------

    private fun pictureNotebook(file: File, picture: ByteArray) {
        val sink = MobixAssetSink()
        val pageModel = Page(
            width = 595.0,
            height = 842.0,
            background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            layers = listOf(Layer(listOf(ImageElement(0.0, 0.0, 10.0, 10.0, picture, id = "img_1")))),
            id = "pg_001",
        )
        val pageJson = JsonWriter.write(MobixPageCodec.encode(pageModel, sink)).toByteArray(Charsets.UTF_8)
        val manifestJson = MobixManifest.forNewDocument(
            documentId = "doc_picture",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = 1700000000000L,
            title = "A picture",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            appVersion = "1.0.0",
        ).copy(
            pages = listOf(
                MobixPart(id = "pg_001", entry = MobixFormat.pageEntry(1), sha256 = MobixAssetSink.sha256(pageJson)),
            ),
        ).encode().toByteArray(Charsets.UTF_8)
        val canvas = MobixCanvasFile(CanvasMode.PAGINATED, 595.0, 842.0).encode().toByteArray(Charsets.UTF_8)

        val envelope = listOf(
            MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifestJson),
            MobixContainer.Entry(MobixFormat.CANVAS_ENTRY, canvas),
            MobixContainer.Entry(MobixFormat.pageEntry(1), pageJson),
        )
        FileOutputStream(file).use { MobixContainer.save(envelope + sink.entries(), it) }
    }

    @Test
    fun anAssetAtTheCapIsReadAndOneBytePastItIsRefused() {
        val picture = ByteArray(8) { it.toByte() }
        val file = temp.newFile("picture.mobix")
        pictureNotebook(file, picture)

        MobixPagedReader(MobixEntrySource.fromFile(file), caps = MobixSafetyCaps(maxAssetBytes = 8)).use { reader ->
            assertEquals(picture.size, (reader.getPage(0).layers.single().elements.single() as ImageElement).data.size)
        }

        MobixPagedReader(MobixEntrySource.fromFile(file), caps = MobixSafetyCaps(maxAssetBytes = 7)).use { reader ->
            val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) { reader.getPage(0) }
            assertTrue(
                "the refusal names the asset: ${failure.message}",
                MobixFormat.ASSETS_DIR in failure.message.orEmpty(),
            )
        }
    }
}
