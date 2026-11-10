package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invariants of the retention channel (`docs/mobix-elements.md`): a field this build did not read
 * must survive **every** operation that touches its element, not only a load and a save.
 *
 * The corpus test proves the round trip; this proves the operations a user actually performs on a
 * document in between — editing one property, copying an element, letting a merge pick a page — and
 * the one rule that keeps the channel honest: it may never shadow a field this build writes itself.
 */
class MobixUnknownFieldInvariantsTest {

    /** A field and a nested object, in the shapes a newer writer would leave. */
    private val unknown = LinkedHashMap<String, JsonNode>().apply {
        put("pressureSeed", JsonNode.of(7))
        put("ocr", JsonNode.obj("status" to JsonNode.of("indexed"), "page" to JsonNode.of(2)))
    }

    private val pointUnknown = mapOf("raw" to JsonNode.of(0.5), "device" to JsonNode.of("wacom"))

    private fun stroke(id: String = "stk_1") = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(1.0, 2.0, 1.0, unknown = pointUnknown)),
        uniformWidth = false,
        id = id,
        unknown = unknown,
    )

    private fun text(id: String = "txt_1") = TextElement(
        font = "Sans",
        size = 12.0,
        x = 10.0,
        y = 20.0,
        color = 0xFF000000.toInt(),
        content = "note",
        id = id,
        unknown = mapOf("lineHeight" to JsonNode.of(1.4)),
    )

    private fun roundTrip(element: Element): Element =
        MobixElementCodec.decode(
            MobixElementCodec.encode(element, MobixAssetSink()),
            MobixAssetSource { null },
        )!!

    private fun asText(unknown: Map<String, JsonNode>): Map<String, String> =
        unknown.mapValues { (_, value) -> JsonWriter.write(value) }

    // ---- editing ---------------------------------------------------------------------------------

    @Test
    fun anEditToAnotherPropertyKeepsTheFieldsItDidNotTouch() {
        val edited = stroke().copy(color = 0xFFFF0000.toInt())
        val back = roundTrip(edited) as Stroke

        assertEquals("the element's own fields", asText(unknown), asText(back.unknown))
        assertEquals("the point's own fields", asText(pointUnknown), asText(back.points.single().unknown))
        assertEquals("and the edit itself landed", 0xFFFF0000.toInt(), back.color)
    }

    @Test
    fun aFieldThisBuildWritesWinsOverOneTheChannelCarries() {
        // A file hand-edited into a contradiction: the unknown channel claims a key the codec owns.
        // The known field must win — writing both would put two answers to one question in the file.
        val shadowing = stroke().copy(unknown = unknown + ("tool" to JsonNode.of("ERASER")))
        val json = JsonWriter.write(MobixElementCodec.encode(shadowing, MobixAssetSink()))

        assertEquals("`tool` is written once", 1, Regex("\"tool\"").findAll(json).count())
        assertTrue("with the value this build read", "\"tool\":\"PEN\"" in json)
        assertTrue("and the genuinely unknown keys are still there", "\"pressureSeed\"" in json)
    }

    // ---- copying ---------------------------------------------------------------------------------

    @Test
    fun aCopiedElementCarriesThemAndCanBeGivenANewIdentity() {
        val copy = stroke().copy(id = ElementId.mint(ElementId.STROKE))
        val back = roundTrip(copy) as Stroke

        assertEquals(asText(unknown), asText(back.unknown))
        assertEquals("the copy is a different element with the same unknown fields", copy.id, back.id)
        assertTrue("and it is not the original's id", back.id != "stk_1")
    }

    // ---- merging ---------------------------------------------------------------------------------

    private fun doc(pages: List<Page>, documentId: String = "doc_merge"): MobixDocument {
        val parts = pages.mapIndexed { index, page ->
            val json = JsonWriter.write(MobixPageCodec.encode(page, MobixAssetSink())).toByteArray(Charsets.UTF_8)
            MobixPart(
                id = page.id ?: "pg_%03d".format(index + 1),
                entry = MobixFormat.pageEntry(index + 1),
                sha256 = MobixAssetSink.sha256(json),
                modified = 1_000L,
            )
        }
        return MobixDocument(
            manifest = MobixManifest.forNewDocument(
                documentId = documentId,
                canvasMode = CanvasMode.PAGINATED,
                nowMillis = 1_000L,
                title = "Merge",
                defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
            ).copy(pages = parts),
            canvas = MobixCanvasFile(CanvasMode.PAGINATED, 595.0, 842.0),
            pages = pages,
        )
    }

    private fun pageOf(vararg elements: Element): Page = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(elements.toList())),
        id = "pg_1",
    )

    @Test
    fun aMergeThatUnionsTwoElementsKeepsBothTheirUnknownFields() {
        val local = doc(listOf(pageOf(stroke())))
        val remote = doc(listOf(pageOf(text())))

        val merged = MobixMergeEngine.merge(local = local, remote = remote, nowMillis = 2_000L)
        val elements = merged.mergedDocument.pages.single().layers.flatMap(Layer::elements)
        assertEquals("both sides' elements are in the merged page", 2, elements.size)

        val written = JsonWriter.write(MobixPageCodec.encode(merged.mergedDocument.pages.single(), MobixAssetSink()))
        for (kept in listOf("pressureSeed", "ocr", "wacom", "lineHeight")) {
            assertTrue("the merge kept `$kept`", kept in written)
        }
    }

    // ---- the channel is JSON, not the `.xopp` attrs channel --------------------------------------

    @Test
    fun anUnknownFieldIsNeverFlattenedIntoTheXoppAttrsChannel() {
        val json = JsonWriter.write(MobixElementCodec.encode(stroke(), MobixAssetSink()))
        assertTrue("the unknown keys are their own fields", "\"pressureSeed\":7" in json)
        assertTrue(
            "and are not stringified into `attrs`, which is the `.xopp` bridge's channel",
            "\"attrs\"" !in json || "pressureSeed" !in json.substringAfter("\"attrs\""),
        )
    }
}
