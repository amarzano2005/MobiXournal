package com.mobixournal.format.mobix

import java.io.InputStream
import java.security.MessageDigest

/** Resolves a bundled binary payload by its entry name (an `assets/img_<sha256>.png`, say). */
internal fun interface MobixAssetSource {
    /** The bytes of [name], or null when this document does not carry it. */
    fun bytes(name: String): ByteArray?

    /**
     * The content of [name] as a **stream**, or null when this document does not carry it.
     *
     * What adopting a recording on open uses: a caller that copies the payload to a store
     * (`BundledAudio`) must not ask a 160 MB recording for its `ByteArray` first. The default reads
     * the bytes — correct for a source that has nothing else, wasteful for one that does — and the
     * streaming source overrides it ([asAssetSource]).
     */
    fun open(name: String): InputStream? = bytes(name)?.inputStream()
}

/** The [MobixAssetSource] view of an opened archive: assets are just entries. */
internal fun MobixContainer.Archive.asAssetSource(): MobixAssetSource = MobixAssetSource { bytes(it) }

/**
 * The [MobixAssetSource] view of a streaming [MobixEntrySource]: assets are the `assets/` entries,
 * read — or **streamed** — on demand. A bare name (no `assets/` prefix) is resolved under the assets
 * directory, matching the names a page's codec hands back.
 */
internal fun MobixEntrySource.asAssetSource(): MobixAssetSource = object : MobixAssetSource {

    private fun entryOf(name: String): String =
        if (name.startsWith(MobixFormat.ASSETS_DIR)) name else "${MobixFormat.ASSETS_DIR}$name"

    override fun bytes(name: String): ByteArray? = readBytes(entryOf(name))

    override fun open(name: String): InputStream? = openEntry(entryOf(name))
}

/**
 * Collects the binary payloads a document references, addressing each by the hash of its own bytes.
 *
 * Content addressing is what makes this a *format* decision rather than housekeeping: a picture pasted
 * on ten pages, or the same PDF background shared by a whole import, is stored **once** and referenced
 * ten times, and no counter or path can go stale — the name *is* the content. [add] is idempotent, so
 * two elements carrying the same bytes converge on the same entry without the caller coordinating.
 *
 * The bytes go in the archive, never into the JSON: base64 inside XML tags is exactly the `.xopp`
 * inefficiency the native format exists to remove (`docs/mobix-format.md`).
 */
internal class MobixAssetSink {

    private val byName = LinkedHashMap<String, MobixPayload>()

    /**
     * Register [bytes] and return the entry name that addresses them. [kind] prefixes the name and
     * [suffix] is the container the bytes are kept in — the caller's own when it knows it (a PNG stays
     * a PNG), sniffed from the bytes otherwise.
     */
    fun add(
        bytes: ByteArray,
        kind: MobixFormat.AssetKind,
        suffix: String = suffixFor(bytes),
    ): String = add(MobixPayload.of(bytes), kind, suffix)

    /**
     * Register a **streamed** payload and return the entry name that addresses it. The name is its
     * content hash, so the content is hashed in bounded chunks and only then written as the archive
     * is built — a recording is never in memory to be named, and is not read twice by being named.
     */
    fun add(
        payload: MobixPayload,
        kind: MobixFormat.AssetKind,
        suffix: String = payload.suffix,
    ): String {
        val name = MobixFormat.assetEntry(payload.sha256, kind, suffix)
        byName.putIfAbsent(name, payload)
        return name
    }

    /** The entries to write, in the order they were first referenced. */
    fun entries(): List<MobixContainer.Entry> =
        byName.map { (name, payload) -> MobixContainer.Entry(name, payload) }

    /** How many distinct payloads the document ended up carrying — the deduplication's own measure. */
    val count: Int get() = byName.size

    companion object {

        /** The lowercase hex SHA-256 of [bytes]: the name an asset is addressed by. */
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /**
         * The container [bytes] are in, for the asset name. Recognises the three raster formats the app
         * can open and display plus the two audio containers an inking recording arrives in — the app's
         * own recorder writes **WAV**, so that is what its recordings are bundled as, and an `ogg`/Opus
         * stream from another writer keeps its own container rather than being renamed. **`bin`
         * otherwise**, because guesses here would be worse than an honest unknown — the name only has to
         * be unique and recognisable, and the reader keys on the hash.
         *
         * This is deliberately not `FileKind`'s sniffing: that answers "what kind of *document* is
         * this, and how do I open it?", while this answers "what container are these *asset* bytes in,
         * so the file name can say so?".
         */
        fun suffixFor(bytes: ByteArray): String {
            fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xff else -1
            fun tag(offset: Int, text: String) = text.indices.all { at(offset + it) == text[it].code }
            return when {
                at(0) == 0x89 && tag(1, "PNG") -> "png"
                at(0) == 0xff && at(1) == 0xd8 && at(2) == 0xff -> "jpg"
                tag(0, "RIFF") && tag(8, "WEBP") -> "webp"
                tag(0, "RIFF") && tag(8, "WAVE") -> "wav"
                tag(0, "OggS") -> "ogg"
                tag(4, "ftyp") -> "m4a"
                else -> "bin"
            }
        }
    }
}
