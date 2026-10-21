package com.mobixournal.format.mobix

import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.CircuitElement
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.ParametricShapeElement
import com.mobixournal.format.model.PlotElement
import com.mobixournal.format.model.RawElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.TableElement
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.Tool
import com.mobixournal.format.model.withIdentity
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The document layer (`docs/mobix-format.md`, `docs/mobix-schema-spec.md`): turning a real `.xopp` into
 * a `.mobix` and reading it back, over the desktop-validated fixtures the drift test uses.
 *
 * What these tests are for is the claim the whole format rests on — **a document that goes through a
 * phone is still the user's document**. So the strongest assertion here is not "the pages count
 * matches" but "the `.xopp` written back out of the `.mobix` is the same XML it went in as": same
 * creator, same title, same preview, same pages with the same elements, audio links, backgrounds and
 * rule parameters. Anything less than that would let a conversion quietly rewrite a file.
 *
 * The other half is the failure behaviour, which is where a document layer earns trust: a page the
 * manifest names but the archive does not carry is a corrupt file rather than an empty page, a schema
 * from a newer build is refused rather than half-read, and an infinite-canvas document is refused
 * rather than opened as an empty page list that a save would then rewrite in a different mode.
 */
class MobixDocumentTest {

    private val fixtures = listOf("plain", "backgrounds", "layers", "text-image")

    /** A fixed clock: every timestamp here is a number the test chose, not one the machine picked. */
    private val now = 1_775_952_000_000L

    private fun load(name: String): Document {
        val stream = javaClass.classLoader!!.getResourceAsStream("fixtures/$name.xopp")
            ?: error("missing fixture resource fixtures/$name.xopp")
        return stream.use { Xopp.open(it) }
    }

    private fun openBytes(bytes: ByteArray): MobixDocument =
        MobixDocument.open(MobixContainer.open(ByteArrayInputStream(bytes)))

    /** A document as the app would produce it, then as `.mobix` bytes. */
    private fun convert(document: Document, source: String? = null): ByteArray =
        MobixDocument.of(document, appVersion = "1.3.0", nowMillis = now, source = source).toBytes(now)

    /**
     * The document without the identity the conversion added — the shape a comparison against a file
     * read from `.xopp` needs, because a `.xopp` has no ids and a conversion mints them once
     * (`ElementId.withIds`, and the document's own id in `manifest.json`). Everything else about the
     * model must be identical, and the file the `.xopp` writer emits is unchanged either way: identity
     * is what the native format *adds* to a document, never something it takes away from one.
     */
    private fun Document.withoutIds(): Document = copy(
        documentId = null,
        pages = pages.map { page ->
            page.copy(
                id = null,
                layers = page.layers.map { layer ->
                    layer.copy(elements = layer.elements.map { element -> element.withoutId() })
                },
            )
        },
    )

    private fun Element.withoutId(): Element = when (this) {
        is Stroke -> copy(id = null)
        is TextElement -> copy(id = null)
        is ImageElement -> copy(id = null)
        is TexImageElement -> copy(id = null)
        is TableElement -> copy(id = null)
        is PlotElement -> copy(id = null)
        is ParametricShapeElement -> copy(id = null)
        is CircuitElement -> copy(id = null)
        is RawElement -> this
    }

    private fun elementsOf(document: Document) = document.pages.flatMap { it.layers.flatMap(Layer::elements) }

    // ---- the round trip that has to hold -----------------------------------------------------------

    @Test
    fun everyFixtureSurvivesXoppToMobixToXoppAsTheSameDocument() {
        for (name in fixtures) {
            val original = load(name)
            val document = openBytes(convert(original)).toDocument()

            assertEquals(
                "$name: the document is not the one that went in",
                original,
                document.withoutIds(),
            )
            assertEquals("$name: creator", original.creator, document.creator)
            assertEquals("$name: file version", original.fileVersion, document.fileVersion)
            assertEquals("$name: title", original.title, document.title)
            assertEquals("$name: preview", original.preview, document.preview)
            assertEquals(
                "$name: the .xopp written back out is not the file that went in",
                Xopp.toXml(original),
                Xopp.toXml(document),
            )
        }
    }

    @Test
    fun pageLayerAndElementCountsSurvive() {
        for (name in fixtures) {
            val original = load(name)
            val document = openBytes(convert(original)).toDocument()

            assertEquals("$name: pages", original.pages.size, document.pages.size)
            for (index in original.pages.indices) {
                assertEquals(
                    "$name: page $index layers",
                    original.pages[index].layers.size,
                    document.pages[index].layers.size,
                )
            }
            assertEquals("$name: elements", elementsOf(original), elementsOf(document).map { it.withoutId() })
            assertEquals("$name: element count", elementsOf(original).size, elementsOf(document).size)
        }
    }

    @Test
    fun layerNamesAndElementOrderSurviveInOrder() {
        val original = load("layers")
        val document = openBytes(convert(original)).toDocument()

        assertEquals(
            original.pages[0].layers.map { it.name },
            document.pages[0].layers.map { it.name },
        )
        // The JSON key order is the drawing order: an element must not come back in a different place,
        // or a document would look different after a save that changed nothing.
        assertEquals(
            original.pages[0].layers[0].elements,
            document.pages[0].layers[0].elements.map { it.withoutId() },
        )
    }

    @Test
    fun everyBackgroundStyleKeepsItsRulingParameters() {
        val original = load("backgrounds")
        val document = openBytes(convert(original)).toDocument()

        val styles = document.pages.map { (it.background as Background.Solid).style }
        assertEquals(listOf("plain", "lined", "ruled", "graph", "dotted"), styles)
        val configurations = document.pages.map { (it.background as Background.Solid).config }
        assertEquals(
            "a ruling's own parameters are the desktop's comma-separated string, kept verbatim",
            original.pages.map { (it.background as Background.Solid).config },
            configurations,
        )
    }

    @Test
    fun theFirstPageBackgroundIsWhatANewPageStartsOn() {
        for (name in fixtures) {
            val document = openBytes(convert(load(name)))
            assertEquals(
                "$name: defaultPaper is the first page's background",
                document.pages.first().background,
                document.manifest.defaultPaper,
            )
        }
    }

    @Test
    fun audioLinksAndUnknownMarkupRideAlong() {
        // The two preservation channels a `.xopp` through a phone already relies on: an attribute we do
        // not interpret (`ts`/`fn`, the audio link) and an element we have no model for.
        val original = Document(
            pages = listOf(
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(
                        Layer(
                            name = "ink",
                            elements = listOf(
                                Stroke(
                                    tool = Tool.PEN,
                                    color = 0xFF000000.toInt(),
                                    capStyle = "round",
                                    points = listOf(StrokePoint(1.0, 2.0, 1.1), StrokePoint(3.0, 4.0, 1.2)),
                                    uniformWidth = true,
                                    extraAttrs = linkedMapOf("ts" to "14250", "fn" to "aud_7a9f1c.opus"),
                                ),
                                RawElement(
                                    name = "vendorThing",
                                    attrs = linkedMapOf("width" to "12"),
                                    body = "<x/>",
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val back = openBytes(convert(original)).toDocument()

        val stroke = back.pages[0].layers[0].elements[0] as Stroke
        assertEquals(linkedMapOf("ts" to "14250", "fn" to "aud_7a9f1c.opus"), stroke.extraAttrs)
        val raw = back.pages[0].layers[0].elements[1] as RawElement
        assertEquals("vendorThing", raw.name)
        assertEquals(RawElement.Syntax.XML, raw.syntax)
        assertEquals(linkedMapOf("width" to "12"), raw.attrs)
        assertEquals("<x/>", raw.body)
    }

    // ---- images as content-addressed assets --------------------------------------------------------

    @Test
    fun imagesAreBundledByContentAndComeBackByteForByte() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 7, 7, 7)

        val back = openBytes(convert(documentWithImage(png, png))).toDocument()

        val image = back.pages[0].layers[0].elements[0] as ImageElement
        assertTrue("the bytes are the ones that went in", png.contentEquals(image.data))
        val reopened = openBytes(convert(documentWithImage(png, png)))
        assertEquals(
            "the same picture on two pages is one entry, addressed by its content",
            1,
            reopened.assetNames.size,
        )
        assertTrue(reopened.assetNames.single().startsWith(MobixFormat.ASSETS_DIR + "img_"))
    }

    private fun documentWithImage(first: ByteArray, second: ByteArray) = Document(
        pages = listOf(
            imagePage(0.0, first),
            imagePage(300.0, second),
        ),
    )

    private fun imagePage(left: Double, bytes: ByteArray) = Page(
        width = 595.0,
        height = 842.0,
        background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        layers = listOf(Layer(listOf(ImageElement(left, 0.0, left + 10.0, 10.0, bytes)))),
    )

    // ---- identity and the manifest's bookkeeping ---------------------------------------------------

    @Test
    fun everyPageGetsOneIdentityThatSurvivesEverySave() {
        val document = MobixDocument.of(load("backgrounds"), appVersion = "1.3.0", nowMillis = now)

        val ids = document.encode(now).let { entries ->
            MobixManifest.decode(entries.first { it.name == MobixFormat.MANIFEST_ENTRY }.bytes.toString(Charsets.UTF_8))
        }.pages.map { it.id }

        assertTrue("a page id is minted for a .xopp, which has none", ids.all { it.startsWith("pg_") })
        assertEquals("no page shares an id", ids.size, ids.distinct().size)

        val again = MobixManifest.decode(
            openBytes(document.toBytes(now)).encode(now + 60_000)
                .first { it.name == MobixFormat.MANIFEST_ENTRY }
                .bytes.toString(Charsets.UTF_8),
        ).pages.map { it.id }
        assertEquals("and a save later does not re-mint them", ids, again)
    }

    @Test
    fun anUntouchedPageKeepsItsModifiedStampSoASaveIsNotAnEdit() {
        val first = openBytes(MobixDocument.of(load("plain"), appVersion = "1.3.0", nowMillis = now).toBytes(now))
        val later = now + 3_600_000

        val rewritten = MobixManifest.decode(
            first.encode(later).first { it.name == MobixFormat.MANIFEST_ENTRY }.bytes.toString(Charsets.UTF_8),
        )

        assertEquals("the document's own stamp moved", later, rewritten.modified)
        assertEquals(
            "but a page whose bytes did not change is not reported as an edit",
            listOf(now),
            rewritten.pages.map { it.modified },
        )
        assertEquals(
            "and its digest is the digest of the bytes written",
            rewritten.pages.map { it.sha256 },
            first.encode(later).filter { MobixFormat.isPageEntry(it.name) }
                .map { MobixAssetSink.sha256(it.bytes) },
        )
    }

    @Test
    fun theSourceOfAXoppIsRemembered() {
        val original = load("plain")

        val document = MobixDocument.of(original, appVersion = "1.3.0", nowMillis = now, source = "udiff.xopp")
        val reopened = openBytes(document.toBytes(now))

        assertEquals(original.creator, reopened.bridge?.creator)
        assertEquals(original.fileVersion, reopened.bridge?.fileVersion)
        assertEquals(original.preview, reopened.bridge?.preview)
        assertEquals("udiff.xopp", reopened.bridge?.source)
        assertEquals(
            "and the record itself round-trips through its own JSON",
            reopened.bridge,
            MobixBridgeFile.decode(reopened.bridge!!.encode()),
        )
    }

    @Test
    fun minAppVersionIsWrittenFromTheCallersVersionAndIsTheCallersQuestionToAsk() {
        val document = openBytes(MobixDocument.of(load("plain"), appVersion = "2.0.0", nowMillis = now).toBytes(now))

        assertEquals("2.0.0", document.manifest.minAppVersion)
        assertTrue("a 1.3 build may open it read-only, and the app decides that", document.manifest.requiresAppNewerThan("1.3.0"))
        assertFalse(document.manifest.requiresAppNewerThan("2.0.0"))
        assertFalse("a build stamp is not a version", document.manifest.requiresAppNewerThan("2.0.0-v2"))
    }

    @Test
    fun theDocumentIdIsFreshForANewDocumentAndStableForAReSave() {
        val first = MobixDocument.of(load("plain"), appVersion = "1.3.0", nowMillis = now)
        val second = MobixDocument.of(load("plain"), appVersion = "1.3.0", nowMillis = now)
        assertNotEquals("ingesting a file makes a document, not a second copy of one", first.manifest.documentId, second.manifest.documentId)

        val reopened = openBytes(first.toBytes(now))
        assertEquals(first.manifest.documentId, reopened.manifest.documentId)
        assertEquals(
            "and a save keeps it",
            first.manifest.documentId,
            openBytes(reopened.toBytes(now + 1)).manifest.documentId,
        )
    }

    /**
     * The identity travels with the *model*, not only inside the archive: opening a `.mobix` into the
     * app's document and saving that document again is an edit of the same file.
     *
     * This is the half the app actually exercises — [DocumentIo.encode] hands the open document to
     * [MobixDocument.of] without naming an id, so if the model did not carry one, every save would
     * mint a new document and a sync client would see a whole file replaced rather than a page edited.
     */
    @Test
    fun theModelCarriesTheIdentitySoAResaveIsTheSameDocument() {
        val opened = openBytes(MobixDocument.of(load("plain"), appVersion = "1.3.0", nowMillis = now).toBytes(now))

        val model = opened.toDocument()
        assertEquals("the model knows which document it is", opened.manifest.documentId, model.documentId)

        val saved = MobixDocument.of(model, appVersion = "1.3.0", nowMillis = now + 1)
        assertEquals("so saving it again is the same document", model.documentId, saved.manifest.documentId)

        val fromXopp = load("plain")
        assertNull("a .xopp has no document identity to carry", fromXopp.documentId)
        assertNotEquals(
            "and a document that never was one gets a fresh id per ingest",
            MobixDocument.of(fromXopp, appVersion = "1.3.0", nowMillis = now).manifest.documentId,
            MobixDocument.of(fromXopp, appVersion = "1.3.0", nowMillis = now).manifest.documentId,
        )
    }

    /**
     * The regression test for `identity-assigned-once-for-a-mobix`: the app adopts the document's
     * identity before it encodes, so the ids in the first file are the ids in the second.
     *
     * The contrast in the same test is what makes it worth having: handing the writer the *un*adopted
     * model, which is what the app used to do, mints a different set on every save — and a merge reads
     * that as the whole page replaced rather than one page edited.
     *
     * Both are read back out of the **bytes**, not out of the in-memory manifest: the page parts (and so
     * their digests) are filled in by the write, which is the thing being tested.
     */
    @Test
    fun aSaveOfAnAdoptedDocumentWritesTheIdsThePreviousSaveDid() {
        // A `.xopp` has no ids anywhere, which is the case that used to re-mint on every save.
        val adopted = load("plain").withIdentity()
        val first = openBytes(MobixDocument.of(adopted, appVersion = "1.3.0", nowMillis = now).toBytes(now))
        val second = openBytes(
            MobixDocument.of(adopted.withIdentity(), appVersion = "1.3.0", nowMillis = now + 60_000)
                .toBytes(now + 60_000),
        )

        assertEquals("still the same document", first.manifest.documentId, second.manifest.documentId)
        assertTrue("and it has pages to compare", first.manifest.pages.isNotEmpty())
        assertEquals(
            "and pages that hash the same, because the ids were the model's already",
            first.manifest.pages.map { it.id to it.sha256 },
            second.manifest.pages.map { it.id to it.sha256 },
        )

        val unadopted = load("plain")
        assertNotEquals(
            "while the same model handed over unidentified re-mints every element",
            openBytes(MobixDocument.of(unadopted, appVersion = "1.3.0", nowMillis = now).toBytes(now))
                .manifest.pages.map { it.sha256 },
            openBytes(MobixDocument.of(unadopted, appVersion = "1.3.0", nowMillis = now).toBytes(now))
                .manifest.pages.map { it.sha256 },
        )
    }

    /**
     * The writer still identifies what it is handed, whatever the caller did: the format requires an id
     * on every page and element, so a writer that relied on its caller having adopted them would write a
     * file no merge could use. Adoption in the app is what keeps model and file in step, not what makes
     * the file valid.
     */
    @Test
    fun aWriterStillIdentifiesADocumentTheModelNeverAdopted() {
        val written = openBytes(MobixDocument.of(load("plain"), appVersion = "1.3.0", nowMillis = now).toBytes(now))

        assertTrue("every page is identified", written.manifest.pages.all { it.id.isNotEmpty() })
        assertTrue("and read back with that id", written.pages.all { it.id != null })
        assertTrue(
            "and so is every element the file carries",
            written.pages.all { page ->
                page.layers.all { layer -> layer.elements.all { ElementId.idOf(it) != null } }
            },
        )
    }

    @Test
    fun aDocumentWithNoPagesIsStillADocument() {
        val empty = MobixDocument.of(Document(pages = emptyList()), appVersion = "1.3.0", nowMillis = now)
        val entries = empty.encode(now)

        assertTrue(entries.any { it.name == MobixFormat.MANIFEST_ENTRY })
        assertTrue(entries.any { it.name == MobixFormat.CANVAS_ENTRY })
        assertFalse(entries.any { it.name.startsWith(MobixFormat.PAGES_DIR) })

        val reopened = openBytes(empty.toBytes(now))
        assertTrue(reopened.pages.isEmpty())
        assertEquals(
            "nothing to lay out, so the canvas states no size rather than inventing one",
            0.0,
            reopened.canvas.widthPt,
            1e-9,
        )
        assertEquals(0.0, reopened.canvas.heightPt, 1e-9)
    }

    // ---- what a rewrite must not do, and what a reader must refuse ---------------------------------

    @Test
    fun anEntryThisBuildDoesNotModelSurvivesARewrite() {
        val fromNewerBuild = MobixContainer.Entry("index/search.json", "{}".toByteArray(Charsets.UTF_8))
        val payload = MobixContainer.Entry("assets/other_thing.bin", byteArrayOf(1, 2, 3))

        val entries = MobixDocument.of(load("plain"), appVersion = "1.3.0", nowMillis = now)
            .encode(now) + fromNewerBuild + payload
        val document = openBytes(MobixContainer.toBytes(entries))

        assertEquals(
            "the unknown entry is preserved; the payload became an asset the document carries",
            listOf("index/search.json"),
            document.preserved.map { it.name },
        )
        assertEquals(listOf("assets/other_thing.bin"), document.assetNames)
        val rewritten = MobixContainer.toBytes(document.encode(now + 1))
        val reopened = MobixContainer.open(ByteArrayInputStream(rewritten))

        assertEquals("the unknown entry is still there, unchanged", "{}", reopened.text("index/search.json"))
        assertTrue("and so is a payload nothing referenced", payload.bytes.contentEquals(reopened.bytes("assets/other_thing.bin")))
        assertEquals("no entry was written twice", reopened.names.size, reopened.names.distinct().size)
    }

    @Test
    fun aPageTheManifestNamesButTheArchiveDoesNotCarryIsACorruptFile() {
        val manifest = """
            {"formatVersion":1,"documentId":"d1","canvasMode":"PAGINATED",
             "pages":[{"id":"pg_1","entry":"pages/page-0001.json"}]}
        """.trimIndent()
        val archive = MobixContainer.toBytes(
            listOf(MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.toByteArray(Charsets.UTF_8))),
        )

        val thrown = assertThrows(MobixContainer.MalformedMobixException::class.java) { openBytes(archive) }
        assertTrue(thrown.message!!.contains("pages/page-0001.json"))
    }

    @Test
    fun aSchemaFromANewerBuildIsRefusedRatherThanHalfRead() {
        val manifest = """{"formatVersion":2,"documentId":"d1","canvasMode":"PAGINATED"}"""

        val thrown = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            openBytes(MobixContainer.toBytes(listOf(MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.toByteArray(Charsets.UTF_8)))))
        }
        assertTrue(thrown.message!!.contains("formatVersion 2"))
    }

    @Test
    fun aChunkMissingFromArchiveIsACorruptFile() {
        val manifest = """{"formatVersion":1,"documentId":"d1","canvasMode":"INFINITE_2D",
            "chunks":[{"id":"ch_1","entry":"chunks/chunk_0_0.json"}]}""".trimIndent()

        val thrown = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            openBytes(MobixContainer.toBytes(listOf(MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.toByteArray(Charsets.UTF_8)))))
        }
        assertTrue(thrown.message!!.contains("chunks/chunk_0_0.json"))
    }

    @Test
    fun aDocumentCarryingBothPagesAndChunksIsRefused() {
        val manifestPaginated = """{"formatVersion":1,"documentId":"d1","canvasMode":"PAGINATED",
            "pages":[{"id":"pg_1","entry":"pages/page-0001.json"}],
            "chunks":[{"id":"ch_1","entry":"chunks/chunk_0_0.json"}]}""".trimIndent()

        val thrown1 = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            openBytes(MobixContainer.toBytes(listOf(MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifestPaginated.toByteArray(Charsets.UTF_8)))))
        }
        assertTrue(thrown1.message!!.contains("PAGINATED document carries chunks/"))

        val manifestInfinite = """{"formatVersion":1,"documentId":"d1","canvasMode":"INFINITE_VERTICAL",
            "pages":[{"id":"pg_1","entry":"pages/page-0001.json"}],
            "chunks":[{"id":"ch_1","entry":"chunks/chunk_0_0.json"}]}""".trimIndent()

        val thrown2 = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            openBytes(MobixContainer.toBytes(listOf(MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifestInfinite.toByteArray(Charsets.UTF_8)))))
        }
        assertTrue(thrown2.message!!.contains("INFINITE_VERTICAL document carries pages/"))
    }

    @Test
    fun aCanvasThatDisagreesWithTheManifestIsACorruptFile() {
        val manifest = """{"formatVersion":1,"documentId":"d1","canvasMode":"PAGINATED"}"""
        val canvas = """{"mode":"INFINITE_2D","widthPt":100,"heightPt":100}"""

        val thrown = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            openBytes(
                MobixContainer.toBytes(
                    listOf(
                        MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.toByteArray(Charsets.UTF_8)),
                        MobixContainer.Entry(MobixFormat.CANVAS_ENTRY, canvas.toByteArray(Charsets.UTF_8)),
                    ),
                ),
            )
        }
        assertTrue(thrown.message!!.contains("canvas.json says INFINITE_2D"))
    }

    @Test
    fun aPageEntryThatIsNotAJsonObjectIsACorruptFile() {
        val manifest = """{"formatVersion":1,"documentId":"d1","canvasMode":"PAGINATED",
            "pages":[{"id":"pg_1","entry":"pages/page-0001.json"}]}""".trimIndent()

        val thrown = assertThrows(MobixContainer.MalformedMobixException::class.java) {
            openBytes(
                MobixContainer.toBytes(
                    listOf(
                        MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.toByteArray(Charsets.UTF_8)),
                        MobixContainer.Entry(MobixFormat.pageEntry(1), "[1,2,3]".toByteArray(Charsets.UTF_8)),
                    ),
                ),
            )
        }
        assertTrue(thrown.message!!.contains("not a JSON object"))
    }

    @Test
    fun aDocumentWrittenHereIsTypedAndCarriesItsManifestFirst() {
        // The container's own rules, asserted from the document layer's side once: a reader identifies
        // the file by its mimetype entry, so that entry has to be there and uncompressed.
        val bytes = MobixDocument.of(load("plain"), appVersion = "1.3.0", nowMillis = now).toBytes(now)

        assertTrue(MobixContainer.isMobix(ByteArrayInputStream(bytes)))
        val reopened = MobixContainer.open(ByteArrayInputStream(bytes))
        assertEquals(MobixFormat.MANIFEST_ENTRY, reopened.names.first())
    }

    @Test
    fun aResaveChangesNothingButTheClock() {
        val first = openBytes(MobixDocument.of(load("text-image"), appVersion = "1.3.0", nowMillis = now).toBytes(now))
        val second = openBytes(first.toBytes(now + 1))

        assertEquals(first.pages, second.pages)
        assertEquals(
            "a save that changed nothing writes the same page bytes: the write is stable",
            first.encode(now + 1).filter { MobixFormat.isPageEntry(it.name) },
            second.encode(now + 1).filter { MobixFormat.isPageEntry(it.name) },
        )
        assertEquals(
            "and the assets are the same content-addressed entries",
            first.encode(now + 1).filter { it.name.startsWith(MobixFormat.ASSETS_DIR) }.map { it.name },
            second.encode(now + 1).filter { it.name.startsWith(MobixFormat.ASSETS_DIR) }.map { it.name },
        )
    }

    @Test
    fun infiniteVerticalDocumentEncodesAndReopensRoundTrip() {
        val stroke = Stroke(
            tool = Tool.PEN,
            color = 0xFF0000FF.toInt(),
            capStyle = "round",
            points = listOf(StrokePoint(50.0, 100.0, width = 3.0, pressure = 0.6f)),
            uniformWidth = true,
            id = "stk_vert_1",
        )
        val chunk0 = MobixChunk(
            cx = 0,
            cy = 0,
            layers = listOf(Layer(elements = listOf(stroke), name = "Layer1")),
            id = "chk_0_0",
        )
        val chunk1 = MobixChunk(
            cx = 0,
            cy = 1,
            layers = listOf(Layer(elements = emptyList())),
            id = "chk_0_1",
        )

        val doc = MobixDocument.forInfinite(
            documentId = "doc_vert_123",
            canvasMode = CanvasMode.INFINITE_VERTICAL,
            widthPt = 595.28,
            heightPt = 841.89,
            chunks = listOf(chunk0, chunk1),
            title = "Vertical Notes",
            nowMillis = now,
        )

        val bytes = doc.toBytes(now)
        val reopened = openBytes(bytes)

        assertEquals(CanvasMode.INFINITE_VERTICAL, reopened.manifest.canvasMode)
        assertEquals(CanvasMode.INFINITE_VERTICAL, reopened.canvas.mode)
        assertEquals(595.28, reopened.canvas.widthPt, 0.001)
        assertEquals("doc_vert_123", reopened.manifest.documentId)
        assertEquals("Vertical Notes", reopened.manifest.title)
        assertEquals(2, reopened.chunks.size)
        assertEquals("chk_0_0", reopened.chunks[0].id)
        assertEquals(0, reopened.chunks[0].cx)
        assertEquals(0, reopened.chunks[0].cy)
        assertEquals(1, reopened.chunks[0].layers.size)
        assertEquals("stk_vert_1", (reopened.chunks[0].layers[0].elements[0] as Stroke).id)

        assertEquals("chk_0_1", reopened.chunks[1].id)
        assertEquals(0, reopened.chunks[1].cx)
        assertEquals(1, reopened.chunks[1].cy)

        // Verify container entries
        val container = MobixContainer.open(ByteArrayInputStream(bytes))
        assertTrue(container.names.contains(MobixFormat.MANIFEST_ENTRY))
        assertTrue(container.names.contains(MobixFormat.CANVAS_ENTRY))
        assertTrue(container.names.contains("chunks/chunk_0_0.json"))
        assertTrue(container.names.contains("chunks/chunk_0_1.json"))
        assertFalse(container.names.any { it.startsWith("pages/") })
    }

    @Test
    fun infinite2dDocumentWithSignedCoordinatesRoundTrip() {
        val chunkNeg = MobixChunk(
            cx = -1,
            cy = 2,
            layers = listOf(Layer(elements = emptyList(), name = "TileNeg")),
            id = "chk_neg_1_2",
        )
        val chunkPos = MobixChunk(
            cx = 3,
            cy = -4,
            layers = listOf(Layer(elements = emptyList(), name = "TilePos")),
            id = "chk_pos_3_neg4",
        )

        val doc = MobixDocument.forInfinite(
            documentId = "doc_2d_free",
            canvasMode = CanvasMode.INFINITE_2D,
            chunks = listOf(chunkNeg, chunkPos),
            nowMillis = now,
        )

        val bytes = doc.toBytes(now)
        val reopened = openBytes(bytes)

        assertEquals(CanvasMode.INFINITE_2D, reopened.manifest.canvasMode)
        assertEquals(CanvasMode.INFINITE_2D, reopened.canvas.mode)
        assertEquals(2, reopened.chunks.size)
        assertEquals(-1, reopened.chunks[0].cx)
        assertEquals(2, reopened.chunks[0].cy)
        assertEquals(3, reopened.chunks[1].cx)
        assertEquals(-4, reopened.chunks[1].cy)

        val container = MobixContainer.open(ByteArrayInputStream(bytes))
        assertTrue(container.names.contains("chunks/chunk_-1_2.json"))
        assertTrue(container.names.contains("chunks/chunk_3_-4.json"))
    }
}
