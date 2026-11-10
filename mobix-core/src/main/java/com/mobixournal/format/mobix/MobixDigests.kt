package com.mobixournal.format.mobix

/**
 * The **digest rules** the reader enforces on the bytes it materialises
 * (`docs/mobix-format.md`, *What a reader must refuse*).
 *
 * Two names in a `.mobix` claim something about bytes: a manifest part records the SHA-256 of the entry
 * it points at, and an asset's own **name** is the SHA-256 of the payload. Until this existed the writer
 * recorded both and the reader checked neither, so a page edited or truncated after it was saved decoded
 * as whatever bytes happened to be there — the one failure mode a checksum exists to catch.
 *
 * The check runs when a part is **materialised** (a page as it is opened, a chunk, an asset when a page
 * asks for it), not when the archive is listed: a 200-page notebook verified only the page the reader
 * drew, which is the same work it already did to draw it. That is also the cost decision: SHA-256 runs at
 * roughly 1 ms per MiB on the machines this project targets, and a page whose text is a MB is a page
 * heavier than any the app produces, so verification adds a small fraction of the inflation it follows.
 */
internal object MobixDigests {

    /**
     * Verify the bytes of [entryName] against [recorded], the digest the manifest lists for that part.
     *
     * A part that records no digest is read as it always was: absence is a writer this build does not
     * own, and inventing a digest would refuse a file nobody's rules require one in.
     */
    fun verifyPart(entryName: String, recorded: String, bytes: ByteArray) {
        if (recorded.isEmpty()) return
        val actual = MobixAssetSink.sha256(bytes)
        if (!actual.equals(recorded, ignoreCase = true)) {
            refuse(entryName, recorded, actual)
        }
    }

    /**
     * Verify [bytes] against the digest their own entry name claims — an asset is addressed by its
     * content, so a payload that does not hash to the name it is stored under is not that asset.
     */
    fun verifyAsset(entryName: String, bytes: ByteArray) {
        val claimed = digestIn(entryName) ?: return
        val actual = MobixAssetSink.sha256(bytes)
        if (!actual.equals(claimed, ignoreCase = true)) {
            refuse(entryName, claimed, actual)
        }
    }

    /** The hex digest an `assets/img_<sha256>.<ext>` name carries, or null for any other entry. */
    fun digestIn(entryName: String): String? {
        val kinds = MobixFormat.AssetKind.entries
        if (kinds.none { MobixFormat.isAssetEntry(entryName, it) }) return null
        val bare = entryName.removePrefix(MobixFormat.ASSETS_DIR)
        val hash = bare.substringAfter('_').substringBefore('.')
        return hash.takeIf { it.length == SHA256_HEX_LENGTH }
    }

    private fun refuse(entryName: String, recorded: String, actual: String): Nothing =
        throw MobixContainer.MalformedMobixException(
            "\"$entryName\" does not match the digest recorded for it" +
                " (recorded $recorded, found $actual): the file was edited or damaged after it was written",
        )

    /** The length of the lowercase hex SHA-256 every name and manifest record carries. */
    private const val SHA256_HEX_LENGTH = 64
}
