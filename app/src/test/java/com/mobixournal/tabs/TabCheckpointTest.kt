package com.mobixournal.tabs

import com.mobixournal.format.model.Document
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TabCheckpointTest {
    @get:Rule val temp = TemporaryFolder()
    private class Counted : TabSnapshots by XoppSnapshots {
        var writes = 0
        var fail = false
        var reusable = true
        var token = 0
        override fun reuseToken(tab: OpenTab): Any = token
        override fun canReuse(tab: OpenTab): Boolean = reusable
        override fun write(file: File, tab: OpenTab, sources: SnapshotSources) {
            writes++
            if (fail) error("controlled write failure")
            XoppSnapshots.write(file, tab, sources)
        }
    }
    private fun session(document: Document = Document(), title: String = "first") =
        TabSession(listOf(OpenTab("a", title, document)), 0)

    @Test fun metadataOnlyUpdatesTheIndexWithoutEncodingTheNotebookAgain() {
        val dir = temp.newFolder(); val codec = Counted(); val store = TabStore(dir, codec)
        val first = session()
        assertTrue(store.save(first))
        assertTrue(store.save(first.copy(tabs = listOf(first.tabs[0].copy(title = "renamed", page = 12)))))
        assertEquals(1, codec.writes)
        assertEquals("renamed", store.load()!!.tabs[0].title)
        assertEquals(12, store.load()!!.tabs[0].page)
    }

    @Test fun missingSnapshotAndChangedSourcesForceANewWrite() {
        val dir = temp.newFolder(); val codec = Counted(); val store = TabStore(dir, codec)
        val picture = temp.newFile().also { it.writeBytes(byteArrayOf(1)) }
        val state = session(); val sources = SnapshotSources(images = mapOf("picture" to picture))
        assertTrue(store.save(state, sources)); assertTrue(store.save(state, sources))
        assertEquals(1, codec.writes)
        picture.appendBytes(byteArrayOf(2))
        assertTrue(store.save(state, sources)); assertEquals(2, codec.writes)
        assertTrue(File(dir, "a.xopp").delete())
        assertTrue(store.save(state, sources)); assertEquals(3, codec.writes)
    }

    @Test fun failedWritesRetryAndMutableAudioNeverReusesAnOldCheckpoint() {
        val codec = Counted(); val store = TabStore(temp.newFolder(), codec); val state = session()
        codec.fail = true
        assertFalse(store.save(state))
        codec.fail = false
        assertTrue(store.save(state)); assertEquals(2, codec.writes)
        codec.reusable = false
        assertTrue(store.save(state)); assertTrue(store.save(state))
        codec.reusable = true
        assertTrue(store.save(state)); assertTrue(store.save(state))
        assertEquals(5, codec.writes)
    }
    @Test fun finishedAudioInvalidatesEvenIfNoCheckpointRanWhileRecording() {
        val codec = Counted(); val store = TabStore(temp.newFolder(), codec); val state = session()
        assertTrue(store.save(state))
        codec.token++
        assertTrue(store.save(state))
        assertTrue(store.save(state))
        assertEquals(2, codec.writes)
    }
}
