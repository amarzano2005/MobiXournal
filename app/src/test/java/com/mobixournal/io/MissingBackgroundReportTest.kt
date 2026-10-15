package com.mobixournal.io

import android.content.ContentResolver
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixManifest
import com.mobixournal.format.mobix.MobixPageCodec
import com.mobixournal.format.mobix.MobixPart
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.mobix.TrackedEntrySource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The background a page names and cannot have is **reported**, and only when its page is read.
 *
 * A `.mobix` opened page by page adopts a page's bundled picture or PDF as that page is read — which
 * can be a frame, a scroll or a session after the open — so the report cannot be made at the open
 * without walking every page, the very walk the paged open exists to avoid. `BundledPageAssets` knows
 * the moment the ask comes back empty; this pins that it records it there, drains it once, and leaves a
 * document whose references resolve up front (a `.xopp`) reporting exactly as it always did.
 */
class MissingBackgroundReportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val cacheDir by lazy { temp.newFolder("cache") }
    private val filesDir by lazy { temp.newFolder("files") }

    private class Counted {
        val counted = mutableListOf<TrackedEntrySource>()

        val open: (File) -> MobixEntrySource = { file ->
            TrackedEntrySource(MobixEntrySource.fromFile(file)).also { counted += it }
        }

        fun count(name: String): Int = counted.sumOf { it.openCount(name) }
    }

    private fun createIo(entrySources: (File) -> MobixEntrySource = MobixEntrySource::fromFile): DocumentIo =
        DocumentIo(
            resolver = object : ContentResolver(null) {},
            cacheDir = cacheDir,
            filesDir = filesDir,
            appVersion = "1.3.0",
            textPdf = null,
            entrySources = entrySources,
        )

    private val imageBytes = ByteArray(256) { (it % 31).toByte() }

    /**
     * A three-page `.mobix` where page 2 names a bundled picture the archive does **not** carry — the
     * shape a share or a copy can leave behind. Page 2's JSON is written with the reference in it and the
     * sink's entries are deliberately left out of the archive.
     */
    private fun writeNotebookMissingItsPicture(file: File): String {
        val sink = MobixAssetSink()
        val reference = sink.add(imageBytes, MobixFormat.AssetKind.IMAGE)

        val pages = SyntheticNotebook.pages(3).toMutableList()
        pages[1] = pages[1].copy(
            background = Background.Pixmap(
                domain = "absolute",
                filename = "content://picture.png",
                bundledAsset = reference,
            ),
        )

        val entries = mutableListOf<MobixContainer.Entry>()
        val parts = mutableListOf<MobixPart>()
        pages.forEachIndexed { index, page ->
            val name = SyntheticNotebook.entryOf(index)
            val json = JsonWriter.write(MobixPageCodec.encode(page, sink)).toByteArray(Charsets.UTF_8)
            entries += MobixContainer.Entry(name, json)
            parts += MobixPart(
                id = page.id ?: SyntheticNotebook.idOf(index),
                entry = name,
                sha256 = MobixAssetSink.sha256(json),
                modified = 1L,
            )
        }
        val manifest = MobixManifest.forNewDocument(
            documentId = "doc_missing_picture",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = 1L,
            title = "Missing picture",
            defaultPaper = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
        ).copy(pages = parts)
        FileOutputStream(file).use { out ->
            MobixContainer.save(
                listOf(
                    MobixContainer.Entry(MobixFormat.MANIFEST_ENTRY, manifest.encode().toByteArray(Charsets.UTF_8)),
                    MobixContainer.Entry(
                        MobixFormat.CANVAS_ENTRY,
                        MobixCanvasFile(CanvasMode.PAGINATED, 595.0, 842.0).encode().toByteArray(Charsets.UTF_8),
                    ),
                ) + entries,
                out,
            )
        }
        return reference
    }

    @Test
    fun aPageWhoseBundledPictureIsAbsentIsReportedOnceWhenItIsRead() {
        val file = temp.newFile("missing.mobix")
        val reference = writeNotebookMissingItsPicture(file)
        val counter = Counted()

        val loaded = createIo(counter.open).read(file) as LoadedFile.Doc
        val resolver = loaded.backgrounds
        assertNotNull("a paged .mobix carries its lazy resolver", resolver)

        assertEquals("the open reads no page", 0, counter.count(SyntheticNotebook.entryOf(1)))
        assertTrue("so nothing is reported before a page needs it", resolver!!.drainMissing().isEmpty())

        // Reading a page that names no bundled asset reports nothing …
        loaded.document.pages[0]
        assertTrue(resolver.drainMissing().isEmpty())

        // … and the page that names the absent one reports it, with the entry the page itself names.
        val background = loaded.document.pages[1].background as Background.Pixmap
        assertNull("nothing can be adopted or resolved", resolver.file(background))
        assertEquals(listOf(reference), resolver.drainMissing())
        assertTrue("a drained report does not come back", resolver.drainMissing().isEmpty())

        // Asking again — the renderer draws the same page every frame — must not report it twice.
        assertNull(resolver.file(background))
        assertTrue("one missing asset is one message, not one per frame", resolver.drainMissing().isEmpty())
    }

    @Test
    fun aDocumentThatResolvesItsBackgroundsAtOpenKeepsReportingThereAndDrainsNothing() {
        // A gzip `.xopp` whose picture is not on disk: the reference is unresolvable, and the open says
        // so the way it always has — the lazy report is not in play, because there is no paged open.
        val file = temp.newFile("linked.xopp")
        val document = Document(
            pages = SyntheticNotebook.pages(2).take(1).map { page ->
                page.copy(
                    background = Background.Pixmap(
                        domain = "absolute",
                        filename = "/nonexistent/definitely-not-here.png",
                    ),
                )
            },
        )
        FileOutputStream(file).use { Xopp.save(document, it) }

        val loaded = createIo().read(file) as LoadedFile.Doc

        assertEquals(SaveFormat.ORIGINAL, loaded.format)
        assertTrue("the unresolvable picture is reported where it always was", loaded.missingImage)
        assertNull("and there is no lazy resolver for it to be reported through", loaded.backgrounds)
        assertFalse(
            "a .xopp never claims its background PDF is missing when it never had one",
            loaded.missingPdf,
        )
    }
}
