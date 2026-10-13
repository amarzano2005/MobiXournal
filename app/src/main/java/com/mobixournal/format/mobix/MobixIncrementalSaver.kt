package com.mobixournal.format.mobix

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
 *     and unknown preserved entries from newer builds) are streamed directly from the [source]
 *     to [output].
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
        dirtyEntries: Map<String, ByteArray>,
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

        val countingOut = CountingOutputStream(output)
        ZipOutputStream(countingOut).use { zip ->
            // Container rule 1: mimetype first, STORED
            putStoredMimetype(zip)

            for (name in allEntryNames) {
                val dirty = dirtyEntries[name]
                if (dirty != null) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(dirty)
                    zip.closeEntry()
                    rewrittenCount++
                    rewrittenBytes += dirty.size.toLong()
                } else {
                    source.openEntry(name)?.use { input ->
                        zip.putNextEntry(ZipEntry(name))
                        val copied = copyStream(input, zip)
                        zip.closeEntry()
                        streamedCount++
                        streamedBytes += copied
                    }
                }
            }
        }

        return MobixIncrementalSaveStats(
            totalEntries = streamedCount + rewrittenCount,
            streamedEntries = streamedCount,
            rewrittenEntries = rewrittenCount,
            streamedRawBytes = streamedBytes,
            rewrittenRawBytes = rewrittenBytes,
            totalOutputBytes = countingOut.bytesWritten,
        )
    }

    private fun putStoredMimetype(zip: ZipOutputStream) {
        val bytes = MobixFormat.MIMETYPE.toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(bytes) }
        zip.putNextEntry(
            ZipEntry(MobixFormat.MIMETYPE_ENTRY).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                this.crc = crc.value
            },
        )
        zip.write(bytes)
        zip.closeEntry()
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
        name.startsWith(MobixFormat.PAGES_DIR) -> 3
        name.startsWith(MobixFormat.CHUNKS_DIR) -> 3
        name.startsWith(MobixFormat.ASSETS_DIR) -> 4
        else -> 5 // Preserved entries / unknown markup last
    }

    private fun copyStream(input: InputStream, output: OutputStream): Long {
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val r = input.read(buf)
            if (r == -1) break
            output.write(buf, 0, r)
            total += r
        }
        return total
    }

    private class CountingOutputStream(val delegate: OutputStream) : OutputStream() {
        var bytesWritten = 0L
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytesWritten++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytesWritten += len
        }

        override fun flush() = delegate.flush()
        override fun close() = delegate.close()
    }
}
