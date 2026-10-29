package com.mobixournal.desktop

import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.format.model.audioRef
import com.mobixournal.format.SaveFormat
import com.mobixournal.io.DocumentEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A recording survives a PC edit — the bug that started this work: opening a `.mobix` in the window and
 * saving it rewrote the archive without the `assets/` entry its strokes named, and the tablet had no audio
 * left to play.
 *
 * The proof is end to end on purpose: the archive is written by the real encoder, the window's own
 * load/save path moves it, and a **fresh** store adopts what the result carries. Nothing here asserts on
 * internal names — only that the bytes a stroke's `fn` asks for are still there after the round trip.
 */
class DesktopAudioStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val recordingName = "2026-08-02T09-14-33.wav"
    private val bytes = ByteArray(2_048) { (it % 251).toByte() }

    private fun strokeAt(ms: Int) = Stroke(
        tool = Tool.PEN,
        color = 0xFF000000.toInt(),
        capStyle = "round",
        points = listOf(StrokePoint(20.0, 20.0, 2.0), StrokePoint(120.0, 60.0, 2.0)),
        uniformWidth = true,
    ).withAudio(AudioRef(recordingName, ms))

    private fun document(vararg strokes: Stroke) = Document(
        pages = listOf(
            Page(
                width = 595.0,
                height = 842.0,
                background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                layers = listOf(Layer(strokes.toList())),
            ),
        ),
    )

    /** The store's folder, with the recording in it — what a recording made on this machine looks like. */
    private fun stockedStore(folder: String = "audio"): DesktopAudioStore {
        val store = DesktopAudioStore(temp.newFolder(folder))
        store.target(recordingName).writeBytes(bytes)
        return store
    }

    @Test
    fun `editing a mobix keeps its bundled recording`() {
        val store = stockedStore()
        val archive = File(temp.root, "lecture.mobix")
        DocumentEncoder.write(
            document = document(strokeAt(1_200)),
            format = SaveFormat.MOBIX,
            target = null,
            out = archive.outputStream(),
            appVersion = "2.0.0",
            audioFiles = store::file,
        )

        // The window opens it: the recording is adopted into a store that had never seen it.
        val opened = DocumentLoader.load(archive)
        val firstStore = DesktopAudioStore(temp.newFolder("pc-audio"))
        assertEquals(1, firstStore.adoptBundled(archive, opened.model))
        assertArrayEquals("the archive's bytes are in the store", bytes, firstStore.file(recordingName)!!.readBytes())

        // …and saves it as a copy, which is the step that used to drop the audio.
        val copy = File(temp.root, "lecture-edited.mobix")
        DesktopSaves.stage(opened, copy, audio = firstStore).use { it.commit() }
        opened.close()

        val reopened = DocumentLoader.load(copy)
        val secondStore = DesktopAudioStore(temp.newFolder("pc-audio-2"))
        assertEquals(1, secondStore.adoptBundled(copy, reopened.model))
        assertArrayEquals(
            "the recording is still in the document after the PC edit",
            bytes,
            secondStore.file(recordingName)!!.readBytes(),
        )
        assertTrue("and no stroke lost its link", reopened.model.pages.single().layers.single().elements
            .filterIsInstance<Stroke>()
            .single()
            .let { it.audioRef() == AudioRef(recordingName, 1_200) })
        reopened.close()
    }

    @Test
    fun `saving a xopp pushes its sidecars beside it and opening brings them back`() {
        val sourceFolder = temp.newFolder("source")
        val source = File(sourceFolder, "notes.xopp")
        com.mobixournal.format.Xopp.save(document(strokeAt(900)), source.outputStream())
        // The `.xopp` convention: the recording sits beside the document, named by the stroke's `fn`.
        File(sourceFolder, recordingName).writeBytes(bytes)

        val opened = DocumentLoader.load(source)
        val local = DesktopAudioStore(temp.newFolder("fresh-audio"))
        assertEquals("the sidecar beside the file is adopted", 1, local.importSidecars(source.parentFile, opened.model))

        val elsewhere = File(temp.newFolder("saved"), "notes.xopp")
        DesktopSaves.stage(opened, elsewhere, audio = local).use { it.commit() }
        opened.close()

        assertTrue("the recording sits beside the saved document", File(elsewhere.parentFile, recordingName).isFile)
        assertArrayEquals(bytes, File(elsewhere.parentFile, recordingName).readBytes())
    }

    @Test
    fun `a document whose recording is gone opens with a sentence naming it`() {
        val empty = DesktopAudioStore(temp.newFolder("empty"))
        val document = document(strokeAt(0))

        assertEquals(listOf(recordingName), empty.missing(document))
        assertNull("nothing is invented", empty.file(recordingName))
    }

    @Test
    fun `an entry cannot escape the store`() {
        assertNull(DesktopAudioStore.safeName("../escape.wav"))
        assertNull(DesktopAudioStore.safeName("folder/take.wav"))
        assertNotNull(DesktopAudioStore.safeName("take.wav"))
        assertEquals("take.wav", DesktopAudioStore.bareName("../folder/take.wav"))
    }
}
