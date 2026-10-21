package com.mobixournal.io

import android.content.ContentResolver
import com.mobixournal.audio.AudioRef
import com.mobixournal.format.model.audioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.SaveFormat
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
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A `.mobix` save that has **no archive to repack over** still carries the payloads its pages name.
 *
 * A `.mobix` opened page by page adopts a page's bundled picture, PDF or recording *when that page is
 * read*, so a page nothing has drawn yet has no local copy and the app's stores answer nothing for it.
 * A save that lands back on the file it was opened from is safe — an unchanged entry is copied out of
 * that archive verbatim (`MobixIncrementalSave`) — but a Save As, or a first save of a document whose
 * staged copy is gone, builds every page from the model, and the model keeps the asset *names* it was
 * read with. Without [DocumentIo] handing the bytes back to the writer, the new file would name
 * `assets/img_<sha>.png` while carrying no such entry: the picture and the recording would be gone
 * from the only copy the user keeps.
 */
class DeferredPayloadSaveTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** One cache/files pair for the whole test: a second `newFolder` of the same name would throw. */
    private val cacheDir by lazy { temp.newFolder("cache") }
    private val filesDir by lazy { temp.newFolder("files") }

    private fun createIo(entrySources: (File) -> MobixEntrySource = MobixEntrySource::fromFile): DocumentIo =
        DocumentIo(
            resolver = object : ContentResolver(null) {},
            cacheDir = cacheDir,
            filesDir = filesDir,
            appVersion = "1.3.0",
            textPdf = null,
            entrySources = entrySources,
        )

    private val imageBytes = ByteArray(1024) { (it % 53).toByte() }
    private val recordingBytes = ByteArray(512) { (it % 41).toByte() }
    private val recordingName = "2026-08-02T09-14-33.wav"

    private class Fixture(val imageAsset: String, val audioAsset: String)

    /**
     * Three pages: page 2 carries a bundled picture, page 3 a stroke whose recording is bundled. The
     * archive holds both payloads under the names the pages reference.
     */
    private fun writeNotebook(file: File): Fixture {
        val sink = MobixAssetSink()
        val imageAsset = sink.add(imageBytes, MobixFormat.AssetKind.IMAGE)
        val audioAsset = sink.add(recordingBytes, MobixFormat.AssetKind.AUDIO, suffix = "wav")

        val pages = SyntheticNotebook.pages(3).toMutableList()
        pages[1] = pages[1].copy(
            background = Background.Pixmap(
                domain = "absolute",
                filename = "content://picture.png",
                bundledAsset = imageAsset,
            ),
        )
        pages[2] = pages[2].copy(
            layers = listOf(
                Layer(
                    listOf(
                        SyntheticNotebook.stroke(30.0, id = SyntheticNotebook.strokeIdOf(2))
                            .withAudio(AudioRef(recordingName, 2500))
                            .copy(bundledAudio = audioAsset),
                    ),
                ),
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
            documentId = "doc_deferred",
            canvasMode = CanvasMode.PAGINATED,
            nowMillis = 1L,
            title = "Deferred payloads",
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
                ) + entries + sink.entries(),
                out,
            )
        }
        return Fixture(imageAsset, audioAsset)
    }

    /** The entries an archive holds, by name, for the assertions below. */
    private fun entriesOf(file: File): Map<String, ByteArray> =
        file.inputStream().use { input ->
            MobixContainer.open(input.buffered()).let { archive ->
                archive.names.associateWith { archive.bytes(it) ?: ByteArray(0) }
            }
        }

    @Test
    fun aSaveWithNoSourceStillCarriesThePictureAndTheRecording() {
        val original = temp.newFile("notebook.mobix")
        val fixture = writeNotebook(original)

        // Open it as the editor does: page by page, so neither payload is adopted (no page is read).
        val io = createIo()
        val loaded = io.read(original) as LoadedFile.Doc
        val document = loaded.document
        assertEquals("no page was read, so nothing was adopted", emptyMap<String, File>(), loaded.images)
        assertNull("nor a background PDF", loaded.pdf)

        // …and save it with no target, so there is no source archive to repack over: the whole
        // archive is built from the model, which is the Save As path.
        val encoded = io.encode(document, pdf = null, format = SaveFormat.MOBIX, target = null)

        val written = entriesOf(encoded.file)
        assertTrue("the picture's entry must travel with the file it is named in", fixture.imageAsset in written)
        assertEquals("and be the picture's own bytes", imageBytes.toList(), written[fixture.imageAsset]!!.toList())
        assertTrue("so must the recording's entry", fixture.audioAsset in written)
        assertEquals("with the recording's own bytes", recordingBytes.toList(), written[fixture.audioAsset]!!.toList())

        // The reopened document still names them, so the pages are not merely carrying dead entries.
        val reopened = createIo().read(encoded.file) as LoadedFile.Doc
        val background = reopened.document.pages[1].background as Background.Pixmap
        assertEquals(fixture.imageAsset, background.bundledAsset)
        val stroke = reopened.document.pages[2].layers.single().elements.single() as Stroke
        assertEquals(fixture.audioAsset, stroke.bundledAudio)
        assertNotNull(stroke.audioRef())
    }

    @Test
    fun aSaveOfADocumentWithNothingBundledCarriesNothingExtra() {
        val plain = temp.newFile("plain.mobix")
        SyntheticNotebook.write(plain, SyntheticNotebook.pages(2))
        val io = createIo()
        val document = (io.read(plain) as LoadedFile.Doc).document

        val encoded = io.encode(document, pdf = null, format = SaveFormat.MOBIX, target = null)
        val written = entriesOf(encoded.file)

        assertTrue(
            "no assets/ entry is invented for a document that never had one",
            written.keys.none { it.startsWith(MobixFormat.ASSETS_DIR) },
        )
    }
}
