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
}
