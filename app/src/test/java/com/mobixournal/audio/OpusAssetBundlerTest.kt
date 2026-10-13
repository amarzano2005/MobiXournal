package com.mobixournal.audio

import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpusAssetBundlerTest {

    @Test
    fun generatesDeterministicContentHashedOpusEntry() {
        val sampleBytes = "OpusAudioDummyData".toByteArray(Charsets.UTF_8)
        val assetName = OpusAssetBundler.assetNameFor(sampleBytes)

        assertTrue("starts with assets/aud_", assetName.startsWith("assets/aud_"))
        assertTrue("ends with .opus", assetName.endsWith(".opus"))
        assertTrue("is recognized as opus asset", OpusAssetBundler.isOpusAsset(assetName))

        val sink = MobixAssetSink()
        val bundledName = OpusAssetBundler.bundle(sampleBytes, sink)
        assertEquals(assetName, bundledName)
        assertEquals(1, sink.count)

        // Adding identical bytes is deduplicated
        val secondName = OpusAssetBundler.bundle(sampleBytes, sink)
        assertEquals(bundledName, secondName)
        assertEquals(1, sink.count)
    }

    @Test
    fun createsAudioRefWithAssetIdAndStartMs() {
        val ref = OpusAssetBundler.createRef("assets/aud_1234567890abcdef.opus", 2500)
        assertEquals("assets/aud_1234567890abcdef.opus", ref.filename)
        assertEquals(2500, ref.startMs)
    }

    @Test
    fun adoptsAndExtractsAudioInMobixDocument() {
        val doc = MobixDocument.of(
            Document(pages = listOf(Page(595.28, 841.89, com.mobixournal.format.model.Background.Solid(0xFFFFFFFF.toInt(), "plain"), emptyList()))),
            appVersion = "2.0.0",
            nowMillis = 1_700_000_000_000L,
        )
        val dummyBytes = byteArrayOf(0x4f, 0x70, 0x75, 0x73, 0x01, 0x02)
        val assetName = "aud_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef.opus"

        val docWithAudio = OpusAssetBundler.adoptAudio(doc, assetName, dummyBytes)
        val extracted = OpusAssetBundler.extractAudio(docWithAudio, assetName)

        assertNotNull(extracted)
        assertArrayEquals(dummyBytes, extracted)

        // Also extractable with assets/ prefix
        val extractedWithPrefix = OpusAssetBundler.extractAudio(docWithAudio, "assets/$assetName")
        assertNotNull(extractedWithPrefix)
        assertArrayEquals(dummyBytes, extractedWithPrefix)
    }

    @Test
    fun isOpusAssetValidatesHashLength() {
        val validHash = "a".repeat(64)
        assertTrue(OpusAssetBundler.isOpusAsset("assets/aud_$validHash.opus"))
        assertTrue(OpusAssetBundler.isOpusAsset("aud_$validHash.opus"))

        assertFalse(OpusAssetBundler.isOpusAsset("assets/img_$validHash.webp"))
        assertFalse(OpusAssetBundler.isOpusAsset("assets/aud_short.opus"))
        assertFalse(OpusAssetBundler.isOpusAsset("aud_invalid!chars.opus"))
    }

    @Test
    fun opusAudioRecorderFailsGracefullyOnHostWithoutHardwareOrPermissions() {
        val recorder = OpusAudioRecorder()
        assertFalse(recorder.isRecording)
        assertEquals(0, recorder.elapsedMs())

        val tempFile = File.createTempFile("test_audio", ".opus").apply { deleteOnExit() }
        val started = recorder.start(tempFile)
        // On JVM host unit tests without audio hardware, start() safely returns false
        if (!started) {
            assertFalse(recorder.isRecording)
            assertNull(recorder.stop())
        }
    }
}
