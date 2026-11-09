package com.mobixournal.desktop.io

import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile

/** Private checkpoint marker in the ZIP comment; it cannot overwrite any document entry. */
internal object RecoveryZipMarker {
    private const val PREFIX = "mobix-recovery:"
    fun read(file: File): Long = if (!file.isFile) 0L else ZipFile(file).use { zip ->
        zip.comment?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.toLongOrNull() ?: 0L
    }
    fun write(file: File, sequence: Long) {
        val comment = (PREFIX + sequence).toByteArray(Charsets.US_ASCII)
        RandomAccessFile(file, "rw").use { data ->
            val length = minOf(data.length(), 65557L).toInt()
            val tail = ByteArray(length)
            val start = data.length() - length
            data.seek(start); data.readFully(tail)
            fun byte(i: Int) = tail[i].toInt() and 255
            val footer = (length - 22 downTo 0).firstOrNull { i ->
                byte(i) == 0x50 && byte(i + 1) == 0x4b && byte(i + 2) == 5 && byte(i + 3) == 6 &&
                    i + 22 + byte(i + 20) + (byte(i + 21) shl 8) == length
            } ?: error("Recovery checkpoint has no ZIP footer")
            data.seek(start + footer + 20)
            data.write(comment.size and 255); data.write(comment.size ushr 8)
            data.write(comment)
            data.setLength(start + footer + 22 + comment.size)
        }
    }
}
