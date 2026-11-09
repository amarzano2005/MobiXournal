package com.mobixournal.desktop

import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.desktop.io.*
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.SyntheticNotebook
import com.mobixournal.format.model.*
import com.mobixournal.io.DocumentEncoder
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopAudioIsolationTest {
    @get:Rule val temp = TemporaryFolder()
    private val name = "recording.wav"
    private fun model(): Document = Document(pages = listOf(
        Page(100.0, 100.0, Background.Solid(-1, "plain"), listOf(Layer(listOf(
            SyntheticNotebook.stroke(1.0).withAudio(AudioRef(name, 0)),
        )))),
    ))

    private fun archive(label: String, bytes: ByteArray): File {
        val audio = temp.newFile("$label.wav").also { it.writeBytes(bytes) }
        return temp.newFile("$label.mobix").also { file ->
            file.outputStream().use { out ->
                DocumentEncoder.write(model(), SaveFormat.MOBIX, null, out, "1.0.0", audioFiles = { audio })
            }
        }
    }

    @Test fun rehomingThenReopeningTheOldPathCannotChangeTheMovedTabsAudio() {
        val original = archive("original", byteArrayOf(1, 2, 3, 4))
        val state = com.mobixournal.desktop.shell.DesktopShellState()
        val moved = state.tabs.open(DocumentLoader.load(original))
        DesktopAudioSession.adopt(moved.document)
        DesktopAudioSession.noteAudioPresence(moved)
        val copy = temp.newFile("copy.mobix")
        assertNotNull(state.saveAs(copy, SaveFormat.MOBIX))
        assertTrue(DesktopAudioSession.of(moved).hasAudio)
        archive("changed", byteArrayOf(9, 9, 9, 9)).copyTo(original, overwrite = true)
        DocumentLoader.load(original).use { reopened ->
            DesktopAudioSession.adopt(reopened)
            assertArrayEquals(byteArrayOf(9, 9, 9, 9), reopened.audioStore.file(name)!!.readBytes())
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), moved.document.audioStore.file(name)!!.readBytes())
        }
        moved.document.close()
    }

    @Test fun sameNameRecordingsStayWithTheirOwnDocumentsAndSaves() {
        for (length in listOf(4, 5)) {
            val firstBytes = byteArrayOf(1, 2, 3, 4)
            val secondBytes = ByteArray(length) { 9 }
            DocumentLoader.load(archive("a$length", firstBytes)).use { first ->
                DocumentLoader.load(archive("b$length", secondBytes)).use { second ->
                    DesktopAudioSession.adopt(first)
                    DesktopAudioSession.adopt(second)
                    assertNotEquals(first.audioStore.folder, second.audioStore.folder)
                    assertArrayEquals(firstBytes, first.audioStore.file(name)!!.readBytes())
                    assertArrayEquals(secondBytes, second.audioStore.file(name)!!.readBytes())
                    val copy = temp.newFile("copy$length.mobix")
                    DesktopSaves.save(first, copy)
                    DocumentLoader.load(copy).use { saved ->
                        val store = DesktopAudioStore(temp.newFolder("check$length"))
                        store.adoptBundled(copy, saved.model)
                        assertArrayEquals(firstBytes, store.file(name)!!.readBytes())
                    }
                }
            }
        }
    }

    @Test fun sidecarWithChangedBytesAndSameLengthIsNotMistakenForTheOldOne() {
        val source = temp.newFolder("sidecars")
        val file = File(source, name)
        val store = DesktopAudioStore(temp.newFolder("store"))
        file.writeBytes(byteArrayOf(1, 2))
        assertEquals(1, store.importSidecars(source, model()))
        file.writeBytes(byteArrayOf(3, 4))
        assertEquals(1, store.importSidecars(source, model()))
        assertArrayEquals(byteArrayOf(3, 4), store.file(name)!!.readBytes())
    }
}
