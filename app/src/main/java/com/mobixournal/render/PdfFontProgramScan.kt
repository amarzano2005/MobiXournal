package com.mobixournal.render

import java.io.File

/**
 * **Does this file promise a font program and not carry it?** — answered from the bytes, without opening the
 * PDF.
 *
 * The repair of a damaged PDF ([PdfFontRepair]) needs PDFBox, which means parsing every object: a price the
 * first open of a healthy PDF should not pay. The damage itself has a cheaper signature. A font program is a
 * stream object, so its dictionary entry is an indirect reference `/FontFile2 448 0 R` that can only point at
 * a top-level object — streams cannot live inside an object stream — and that object appears literally in the
 * file as `448 0 obj`. If every such reference finds its object, the file carries the programs it names and
 * needs no repair; one that does not is the truncated export the repair exists for.
 *
 * The scan reads the file's bytes and nothing else: no decompression, no object graph, no PDFBox, no string
 * copies. It is one pass when the file names no font program at all (the common case for a PDF typeset in
 * base-14 faces), two when it does: the references first, the object headers second. A reference that appears
 * inside a content stream or an annotation counts as suspicious too, and that costs only the slow path — the
 * repair then finds nothing to rebuild and the original file is used.
 */
internal object PdfFontProgramScan {

    /** True when [file] names a font program whose object is not in the file. */
    fun suspectsMissingProgram(file: File): Boolean {
        if (!file.isFile) return false
        val refs = HashSet<Long>()
        eachChunk(file) { bytes, length -> collectProgramRefs(bytes, length, refs) }
        if (refs.isEmpty()) return false
        val objects = HashSet<Long>()
        eachChunk(file) { bytes, length -> collectObjects(bytes, length, objects) }
        return refs.any { it !in objects }
    }

    /** `/FontFile`, `/FontFile2`, `/FontFile3` followed by `number generation R`. */
    private fun collectProgramRefs(bytes: ByteArray, length: Int, into: MutableSet<Long>) {
        var i = 0
        while (i < length) {
            if (bytes[i] == SLASH && matches(bytes, i, length, FONT_FILE)) {
                var at = i + FONT_FILE.size
                if (at < length && (bytes[at] == '2'.code.toByte() || bytes[at] == '3'.code.toByte())) at++
                val number = readNumber(bytes, at, length)
                val generation = number?.let { readNumber(bytes, it.second, length) }
                val after = generation?.let { skipBlanks(bytes, it.second, length) }
                if (number != null && generation != null && after != null && matches(bytes, after, length, R)) {
                    into.add(key(number.first, generation.first))
                    i = after
                }
            }
            i++
        }
    }

    /** Object headers, `448 0 obj`. */
    private fun collectObjects(bytes: ByteArray, length: Int, into: MutableSet<Long>) {
        var i = 0
        while (i < length) {
            val number = readNumber(bytes, i, length)
            if (number == null) {
                i++
                continue
            }
            val generation = readNumber(bytes, number.second, length)
            val after = generation?.let { skipBlanks(bytes, it.second, length) }
            if (generation != null && after != null && matches(bytes, after, length, OBJ)) {
                into.add(key(number.first, generation.first))
                i = after
            } else {
                i = maxOf(i + 1, number.second)
            }
        }
    }

    /** A run of digits starting at [from] (after any blanks), with the index just past it. */
    private fun readNumber(bytes: ByteArray, from: Int, length: Int): Pair<Long, Int>? {
        var at = skipBlanks(bytes, from, length)
        val start = at
        while (at < length && bytes[at].isDigit()) at++
        if (at == start || at - start > 10) return null
        return bytes.decodeNumber(start, at).let { it to at }
    }

    private fun ByteArray.decodeNumber(from: Int, to: Int): Long {
        var value = 0L
        for (i in from until to) value = value * 10 + (this[i] - '0'.code.toByte())
        return value
    }

    private fun Byte.isDigit(): Boolean = this >= '0'.code.toByte() && this <= '9'.code.toByte()

    private fun skipBlanks(bytes: ByteArray, from: Int, length: Int): Int {
        var at = from
        while (at < length && (bytes[at] == SPACE || bytes[at] == NEWLINE || bytes[at] == RETURN || bytes[at] == TAB)) at++
        return at
    }

    private fun matches(bytes: ByteArray, from: Int, length: Int, token: ByteArray): Boolean {
        if (from < 0 || from + token.size > length) return false
        for (i in token.indices) if (bytes[from + i] != token[i]) return false
        return true
    }

    /** Object number and generation as one value; a reference and its header agree byte for byte. */
    private fun key(number: Long, generation: Long): Long = number * 100_000L + generation

    /** Feed each window of the file's bytes, overlapping so a token cannot fall between two. */
    private fun eachChunk(file: File, action: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(CHUNK_BYTES + OVERLAP)
        file.inputStream().use { input ->
            var carried = 0
            while (true) {
                val read = input.read(buffer, carried, CHUNK_BYTES)
                if (read <= 0) {
                    if (carried > 0) action(buffer, carried)
                    return
                }
                val filled = carried + read
                action(buffer, filled)
                carried = minOf(OVERLAP, filled)
                System.arraycopy(buffer, filled - carried, buffer, 0, carried)
            }
        }
    }

    private val FONT_FILE = "/FontFile".toByteArray(Charsets.US_ASCII)
    private val OBJ = "obj".toByteArray(Charsets.US_ASCII)
    private val R = "R".toByteArray(Charsets.US_ASCII)

    private const val SLASH = '/'.code.toByte()
    private const val SPACE = ' '.code.toByte()
    private const val NEWLINE = '\n'.code.toByte()
    private const val RETURN = '\r'.code.toByte()
    private const val TAB = '\t'.code.toByte()
    private const val OVERLAP = 64
    private const val CHUNK_BYTES = 4 * 1024 * 1024
}
