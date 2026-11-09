package com.mobixournal.audio

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * A recording's envelope, kept **beside it** as a few hundred bytes.
 *
 * A waveform is a dozen or so numbers per second of audio, so it is cheap to store and expensive to
 * rebuild: a lecture is decoded for a second or two the first time and would be decoded again after
 * every restart. A sidecar answers with the numbers that were already computed, which is what makes
 * the bars appear **with** the sound rather than a decode after it (`docs/audio.md`).
 *
 * The file names the audio it belongs to — its length and timestamp — so a recording replaced under
 * the same name can never wear the old take's envelope. Every failure (missing, torn, written by
 * another version, a wrong sample count) answers `null` and the caller decodes as it always did; the
 * sidecar is a shortcut, never a source of truth.
 */
internal object WaveformSidecar {

    private const val MAGIC = 0x4D585756 // "MXVW"

    private const val VERSION = 1

    /** Where [audio]'s envelope lives: its own name plus `.wave`, so the folder stays scannable. */
    fun fileFor(audio: File): File = File(audio.parentFile, audio.name + ".wave")

    /** The stored envelope for [audio], or null when it was not written for **this** exact file. */
    fun read(audio: File, targetSamples: Int): List<Float>? {
        if (targetSamples <= 0) return null
        return runCatching {
            val sidecar = fileFor(audio)
            if (!sidecar.isFile) return null
            DataInputStream(sidecar.inputStream().buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                if (input.readLong() != audio.length()) return null
                if (input.readLong() != audio.lastModified()) return null
                if (input.readInt() != targetSamples) return null
                List(targetSamples) { input.readFloat() }
            }
        }.getOrNull()
    }

    /** Keep [samples] beside [audio]; a folder that refuses the write is simply no shortcut. */
    fun write(audio: File, samples: List<Float>) {
        if (samples.isEmpty()) return
        runCatching {
            val sidecar = fileFor(audio)
            val temporary = File(sidecar.parentFile, sidecar.name + ".tmp")
            DataOutputStream(temporary.outputStream().buffered()).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeLong(audio.length())
                out.writeLong(audio.lastModified())
                out.writeInt(samples.size)
                samples.forEach(out::writeFloat)
            }
            // A rename onto the answer is atomic enough: a reader sees the old envelope or the new one,
            // never half of either.
            if (!temporary.renameTo(sidecar)) {
                sidecar.delete()
                temporary.renameTo(sidecar)
            }
        }
    }
}
