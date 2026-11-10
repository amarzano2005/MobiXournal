package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.Tool
import com.mobixournal.format.model.withPage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The golden `.mobix` v1 corpus (`mobix-core/src/test/resources/fixtures/mobix-v1/`): what a
 * release must keep opening, and what it demonstrably does with each construct.
 *
 * The corpus is versioned because a compatibility claim with no file behind it is a memory, not a
 * test. Every fixture is driven here through the whole chain the app uses — `MobixContainer.open`,
 * `MobixPagedReader`, `MobixDocument`, a save to a new file and an incremental save — and
 * [theCorpusIsTheOneThisTestDrives] fails when a fixture sits in the directory that no test names.
 *
 * Every assertion here is a **claim**: the construct survives, and the assertion says which part of
 * the format carries it. A construct this build cannot draw (an unmodelled element, a paper whose
 * kind is from a newer build) is asserted to be *preserved*, which is the promise the capability
 * table in `docs/mobix-format.md` makes — so a regression that starts dropping one of them fails
 * here, and a change of promise has to change the table and this file together.
 */
class MobixV1FixturesTest {

    /** Every fixture, in the order the corpus README lists them. */
    private val corpus = listOf(
        "01-minimal-single-page",
        "02-multi-page-order",
        "03-bundled-assets",
        "04-incremental-save",
        "05-unknown-entry",
        "06-unknown-envelope-fields",
        "07-unknown-element-type",
        "08-unknown-top-level-field",
        "09-unknown-nested-fields",
        "10-chunked-document",
        "11-unknown-vocabulary-values",
        "12-unknown-background-type",
        "13-required-capability-unknown",
        "14-optional-capability-unknown",
        "15-capability-higher-version",
        "16-capability-additive-order",
        "17-third-party-writer",
    )

    /**
     * Every fixture is openable now that an unknown vocabulary value preserves its element instead of
     * refusing the page — including `11`, whose `FOUNTAIN_PEN` stroke comes back as a preserved
     * element ([anUnknownVocabularyValuePreservesItsElement]).
     */
    private val openable = corpus

    private val now = 1_790_000_000_000L

    // ---- helpers ---------------------------------------------------------------------------------

    private fun bytes(name: String): ByteArray {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/mobix-v1/$name.mobix")
            ?: error("missing fixture resource fixtures/mobix-v1/$name.mobix")
        return stream.use { it.readBytes() }
    }

    private fun archive(name: String): MobixContainer.Archive =
        MobixContainer.open(ByteArrayInputStream(bytes(name)))

    /** A fixture copied to a temporary file: the paged reader and a repack both want a real archive. */
    private fun tempFile(name: String): File =
        File.createTempFile("mobix-v1-$name-", ".mobix").apply {
            deleteOnExit()
            writeBytes(bytes(name))
        }

    private fun manifestOf(archive: MobixContainer.Archive): MobixManifest =
        MobixManifest.decode(
            archive.require(MobixFormat.MANIFEST_ENTRY).bytes.toString(Charsets.UTF_8),
            archive.asAssetSource(),
        )

    /**
     * Each part of [bytes], decoded to the model and re-encoded — the canonical form of a page, so
     * a comparison is about content and not about the JSON spelling the fixture happened to use.
     */
    private fun pageJsons(bytes: ByteArray): List<String> {
        val archive = MobixContainer.open(ByteArrayInputStream(bytes))
        val assets = archive.asAssetSource()
        val manifest = manifestOf(archive)
        return manifest.content.map { part ->
            val node = JsonParser.parse(archive.require(part.entry).bytes.toString(Charsets.UTF_8))
                as JsonNode.Obj
            if (manifest.canvasMode == CanvasMode.PAGINATED) {
                JsonWriter.write(MobixPageCodec.encode(MobixPageCodec.decode(node, assets), MobixAssetSink()))
            } else {
                val (cx, cy) = MobixFormat.parseChunkEntry(part.entry) ?: (0 to 0)
                JsonWriter.write(
                    MobixChunkCodec.encode(
                        MobixChunkCodec.decode(node, assets, fallbackCx = cx, fallbackCy = cy),
                        MobixAssetSink(),
                    ),
                )
            }
        }
    }

    // ---- the corpus itself -----------------------------------------------------------------------

    @Test
    fun theCorpusIsTheOneThisTestDrives() {
        val directory = runCatching {
            File(javaClass.classLoader!!.getResource("fixtures/mobix-v1")!!.toURI())
        }.getOrNull()
        val onDisk = directory?.listFiles()
            ?.filter { it.extension == "mobix" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()

        if (onDisk != null) {
            assertEquals(
                "a fixture in the corpus that no test names is a file nobody reads; add it here",
                corpus.sorted(),
                onDisk,
            )
        }
        assertEquals("the corpus the README documents", 17, corpus.size)
        for (name in corpus) {
            assertTrue("$name is a readable archive", bytes(name).size > 200)
        }
    }

    // ---- the chain every fixture has to survive --------------------------------------------------

    @Test
    fun everyFixtureOpensThroughTheContainerThePagedReaderAndTheDocumentLayer() {
        for (name in openable) {
            val file = tempFile(name)
            try {
                val archive = archive(name)
                assertEquals("$name: the container's type", MobixFormat.MIMETYPE, archive.mimetype)
                val manifest = manifestOf(archive)
                assertEquals("$name: the schema is v1", 1, manifest.formatVersion)

                val document = MobixDocument.open(archive)
                val pages = if (document.pages.isNotEmpty()) document.pages else document.chunks.map { it.toPage() }
                assertEquals("$name: part count", manifest.content.size, pages.size)
                assertEquals("$name: page ids and order", manifest.content.map { it.id }, pages.map { it.id })

                MobixPagedReader.open(file).use { reader ->
                    assertEquals("$name: the reader's page count", manifest.content.size, reader.pageCount)
                    assertEquals(
                        "$name: ids without opening a page",
                        manifest.content.map { it.id },
                        (0 until reader.pageCount).map { reader.pageId(it) },
                    )
                    val first = reader.getPage(0)
                    assertTrue("$name: page 0 has a size", first.width > 0.0 && first.height > 0.0)
                    reader.getPage(reader.pageCount - 1)
                    assertEquals(
                        "$name: the sliding window",
                        minOf(3, reader.pageCount),
                        reader.ensureWindow(0).size,
                    )
                    reader.clearCache()
                }

                MobixEntrySource.fromFile(file).use { source ->
                    val streamed = MobixDocument.open(source)
                    val streamedPages =
                        if (streamed.pages.isNotEmpty()) streamed.pages else streamed.chunks.map { it.toPage() }
                    assertEquals("$name: the streaming open", pages.size, streamedPages.size)
                }
            } finally {
                file.delete()
            }
        }
    }

    @Test
    fun aSaveToANewFileKeepsEveryPartThisBuildModelled() {
        for (name in openable) {
            val before = pageJsons(bytes(name))
            val saved = MobixDocument.open(archive(name)).toBytes(now)
            assertEquals("$name: a page changed on a save to a new file", before, pageJsons(saved))
        }
    }

    // ---- 01: the minimal document ----------------------------------------------------------------

    @Test
    fun aMinimalManifestOpensWithItsDocumentedDefaults() {
        val archive = archive("01-minimal-single-page")
        val manifest = manifestOf(archive)

        assertEquals("a minimal file carries exactly the three required fields", 1, manifest.formatVersion)
        assertEquals("1.0.0", manifest.minAppVersion)
        assertFalse("the fixture is minimal on purpose", archive.names.any { it == MobixFormat.CANVAS_ENTRY })

        val document = MobixDocument.open(archive)
        assertEquals(1, document.pages.size)
        assertEquals("a missing canvas.json takes its size from the page", document.pages[0].width, document.canvas.widthPt, 0.0)
        assertEquals(document.pages[0].height, document.canvas.heightPt, 0.0)
        assertEquals(CanvasMode.PAGINATED, document.canvas.mode)
        assertTrue(document.preserved.isEmpty())
    }

    // ---- 02: the manifest is the page order ------------------------------------------------------

    @Test
    fun thePageOrderComesFromTheManifestAndTheIdsAreStable() {
        val manifest = manifestOf(archive("02-multi-page-order"))

        assertEquals(
            "the order the manifest declares, not the name order",
            listOf("pages/page-0003.json", "pages/page-0001.json", "pages/page-0002.json"),
            manifest.content.map { it.entry },
        )
        assertEquals("identity is not position", listOf("pg_7", "pg_2", "pg_9"), manifest.content.map { it.id })

        val document = MobixDocument.open(archive("02-multi-page-order"))
        assertEquals(
            "the model is in manifest order, whatever the entry names say",
            listOf("pg_7", "pg_2", "pg_9"),
            document.pages.map { it.id },
        )
        assertEquals("and so is the content", "third page", (document.pages[0].layers.single().elements.single() as com.mobixournal.format.model.TextElement).content)

        val saved = manifestOf(MobixContainer.open(ByteArrayInputStream(document.toBytes(now))))
        assertEquals("a save keeps the ids in the same order", listOf("pg_7", "pg_2", "pg_9"), saved.content.map { it.id })
        assertEquals(
            "a save renumbers the entries by position — the manifest order is the document's, the " +
                "name is plumbing",
            listOf("pages/page-0001.json", "pages/page-0002.json", "pages/page-0003.json"),
            saved.content.map { it.entry },
        )
        assertEquals("and the layer names", "Ink", document.pages[1].layers.single().name)
    }

    // ---- 03: content-addressed payloads ----------------------------------------------------------

    @Test
    fun bundledPayloadsAreAddressedByTheirOwnBytesAndSurvive() {
        val archive = archive("03-bundled-assets")
        val assets = archive.entries.filter { it.name.startsWith(MobixFormat.ASSETS_DIR) }
        assertEquals("a picture, a PDF and a recording", 3, assets.size)
        for (entry in assets) {
            assertTrue(
                "${entry.name} is named by the hash of its own bytes",
                entry.name.contains(MobixAssetSink.sha256(entry.bytes)),
            )
        }

        val document = MobixDocument.open(archive)
        val elements = document.pages.flatMap { it.layers.flatMap(Layer::elements) }
        val images = elements.filterIsInstance<com.mobixournal.format.model.ImageElement>()
        assertEquals("the same picture on two elements is one entry", 1, images.map { MobixAssetSink.sha256(it.data) }.distinct().size)

        val saved = MobixContainer.open(ByteArrayInputStream(document.toBytes(now)))
        for (entry in assets) {
            assertArrayEquals("${entry.name} after a save", entry.bytes, saved.bytes(entry.name))
        }
        assertTrue("the PDF background still names its bundled entry", saved.text("pages/page-0001.json")!!.contains("assets/img_"))
        assertTrue("the recording still names its bundled entry", saved.text("pages/page-0001.json")!!.contains("assets/aud_"))
    }

    // ---- 04: an incrementally saved file ---------------------------------------------------------

    @Test
    fun oneEditedPageOfAnIncrementallySavedFileRepacksAlone() {
        val file = tempFile("04-incremental-save")
        val original = archive("04-incremental-save")
        val manifest = manifestOf(original)

        for (part in manifest.content) {
            assertEquals(
                "${part.entry}: the manifest records the digest of the bytes the archive stores — " +
                    "what a later save reads instead of the page",
                MobixAssetSink.sha256(original.require(part.entry).bytes),
                part.sha256,
            )
        }

        try {
            MobixEntrySource.fromFile(file).use { source ->
                val document = MobixDocument.openPaged(MobixPagedReader(source)).toDocument()
                val page = document.pages[1]
                val stroke = Stroke(
                    points = listOf(StrokePoint(500.0, 500.0, 1.0), StrokePoint(520.0, 520.0, 1.0)),
                    color = 0xFF000000.toInt(),
                    tool = Tool.PEN,
                    capStyle = "round",
                    uniformWidth = false,
                )
                val edited = document.withPage(1, page.copy(layers = page.layers + Layer(listOf(stroke))))
                val tracked = TrackedEntrySource(source)
                val output = ByteArrayOutputStream()

                val stats = MobixIncrementalSave.saveOver(
                    source = tracked,
                    document = edited,
                    appVersion = "2.0.0",
                    nowMillis = now,
                    output = output,
                )

                assertEquals(
                    "an untouched page is copied, never inflated",
                    0,
                    tracked.inflateCount("pages/page-0001.json"),
                )
                assertEquals(0, tracked.inflateCount("pages/page-0003.json"))
                assertTrue(
                    "only the envelope and the edited page are rebuilt, got ${stats.rewrittenEntries}",
                    stats.rewrittenEntries <= 4,
                )

                val repacked = output.toByteArray()
                val saved = MobixContainer.open(ByteArrayInputStream(repacked))
                assertArrayEquals(
                    "an untouched page is the same bytes, not a rebuilt one",
                    original.bytes("pages/page-0001.json"),
                    saved.bytes("pages/page-0001.json"),
                )
                assertArrayEquals(
                    original.bytes("pages/page-0003.json"),
                    saved.bytes("pages/page-0003.json"),
                )
                assertArrayEquals(
                    "the bundled background is streamed through",
                    original.entries.first { it.name.startsWith(MobixFormat.ASSETS_DIR) }.bytes,
                    saved.entries.first { it.name.startsWith(MobixFormat.ASSETS_DIR) }.bytes,
                )

                val reopened = MobixDocument.open(saved)
                assertEquals("the page list did not move", manifest.content.map { it.id }, reopened.pages.map { it.id })
                assertEquals("the edited page is the one that changed", 2, reopened.pages[1].layers.flatMap(Layer::elements).size)
                assertEquals("and the unedited pages kept their ink", 1, reopened.pages[0].layers.flatMap(Layer::elements).size)
            }
        } finally {
            file.delete()
        }
    }

    // ---- 05 and 06: unknown entries and envelope fields ------------------------------------------

    @Test
    fun unknownArchiveEntriesSurviveBothSavePaths() {
        val unknown = listOf("future/thing.bin", "index/search.idx")
        val original = archive("05-unknown-entry")

        val saved = MobixContainer.open(ByteArrayInputStream(MobixDocument.open(original).toBytes(now)))
        val file = tempFile("05-unknown-entry")
        try {
            MobixEntrySource.fromFile(file).use { source ->
                val output = ByteArrayOutputStream()
                MobixIncrementalSave.saveOver(
                    source = source,
                    document = MobixDocument.open(source).toDocument(),
                    appVersion = "2.0.0",
                    nowMillis = now,
                    output = output,
                )
                val repacked = MobixContainer.open(ByteArrayInputStream(output.toByteArray()))
                for (name in unknown) {
                    assertArrayEquals("$name after a save to a new file", original.bytes(name), saved.bytes(name))
                    assertArrayEquals("$name after an incremental save", original.bytes(name), repacked.bytes(name))
                }
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun unknownEnvelopeFieldsKeepTheirValueAndOrder() {
        val original = archive("06-unknown-envelope-fields")
        val before = manifestOf(original)
        val beforeCanvas = MobixCanvasFile.decode(original.require(MobixFormat.CANVAS_ENTRY).bytes.toString(Charsets.UTF_8))

        val saved = MobixContainer.open(ByteArrayInputStream(MobixDocument.open(original).toBytes(now)))
        val after = manifestOf(saved)
        val afterCanvas = MobixCanvasFile.decode(saved.require(MobixFormat.CANVAS_ENTRY).bytes.toString(Charsets.UTF_8))

        assertEquals(
            "the manifest's own fields",
            before.extra.mapValues { JsonWriter.write(it.value) },
            after.extra.mapValues { JsonWriter.write(it.value) },
        )
        assertEquals(
            "a page part's own fields",
            before.pages.single().extra.mapValues { JsonWriter.write(it.value) },
            after.pages.single().extra.mapValues { JsonWriter.write(it.value) },
        )
        assertEquals(
            "canvas.json's own fields",
            beforeCanvas.extra.mapValues { JsonWriter.write(it.value) },
            afterCanvas.extra.mapValues { JsonWriter.write(it.value) },
        )
        assertEquals(
            "and inside origin",
            beforeCanvas.origin.extra.mapValues { JsonWriter.write(it.value) },
            afterCanvas.origin.extra.mapValues { JsonWriter.write(it.value) },
        )
        assertEquals("serif", (after.pages.single().extra["fontHint"] as JsonNode.Str).value)
        assertEquals("a nested value is kept whole", "{\"status\":\"indexed\"}", JsonWriter.write(after.pages.single().extra["ocr"]!!))
        assertTrue("and written last", saved.text(MobixFormat.MANIFEST_ENTRY)!!.indexOf("thumbnails") > saved.text(MobixFormat.MANIFEST_ENTRY)!!.indexOf("pages"))
    }

    // ---- 07: an element type with no model -------------------------------------------------------

    @Test
    fun anUnmodelledElementTypeAndAXoppCarriedElementSurviveWhole() {
        val document = MobixDocument.open(archive("07-unknown-element-type"))
        val elements = document.pages.single().layers.flatMap(Layer::elements)
        assertEquals(2, elements.size)

        val json = elements[0] as RawElement
        assertEquals("3dPlotElement", json.name)
        assertEquals(RawElement.Syntax.JSON, json.syntax)
        assertTrue("its own body is the whole object", json.body.contains("sin(x) * cos(y)"))

        val xml = elements[1] as RawElement
        assertEquals(RawElement.Syntax.XML, xml.syntax)
        assertEquals("12", xml.attrs["width"])
        assertEquals("<image2 width=\"12\"/>", xml.body)

        val saved = pageJsons(document.toBytes(now)).joinToString("\n")
        assertTrue("the unknown type survives the save", "\"3dPlotElement\"" in saved)
        assertTrue("with its own fields", "sin(x) * cos(y)" in saved)
        assertTrue("and the .xopp-carried element keeps its marker", "\"syntax\":\"xml\"" in saved)
    }

    // ---- 08 and 09: the known gaps, asserted as gaps ---------------------------------------------

    @Test
    fun anUnknownTopLevelFieldOnAKnownTypeSurvivesTheNextSave() {
        val name = "08-unknown-top-level-field"
        val elements = MobixDocument.open(archive(name)).pages.single().layers.flatMap(Layer::elements)
        assertTrue("the element itself is modelled", elements[0] is Stroke)
        assertTrue(elements[1] is com.mobixournal.format.model.TextElement)

        val saved = pageJsons(MobixDocument.open(archive(name)).toBytes(now)).joinToString("\n")
        for (kept in listOf("pressureSeed", "tiltCurve", "lineHeight", "hyphenation")) {
            assertTrue(
                "`$kept` came from a newer writer and is carried in the element's `unknown` map " +
                    "(docs/mobix-elements.md); it is written back after the fields this build spells",
                kept in saved,
            )
        }
        assertTrue("the fields this build knows are still there", "\"tool\":\"PEN\"" in saved)
        assertTrue(saved.contains("\"content\":\"line height\""))
        assertEquals(
            "a known key is written once, never twice: the unknown map may not shadow one",
            1,
            Regex("\\\"tool\\\"").findAll(saved).count(),
        )
    }

    @Test
    fun unknownNestedFieldsInsideAKnownTypeSurviveToo() {
        val name = "09-unknown-nested-fields"
        val elements = MobixDocument.open(archive(name)).pages.single().layers.flatMap(Layer::elements)
        assertTrue("the table is modelled, not preserved", elements[0] is TableElement)
        assertTrue("the plot is modelled too", elements[1] is PlotElement)

        val saved = pageJsons(MobixDocument.open(archive(name)).toBytes(now)).joinToString("\n")
        for (kept in listOf("fontFamily", "paddingPt", "hatch", "dashPattern", "label")) {
            assertTrue(
                "`$kept` sits in a sub-object or an array item of a known type; the codec that owns " +
                    "that object carries it (docs/mobix-elements.md, the inventory)",
                kept in saved,
            )
        }
        assertTrue("the known cell text survives", "\"text\":\"x\"" in saved)
        assertTrue("and the curve's expression", "\"source\":\"sin(x)\"" in saved)
    }

    // ---- 11: unknown vocabulary values -----------------------------------------------------------

    @Test
    fun anUnknownVocabularyValuePreservesItsElement() {
        val file = tempFile("11-unknown-vocabulary-values")
        try {
            MobixPagedReader.open(file).use { reader ->
                assertEquals(3, reader.pageCount)

                val stroke = reader.getPage(0).layers.single().elements.single()
                assertTrue(
                    "a geometry element's unknown `tool` preserves the element and lets the page open: " +
                        "refusing the page would throw away the rest of the user's drawing",
                    stroke is RawElement,
                )
                assertTrue(
                    "including the value this build cannot read",
                    "FOUNTAIN_PEN" in JsonWriter.write(MobixElementCodec.encode(stroke, MobixAssetSink())),
                )

                val table = reader.getPage(1).layers.single().elements.single()
                assertTrue("a living family's unknown vocabulary preserves the element whole", table is RawElement)
                val tableJson = JsonWriter.write(MobixElementCodec.encode(table, MobixAssetSink()))
                assertTrue("including the value this build cannot read", "JUSTIFY" in tableJson)

                val plot = reader.getPage(2).layers.single().elements.single()
                assertTrue(plot is RawElement)
                assertTrue(
                    "an unknown curve kind is preserved too",
                    "FOURIER" in JsonWriter.write(MobixElementCodec.encode(plot, MobixAssetSink())),
                )
            }
        } finally {
            file.delete()
        }
    }

    // ---- 12: an unknown background type ----------------------------------------------------------

    @Test
    fun anUnknownBackgroundTypeIsDrawnAsAPlainSheetAndWrittenBackUnchanged() {
        val name = "12-unknown-background-type"
        val archive = archive(name)
        val manifest = manifestOf(archive)
        val document = MobixDocument.open(archive)

        assertNotNull("the manifest's defaultPaper keeps the paper whole", manifest.defaultPaper?.let { it as? Background.Solid }?.preserved)
        val background = document.pages.single().background as Background.Solid
        assertNotNull("the page's background keeps it too", background.preserved)
        assertEquals(
            "and drawn with the ruling the object itself declares, as the fallback always did",
            "isometric",
            background.style,
        )

        val saved = MobixContainer.open(ByteArrayInputStream(MobixDocument.open(archive).toBytes(now)))
        val pageText = saved.text("pages/page-0001.json")!!
        assertTrue("the gradient survives the save field for field", "gradient" in pageText)
        assertTrue(pageText.contains("\"from\""))
        assertFalse(
            "and is not replaced by the sheet it was drawn as: that would change the user's page",
            "\"type\":\"solid\"" in pageText,
        )
        assertTrue("the manifest's defaultPaper keeps it too", "gradient" in saved.text(MobixFormat.MANIFEST_ENTRY)!!)
    }

    // ---- 10: a chunked document ------------------------------------------------------------------

    @Test
    fun aChunkedDocumentStreamsSavesAndKeepsItsCoordinates() {
        val document = MobixDocument.open(archive("10-chunked-document"))

        assertTrue("a chunked document has no pages", document.pages.isEmpty())
        assertEquals(2, document.chunks.size)
        assertEquals(CanvasMode.INFINITE_VERTICAL, document.canvas.mode)
        assertEquals("signed coordinates survive", listOf(0 to 0, -1 to 2), document.chunks.map { it.cx to it.cy })
        assertEquals(MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT, document.canvas.chunkSizePt, 0.0)

        val file = tempFile("10-chunked-document")
        try {
            MobixPagedReader.open(file).use { reader ->
                assertEquals(2, reader.pageCount)
                assertEquals("a chunk is a page to every caller", listOf("chk_0_0", "chk_-1_2"), (0 until 2).map { reader.pageId(it) })
                assertEquals(1024.0, reader.getPage(0).width, 0.0)
            }
        } finally {
            file.delete()
        }

        val saved = MobixDocument.open(MobixContainer.open(ByteArrayInputStream(document.toBytes(now))))
        assertEquals("the mode is written back", CanvasMode.INFINITE_VERTICAL, saved.manifest.canvasMode)
        assertEquals(listOf(0 to 0, -1 to 2), saved.chunks.map { it.cx to it.cy })
        assertTrue(saved.manifest.pages.isEmpty())
    }
}
