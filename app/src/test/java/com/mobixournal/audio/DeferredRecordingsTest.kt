package com.mobixournal.audio

import com.mobixournal.format.mobix.MobixPayload
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream

class DeferredRecordingsTest {
    @Test fun missingFirstAssetDoesNotHideALaterPlayableRecording() {
        val refs = AudioReferences(listOf(IndexedAudio(0, AudioRef("bad.wav", 500), "bad"),
            IndexedAudio(1, AudioRef("good.wav", 700), "good")))
        val good = temp.newFile("good.wav")
        assertEquals(AudioRef("good.wav", 0), refs.playableCandidate(0, {
            if (it == "bad.wav") throw java.io.IOException("missing entry") else good
        }))
    }
    @get:Rule val temp = TemporaryFolder()
    @Test fun registrationReadsNoAudioAndResolvingTwiceCopiesItOnce() {
        var reads = 0
        val store = DeferredRecordings({ File(temp.root, it) }, { name, input -> adoptAudioFile(File(temp.root, name), input) })
        val payload = MobixPayload.ofSource(open = { reads++; "OggS-data".byteInputStream() })
        store.register("a.ogg", "first", payload)
        assertTrue(store.known("a.ogg")); assertEquals(0, reads)
        assertEquals("OggS-data", store.resolve("a.ogg")!!.readText())
        store.register("a.ogg", "first", payload)
        store.resolve("a.ogg"); assertEquals(1, reads)
        store.register("a.ogg", "replacement", MobixPayload.of("replacement".toByteArray()))
        assertEquals("replacement", store.resolve("a.ogg")!!.readText())
    }

    @Test fun aFailedCopyPreservesThePreviousFileAndCanBeRetried() {
        val file = temp.newFile("a.wav").apply { writeText("old") }
        val broken = object : InputStream() { override fun read(): Int = throw java.io.IOException("interrupted") }
        try { adoptAudioFile(file, broken); fail() } catch (_: java.io.IOException) { }
        assertEquals("old", file.readText())
        assertEquals(listOf("a.wav"), temp.root.list()!!.toList())
        var attempts = 0
        val store = DeferredRecordings({ file }, { _, input -> adoptAudioFile(file, input) })
        store.register("a.wav", "asset", MobixPayload.ofSource(open = { if (++attempts == 1) broken else "new".byteInputStream() }))
        try { store.resolve("a.wav"); fail() } catch (_: java.io.IOException) { }
        assertEquals("new", store.resolve("a.wav")!!.readText())
    }
}
