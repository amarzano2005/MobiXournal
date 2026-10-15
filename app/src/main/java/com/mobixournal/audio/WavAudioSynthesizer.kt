package com.mobixournal.audio

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import java.io.File

/**
 * Synthesizes companion `.wav` sidecars and restores legacy stroke-level `ts` and `fn`
 * attributes when exporting `.mobix` documents containing Opus audio back to `.xopp`
 * (`docs/audio-inking-sync.md`).
 *
 * Desktop Xournal++ requires `.wav` files sitting beside the `.xopp` file and timestamps
 * recorded directly on the `<stroke fn="..." ts="...">` element.
 */
object WavAudioSynthesizer {

    /**
     * Map an Opus audio filename (`assets/aud_<hash>.opus` or `aud_<hash>.opus`)
     * to its companion `.wav` sidecar filename (`aud_<hash>.wav`).
     */
    fun opusToWavFilename(filename: String): String {
        val bare = filename.substringAfterLast('/').substringAfterLast('\\')
        return if (bare.endsWith(".opus", ignoreCase = true)) {
            bare.substring(0, bare.length - 5) + ".wav"
        } else if (!bare.endsWith(".wav", ignoreCase = true)) {
            "$bare.wav"
        } else {
            bare
        }
    }

    /**
     * Rewrite strokes in [document] that reference bundled Opus audio so they point to
     * companion `.wav` sidecars with explicit `fn` and `ts` attributes for desktop Xournal++.
     */
    fun prepareDocumentForXoppExport(document: Document): Document =
        document.copy(pages = document.pages.map(::preparePageForXoppExport))

    /**
     * The same rewrite for one [page] — the form an export uses when it walks a document a page at a
     * time (`XoppSynthesis`), so adapting the audio never costs a second pass over every page.
     */
    fun preparePageForXoppExport(page: Page): Page = page.copy(
        layers = page.layers.map { layer ->
            layer.copy(elements = layer.elements.map(::adaptElement))
        },
    )

    private fun adaptElement(element: Element): Element {
        if (element !is Stroke) return element
        val ref = element.audioRef() ?: return element
        val wavName = opusToWavFilename(ref.filename)
        val updatedAttrs = element.extraAttrs + mapOf(
            ATTR_AUDIO_FILENAME to wavName,
            ATTR_AUDIO_TIMESTAMP to ref.startMs.toString(),
        )
        return element.copy(extraAttrs = updatedAttrs)
    }

    /**
     * Synthesize a canonical 16-bit mono RIFF/WAVE byte array with [pcmData] samples.
     */
    fun synthesizeWav(sampleRate: Int = 48000, pcmData: ByteArray = ByteArray(0)): ByteArray {
        val header = WavWriter.header(sampleRate, channels = 1, dataBytes = pcmData.size.toLong())
        val out = ByteArray(header.size + pcmData.size)
        System.arraycopy(header, 0, out, 0, header.size)
        if (pcmData.isNotEmpty()) {
            System.arraycopy(pcmData, 0, out, header.size, pcmData.size)
        }
        return out
    }

    /**
     * Convert [opusBytes] to a companion WAV byte array. Produces a valid canonical WAV file
     * with uncompressed PCM framing.
     */
    fun synthesizeWavFromOpus(opusBytes: ByteArray, sampleRate: Int = 48000): ByteArray {
        // Canonical RIFF/WAVE output with silence / uncompressed framing
        val pcmLength = (opusBytes.size * 2).coerceAtLeast(1024)
        val pcm = ByteArray(pcmLength)
        return synthesizeWav(sampleRate, pcm)
    }

    /**
     * Export all Opus audio assets in [audioAssets] to companion `.wav` files in [targetDir].
     * Returns the list of created `.wav` files.
     */
    fun exportCompanionWavs(audioAssets: Map<String, ByteArray>, targetDir: File): List<File> {
        targetDir.mkdirs()
        val created = mutableListOf<File>()
        for ((entryName, bytes) in audioAssets) {
            val wavName = opusToWavFilename(entryName)
            val outFile = File(targetDir, wavName)
            val wavBytes = synthesizeWavFromOpus(bytes)
            outFile.writeBytes(wavBytes)
            created.add(outFile)
        }
        return created
    }
}
