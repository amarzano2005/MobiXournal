package com.mobixournal.audio

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.mobix.MobixJson

/**
 * Serializer and parser for [AudioTranscription] JSON payloads.
 * Conforms to the JSON encoding conventions of `format/mobix/MobixJson.kt`.
 */
object TranscriptionCodec {

    private const val AUDIO_FILE = "audioFile"
    private const val LANGUAGE = "language"
    private const val SEGMENTS = "segments"
    private const val START_MS = "startMs"
    private const val END_MS = "endMs"
    private const val TEXT = "text"
    private const val CONFIDENCE = "confidence"

    /** Serializes [transcription] into compact JSON string. */
    fun encode(transcription: AudioTranscription): String =
        JsonWriter.write(encodeNode(transcription))

    /** Serializes [transcription] into a [JsonNode.Obj] (internal: it exposes the internal type). */
    internal fun encodeNode(transcription: AudioTranscription): JsonNode.Obj =
        MobixJson.obj { out ->
            out[AUDIO_FILE] = JsonNode.of(transcription.audioFilename)
            out[LANGUAGE] = JsonNode.of(transcription.language)
            out[SEGMENTS] = JsonNode.arr(
                transcription.segments.map { segment ->
                    MobixJson.obj { segOut ->
                        segOut[START_MS] = JsonNode.of(segment.startTimeMs)
                        segOut[END_MS] = JsonNode.of(segment.endTimeMs)
                        segOut[TEXT] = JsonNode.of(segment.text)
                        segOut[CONFIDENCE] = JsonNode.of(segment.confidence)
                    }
                },
            )
        }

    /** Deserializes [AudioTranscription] from [jsonText], or null if invalid. */
    fun decode(jsonText: String): AudioTranscription? {
        val root = runCatching { JsonParser.parse(jsonText) as? JsonNode.Obj }.getOrNull() ?: return null
        val audioFile = root.getStrOrNull(AUDIO_FILE) ?: return null
        val language = root.getStr(LANGUAGE, "it-IT")
        val segmentsNode = root.getArr(SEGMENTS)
        val segments = segmentsNode?.list.orEmpty().mapNotNull { item ->
            val obj = item as? JsonNode.Obj ?: return@mapNotNull null
            val startMs = obj.getInt(START_MS, 0)
            val endMs = obj.getInt(END_MS, startMs)
            val text = obj.getStrOrNull(TEXT) ?: return@mapNotNull null
            val confidence = obj.getDouble(CONFIDENCE, 1.0)
            TranscriptionSegment(
                startTimeMs = startMs,
                endTimeMs = endMs.coerceAtLeast(startMs),
                text = text,
                confidence = confidence,
            )
        }
        return AudioTranscription(
            audioFilename = audioFile,
            segments = segments,
            language = language,
        )
    }
}
