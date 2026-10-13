package com.mobixournal.audio

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavAudioSynthesizerTest {

    @Test
    fun opusToWavFilenameConvertsExtensionsCorrectly() {
        assertEquals("aud_abc123.wav", WavAudioSynthesizer.opusToWavFilename("assets/aud_abc123.opus"))
        assertEquals("aud_abc123.wav", WavAudioSynthesizer.opusToWavFilename("aud_abc123.opus"))
        assertEquals("recording.wav", WavAudioSynthesizer.opusToWavFilename("recording.wav"))
        assertEquals("audio.wav", WavAudioSynthesizer.opusToWavFilename("audio"))
    }

    @Test
    fun prepareDocumentForXoppExportRewritesStrokesToCompanionWav() {
        val strokeWithOpus = Stroke(
            tool = Tool.PEN,
            color = 0xFF000000.toInt(),
            capStyle = "round",
            points = listOf(StrokePoint(10.0, 10.0, 1.0, dtMs = 0)),
            uniformWidth = true,
            extraAttrs = mapOf("fn" to "assets/aud_lecture.opus", "ts" to "3200"),
        )
        val doc = Document(
            pages = listOf(
                Page(
                    width = 595.0,
                    height = 842.0,
                    background = Background.Solid(0xFFFFFFFF.toInt(), "plain"),
                    layers = listOf(Layer(elements = listOf(strokeWithOpus))),
                ),
            ),
        )

        val prepared = WavAudioSynthesizer.prepareDocumentForXoppExport(doc)
        val updatedStroke = prepared.pages[0].layers[0].elements[0] as Stroke
        val ref = updatedStroke.audioRef()

        assertEquals("aud_lecture.wav", ref?.filename)
        assertEquals(3200, ref?.startMs)
        assertEquals("aud_lecture.wav", updatedStroke.extraAttrs[ATTR_AUDIO_FILENAME])
        assertEquals("3200", updatedStroke.extraAttrs[ATTR_AUDIO_TIMESTAMP])
    }

    @Test
    fun synthesizeWavEmitsValidRiffWaveHeader() {
        val pcmData = ByteArray(100) { 0x12 }
        val wavBytes = WavAudioSynthesizer.synthesizeWav(sampleRate = 48000, pcmData = pcmData)

        assertEquals(44 + 100, wavBytes.size)
        val riff = String(wavBytes, 0, 4, Charsets.US_ASCII)
        val wave = String(wavBytes, 8, 4, Charsets.US_ASCII)
        val fmt = String(wavBytes, 12, 4, Charsets.US_ASCII)
        val data = String(wavBytes, 36, 4, Charsets.US_ASCII)

        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)
        assertEquals("fmt ", fmt)
        assertEquals("data", data)

        val bb = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileSizeMinus8 = bb.getInt(4)
        val dataLength = bb.getInt(40)
        assertEquals(wavBytes.size - 8, fileSizeMinus8)
        assertEquals(100, dataLength)
    }

    @Test
    fun exportCompanionWavsCreatesFilesOnDisk() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "mobix_wav_test_${System.currentTimeMillis()}")
        tempDir.deleteOnExit()

        val audioMap = mapOf(
            "assets/aud_test1.opus" to "DummyOpus1".toByteArray(Charsets.UTF_8),
            "assets/aud_test2.opus" to "DummyOpus2".toByteArray(Charsets.UTF_8),
        )

        val createdFiles = WavAudioSynthesizer.exportCompanionWavs(audioMap, tempDir)
        assertEquals(2, createdFiles.size)
        assertTrue(createdFiles[0].isFile)
        assertTrue(createdFiles[0].name.endsWith(".wav"))
        assertTrue(createdFiles[1].isFile)
        assertTrue(createdFiles[1].name.endsWith(".wav"))

        tempDir.deleteRecursively()
    }
}
