package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.withIdentity
import com.mobixournal.format.model.withIdentityOn
import com.mobixournal.format.model.withPage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A save that edits one page of a `.mobix` **opens** one page.
 *
 * This is the second half of the incremental save: the diff in [MobixIncrementalSave] decides which
 * entries are written, but it can only do that by hashing their bytes — and hashing an untouched page
 * costs that page its JSON. [MobixPageReuse] is what removes that cost, by answering *before* the page
 * is built: a page the document's own list still holds from the source is a page whose bytes are
 * already in the file, so it is copied and never opened.
 *
 * The assertions are counts of real page reads ([CountingPages] is the only door a page comes through):
 * a page this save does not write is a page the reader is never asked for.
 */
class MobixPageReuseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val now = 1_700_000_000_000L

    /**
     * The source's own pages, with every materialisation recorded. [PageSource.page] is the single door
     * a page model comes through, which is what makes "the save did not read page 40" an assertion
     * rather than an intention.
     */
    private class CountingPages(private val delegate: PageSource) : PageSource {

        val opened = mutableListOf<Int>()

        override val pageCount: Int get() = delegate.pageCount

        override fun pageId(index: Int): String? = delegate.pageId(index)

        override fun pageSize(index: Int): PageSize = delegate.pageSize(index)

        override fun sourceParts(): List<MobixPart>? = delegate.sourceParts()

        override fun page(index: Int): Page = delegate.page(index).also { opened += index }
    }

    /** A `.mobix` on disk, opened the way the editor opens one: page by page, behind the counter. */
    private class Opened(val document: Document, val pages: CountingPages, val reader: MobixPagedReader) : AutoCloseable {
        override fun close() = reader.close()
    }

    private fun notebook(file: File, pageCount: Int = 10): Opened {
        SyntheticNotebook.write(file, SyntheticNotebook.pages(pageCount))
        val reader = MobixPagedReader(MobixEntrySource.fromFile(file))
        val pages = CountingPages(PageSource.of(reader))
        return Opened(MobixDocument.openPaged(reader, pages).toDocument(), pages, reader)
    }

    /** [document] with page [index] given one more stroke — what drawing on a page does to the model. */
    private fun Document.editPage(index: Int): Document = withPage(
        index,
        pages[index].copy(
            layers = pages[index].layers + Layer(listOf(SyntheticNotebook.stroke(500.0, id = "stk_new"))),
        ),
    )

    private fun reuseOf(file: File, document: Document): MobixPageReuse =
        MobixEntrySource.fromFile(file).use { MobixPageReuse.of(document.pages, it) }

    /** The one-page save: the plan, and what the repack did with it. */
    private class Saved(val bytes: ByteArray, val plan: MobixSavePlan, val stats: MobixIncrementalSaveStats)

    private fun save(file: File, document: Document, reuse: MobixPageReuse, nowMillis: Long = now + 60_000): Saved {
        val mobix = MobixDocument.of(
            document = document,
            appVersion = "1.3.0",
            nowMillis = nowMillis,
            reuse = reuse,
        )
        val plan = mobix.encode(nowMillis = nowMillis, reuse = reuse)
        val output = ByteArrayOutputStream()
        val stats = MobixEntrySource.fromFile(file).use { from ->
            MobixIncrementalSave.repack(from, plan, output)
        }
        return Saved(output.toByteArray(), plan, stats)
    }

    private fun entriesOf(bytes: ByteArray): Map<String, ByteArray> =
        MobixContainer.open(ByteArrayInputStream(bytes)).entries.associate { it.name to it.bytes }

    private fun openDocument(bytes: ByteArray): Document =
        MobixDocument.open(MobixContainer.open(ByteArrayInputStream(bytes))).toDocument()

    // ---- the win -----------------------------------------------------------------------------------

    @Test
    fun aSaveThatEditsOnePageOpensThatPageAndTheFirst() {
        val file = temp.newFile("notebook.mobix")
        val opened = notebook(file)
        opened.use {
            val edited = opened.document.editPage(4)
            val plan = reuseOf(file, edited)

            assertEquals("every untouched page is reused", 9, plan.reused.size)
            assertEquals("and only the edited page is written", setOf(4), plan.written)

            opened.pages.opened.clear()
            val saved = save(file, edited, plan)

            // The edited page is in the model already (the list overlays it), so the source is asked for
            // exactly one thing: page 0, which states the document's own paper and page size.
            assertEquals(
                "the save opened the first page for the document's paper, and no other",
                listOf(0),
                opened.pages.opened,
            )
            // The canvas and the nine untouched pages stream out of the file; the manifest, the
            // document's source record (this fixture carries none) and the edited page are written.
            assertEquals("ten entries stream", 10, saved.stats.streamedEntries)
            assertEquals("three are written", 3, saved.stats.rewrittenEntries)
            assertEquals("the plan names every page of the file", 10, saved.plan.order.count { MobixFormat.isPageEntry(it) })

            // The untouched pages are the source's own bytes, byte for byte…
            val before = entriesOf(file.readBytes())
            val after = entriesOf(saved.bytes)
            for (index in 0 until 10) {
                val name = SyntheticNotebook.entryOf(index)
                if (index != 4) {
                    assertEquals("page $index is the source's own bytes", before[name]!!.toList(), after[name]!!.toList())
                } else {
                    assertTrue("the edited page is not", before[name]!!.toList() != after[name]!!.toList())
                }
            }
            // …and what comes out is the edited document, page for page.
            assertEquals(edited.pages.toList(), openDocument(saved.bytes).pages)
        }
    }

    @Test
    fun aReSaveThatChangedNothingWritesTheManifestAndOpensNoPage() {
        val file = temp.newFile("notebook.mobix")
        val opened = notebook(file)
        opened.use {
            val plan = reuseOf(file, opened.document)
            opened.pages.opened.clear()
            val saved = save(file, opened.document, plan)

            assertEquals("every page is reused", 10, plan.reused.size)
            assertEquals("no page of the document is built", 0, saved.plan.entries.count { MobixFormat.isPageEntry(it.name) })
            // The manifest always moves, and the source record is written whenever the document has one
            // (this fixture carries none, so it is new here) — and that is all: the canvas and the ten
            // pages stream out of the file, untouched.
            assertEquals(2, saved.stats.rewrittenEntries)
            assertEquals("the canvas and the ten pages stream", 11, saved.stats.streamedEntries)
            assertEquals(
                "a re-save that changed nothing opens a page only for the document's paper",
                listOf(0),
                opened.pages.opened,
            )
            assertEquals(opened.document.pages.toList(), openDocument(saved.bytes).pages)
        }
    }

    @Test
    fun theShippedSaveReadsNoPageEntryAtAll() {
        val file = temp.newFile("notebook.mobix")
        val opened = notebook(file)
        opened.use {
            val edited = opened.document.editPage(4)
            val tracked = TrackedEntrySource(MobixEntrySource.fromFile(file))
            opened.pages.opened.clear()

            val output = ByteArrayOutputStream()
            val stats = MobixIncrementalSave.saveOver(
                source = tracked,
                document = edited,
                appVersion = "1.3.0",
                nowMillis = now + 60_000,
                output = output,
            )
            tracked.close()

            // The whole point, at the level the app's save runs at: a page that is copied is copied as
            // **bytes** — its entry is never inflated, so its JSON is never parsed, rebuilt or hashed.
            for (index in 0 until 10) {
                val name = SyntheticNotebook.entryOf(index)
                assertEquals("page $index's JSON is never read", 0, tracked.inflateCount(name))
                if (index != 4) {
                    assertTrue("page $index is copied out of the file, bytes and all", tracked.openCount(name) > 0)
                }
            }
            assertEquals("the manifest is the one part the diff has to read", 1, tracked.inflateCount(MobixFormat.MANIFEST_ENTRY))
            assertEquals("the canvas and the nine copied pages stream", 10, stats.streamedEntries)
            assertEquals(listOf(0), opened.pages.opened)
            assertEquals(edited.pages.toList(), openDocument(output.toByteArray()).pages)
        }
    }

    // ---- when it must not apply --------------------------------------------------------------------

    @Test
    fun aDocumentWithPagesOfItsOwnReusesNothing() {
        val file = temp.newFile("notebook.mobix")
        SyntheticNotebook.write(file, SyntheticNotebook.pages(3))

        val inMemory = Document(pages = SyntheticNotebook.pages(3), documentId = "doc_memory")

        assertEquals("in-memory pages are not a source's own", MobixPageReuse.none, reuseOf(file, inMemory))
        assertNull("and there is no written set to narrow identity by", reuseOf(file, inMemory).written)
    }

    @Test
    fun aDocumentWhosePageListWasReplacedReusesNothing() {
        val file = temp.newFile("notebook.mobix")
        val opened = notebook(file)
        opened.use {
            // What a page insert or delete does: the list stops being the reader's own view.
            val materialised = opened.document.copy(pages = opened.document.pages.toMutableList())

            assertEquals(MobixPageReuse.none, reuseOf(file, materialised))
        }
    }

    @Test
    fun aPageWhoseEntryIsNotWhereItsIndexSaysIsRebuilt() {
        val file = temp.newFile("hand-named.mobix")
        // An archive that names its pages its own way: the bytes are there, but not at the name this
        // index writes, so the page is rewritten rather than copied to another name.
        val entries = mutableListOf<MobixContainer.Entry>()
        val parts = mutableListOf<MobixPart>()
        SyntheticNotebook.pages(3).forEachIndexed { index, page ->
            val name = "pages/sheet-${index + 1}.json"
            val json = JsonWriter.write(MobixPageCodec.encode(page, MobixAssetSink())).toByteArray(Charsets.UTF_8)
            entries += MobixContainer.Entry(name, json)
            parts += MobixPart(
                id = page.id ?: SyntheticNotebook.idOf(index),
                entry = name,
                sha256 = MobixAssetSink.sha256(json),
                modified = now,
            )
        }
        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_odd_names",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = now,
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        ).copy(pages = parts)
        FileOutputStream(file).use { out ->
            MobixContainer.save(
                listOf(
                    MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.encode().toByteArray(Charsets.UTF_8)),
                    MobixContainer.Entry(
                        MobixFormat.CANVAS_ENTRY,
                        MobixCanvasFile(CanvasMode.PAGINATED, SyntheticNotebook.WIDTH_PT, SyntheticNotebook.HEIGHT_PT)
                            .encode().toByteArray(Charsets.UTF_8),
                    ),
                ) + entries,
                out,
            )
        }

        val reader = MobixPagedReader(MobixEntrySource.fromFile(file))
        reader.use {
            val pages = CountingPages(PageSource.of(reader))
            val document = MobixDocument.openPaged(reader, pages).toDocument()

            assertEquals("a page the index does not name is not reused", MobixPageReuse.none, reuseOf(file, document))

            // The save still writes the whole document — today's behaviour, unchanged.
            val saved = save(file, document, MobixPageReuse.none)
            assertEquals(3, saved.plan.order.count { MobixFormat.isPageEntry(it) })
            assertEquals("every page is built again", 3, saved.plan.entries.count { MobixFormat.isPageEntry(it.name) })
        }
    }

    @Test
    fun anEntryTheSourceNoLongerHoldsIsRebuilt() {
        val file = temp.newFile("notebook.mobix")
        val opened = notebook(file)
        opened.use {
            // A source whose central directory no longer lists a page: the plan must not claim it.
            val missing = SyntheticNotebook.entryOf(6)
            val source = MobixEntrySource.fromFile(file).use { from ->
                MobixEntrySource.fromMap(
                    from.entryNames.filterNot { it == missing }.associateWith { name -> from.readBytes(name)!! },
                )
            }

            val plan = MobixPageReuse.of(opened.document.pages, source)

            assertEquals("the entry that is gone is written again", 9, plan.reused.size)
            assertTrue("and its page is not among the reused ones", missing !in plan.reused.values.map { it.entry })
        }
    }

    // ---- identity -----------------------------------------------------------------------------------

    @Test
    fun adoptingTheIdentityOfThePagesASaveWritesOpensNoOtherPage() {
        val file = temp.newFile("notebook.mobix")
        val opened = notebook(file, pageCount = 200)
        opened.use {
            val edited = opened.document.editPage(150)
            val reuse = reuseOf(file, edited)
            assertEquals("only the edited page is written", setOf(150), reuse.written)

            opened.pages.opened.clear()
            val identified = reuse.identified(edited)

            // The page this save writes is in the model already (the page list overlays it), so
            // identifying what the save writes costs the archive nothing — and every page it copies is
            // left alone. What the editor did before this exists is the assertion below it.
            assertEquals("no page is opened to identify the pages this save writes", emptyList<Int>(), opened.pages.opened)
            assertEquals("the document keeps the id it was opened with", edited.documentId, identified.documentId)
            assertTrue(
                "the page this save writes carries its ids",
                identified.pages[150].layers.flatMap { it.elements }.all { ElementId.idOf(it) != null },
            )

            // The whole-document form, which is what [DrawingSurfaceView.adoptIdentity] used to call
            // before every `.mobix` save: it opens the notebook to find nothing to do.
            opened.pages.opened.clear()
            opened.document.withIdentity()
            assertEquals(
                "the whole-document adoption opens every page — the cost this removes",
                (0 until 200).toList(),
                opened.pages.opened,
            )
        }
    }

    @Test
    fun theIdentityASaveWritesIsTheIdentityTheModelKeeps() {
        val file = temp.newFile("notebook.mobix")
        val opened = notebook(file, pageCount = 3)
        opened.use {
            // The document's own id is the case the editor's adoption still has to do, whatever the
            // pages look like (a page read out of an archive carries ids — its decoder mints them); and
            // it is minted into the *manifest*, so a model that did not adopt it would hand out a fresh
            // one on the next save (`format/model/DocumentIdentity.kt`).
            val edited = opened.document.editPage(1).copy(documentId = null)
            val reuse = reuseOf(file, edited)
            assertEquals(setOf(1), reuse.written)

            opened.pages.opened.clear()
            val identified = reuse.identified(edited)

            assertNotNull("the adoption mints the document's own id", identified.documentId)
            assertEquals(
                "and it does so without opening a page — the pages it copies are never asked for one",
                emptyList<Int>(),
                opened.pages.opened,
            )

            val saved = save(file, identified, reuse)
            assertEquals(
                "the file holds the id the model holds",
                identified.documentId,
                openDocument(saved.bytes).documentId,
            )
        }
    }

    @Test
    fun identityIsAdoptedOnThePagesASaveWrites() {
        val file = temp.newFile("notebook.mobix")
        val opened = notebook(file)
        opened.use {
            // A file whose pages carry no element ids — another writer's, or a `.xopp` import — with the
            // plan saying only page 2 will be built.
            val unidentified = opened.document.copy(
                documentId = null,
                pages = opened.document.pages
                    .map { page -> page.copy(layers = page.layers.map { layer -> layer.copy(elements = layer.elements.map { e -> e.withoutId() }) }) }
                    .toMutableList(),
            )

            val identified = unidentified.withIdentityOn(setOf(2))

            assertNotNull("the document gets an id of its own", identified.documentId)
            assertTrue(
                "the page that will be written is identified",
                identified.pages[2].layers.flatMap { it.elements }.all { ElementId.idOf(it) != null },
            )
            assertTrue(
                "and the pages a save copies are left exactly as they were",
                identified.pages[0].layers.flatMap { it.elements }.all { ElementId.idOf(it) == null },
            )
            assertEquals("every page is still there", 10, identified.pages.size)
            assertSame(
                "a page nothing was written for is not even copied",
                unidentified.pages[9],
                identified.pages[9],
            )
        }
    }
}

/** The same element with no id of its own, for the identity tests above. */
private fun Element.withoutId(): Element = when (this) {
    is Stroke -> copy(id = null)
    is com.mobixournal.format.model.TextElement -> copy(id = null)
    is com.mobixournal.format.model.ImageElement -> copy(id = null)
    is com.mobixournal.format.model.TexImageElement -> copy(id = null)
    is com.mobixournal.format.model.TableElement -> copy(id = null)
    is com.mobixournal.format.model.PlotElement -> copy(id = null)
    is com.mobixournal.format.model.ParametricShapeElement -> copy(id = null)
    is com.mobixournal.format.model.CircuitElement -> copy(id = null)
    is com.mobixournal.format.model.RawElement -> this
}
