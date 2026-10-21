package com.mobixournal.format.mobix

import com.mobixournal.audio.AudioRef
import com.mobixournal.audio.withAudio
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.StrokePoint
import com.mobixournal.format.model.Tool
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A recording is written **through** the archive, never held by it.
 *
 * A 30-minute lecture is ~160 MB of WAV, and the save used to read it into a `ByteArray` to name it
 * (content addressed), then again to deflate it, then hold the deflated copy — three recordings' worth
 * of heap for one file, which is the `OutOfMemoryError` a long recording produced on saving. The
 * payload seam ([MobixPayload]) exists so none of that happens; this test pins it by watching **how
 * much a payload is ever asked for at once**, not by measuring the heap: every pass over a recording
 * must read it in bounded chunks, on the saves that write it (a full write) and on the ones that copy
 * it out of the source archive (an incremental repack).
 */
class MobixRecordingStreamingTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val sidecar = "2026-08-02T09-14-33.wav"

    /** The longest single read any pass over the recording asked for, in bytes. */
    private val longestRead = mutableListOf<Int>()

    /** The first bytes of a RIFF/WAVE file — enough for the asset name to say what it is. */
    private val wavHeader = "RIFF".toByteArray(Charsets.US_ASCII) + ByteArray(4) +
        "WAVE".toByteArray(Charsets.US_ASCII)

    /** A recording of [length] bytes, produced without ever allocating it. */
    private inner class SyntheticRecording(private val length: Long) : InputStream() {

        private var position = 0L

        private fun byteAt(at: Long): Int = if (at < wavHeader.size) wavHeader[at.toInt()].toInt() else 0

        override fun read(): Int {
            if (position >= length) return -1
            return byteAt(position++)
        }

        override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
            longestRead += count
            if (position >= length) return -1
            val n = minOf(count.toLong(), length - position).toInt()
            for (i in 0 until n) buffer[offset + i] = byteAt(position + i).toByte()
            position += n
            return n
        }
    }

    private fun payload(length: Long): MobixPayload =
        MobixPayload.ofSource({ SyntheticRecording(length) }, length)

    /** A one-page notebook whose only stroke replays from [sidecar]. */
    private fun notebook(): Document = Document(
        pages = listOf(
            SyntheticNotebook.pages(1).single().copy(
                layers = listOf(
                    Layer(
                        listOf(
                            Stroke(
                                tool = Tool.PEN,
                                color = 0xFF000000.toInt(),
                                capStyle = "round",
                                points = listOf(StrokePoint(10.0, 10.0, 1.0), StrokePoint(18.0, 18.0, 1.0)),
                                uniformWidth = true,
                            ).withAudio(AudioRef(sidecar, 0)),
                        ),
                    ),
                ),
            ),
        ),
        title = "Lecture",
        documentId = "doc_long_recording",
    )

    private fun sha256OfSyntheticRecording(length: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(wavHeader)
        val zeros = ByteArray(64 * 1024)
        var left = length - wavHeader.size
        while (left > 0) {
            val n = minOf(zeros.size.toLong(), left).toInt()
            digest.update(zeros, 0, n)
            left -= n
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun aLongRecordingIsHashedDeflatedAndCopiedInBoundedChunks() {
        val length = 8L * 1024 * 1024
        val document = MobixDocument.of(notebook(), appVersion = "1.3.0", nowMillis = 1L)

        // Naming the entry is a hashing pass; nothing is allocated for it.
        val plan = document.encode(nowMillis = 1L, audio = { payload(length) })
        val audioEntry = plan.entries.single { it.name.startsWith(MobixFormat.ASSETS_DIR) }
        assertEquals(
            "the recording is addressed by the hash of its own bytes",
            MobixFormat.assetEntry(sha256OfSyntheticRecording(length), MobixFormat.AssetKind.AUDIO, "wav"),
            audioEntry.name,
        )
        assertFalse("the recording is a stream, not an array", audioEntry.payload.isInMemory)

        // A full write: the deflate pass reads it as it goes.
        val whole = temp.newFile("lecture.mobix")
        FileOutputStream(whole).use { MobixContainer.save(plan.entries, it) }
        assertTrue(
            "a full write reads in bounded chunks: longest=${longestRead.maxOrNull()}",
            longestRead.all { it <= MOBIX_STREAM_CHUNK },
        )
        assertTrue(
            "and the archive is the deflated recording, not the recording: ${whole.length()} bytes",
            whole.length() < length,
        )

        // An incremental repack copies the entry out of the source archive — also in bounded chunks,
        // and without inflating it.
        val opened = MobixEntrySource.fromFile(whole).use { MobixDocument.open(it).toDocument() }
        val longestBeforeCopy = longestRead.size
        val repacked = temp.newFile("lecture-repacked.mobix")
        MobixEntrySource.fromFile(whole).use { source ->
            FileOutputStream(repacked).use { out ->
                MobixIncrementalSave.saveOver(
                    source = source,
                    document = opened,
                    appVersion = "1.3.0",
                    nowMillis = 2L,
                    output = out,
                    audio = { null },
                )
            }
        }
        val copied = MobixEntrySource.fromFile(repacked).use { it.readBytes(audioEntry.name)!! }
        assertEquals(
            "the recording is the source's own compressed bytes",
            MobixEntrySource.fromFile(whole).use { it.readBytes(audioEntry.name)!!.toList() },
            copied.toList(),
        )
        assertEquals(
            "and the copy never read the source's inflated content",
            longestBeforeCopy,
            longestRead.size,
        )
    }

    private companion object {
        /** The chunk every streamed write, hash and copy passes through (`MobixPayload`/`MobixZip`). */
        const val MOBIX_STREAM_CHUNK = 64 * 1024
    }
}
