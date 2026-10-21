package com.mobixournal.format.mobix

import java.io.OutputStream

/**
 * Result metrics from an incremental `.mobix` repack operation.
 *
 * @property totalEntries Total number of payload entries in the resulting archive (excluding mimetype).
 * @property streamedEntries Number of unchanged entries streamed directly without re-compression.
 * @property rewrittenEntries Number of dirty/new entries freshly compressed (manifest + modified pages).
 * @property streamedRawBytes Total bytes streamed directly from the source archive.
 * @property rewrittenRawBytes Total uncompressed bytes of rewritten entries written into the archive.
 * @property totalOutputBytes Total size in bytes of the finished repacked archive.
 */
internal data class MobixIncrementalSaveStats(
    val totalEntries: Int,
    val streamedEntries: Int,
    val rewrittenEntries: Int,
    val streamedRawBytes: Long,
    val rewrittenRawBytes: Long,
    val totalOutputBytes: Long,
)

/**
 * Incremental repack engine for `.mobix` archives (`docs/mobix-format.md`).
 *
 * ZIP archives do not support in-place mutation. To achieve millisecond saves on multi-page notebooks:
 *  1. [MobixFormat.MIMETYPE_ENTRY] is written first, STORED and uncompressed (container rule 1).
 *  2. Unchanged entries (unmodified pages whose `sha256` has not changed, background PDFs, pictures,
 *     and unknown preserved entries from newer builds) are **copied verbatim** from the [source] to
 *     [output]: the bytes the archive already holds, not the bytes they inflate to, so a background
 *     PDF is neither inflated nor deflated on the way through ([MobixZip]).
 *  3. Only dirty entries (the updated `manifest.json` and pages whose content bytes changed)
 *     are compressed from memory.
 *
 * Preserves container rule 4: unknown parts from newer formats present in [source] that are not
 * superseded are streamed verbatim into the destination.
 */
internal object MobixIncrementalSaver {

    /**
     * Repack [source] into [output], applying the updated [dirtyEntries].
     *
     * @param source The existing archive entry source providing current entries.
     * @param dirtyEntries Map of entry name to new uncompressed bytes for entries that changed
     *                     (must at least contain [MobixFormat.MANIFEST_ENTRY]).
     * @param output The output stream receiving the repacked archive (finished and closed).
     * @param explicitOrder Optional list of entry names defining archive ordering. If null, orders:
     *                      manifest, canvas, pages, assets, others.
     * @return [MobixIncrementalSaveStats] with measurements of streamed vs rewritten entries and bytes.
     */
    fun repack(
        source: MobixEntrySource,
        dirtyEntries: Map<String, MobixPayload>,
        output: OutputStream,
        explicitOrder: List<String>? = null,
    ): MobixIncrementalSaveStats {
        require(dirtyEntries.containsKey(MobixFormat.MANIFEST_ENTRY) || source.entryNames.contains(MobixFormat.MANIFEST_ENTRY)) {
            "a .mobix archive must contain ${MobixFormat.MANIFEST_ENTRY}"
        }
        require(!dirtyEntries.containsKey(MobixFormat.MIMETYPE_ENTRY)) {
            "${MobixFormat.MIMETYPE_ENTRY} is container-managed and cannot be in dirtyEntries"
        }

        val allEntryNames = if (explicitOrder != null) {
            val union = (explicitOrder + source.entryNames + dirtyEntries.keys).distinct()
            union.filter { it != MobixFormat.MIMETYPE_ENTRY }
        } else {
            sortEntries(source.entryNames + dirtyEntries.keys)
        }

        var streamedCount = 0
        var rewrittenCount = 0
        var streamedBytes = 0L
        var rewrittenBytes = 0L

        val zip = MobixZip.Writer(output)
        // Container rule 1: mimetype first, STORED, and never recompressed.
        zip.putStored(MobixFormat.MIMETYPE_ENTRY, MobixFormat.MIMETYPE.toByteArray(Charsets.UTF_8))

        for (name in allEntryNames) {
            val dirty = dirtyEntries[name]
            if (dirty != null) {
                zip.putDeflated(name, dirty)
                rewrittenCount++
                rewrittenBytes += dirty.length
                continue
            }
            // An entry the source holds as it wrote it is copied verbatim; one it can only inflate
            // (an in-memory source) is deflated here instead. Either way it is not re-created.
            val raw = source.rawEntry(name)
            if (raw != null) {
                zip.putRaw(name, raw)
                streamedCount++
                streamedBytes += raw.size
                continue
            }
            source.readBytes(name)?.let { bytes ->
                zip.putDeflated(name, bytes)
                streamedCount++
                streamedBytes += bytes.size.toLong()
            }
        }
        val total = zip.finish()

        return MobixIncrementalSaveStats(
            totalEntries = streamedCount + rewrittenCount,
            streamedEntries = streamedCount,
            rewrittenEntries = rewrittenCount,
            streamedRawBytes = streamedBytes,
            rewrittenRawBytes = rewrittenBytes,
            totalOutputBytes = total,
        )
    }

    private fun sortEntries(names: Collection<String>): List<String> {
        val unique = names.filter { it != MobixFormat.MIMETYPE_ENTRY }.distinct()
        return unique.sortedWith { a, b ->
            val rankA = rankOf(a)
            val rankB = rankOf(b)
            if (rankA != rankB) rankA.compareTo(rankB) else a.compareTo(b)
        }
    }

    private fun rankOf(name: String): Int = when {
        name == MobixFormat.MANIFEST_ENTRY -> 0
        name == MobixFormat.CANVAS_ENTRY -> 1
        name == MobixFormat.BRIDGE_ENTRY -> 2
        name == MobixFormat.TRANSCRIPT_ENTRY -> 2
        name.startsWith(MobixFormat.PAGES_DIR) -> 3
        name.startsWith(MobixFormat.CHUNKS_DIR) -> 3
        name.startsWith(MobixFormat.ASSETS_DIR) -> 4
        else -> 5 // Preserved entries / unknown markup last
    }

}
