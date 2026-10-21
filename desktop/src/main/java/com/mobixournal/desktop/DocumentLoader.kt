package com.mobixournal.desktop

import com.mobixournal.format.Xopp
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import java.io.File

/** A document the shell has open, with the file it came from. */
data class DesktopDocument(val file: File, val document: Document) {
    val pageCount: Int get() = document.pages.size

    /** How many ink strokes the whole document holds — the preview's "is this really my page" figure. */
    val strokeCount: Int
        get() = document.pages.sumOf { page ->
            page.layers.sumOf { layer -> layer.elements.count { it is Stroke } }
        }
}

/**
 * Opening a file, with the preview's limits stated instead of guessed at.
 *
 * `.xopp` is the format the app already writes and this shell can read today: gzip + XML through the
 * app's own [Xopp] reader, which is also what the Android editor uses. A self-contained ZIP `.xopp`
 * and a native `.mobix` both need their container paths wired in (`format/XoppZip.kt`,
 * `format/mobix/`) and are reported as unsupported rather than failing deep inside a reader.
 */
object DocumentLoader {

    fun load(file: File): DesktopDocument {
        require(file.isFile) { "File non trovato: ${file.path}" }
        require(!file.extension.equals("mobix", ignoreCase = true)) {
            "I documenti .mobix non sono ancora aperti da questa anteprima: usa un file .xopp."
        }
        require(!isZip(file)) {
            "Questo è un .xopp ZIP autoconsistente: l'anteprima apre per ora il .xopp gzip."
        }
        return DesktopDocument(file, Xopp.open(file.inputStream().buffered()))
    }

    /** A ZIP `.xopp` starts with `PK`; a gzip `.xopp` with `1f 8b`, and XML is neither. */
    private fun isZip(file: File): Boolean {
        val magic = file.inputStream().use { input ->
            ByteArray(2).also { input.read(it) }
        }
        return magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
    }
}
