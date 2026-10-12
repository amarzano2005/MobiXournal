package com.mobixournal.format.mobix

/**
 * The fixed vocabulary of the `.mobix` container: its typed mimetype, the entry names every document
 * has, and the naming scheme for the parts a document may have many of. The layout is owned by
 * `docs/mobix-schema-spec.md`; this object is the code's mirror of it, and the one place those
 * strings are written down — a reader, a writer and a test that all spell `"manifest.json"` for
 * themselves is how a format drifts from its documentation.
 *
 * Pure Kotlin with no Android and no `java.io.File`, like the rest of `format/`: the same code runs
 * on-device and in JVM unit tests (see `docs/stack.md`).
 */
object MobixFormat {

    /**
     * The archive's type, written into [MIMETYPE_ENTRY] and **the only thing that tells a `.mobix`
     * from a ZIP-package `.xopp`**: both are ZIPs and both begin `PK\x03\x04`, so the leading bytes
     * answer "it is a ZIP" and nothing more (see container rule 2 in `docs/mobix-schema-spec.md`).
     */
    const val MIMETYPE = "application/vnd.mobixournal+zip"

    /** The suffix the app suggests for a `.mobix` save. A hint only — classification never uses it. */
    const val EXTENSION = ".mobix"

    /** The first entry, STORED uncompressed: what a reader that only wants to *identify* the file reads. */
    const val MIMETYPE_ENTRY = "mimetype"

    /** The document envelope: version stamps, ids, timestamps and the page/chunk order. Required. */
    const val MANIFEST_ENTRY = "manifest.json"

    /** The spatial geometry mode and its dimensions (`docs/infinite-canvas.md` owns the schema). */
    const val CANVAS_ENTRY = "canvas.json"

    /** Paginated mode: one entry per page, named by [pageEntry]. */
    const val PAGES_DIR = "pages/"

    /** Infinite modes: one entry per occupied tile, named by [chunkEntry]. */
    const val CHUNKS_DIR = "chunks/"

    /** Binary assets, named by content hash ([assetEntry]) — never inline base64. */
    const val ASSETS_DIR = "assets/"


    /** The `.xopp` bridge's sidecar directory (`docs/mobix-format.md`). */
    const val BRIDGE_DIR = "xopp-bridge/"

    /** The bridge's bookkeeping file: the source it came from and the synthesis hashes. */
    const val BRIDGE_ENTRY = "xopp-bridge/metadata.json"

    /**
     * The entry name of the [index]-th page, **one-based** as the format writes it
     * (`page-0001.json`). The four digits are padding, not a limit: page 10000 is
     * `page-10000.json`, and the manifest — not the name order — is what fixes the document's page
     * order, so a reordered or spliced page list still reads back correctly.
     */
    fun pageEntry(index: Int): String {
        require(index >= 1) { "page indices are one-based: $index" }
        return PAGES_DIR + "page-" + index.toString().padStart(4, '0') + ".json"
    }

    /**
     * The entry name of the chunk at signed chunk coordinates [cx], [cy] (`chunk_-3_2.json`). Plain
     * decimal integers with no padding and no `+`, so `-1` and `1` cannot collide — an infinite 2D
     * board has both (see *Chunk files on disk* in `docs/infinite-canvas.md`).
     */
    fun chunkEntry(cx: Int, cy: Int): String = "$CHUNKS_DIR" + "chunk_${cx}_${cy}.json"

    /**
     * The entry name of a binary asset, addressed by the lowercase hex [sha256] of its bytes: the
     * same picture pasted on ten pages is stored once and referenced ten times, and a name derived
     * from the content cannot go stale the way a counter can.
     *
     * [suffix] is the container the bytes are stored in, and defaults to the kind's canonical one.
     * It is a parameter because an image is stored **in the container it arrived in** — a PNG stays a
     * PNG rather than being re-encoded to WebP, which would change the pixels the user pasted — while
     * audio is always Opus (one codec means one decoder, on Android and on desktop alike).
     */
    fun assetEntry(sha256: String, kind: AssetKind, suffix: String = kind.defaultSuffix): String {
        require(sha256.length == 64 && sha256.all { it in "0123456789abcdef" }) {
            "an asset name is the lowercase hex sha256 of its bytes, got \"$sha256\""
        }
        require(suffix.isNotEmpty() && suffix.all { it.isLetterOrDigit() }) {
            "an asset container is an extension without its dot, got \"$suffix\""
        }
        return "$ASSETS_DIR${kind.prefix}_$sha256.$suffix"
    }

    /** Whether [name] is a page entry this build would read. */
    fun isPageEntry(name: String): Boolean = name.startsWith(PAGES_DIR) && name.endsWith(".json")

    /** Whether [name] is a chunk entry this build would read. */
    fun isChunkEntry(name: String): Boolean = name.startsWith(CHUNKS_DIR) && name.endsWith(".json")

    /**
     * An asset's kind: the prefix its entry name carries, so an asset is recognisable — and
     * greppable — by kind from its name alone, plus the container it is stored in by default.
     */
    enum class AssetKind(val prefix: String, val defaultSuffix: String) {
        /** A picture: a page background, an embedded image, or a rendered formula. */
        IMAGE("img", "webp"),

        /** Bundled audio, linked to stroke timestamps (`docs/audio-inking-sync.md`). */
        AUDIO("aud", "opus"),
    }
}
