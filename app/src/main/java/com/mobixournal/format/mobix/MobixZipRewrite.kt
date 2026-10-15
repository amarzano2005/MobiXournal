package com.mobixournal.format.mobix

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.util.Calendar
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry

/**
 * Rewriting a ZIP **without recompressing it**: reading an entry the way the archive stores it, and
 * writing an archive whose entries can be copied verbatim.
 *
 * `java.util.zip` has no vocabulary for this. `ZipInputStream`/`ZipFile` hand back *inflated* entry
 * bytes, so streaming an unchanged entry through them means inflating and deflating it again — the work
 * an incremental save exists to avoid — and `ZipOutputStream` always deflates what it is given, so it
 * cannot be handed bytes it did not produce. Hence the two narrow pieces here: a read of the central
 * directory, which is the only place that states where an entry's bytes are, and a writer that emits
 * local headers, the central directory and the end record itself.
 *
 * Both are deliberately limited: STORED and DEFLATED only, no encryption, and **no ZIP64** — an archive
 * that needs it is refused here and left to the re-deflating path. Every local header is written with
 * its sizes and CRC already known, so no data descriptor is needed, which is also why an archive this
 * writes comes out slightly *smaller* than one `ZipOutputStream` produced.
 */
internal object MobixZip {

    /** An entry as the archive stores it: how it is compressed, and its compressed bytes. */
    class RawEntry(
        val method: Int,
        val crc: Long,
        val size: Long,
        val compressedSize: Long,
        val bytes: ByteArray,
    )

    /**
     * The bytes [name] is stored as in [file], or null when this archive cannot be read that way — ZIP64,
     * encrypted, not a ZIP at all, or no such entry. A null is not an error: the caller re-deflates.
     *
     * One entry, one file open: a caller that copies many entries wants [Index] instead, which reads the
     * file once.
     */
    fun rawEntry(file: File, name: String): RawEntry? =
        runCatching { Index(file).use { it.rawEntry(name) } }.getOrNull()

    /**
     * One file's entries, as the archive stores them, **read once**.
     *
     * A repack copies every unchanged entry out of the source, so reading the central directory per
     * entry — which is what [rawEntry] does, and what the first version of this did for every copy — made
     * a save quadratic: a two-hundred-page notebook re-parsed two hundred records two hundred times,
     * which cost half a second and buried everything the save was actually doing
     * (`MobixIncrementalSaveBenchmarkTest` is the measurement that found it). The directory is the
     * archive's own index, so this holds it while the file is open and answers every copy from it.
     */
    class Index(file: File) : AutoCloseable {

        private val raf = RandomAccessFile(file, "r")
        private val records = centralDirectory(raf).associateBy { it.name }

        /** The bytes [name] is stored as, or null when this archive cannot hand them over. */
        @Synchronized
        fun rawEntry(name: String): RawEntry? {
            val record = records[name] ?: return null
            if (record.method != ZipEntry.STORED && record.method != ZipEntry.DEFLATED) return null
            val dataOffset = record.localHeaderOffset + LOCAL_HEADER_SIZE +
                localHeaderSizes(raf, record.localHeaderOffset)
            raf.seek(dataOffset)
            val bytes = ByteArray(record.compressedSize.toInt())
            raf.readFully(bytes)
            return RawEntry(record.method, record.crc, record.size, record.compressedSize, bytes)
        }

        override fun close() = raf.close()
    }

    private class Record(
        val name: String,
        val method: Int,
        val crc: Long,
        val size: Long,
        val compressedSize: Long,
        val localHeaderOffset: Long,
    )

    /** The name and extra-field lengths of the local header at [offset]; the entry's data starts after. */
    private fun localHeaderSizes(raf: RandomAccessFile, offset: Long): Int {
        raf.seek(offset + 26)
        return readShort(raf) + readShort(raf)
    }

    /**
     * Every entry the archive's central directory lists. The local headers cannot be walked instead: an
     * entry written with a data descriptor — which is what `ZipOutputStream` produces — leaves its sizes
     * zero in the local header, so there is no way to skip over its bytes. The central directory always
     * has them.
     */
    private fun centralDirectory(raf: RandomAccessFile): List<Record> {
        val end = endRecord(raf) ?: return emptyList()
        val out = ArrayList<Record>(end.count.coerceAtLeast(0))
        var at = end.offset
        repeat(end.count) {
            raf.seek(at)
            if (readInt(raf) != CENTRAL_SIGNATURE) return out
            readShort(raf) // version made by
            raf.seek(at + 8)
            val flags = readShort(raf)
            val method = readShort(raf)
            raf.seek(at + 16)
            val crc = readInt(raf).toLong() and 0xFFFFFFFFL
            val compressed = readInt(raf).toLong() and 0xFFFFFFFFL
            val size = readInt(raf).toLong() and 0xFFFFFFFFL
            val nameLength = readShort(raf)
            val extraLength = readShort(raf)
            val commentLength = readShort(raf)
            raf.seek(at + 42)
            val headerOffset = readInt(raf).toLong() and 0xFFFFFFFFL
            raf.seek(at + CENTRAL_HEADER_SIZE)
            val name = ByteArray(nameLength).also { raf.readFully(it) }.toString(Charsets.UTF_8)
            // An entry this code cannot rewrite faithfully is reported as absent rather than half-copied.
            // Bit 3 (a data descriptor) is *not* a problem here: the central directory is the record that
            // has the real sizes, which is why it is what this reads — the local header of such an entry
            // says zero.
            val usable = flags and FLAG_ENCRYPTED == 0 &&
                compressed != ZIP64_MARKER && size != ZIP64_MARKER && headerOffset != ZIP64_MARKER
            if (usable) out += Record(name, method, crc, size, compressed, headerOffset)
            at += CENTRAL_HEADER_SIZE + nameLength + extraLength + commentLength
        }
        return out
    }

    private class EndRecord(val offset: Long, val count: Int)

    private fun endRecord(raf: RandomAccessFile): EndRecord? {
        val length = raf.length()
        val window = minOf(length, MAX_EOCD_SEARCH.toLong()).toInt()
        val tail = ByteArray(window)
        raf.seek(length - window)
        raf.readFully(tail)
        // The last signature whose stated comment length accounts for exactly the bytes left is the one.
        for (at in window - EOCD_SIZE downTo 0) {
            if (tail.intAt(at) != EOCD_SIGNATURE) continue
            if (at + EOCD_SIZE + tail.shortAt(at + 20) == window) {
                return EndRecord(tail.intAt(at + 16).toLong() and 0xFFFFFFFFL, tail.shortAt(at + 10))
            }
        }
        return null
    }

    /** A minimal writer: local headers whose sizes are known, then the central directory, then the EOCD. */
    class Writer(private val out: OutputStream) {

        private class Centred(
            val name: String,
            val method: Int,
            val crc: Long,
            val size: Long,
            val compressedSize: Long,
            val offset: Long,
        )

        private val centred = mutableListOf<Centred>()
        private var written = 0L

        private val calendar = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
        private val dosDate = ((calendar.get(Calendar.YEAR) - 1980) shl 9) or
            ((calendar.get(Calendar.MONTH) + 1) shl 5) or calendar.get(Calendar.DAY_OF_MONTH)
        private val dosTime = (calendar.get(Calendar.HOUR_OF_DAY) shl 11) or
            (calendar.get(Calendar.MINUTE) shl 5) or (calendar.get(Calendar.SECOND) / 2)

        /** [bytes] stored uncompressed — the mimetype, which the container requires that way. */
        fun putStored(name: String, bytes: ByteArray) =
            put(name, ZipEntry.STORED, crcOf(bytes), bytes, bytes.size.toLong())

        /** [bytes] deflated fresh: content this save produced. */
        fun putDeflated(name: String, bytes: ByteArray) =
            put(name, ZipEntry.DEFLATED, crcOf(bytes), deflate(bytes), bytes.size.toLong())

        /** [entry]'s bytes copied verbatim — nothing is inflated, deflated or rewritten. */
        fun putRaw(name: String, entry: RawEntry) =
            put(name, entry.method, entry.crc, entry.bytes, entry.size)

        /**
         * One entry: [data] is exactly what the archive stores and [uncompressedSize] what it expands
         * to, so the compressed size is never stated twice (and can never disagree with the bytes).
         */
        private fun put(name: String, method: Int, crc: Long, data: ByteArray, uncompressedSize: Long) {
            val offset = written
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            val header = ByteArray(LOCAL_HEADER_SIZE)
            header.writeIntLe(0, LOCAL_SIGNATURE)
            header.writeShortLe(4, VERSION_NEEDED)
            header.writeShortLe(6, 0)
            header.writeShortLe(8, method)
            header.writeShortLe(10, dosTime)
            header.writeShortLe(12, dosDate)
            header.writeIntLe(14, crc)
            header.writeIntLe(18, data.size)
            header.writeIntLe(22, uncompressedSize)
            header.writeShortLe(26, nameBytes.size)
            header.writeShortLe(28, 0)
            out.write(header)
            out.write(nameBytes)
            out.write(data)
            written += header.size + nameBytes.size + data.size
            centred += Centred(name, method, crc, uncompressedSize, data.size.toLong(), offset)
        }

        /** Write the central directory and the end record; report the archive's size in bytes. */
        fun finish(): Long {
            val start = written
            for (entry in centred) {
                val nameBytes = entry.name.toByteArray(Charsets.UTF_8)
                val record = ByteArray(CENTRAL_HEADER_SIZE)
                record.writeIntLe(0, CENTRAL_SIGNATURE)
                record.writeShortLe(4, VERSION_NEEDED)
                record.writeShortLe(6, VERSION_NEEDED)
                record.writeShortLe(8, 0)
                record.writeShortLe(10, entry.method)
                record.writeShortLe(12, dosTime)
                record.writeShortLe(14, dosDate)
                record.writeIntLe(16, entry.crc)
                record.writeIntLe(20, entry.compressedSize)
                record.writeIntLe(24, entry.size)
                record.writeShortLe(28, nameBytes.size)
                record.writeIntLe(42, entry.offset)
                out.write(record)
                out.write(nameBytes)
                written += record.size + nameBytes.size
            }
            val directorySize = written - start
            val eocd = ByteArray(EOCD_SIZE)
            eocd.writeIntLe(0, EOCD_SIGNATURE)
            eocd.writeShortLe(8, centred.size)
            eocd.writeShortLe(10, centred.size)
            eocd.writeIntLe(12, directorySize)
            eocd.writeIntLe(16, start)
            out.write(eocd)
            written += eocd.size
            out.flush()
            return written
        }

        private fun deflate(bytes: ByteArray): ByteArray {
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            try {
                deflater.setInput(bytes)
                deflater.finish()
                val deflated = ByteArrayOutputStream(bytes.size.coerceAtLeast(64))
                val buffer = ByteArray(8192)
                while (!deflater.finished()) deflated.write(buffer, 0, deflater.deflate(buffer))
                return deflated.toByteArray()
            } finally {
                deflater.end()
            }
        }
    }

    private fun crcOf(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

    private const val LOCAL_SIGNATURE = 0x04034b50
    private const val CENTRAL_SIGNATURE = 0x02014b50
    private const val EOCD_SIGNATURE = 0x06054b50
    private const val LOCAL_HEADER_SIZE = 30
    private const val CENTRAL_HEADER_SIZE = 46
    private const val EOCD_SIZE = 22
    private const val VERSION_NEEDED = 20
    private const val FLAG_ENCRYPTED = 0x1
    private const val ZIP64_MARKER = 0xFFFFFFFFL
    private const val MAX_EOCD_SEARCH = 65557

    /** ZIP is little-endian everywhere; [RandomAccessFile] reads big-endian, so nothing goes direct. */
    private fun readShort(raf: RandomAccessFile): Int =
        ByteArray(2).also { raf.readFully(it) }.shortAt(0)

    private fun readInt(raf: RandomAccessFile): Int =
        ByteArray(4).also { raf.readFully(it) }.intAt(0)

    private fun ByteArray.shortAt(at: Int): Int = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)
    private fun ByteArray.intAt(at: Int): Int =
        (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8) or
            ((this[at + 2].toInt() and 0xFF) shl 16) or ((this[at + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.writeShortLe(at: Int, value: Int) {
        this[at] = (value and 0xFF).toByte()
        this[at + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun ByteArray.writeIntLe(at: Int, value: Int) {
        this[at] = (value and 0xFF).toByte()
        this[at + 1] = ((value shr 8) and 0xFF).toByte()
        this[at + 2] = ((value shr 16) and 0xFF).toByte()
        this[at + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun ByteArray.writeIntLe(at: Int, value: Long) = writeIntLe(at, value.toInt())
}
