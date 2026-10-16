package com.mobixournal.io

import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A recording captured Opus is adopted **under the sidecar name the stroke carries** when the page that
 * names it is read (`docs/audio-inking-sync.md`): the file lands where a recording made here lands, with
 * the `.ogg` name the stroke's `fn` says, so playback of that stroke needs nothing else and a `.xopp`
 * export pushes out the file that is really there.
 */
class BundledAudioAdoptionTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** An Ogg/Opus stream, the container the app's own `.mobix` recordings arrive in. */
    private val recording = "OggS".toByteArray(Charsets.US_ASCII) +
        ByteArray(64) { (it % 17 + 1).toByte() }

    private val sidecar = "2026-08-02T09-14-33.ogg"

    /** A one-page notebook holding one stroke that replays from the compressed recording. */
    private fun notebook(): Document {
        val pages = SyntheticNotebook.pages(1).toMutableList()
        pages[0] = pages[0].copy(
            layers = listOf(
                Layer(
                    listOf(
                        Stroke(
                            tool = Tool.PEN,
                            color = 0xFF000000.toInt(),
                            capStyle = "round",
                            points = listOf(StrokePoint(10.0, 10.0, 1.0), StrokePoint(20.0, 20.0, 1.0)),
                            uniformWidth = true,
                        ).withAudio(AudioRef(sidecar, 0)),
                    ),
                ),
            ),
        )
        return Document(pages = pages, title = "Recorded", documentId = "doc_opus")
    }

    /** The notebook as it is on disk, with its Opus recording bundled into it. */
    private fun bundledFile(): File {
        val bytes = MobixDocument.of(notebook(), appVersion = "1.3.0", nowMillis = 1L)
            .toBytes(nowMillis = 1L, audioBytes = { name -> recording.takeIf { name == sidecar } })
        return temp.newFile("recorded.mobix").apply { writeBytes(bytes) }
    }

    @Test
    fun `the archive carries the ogg stream under its own container name`() {
        val file = bundledFile()

        val name = MobixEntrySource.fromFile(file).use { entries ->
            entries.entryNames.single { it.startsWith(MobixFormat.ASSETS_DIR) }
        }

        assertEquals("the container the bytes are in is what names them", "ogg", MobixAssetSink.suffixFor(recording))
        assertTrue("the entry says so too: $name", name.endsWith(".ogg"))
        assertTrue(MobixFormat.isAssetEntry(name, MobixFormat.AssetKind.AUDIO))
    }

    @Test
    fun `reading the page adopts the recording under the sidecar name it carries`() {
        val file = bundledFile()
        val pages = MobixEntrySource.fromFile(file).use { MobixDocument.open(it).toDocument().pages }
        val adopted = LinkedHashMap<String, ByteArray>()

        MobixEntrySource.fromFile(file).use { entries ->
            val assets = BundledPageAssets(
                delegate = PageSource.of(pages),
                entries = entries,
                storePdf = { bytes -> temp.newFile().also { it.writeBytes(bytes) } },
                storeImage = { bytes -> temp.newFile().also { it.writeBytes(bytes) } },
                resolveLinked = { null },
                storeAudio = BundledAudio { reference, bytes -> adopted[reference] = bytes },
            )
            assets.page(0)
        }

        assertEquals("adopted under the name the stroke replays from", setOf(sidecar), adopted.keys)
        assertEquals("and it is the compressed stream the archive held", recording.toList(), adopted[sidecar]!!.toList())
    }
}
