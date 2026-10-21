package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.model.Background
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `.mobix` envelope codecs, against the rules `docs/mobix-schema-spec.md` and
 * `docs/infinite-canvas.md` state: the manifest carries the page order, unknown fields survive a
 * rewrite, only the three fields a reader cannot invent are required, and `minAppVersion` is what
 * says whether this build can keep a file intact.
 */
class MobixManifestTest {

    private val minimal = """{"formatVersion":1,"documentId":"doc-1","canvasMode":"PAGINATED"}"""

    private fun part(id: String, entry: String) =
        """{"id":"$id","entry":"$entry","modified":1775955600000,"sha256":"abc"}"""

    /** The top-level keys of [json], in written order. */
    private fun topLevelKeys(json: String): List<String> =
        (JsonParser.parse(json) as JsonNode.Obj).map.keys.toList()

    @Test
    fun aMinimalManifestOpensWithDocumentedDefaults() {
        val manifest = MobixManifest.decode(minimal)

        assertEquals(1, manifest.formatVersion)
        assertEquals("doc-1", manifest.documentId)
        assertEquals(CanvasMode.PAGINATED, manifest.canvasMode)
        assertEquals("an absent floor means any app can read it", "0.0.0", manifest.minAppVersion)
        assertEquals(0L, manifest.created)
        assertEquals(0L, manifest.modified)
        assertNull(manifest.title)
        assertTrue(manifest.pages.isEmpty())
        assertTrue(manifest.chunks.isEmpty())
        assertNull(manifest.defaultPaper)
    }

    @Test
    fun aManifestRoundTripsFieldForField() {
        val manifest = MobixManifest(
            formatVersion = 1,
            minAppVersion = "2.0.0",
            documentId = "f47ac10b-58cc-4372-a567-0e02b2c3d479",
            canvasMode = CanvasMode.INFINITE_VERTICAL,
            created = 1775952000000L,
            modified = 1775955600000L,
            title = "Analisi Matematica II",
            chunks = listOf(MobixPart("ch_0_0", "chunks/chunk_0_0.json", 1775955600000L, "9f86d081")),
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "ruled", "0.50,0.5,0.5,0.10,1.0,3"),
        )

        val back = MobixManifest.decode(manifest.encode())

        assertEquals(manifest, back)
        assertEquals(
            "the mode decides which list is authoritative",
            manifest.chunks,
            back.content,
        )
    }

    @Test
    fun thePageOrderComesFromTheManifestNotFromTheNames() {
        val outOfOrder = """
            {"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED","pages":[
              ${part("pg_2", "pages/page-0002.json")},
              ${part("pg_1", "pages/page-0001.json")},
              ${part("pg_7", "pages/page-0007.json")}]}
        """.trimIndent()

        val manifest = MobixManifest.decode(outOfOrder)

        assertEquals(
            listOf("pages/page-0002.json", "pages/page-0001.json", "pages/page-0007.json"),
            manifest.content.map { it.entry },
        )
    }

    @Test
    fun unknownFieldsAndTheirValuesSurviveARewrite() {
        val fromAFuture = """
            {"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED",
             "thumbnails":{"pages":["a"]},"futureFlag":true,
             "pages":[{"id":"pg_1","entry":"pages/page-0001.json","fontHint":"serif"}]}
        """.trimIndent()

        val encoded = MobixManifest.decode(fromAFuture).encode()
        val back = MobixManifest.decode(encoded)

        assertTrue(
            "a nested field this build does not model is kept whole: $encoded",
            "\"thumbnails\":{\"pages\":[\"a\"]}" in encoded,
        )
        assertTrue("a boolean field is kept", back.extra.containsKey("futureFlag"))
        assertEquals("and a part's own field too", "serif", (back.pages.single().extra["fontHint"] as JsonNode.Str).value)
        assertEquals(
            "known fields in the format's order, unknown ones last",
            listOf(
                "formatVersion", "minAppVersion", "documentId", "canvasMode", "created", "modified",
                "pages", "thumbnails", "futureFlag",
            ),
            topLevelKeys(encoded),
        )
    }

    @Test
    fun theThreeFieldsAReaderCannotInventAreRequired() {
        for (key in listOf("formatVersion", "documentId", "canvasMode")) {
            val without = minimal.replace(Regex("\"$key\":[^,}]+[,]?"), "").replace(",}", "}")
            val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
                MobixManifest.decode(without)
            }
            assertTrue(
                "the message names what is missing ($key): ${failure.message}",
                key in failure.message.orEmpty(),
            )
        }
    }

    @Test
    fun unknownFieldsKeepTheirValuesNotJustTheirNames() {
        // The manifest is rewritten in the format's own field order, so an unknown *nested* value is
        // preserved as a value (its key order, its types) rather than as source text — see JsonWriter.
        val encoded = MobixManifest.decode(
            """{"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED",
                "future":{"b":2,"a":[true,null,"x"],"c":1.5}}""",
        ).encode()

        assertEquals("""{"b":2,"a":[true,null,"x"],"c":1.5}}""", encoded.substringAfter("\"future\":"))
    }

    @Test
    fun aManifestThatIsNotAnObjectIsRefused() {
        assertThrows(MobixContainer.MalformedMobixException::class.java) { MobixManifest.decode("[1,2]") }
        assertThrows(MobixContainer.MalformedMobixException::class.java) { MobixManifest.decode("") }
    }

    @Test
    fun anUnknownCanvasModeIsRefusedRatherThanGuessedAt() {
        val failure = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixManifest.decode("""{"formatVersion":1,"documentId":"d","canvasMode":"INFINITE_3D"}""")
        }
        assertTrue(failure.message.orEmpty(), "INFINITE_3D" in failure.message.orEmpty())
    }

    @Test
    fun minAppVersionDecidesWhetherThisBuildCanKeepTheFileIntact() {
        val needsTwo = MobixManifest.decode(
            """{"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED","minAppVersion":"2.0.0"}""",
        )

        assertTrue("1.3.0 cannot keep it", needsTwo.requiresAppNewerThan("1.3.0"))
        assertFalse("2.0.0 can", needsTwo.requiresAppNewerThan("2.0.0"))
        assertFalse("a debug build stamp is not a version", needsTwo.requiresAppNewerThan("2.0.0-v2"))
        assertFalse("a later build can", needsTwo.requiresAppNewerThan("2.1.3"))
        assertTrue("a missing component counts as zero", compareVersions("2.1", "2.0.9") > 0)
        assertEquals(0, compareVersions("2.0", "2.0.0"))
    }

    @Test
    fun aNewDocumentStartsOnTheCurrentSchema() {
        val manifest = MobixManifest.forNewDocument(
            documentId = "new-1",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = 1775952000000L,
            appVersion = "2.0.0",
        )

        assertEquals(MobixManifest.CURRENT_FORMAT_VERSION, manifest.formatVersion)
        assertEquals(manifest.created, manifest.modified)
        assertFalse(manifest.requiresAppNewerThan("2.0.0"))
        assertTrue(manifest.content.isEmpty())
    }

    @Test
    fun theDefaultPaperIsTheSameShapeAsAPagesBackground() {
        // One paper shape in the whole format: what a new page starts on and what this page is on are
        // the same object, so they cannot drift — and both spell a colour the way `.xopp` does.
        val manifest = MobixManifest.decode(
            """{"formatVersion":1,"documentId":"d","canvasMode":"PAGINATED",
                "defaultPaper":{"type":"solid","color":"#FFFFFF00","style":"ruled",
                                "config":"0.50,0.5,0.5,0.10,1.0,6"}}""",
        )

        assertEquals(
            Background.Solid(0x00FFFFFF, "ruled", "0.50,0.5,0.5,0.10,1.0,6"),
            manifest.defaultPaper,
        )
        assertEquals("and survives its own rewrite", manifest, MobixManifest.decode(manifest.encode()))
    }

    // ---- canvas.json -------------------------------------------------------------------------

    @Test
    fun canvasGeometryRoundTripsAndKeepsItsOwnChunkGrid() {
        val canvas = MobixCanvasFile(
            mode = CanvasMode.INFINITE_VERTICAL,
            widthPt = 595.28,
            heightPt = 841.89,
            chunkSizePt = 512.0,
            origin = Origin(pageNo = 3, offsetPt = 12.5),
        )

        val back = MobixCanvasFile.decode(canvas.encode())

        assertEquals(canvas, back)
        assertEquals(
            "the grid a document was written against is part of the document",
            512.0,
            back.chunkSizePt,
            0.0,
        )
    }

    @Test
    fun aCanvasWithoutAChunkGridFallsBackToTheDefault() {
        val back = MobixCanvasFile.decode("""{"mode":"INFINITE_2D","widthPt":0,"heightPt":0}""")

        assertEquals(MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT, back.chunkSizePt, 0.0)
        assertTrue("an absent origin is page one at pt zero", back.origin.pageNo == 1)
        assertEquals(0.0, back.origin.offsetPt, 0.0)
        // A zero or negative grid would put every chunk at the same coordinate; refuse it.
        assertEquals(
            MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
            MobixCanvasFile.decode("""{"mode":"INFINITE_2D","chunkSizePt":0}""").chunkSizePt,
            0.0,
        )
    }

    @Test
    fun aCanvasNeedsAKnownMode() {
        assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixCanvasFile.decode("""{"widthPt":595.28}""")
        }
        assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixCanvasFile.decode("""{"mode":"PAGINATED_V2"}""")
        }
        assertThrows(MobixContainer.MalformedMobixException::class.java) {
            MobixCanvasFile.decode("not json at all")
        }
    }
}
