package com.mobixournal.format.mobix

import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.audioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A recording travels **inside** the `.mobix`, which is the one promise a native container makes: move
 * the file to another folder, or hand it to another device, and the strokes still replay
 * (`docs/audio-inking-sync.md`).
 *
 * Two names have to stay apart for that to work and for the `.xopp` export to keep working: `filename`
 * is the sidecar the stroke replayed from and `assetId` is the entry the archive carries the bytes in.
 * The recording is addressed by the hash of its own bytes, so forty strokes over one recording cost one
 * entry — the same rule the pictures follow.
 */
class MobixAudioBundleTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** A recording in the container the app's own recorder writes: 16-bit mono RIFF/WAVE. */
    private val recording = "RIFF".toByteArray(Charsets.US_ASCII) +
        ByteArray(4) { 0 } +
        "WAVEfmt ".toByteArray(Charsets.US_ASCII) +
        ByteArray(64) { (it % 31).toByte() }

    private val sidecar = "2026-08-02T09-14-33.wav"

    private fun strokeAt(x: Double, startMs: Int) = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(x, 10.0, 1.0), StrokePoint(x + 8.0, 18.0, 1.0)),
        uniformWidth = true,
    ).withAudio(AudioRef(sidecar, startMs))

    /** A two-page notebook whose page 2 carries two strokes that replay from the same recording. */
    private fun notebook(): Document {
        val pages = SyntheticNotebook.pages(2).toMutableList()
        pages[1] = pages[1].copy(layers = listOf(Layer(listOf(strokeAt(10.0, 0), strokeAt(40.0, 4200)))))
        return Document(pages = pages, title = "Recorded", documentId = "doc_audio")
    }

    private fun Document.toStrokes(): List<Stroke> =
        pages.flatMap { it.layers.flatMap(Layer::elements) }.filterIsInstance<Stroke>()

    /** The notebook as it would be on disk, with the recording bundled into it. */
    private fun bundledBytes(): ByteArray = MobixDocument.of(notebook(), appVersion = "1.3.0", nowMillis = 1L)
        .toBytes(nowMillis = 1L, audioBytes = { recording })

    private fun openBytes(bytes: ByteArray): MobixDocument =
        MobixDocument.open(MobixContainer.open(ByteArrayInputStream(bytes)))

    private fun pageJsonOf(entries: List<MobixContainer.Entry>, index: Int): JsonNode.Obj =
        JsonParser.parse(
            entries.single { it.name == MobixFormat.pageEntry(index + 1) }.bytes.toString(Charsets.UTF_8),
        ) as JsonNode.Obj

    private fun audioRefOf(entries: List<MobixContainer.Entry>, index: Int): JsonNode.Obj {
        val page = pageJsonOf(entries, index)
        val layers = page.getArr("layers")!!
        val elements = (layers.list.single() as JsonNode.Obj).getArr("elements")!!
        return (elements.list.first() as JsonNode.Obj).getObj("audioRef")!!
    }

    @Test
    fun oneRecordingBehindManyStrokesIsOneEntry() {
        val entries = MobixDocument.of(notebook(), appVersion = "1.3.0", nowMillis = 1L)
            .encode(nowMillis = 1L, audioBytes = { name -> recording.takeIf { name == sidecar } })

        val bundled = entries.filter { it.name.startsWith(MobixFormat.ASSETS_DIR) }
        assertEquals("one recording, however many strokes replay from it", 1, bundled.size)
        // The bytes are WAV and the name says so — an asset is stored in the container it arrived in.
        assertEquals("wav", MobixAssetSink.suffixFor(recording))
        assertEquals(
            MobixFormat.assetEntry(MobixAssetSink.sha256(recording), MobixFormat.AssetKind.AUDIO, "wav"),
            bundled.single().name,
        )
        assertEquals(recording.toList(), bundled.single().bytes.toList())

        val ref = audioRefOf(entries, 1)
        assertEquals("the entry the archive carries the bytes in", bundled.single().name, ref.getStrOrNull("assetId"))
        assertEquals("and the sidecar the stroke replayed from", sidecar, ref.getStrOrNull("filename"))
    }

    @Test
    fun aRecordingTheStoresDoNotHoldStillNamesItsSidecar() {
        val entries = MobixDocument.of(notebook(), appVersion = "1.3.0", nowMillis = 1L)
            .encode(nowMillis = 1L)

        assertTrue(
            "nothing was bundled, so the archive carries no asset",
            entries.none { it.name.startsWith(MobixFormat.ASSETS_DIR) },
        )
        val ref = audioRefOf(entries, 1)
        assertNull("and the stroke names no entry either", ref.getStrOrNull("assetId"))
        assertEquals(sidecar, ref.getStrOrNull("filename"))
    }

    @Test
    fun theOpenKeepsTheSidecarNameAndTheEntryApart() {
        val bytes = MobixDocument.of(notebook(), appVersion = "1.3.0", nowMillis = 1L)
            .toBytes(nowMillis = 1L, audioBytes = { recording })
        val entry = MobixFormat.assetEntry(
            MobixAssetSink.sha256(recording),
            MobixFormat.AssetKind.AUDIO,
            "wav",
        )

        val document = MobixDocument.open(MobixContainer.open(ByteArrayInputStream(bytes))).toDocument()

        val strokes = document.toStrokes().filter { it.audioRef() != null }
        assertEquals(2, strokes.size)
        for (stroke in strokes) {
            assertEquals("the sidecar the .xopp export has to write a file for", sidecar, stroke.audioRef()!!.filename)
            assertEquals("the entry the bytes live in", entry, stroke.bundledAudio)
            assertNull("the entry is the model's own field, never an extra attribute", stroke.extraAttrs[entry])
        }
        assertEquals(
            "the timing each stroke was drawn at survives",
            listOf(0, 4200),
            strokes.map { it.audioRef()!!.startMs },
        )
    }

    @Test
    fun anAlreadyBundledRecordingIsStillNamedWhenTheStoresHaveNoCopy() {
        // An open document whose local copy was swept: the model knows the entry, so a rewrite has to
        // keep naming it. The bytes themselves come from the archive the save repacks over (the test
        // below), which is why naming it is the whole job here.
        val opened = openBytes(bundledBytes()).toDocument()

        val entries = MobixDocument.of(opened, appVersion = "1.3.0", nowMillis = 2L).encode(nowMillis = 2L)

        val ref = audioRefOf(entries, 1)
        assertNotNull("the entry survives a save whose stores hold no bytes", ref.getStrOrNull("assetId"))
        assertEquals(sidecar, ref.getStrOrNull("filename"))
        assertEquals(
            "and nothing else was invented into the archive",
            0,
            entries.count { it.name.startsWith(MobixFormat.ASSETS_DIR) },
        )
    }

    @Test
    fun aSaveOverTheFileItCameFromKeepsTheRecording() {
        // The promise in full: the file is saved again, with no recording in any store, and the audio
        // is still in it — the entry is copied out of the archive the document was opened from.
        val file = temp.newFile("recorded.mobix")
        file.writeBytes(bundledBytes())
        val opened = MobixEntrySource.fromFile(file).use { MobixDocument.open(it) }
        val entry = MobixFormat.assetEntry(MobixAssetSink.sha256(recording), MobixFormat.AssetKind.AUDIO, "wav")

        val entries = MobixDocument.of(
            opened.toDocument(),
            appVersion = "1.3.0",
            nowMillis = 2L,
        ).encode(nowMillis = 2L)
        val out = ByteArrayOutputStream()
        MobixEntrySource.fromFile(file).use { from -> MobixIncrementalSave.repack(from, entries, out) }
        val saved = openBytes(out.toByteArray())

        assertEquals(
            "the recording is still the bytes that were recorded",
            recording.toList(),
            saved.asset(entry)?.toList(),
        )
    }

    @Test
    fun aPageWithoutAudioBundlesNothing() {
        val plain = Document(pages = SyntheticNotebook.pages(1), title = "Plain", documentId = "doc_plain")

        val entries = MobixDocument.of(plain, appVersion = "1.3.0", nowMillis = 1L).encode(nowMillis = 1L)

        assertTrue(entries.none { it.name.startsWith(MobixFormat.ASSETS_DIR) })
    }
}
