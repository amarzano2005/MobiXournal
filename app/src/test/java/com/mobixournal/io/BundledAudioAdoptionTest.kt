package com.mobixournal.io

import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPayload
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
 * names it is read (`docs/audio.md`): the file lands where a recording made here lands, with
 * the `.ogg` name the stroke's `fn` says, so playback of that stroke needs nothing else and a `.xopp`
 * export pushes out the file that is really there.
 */
class BundledAudioAdoptionTest {
    @Test fun `inserted and removed page views still use independent readers`() {
        val file = bundledFile()
        val pair = com.mobixournal.format.mobix.MobixPagedReader.open(file).use { it.parts.toList() to it.getPage(0) }
        var readable = true
        val source = object : PageSource {
            override val pageCount = 1
            override fun pageId(index: Int) = pair.first[index].id
            override fun sourceParts() = pair.first
            override fun page(index: Int): com.mobixournal.format.model.Page {
                check(readable) { "closed canvas reader" }
                return pair.second
            }
        }
        val inserted = com.mobixournal.render.PageOps.addBefore(source.asPageList(), 0, 100.0, 100.0)
        val removed = com.mobixournal.render.PageOps.removeAt(inserted, 0)
        readable = false
        val before = com.mobixournal.audio.readAudioPreview(Document(pages = inserted), file,
            com.mobixournal.audio.AudioReferenceIndex(), { _, _, _ -> })
        assertEquals(1, before.firstPage(sidecar))
        val after = com.mobixournal.audio.readAudioPreview(Document(pages = removed), file,
            com.mobixournal.audio.AudioReferenceIndex(), { _, _, _ -> })
        assertEquals(0, after.firstPage(sidecar))
    }

    @Test fun `page registration does not inflate audio and its payload survives reader closure`() {
        val file = bundledFile()
        val pages = MobixEntrySource.fromFile(file).use { MobixDocument.open(it).toDocument().pages }
        var opened = 0
        var pending: MobixPayload? = null
        MobixEntrySource.fromFile(file).use { entries ->
            val spy = object : MobixEntrySource by entries {
                override fun openEntry(name: String): java.io.InputStream? {
                    if (MobixFormat.isAssetEntry(name, MobixFormat.AssetKind.AUDIO)) opened++
                    return entries.openEntry(name)
                }
            }
            val assets = BundledPageAssets(PageSource.of(pages), spy,
                { error("no PDF") }, { error("no picture") }, { null },
                object : BundledAudio {
                    override fun adopt(reference: String, source: java.io.InputStream) = error("UI must not copy audio")
                    override fun register(reference: String, token: String, payload: MobixPayload) { pending = payload }
                }, audioArchive = file)
            assets.page(0)
        }
        assertEquals(0, opened)
        assertEquals(recording.toList(), pending!!.open().use { it.readBytes() }.toList())
    }

    @Test fun `background audio indexing never asks the canvas reader for a page`() {
        val file = bundledFile()
        val parts = com.mobixournal.format.mobix.MobixPagedReader.open(file).use { it.parts.toList() }
        val source = object : PageSource {
            override val pageCount = parts.size
            override fun pageId(index: Int) = parts[index].id
            override fun sourceParts() = parts
            override fun page(index: Int): com.mobixournal.format.model.Page = error("canvas reader must stay idle")
        }
        val doc = Document(pages = source.asPageList())
        val index = com.mobixournal.audio.AudioReferenceIndex()
        val refs = com.mobixournal.audio.readAudioPreview(doc, file, index, { _, _, _ -> })
        assertEquals(sidecar, refs.candidate(0) { true }!!.filename)
        assertEquals(0, refs.firstPage(sidecar))
        assertTrue("the worker closed both ZIP handles", file.renameTo(File(temp.root, "moved.mobix")))
    }

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
            .toBytes(nowMillis = 1L, audio = { name -> recording.takeIf { name == sidecar }?.let(MobixPayload::of) })
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
                storeAudio = BundledAudio { reference, source -> adopted[reference] = source.readBytes() },
            )
            assets.page(0)
        }

        assertEquals("adopted under the name the stroke replays from", setOf(sidecar), adopted.keys)
        assertEquals("and it is the compressed stream the archive held", recording.toList(), adopted[sidecar]!!.toList())
    }
}
