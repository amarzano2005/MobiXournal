package com.mobixournal.audio

import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import com.mobixournal.render.DocumentSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionTest {

    @Test
    fun transcriptionSegmentModelWorks() {
        val segment = TranscriptionSegment(
            startTimeMs = 1200,
            endTimeMs = 3500,
            text = "matrice jacobiana",
            confidence = 0.95,
        )
        assertEquals(2300, segment.durationMs)
        assertTrue(segment.containsTime(1200))
        assertTrue(segment.containsTime(2000))
        assertTrue(segment.containsTime(3500))
        org.junit.Assert.assertFalse(segment.containsTime(1199))
        org.junit.Assert.assertFalse(segment.containsTime(3501))
    }

    @Test
    fun transcriptionCodecRoundTrips() {
        val original = AudioTranscription(
            audioFilename = "2026-10-18T10-30-00.ogg",
            language = "it-IT",
            segments = listOf(
                TranscriptionSegment(0, 1500, "Oggi spieghiamo", 0.98),
                TranscriptionSegment(1600, 3200, "la trasformata di Fourier", 0.92),
            ),
        )
        val json = TranscriptionCodec.encode(original)
        val decoded = TranscriptionCodec.decode(json)
        assertNotNull(decoded)
        assertEquals(original.audioFilename, decoded?.audioFilename)
        assertEquals(original.language, decoded?.language)
        assertEquals(2, decoded?.segments?.size)
        assertEquals("la trasformata di Fourier", decoded?.segments?.get(1)?.text)
        assertEquals(3200, decoded?.segments?.get(1)?.endTimeMs)
    }

    @Test
    fun audioInkAlignerFindsStrokesForSpokenSegment() {
        val stroke = Stroke(
            tool = Tool.PEN,
            color = -16777216,
            capStyle = "round",
            uniformWidth = true,
            points = listOf(
                StrokePoint(x = 10.0, y = 20.0, width = 2.0, dtMs = 0),
                StrokePoint(x = 30.0, y = 40.0, width = 2.0, dtMs = 500),
            ),
            extraAttrs = mapOf("fn" to "lecture.ogg", "ts" to "2000"),
        )
        val page = Page(
            width = 595.0,
            height = 842.0,
            background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
            layers = listOf(Layer(elements = listOf(stroke), name = "Layer 1")),
        )
        val doc = com.mobixournal.format.model.Document(pages = listOf(page))

        val transcription = AudioTranscription(
            audioFilename = "lecture.ogg",
            segments = listOf(
                TranscriptionSegment(1800, 2600, "formula fondamentale"),
            ),
        )

        val aligned = AudioInkAligner.align(doc, transcription)
        assertEquals(1, aligned.size)
        assertEquals(0, aligned[0].pageIndex)
        assertEquals(2000, aligned[0].timeMs)
        assertEquals(10.0, aligned[0].bounds.left, 0.01)
        assertEquals(40.0, aligned[0].bounds.bottom, 0.01)
    }

    @Test
    fun documentSearchIntegratesAudioTranscription() {
        val stroke = Stroke(
            tool = Tool.PEN,
            color = -16777216,
            capStyle = "round",
            uniformWidth = true,
            points = listOf(
                StrokePoint(x = 50.0, y = 50.0, width = 2.0, dtMs = 0),
                StrokePoint(x = 80.0, y = 70.0, width = 2.0, dtMs = 300),
            ),
            extraAttrs = mapOf("fn" to "clip.ogg", "ts" to "5000"),
        )
        val page = Page(
            width = 595.0,
            height = 842.0,
            background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
            layers = listOf(Layer(elements = listOf(stroke), name = "Layer 1")),
        )
        val doc = com.mobixournal.format.model.Document(pages = listOf(page))

        val transcription = AudioTranscription(
            audioFilename = "clip.ogg",
            segments = listOf(
                TranscriptionSegment(4800, 5400, "teorema di Stokes"),
            ),
        )

        val hits = DocumentSearch.find(
            doc = doc,
            pdfTextIndex = null,
            rawQuery = "Stokes",
            handwritingIndex = null,
            audioTranscription = transcription,
        )
        assertEquals(1, hits.size)
        assertEquals(0, hits[0].pageIndex)
        assertEquals(50.0, hits[0].boxes[0].left, 0.01)
    }

    @Test
    fun triDirectionalSeekingAlignsStrokeBoundsWithAudioTimestamp() {
        val stroke = Stroke(
            tool = Tool.PEN,
            color = -16777216,
            capStyle = "round",
            uniformWidth = true,
            points = listOf(
                StrokePoint(x = 100.0, y = 200.0, width = 2.0, dtMs = 0),
                StrokePoint(x = 150.0, y = 220.0, width = 2.0, dtMs = 400),
            ),
            extraAttrs = mapOf("fn" to "lecture.ogg", "ts" to "10000"),
        )
        val page = Page(
            width = 595.0,
            height = 842.0,
            background = com.mobixournal.format.model.Background.Solid(-1, "plain"),
            layers = listOf(Layer(elements = listOf(stroke), name = "Layer 1")),
        )
        val doc = com.mobixournal.format.model.Document(pages = listOf(page))
        val transcription = AudioTranscription(
            audioFilename = "lecture.ogg",
            segments = listOf(
                TranscriptionSegment(9500, 10500, "formula di Eulero"),
            ),
        )

        // (1) Audio playhead seeks stroke bounds on canvas:
        val boundsHits = AudioInkAligner.findStrokesAtTime(doc, "lecture.ogg", 10200)
        assertEquals(1, boundsHits.size)
        assertEquals(0, boundsHits[0].first)
        assertEquals(100.0, boundsHits[0].second.left, 0.01)
        assertEquals(150.0, boundsHits[0].second.right, 0.01)

        // (2) Transcript segment lookup at audio playhead:
        val active = transcription.segmentAt(10200)
        assertNotNull(active)
        assertEquals("formula di Eulero", active?.text)
    }

    @Test
    fun transcriptionRoundTripsThroughMobixPackaging() {
        val transcription = AudioTranscription(
            audioFilename = "clip_7a9f.ogg",
            language = "it-IT",
            segments = listOf(
                TranscriptionSegment(0, 1500, "Introduzione"),
                TranscriptionSegment(1500, 4200, "Primo teorema fondamentale"),
            ),
        )
        val encodedJson = TranscriptionCodec.encode(transcription)
        val payloadBytes = encodedJson.toByteArray(Charsets.UTF_8)

        val mobixDoc = com.mobixournal.format.mobix.MobixDocument.of(
            document = com.mobixournal.format.model.Document(pages = emptyList()),
            appVersion = "2.0.0",
            nowMillis = 1000L,
            payloads = mapOf(
                com.mobixournal.format.mobix.MobixFormat.TRANSCRIPT_ENTRY to
                    com.mobixournal.format.mobix.MobixPayload.of(payloadBytes),
            ),
        )

        val plan = mobixDoc.encode(1000L)
        val transcriptEntry = plan.entries.find { it.name == com.mobixournal.format.mobix.MobixFormat.TRANSCRIPT_ENTRY }
        assertNotNull(transcriptEntry)

        val decoded = TranscriptionCodec.decode(String(transcriptEntry!!.bytes, Charsets.UTF_8))
        assertNotNull(decoded)
        assertEquals("clip_7a9f.ogg", decoded?.audioFilename)
        assertEquals("it-IT", decoded?.language)
        assertEquals(2, decoded?.segments?.size)
        assertEquals("Primo teorema fondamentale", decoded?.segments?.get(1)?.text)
    }

    @Test
    fun audioPcmExtractorResamplesCorrectly() {
        // 44.1 kHz 16-bit mono: 44100 samples -> 16000 samples
        val samples44k = 4410
        val pcm44k = ByteArray(samples44k * 2) { 0 }
        val resampled = AudioPcmExtractor.resamplePcm16Mono(pcm44k, 44100, 16000)
        assertEquals(1600 * 2, resampled.size)
    }

    @Test
    fun audioPcmExtractorHandlesMissingOrInvalidFile() {
        val nonExistent = java.io.File("does_not_exist.wav")
        val result = AudioPcmExtractor.extract16kMonoPcm(nonExistent)
        org.junit.Assert.assertNull(result)
    }

    @Test
    fun audioVadSegmenterSplitsSpeechAndSilenceIntoChunks() {
        // 16 kHz 16-bit mono = 32,000 bytes per second
        // Create 6 seconds: 2s speech (high amplitude), 1s silence, 3s speech
        val sampleRate = 16000
        val totalSeconds = 6
        val pcm = ByteArray(totalSeconds * sampleRate * 2)

        fun fillSine(startSec: Int, durationSec: Int, amplitude: Short) {
            val startSample = startSec * sampleRate
            val endSample = (startSec + durationSec) * sampleRate
            for (i in startSample until endSample) {
                val idx = i * 2
                pcm[idx] = (amplitude.toInt() and 0xFF).toByte()
                pcm[idx + 1] = ((amplitude.toInt() shr 8) and 0xFF).toByte()
            }
        }

        // Utterance 1: 0s to 2s
        fillSine(0, 2, 8000)
        // 2s to 3s is silence (all 0)
        // Utterance 2: 3s to 6s
        fillSine(3, 3, 8000)

        val chunks = AudioVadSegmenter.segment(pcm, minChunkMs = 1000, maxChunkMs = 5000, silenceGapMs = 400)
        assertEquals(2, chunks.size)
        // First chunk around [0..2000ms]
        assertEquals(0, chunks[0].startMs)
        assertTrue("End of chunk 0 should be around 2000-2500ms but was ${chunks[0].endMs}", chunks[0].endMs in 1800..2600)
        // Second chunk around [3000..6000ms]
        assertTrue("Start of chunk 1 should be around 2900-3100ms but was ${chunks[1].startMs}", chunks[1].startMs in 2800..3200)
        assertEquals(6000, chunks[1].endMs)
    }

    @Test
    fun whisperModelTypeEnumsAndPathsAreCorrect() {
        assertEquals("whisper-tiny", WhisperModelType.TINY.id)
        assertEquals("tiny", WhisperModelType.TINY.prefix)
        assertTrue(WhisperModelType.TINY.isTiny)
        assertEquals(39, WhisperModelType.TINY.approximateSizeMb)

        assertEquals("whisper-base", WhisperModelType.BASE.id)
        assertEquals("base", WhisperModelType.BASE.prefix)
        org.junit.Assert.assertFalse(WhisperModelType.BASE.isTiny)
        assertEquals(75, WhisperModelType.BASE.approximateSizeMb)
    }

    @Test
    fun aModelIdResolvesToItsModelAndFallsBackToTiny() {
        assertEquals(WhisperModelType.BASE, WhisperModelManager.fromId("whisper-base"))
        assertEquals(WhisperModelType.TINY, WhisperModelManager.fromId("whisper-tiny"))
        assertEquals(WhisperModelType.TINY, WhisperModelManager.fromId(null))
        assertEquals(WhisperModelType.TINY, WhisperModelManager.fromId("not-a-model"))
    }

    @Test
    fun whisperModelManagerDetectsEmptyOrMissingModel() {
        val tempDir = java.nio.file.Files.createTempDirectory("whisper_test").toFile()
        try {
            val isTinyInstalled = WhisperModelManager.isModelInstalled(
                context = object : android.content.ContextWrapper(null) {
                    override fun getFilesDir(): java.io.File = tempDir
                },
                modelType = WhisperModelType.TINY,
            )
            org.junit.Assert.assertFalse(isTinyInstalled)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
