package com.mobixournal.format.mobix

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The `.mobix` **container**: a ZIP archive of named parts whose type is declared by a stored
 * `mimetype` entry, exactly as `docs/mobix-schema-spec.md` specifies it. This is the envelope only —
 * it knows entry names, bytes and the format's own identity, and nothing at all about what the
 * `manifest.json` or a page's JSON *contains*. That is deliberate: the schema layer is a separate
 * step, so the part of the format that has to be right before anything else already is, and a change
 * to the element model cannot break it.
 *
 * Four rules from the spec are enforced here rather than remembered by callers:
 *
 *  1. **The mimetype is the first entry and STORED** uncompressed, so a reader that only wants to
 *     identify the file never inflates anything (its CRC and sizes are precomputed here because
 *     `ZipOutputStream` refuses a STORED entry without them).
 *  2. **The mimetype — not the file name — is the type.** Both a `.mobix` and a ZIP-package `.xopp`
 *     are ZIPs starting `PK\x03\x04`, so [mimetypeOf] is how a reader tells them apart; the suffix is
 *     a hint at most, since a SAF `content://` URI often has no usable name.
 *  3. **`manifest.json` is required** in both directions: [open] rejects an archive without one and
 *     [save] refuses to write one.
 *  4. **Unknown entries survive a rewrite.** [save] writes back every entry it is handed, in order,
 *     so a document written by a newer build (an entry this build does not model) is not silently
 *     stripped by opening and saving it here.
 *
 * Only the JDK's `java.util.zip` is used, like `XoppZip` — the format layer stays dependency-free
 * and JVM-testable (`docs/stack.md`).
 */
object MobixContainer {

    /** Thrown when an archive is not a readable `.mobix`: wrong type, or a required part missing. */
    class MalformedMobixException(message: String) : IllegalArgumentException(message)

    /**
     * One part of the archive: its entry [name] and the [payload] its bytes come from.
     *
     * The payload is a **source, not a copy** ([MobixPayload]): a page's JSON is bytes already in
     * memory, while a recording or a background PDF is a file that is hashed and deflated in bounded
     * chunks as the archive is written. A save therefore never holds a document's audio in the heap,
     * whatever its length.
     *
     * Compared by name, length and — when both sides are already in memory — content: two entries
     * read from two runs are what the round-trip tests compare, and asking a 160 MB recording for its
     * `ByteArray` just to compare entries would reintroduce the allocation this class avoids.
     */
    class Entry(val name: String, val payload: MobixPayload) {

        /** An entry whose bytes are already in memory — a page, the manifest, a container test. */
        constructor(name: String, bytes: ByteArray) : this(name, MobixPayload.of(bytes))

        /** The entry's bytes, materialised. Reading them back off a large payload is a caller's own cost. */
        val bytes: ByteArray get() = payload.bytes()

        val size: Int get() = payload.length.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entry) return false
            if (other.name != name || other.payload.length != payload.length) return false
            val mine = if (payload.isInMemory) payload.bytes() else null
            val theirs = if (other.payload.isInMemory) other.payload.bytes() else null
            return if (mine != null && theirs != null) mine.contentEquals(theirs) else true
        }

        override fun hashCode(): Int = 31 * name.hashCode() + payload.length.hashCode()

        override fun toString(): String = "Entry($name, $size bytes)"
    }

    /**
     * An opened `.mobix`: its type plus every payload entry, in archive order. The `mimetype` entry
     * is *not* in [entries] — it belongs to the container, is known from [mimetype], and is written
     * by [save] from [MobixFormat.MIMETYPE] rather than taken from a caller who could mislabel it.
     *
     * This reads the whole archive into memory, which is right for the manifest and pages it is
     * built for and wrong for a 200-page notebook — page-virtualisation streaming (the roadmap's
     * `docs/mobix-format.md`) reads entries on demand and is a separate, later piece. Keeping this
     * class the *envelope* is what lets that arrive without changing it.
     */
    class Archive(val mimetype: String, val entries: List<Entry>) {

        /** The entry [name], or null. */
        fun entry(name: String): Entry? = entries.firstOrNull { it.name == name }

        /** The bytes of entry [name], or null. */
        fun bytes(name: String): ByteArray? = entry(name)?.bytes

        /** The UTF-8 text of entry [name], or null. */
        fun text(name: String): String? = bytes(name)?.toString(Charsets.UTF_8)

        /** The entry [name], which this document must have — a missing one is a corrupt file. */
        fun require(name: String): Entry = entry(name)
            ?: throw MalformedMobixException("$name is missing from the .mobix archive")

        /** Every entry name, in archive order. */
        val names: List<String> get() = entries.map { it.name }
    }

    /**
     * The mimetype declared by the archive [input] is positioned at, or null when it declares none
     * (a plain ZIP, a ZIP-package `.xopp`, a document written by something that is not this app).
     *
     * Scanned rather than assumed to be first and inflated rather than assumed STORED: a writer must
     * put it first and uncompressed, but a *reader* that trusted that would fail on a file from any
     * other tool — and refusing a valid-looking document is the worse of the two errors. Reads from
     * the current position and does not close [input]; a caller that needs the stream afterwards
     * (to hand it to [open]) must rewind it.
     */
    fun mimetypeOf(input: InputStream): String? {
        ZipInputStream(input).let { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == MobixFormat.MIMETYPE_ENTRY) {
                    return zip.readBytes().toString(Charsets.UTF_8).trim()
                }
                zip.closeEntry()
            }
        }
    }

    /** Whether [input] is a `.mobix` rather than one of the other ZIP-shaped formats. */
    fun isMobix(input: InputStream): Boolean = mimetypeOf(input) == MobixFormat.MIMETYPE

    /**
     * Open the `.mobix` archive [input] is positioned at (not closed).
     *
     * Reads from the current position and does **not** close [input], like `XoppZip.open`: the caller
     * owns the stream (it may be a staging file it is about to rewind, or a network-backed one).
     *
     * @throws MalformedMobixException when the archive is not typed `application/vnd.mobixournal+zip`
     *   (i.e. it is some other ZIP — a ZIP-package `.xopp` fails here, which is the point), or when
     *   it declares the right type but carries no `manifest.json`.
     */
    fun open(input: InputStream): Archive {
        var mimetype: String? = null
        val entries = mutableListOf<Entry>()
        ZipInputStream(input).let { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    // Directory entries carry no bytes and the writer never needs them; skipping one
                    // loses nothing, since a ZIP's directory list is optional metadata.
                    entry.isDirectory -> Unit
                    entry.name == MobixFormat.MIMETYPE_ENTRY ->
                        mimetype = zip.readBytes().toString(Charsets.UTF_8).trim()
                    else -> entries += Entry(entry.name, zip.readBytes())
                }
                zip.closeEntry()
            }
        }
        val declared = mimetype ?: throw MalformedMobixException(
            "not a .mobix archive: it declares no ${MobixFormat.MIMETYPE_ENTRY} entry" +
                " (a ZIP-package .xopp is a ZIP too — see MobixContainer.mimetypeOf)",
        )
        if (declared != MobixFormat.MIMETYPE) {
            throw MalformedMobixException(
                "not a .mobix archive: its mimetype is \"$declared\", not \"${MobixFormat.MIMETYPE}\"",
            )
        }
        if (entries.none { it.name == MobixFormat.MANIFEST_ENTRY }) {
            throw MalformedMobixException(
                "${MobixFormat.MANIFEST_ENTRY} is missing: a .mobix without its manifest is not a document",
            )
        }
        return Archive(declared, entries)
    }

    /**
     * Write [entries] as a `.mobix` on [output]: the declared [MobixFormat.MIMETYPE], STORED and
     * first, then every payload entry **in the order given**, DEFLATED. The stream is finished and
     * closed — a ZIP is only readable once its central directory has been written.
     *
     * Two things are refused rather than written quietly, because a file this app produces has to be
     * a document: [entries] must carry [MobixFormat.MANIFEST_ENTRY], and no entry may be named
     * `mimetype` (the container writes that one — a caller-supplied copy could only mislabel it).
     */
    fun save(entries: List<Entry>, output: OutputStream) {
        require(entries.any { it.name == MobixFormat.MANIFEST_ENTRY }) {
            "a .mobix needs a ${MobixFormat.MANIFEST_ENTRY} entry"
        }
        require(entries.none { it.name == MobixFormat.MIMETYPE_ENTRY }) {
            "${MobixFormat.MIMETYPE_ENTRY} is the container's own entry — it is written from MobixFormat.MIMETYPE"
        }
        require(entries.distinctBy { it.name }.size == entries.size) {
            "duplicate entry names: ${entries.groupBy { it.name }.filterValues { it.size > 1 }.keys}"
        }
        ZipOutputStream(output).use { zip ->
            putStored(zip, MobixFormat.MIMETYPE_ENTRY, MobixFormat.MIMETYPE.toByteArray(Charsets.UTF_8))
            for (entry in entries) {
                zip.putNextEntry(ZipEntry(entry.name))
                // Streamed, never handed over as one array: a recording is deflated as it is read, so
                // the write costs a 64 KiB buffer whatever the entry weighs.
                entry.payload.open().use { input ->
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n > 0) zip.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    /** [save] over an [archive]'s payload. */
    fun save(archive: Archive, output: OutputStream) = save(archive.entries, output)

    /** [archive] as a `.mobix` byte array — the shape a caller stages to a file or a URI. */
    fun toBytes(entries: List<Entry>): ByteArray =
        java.io.ByteArrayOutputStream().also { save(entries, it) }.toByteArray()

    /** [toBytes] over an [archive]'s payload. */
    fun toBytes(archive: Archive): ByteArray = toBytes(archive.entries)

    /** The chunk a streamed entry is written in, so no payload is ever held whole. */
    private const val STREAM_BUFFER_BYTES = 64 * 1024

    /**
     * A STORED entry: no compression, with the size and CRC the ZIP format requires for one (a
     * DEFLATED entry lets the stream fill those in; a STORED entry cannot, because the reader must
     * know the length before it has read the bytes).
     */
    private fun putStored(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes) }
        zip.putNextEntry(
            ZipEntry(name).apply {
                method = ZipEntry.STORED
                size = bytes.size.toLong()
                compressedSize = bytes.size.toLong()
                this.crc = crc.value
            },
        )
        zip.write(bytes)
        zip.closeEntry()
    }
}
