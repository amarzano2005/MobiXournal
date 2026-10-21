package com.mobixournal.format.mobix

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * One payload a `.mobix` entry carries, **streamed rather than held**: the bytes of a picture, a
 * background PDF or a recording, addressed by content without ever being one `ByteArray`.
 *
 * The format is content-addressed (`MobixAssetSink` names an asset after its SHA-256), so writing one
 * needs its hash and its bytes but never both in memory at once. A 30-minute recording is ~160 MB of
 * WAV; reading it into a `ByteArray` to hash it, and again to deflate it, is what made saving a long
 * recording die with an `OutOfMemoryError` on a phone. A payload therefore carries *how to open* the
 * content — a file, an archive entry, or a small byte array — and the hashing, sniffing and writing
 * passes all read it in bounded chunks.
 *
 * A payload is immutable and safe to hand to more than one entry; [sha256] is computed at most once.
 */
class MobixPayload private constructor(
    /** The bytes when this payload already *is* them; null for a file- or archive-backed one. */
    private val cached: ByteArray?,
    /** The file this payload streams, when it is one. */
    private val file: File?,
    /** How to open the content when it is neither of the above. */
    private val opener: (() -> InputStream)?,
    /** The content's length when the caller knows it; a negative value means "count it when asked". */
    private val declaredLength: Long,
) {

    /** The payload's length in bytes — from the file, the declaration, or one counting read. */
    val length: Long by lazy {
        when {
            cached != null -> cached.size.toLong()
            file != null -> file.length()
            declaredLength >= 0L -> declaredLength
            else -> countBytes()
        }
    }

    /** Whether this payload is small enough to hand out as a `ByteArray` without a stream. */
    val isInMemory: Boolean get() = cached != null

    /** Open the payload's content. The caller closes it. */
    fun open(): InputStream {
        cached?.let { return it.inputStream() }
        file?.let { return it.inputStream() }
        val stream = opener?.invoke()
        return stream ?: throw FileNotFoundException("this payload has no content to open")
    }

    /**
     * The payload as a `ByteArray` — what a decoder or a small envelope entry wants, and **not** what a
     * writer wants (it streams [open] instead). Materialising a recording here is the mistake this
     * class exists to prevent, so only a caller that genuinely needs the bytes asks for them.
     */
    fun bytes(): ByteArray =
        cached ?: open().use { it.readBytes() }

    /** The first [count] bytes, for sniffing a container without reading the whole payload. */
    fun head(count: Int): ByteArray {
        cached?.let { return it.copyOfRange(0, minOf(count, it.size)) }
        return open().use { input ->
            val out = ByteArray(count)
            var read = 0
            while (read < count) {
                val n = input.read(out, read, count - read)
                if (n <= 0) break
                read += n
            }
            if (read == count) out else out.copyOf(read)
        }
    }

    /** The lowercase hex SHA-256 of the content — the name an asset is addressed by. */
    val sha256: String by lazy {
        val cached = cached
        if (cached != null) {
            MobixAssetSink.sha256(cached)
        } else {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(BUFFER_BYTES)
            open().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n > 0) digest.update(buffer, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /** The container the content is in, for an asset's entry name (`png`, `pdf`, `ogg`, `wav`, …). */
    val suffix: String by lazy { MobixAssetSink.suffixFor(head(SNIFF_BYTES)) }

    private fun countBytes(): Long {
        var total = 0L
        val buffer = ByteArray(BUFFER_BYTES)
        open().use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) total += n
            }
        }
        return total
    }

    companion object {
        /** The chunk a hashing/counting pass reads at a time — large enough to be cheap, small enough to be free. */
        private const val BUFFER_BYTES = 64 * 1024

        /** How many leading bytes are enough to name a container; `RIFF`/`OggS`/`ftyp` all sit in the first 12. */
        private const val SNIFF_BYTES = 16

        /** A payload that is already in memory (a page's JSON, a small picture, a test's fixture). */
        fun of(bytes: ByteArray): MobixPayload =
            MobixPayload(cached = bytes, file = null, opener = null, declaredLength = bytes.size.toLong())

        /** A payload that streams [file] — a recording or a background on disk, in any size. */
        fun of(file: File): MobixPayload =
            MobixPayload(cached = null, file = file, opener = null, declaredLength = file.length())

        /**
         * A payload read through [open] when a write asks for it — an `assets/` entry of an open
         * archive, whose bytes stay in the file until the entry is actually written.
         */
        fun ofSource(open: () -> InputStream?, length: Long = -1L): MobixPayload =
            MobixPayload(
                cached = null,
                file = null,
                opener = { open() ?: throw FileNotFoundException("this payload's entry is no longer open") },
                declaredLength = length,
            )

        /**
         * A payload read out of an **entry of a ZIP** (the staged `.mobix` a document was opened from),
         * opened on demand. This is what lets a Save As carry an asset the model still references
         * without materialising it: the archive is still on disk, so the entry can be inflated straight
         * into the write. [length] may be omitted when the caller does not know it.
         */
        fun ofArchiveEntry(archive: File, entry: String, length: Long = -1L): MobixPayload =
            MobixPayload(
                cached = null,
                file = null,
                opener = {
                    val zip = ZipFile(archive)
                    val found = zip.getEntry(entry)
                    if (found == null) {
                        zip.close()
                        throw FileNotFoundException("$entry is not in ${archive.name}")
                    }
                    // Closing the stream closes the archive with it: the whole entry is one read.
                    object : InputStream() {
                        private val inner = zip.getInputStream(found)
                        override fun read(): Int = inner.read()
                        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
                        override fun close() {
                            try {
                                inner.close()
                            } finally {
                                zip.close()
                            }
                        }
                    }
                },
                declaredLength = length,
            )
    }
}
