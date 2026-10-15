package com.mobixournal.audio

import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixFormat
import java.io.File

/**
 * Handles content-hashed bundling and resolution of compressed Opus audio assets
 * within the `.mobix` container (`docs/audio-inking-sync.md` and `docs/mobix-schema-spec.md`).
 *
 * Each audio clip is addressed by the SHA-256 hash of its bytes as `assets/aud_<sha256>.opus`.
 * This deduplicates identical audio clips and binds them directly into the document container.
 */
internal object OpusAssetBundler {

    /**
     * Compute the content hash and register [bytes] into [sink].
     * Returns the archive entry path, e.g. `assets/aud_<sha256>.opus`.
     */
    fun bundle(bytes: ByteArray, sink: MobixAssetSink): String {
        return sink.add(bytes, MobixFormat.AssetKind.AUDIO, "opus")
    }

    /**
     * Read [file] and register its bytes into [sink].
     * Returns the archive entry path, e.g. `assets/aud_<sha256>.opus`.
     */
    fun bundle(file: File, sink: MobixAssetSink): String = bundle(file.readBytes(), sink)

    /**
     * Compute the content-addressed entry name `assets/aud_<sha256>.opus` for [bytes].
     */
    fun assetNameFor(bytes: ByteArray): String {
        val sha256 = MobixAssetSink.sha256(bytes)
        return MobixFormat.assetEntry(sha256, MobixFormat.AssetKind.AUDIO, "opus")
    }

    /**
     * Create an [AudioRef] referencing [assetName] at offset [startMs].
     */
    fun createRef(assetName: String, startMs: Int = 0): AudioRef =
        AudioRef(filename = assetName, startMs = startMs)

    /**
     * Return a new [MobixDocument] carrying [bytes] registered under [assetName].
     */
    fun adoptAudio(document: MobixDocument, assetName: String, bytes: ByteArray): MobixDocument {
        val normalized = normalizeAssetName(assetName)
        val updatedPayloads = document.allPayloads + (normalized to bytes)
        return MobixDocument(
            manifest = document.manifest,
            canvas = document.canvas,
            pages = document.pages,
            chunks = document.chunks,
            bridge = document.bridge,
            payloads = updatedPayloads,
            preserved = document.preserved,
        )
    }

    /**
     * Retrieve the bytes of [assetName] from [document], tolerating optional `assets/` prefix.
     */
    fun extractAudio(document: MobixDocument, assetName: String): ByteArray? {
        val normalized = normalizeAssetName(assetName)
        return document.asset(normalized) ?: document.asset(assetName)
    }

    /**
     * Whether [filename] refers to a bundled Opus asset (`aud_<sha256>.opus` or `assets/aud_<sha256>.opus`).
     */
    fun isOpusAsset(filename: String): Boolean =
        MobixFormat.isAssetEntry(filename, MobixFormat.AssetKind.AUDIO) &&
            filename.removePrefix(MobixFormat.ASSETS_DIR).endsWith(".opus")

    /**
     * Normalize an asset name to ensure it has the canonical `assets/` prefix.
     */
    fun normalizeAssetName(name: String): String =
        if (name.startsWith(MobixFormat.ASSETS_DIR)) name else "${MobixFormat.ASSETS_DIR}$name"
}
