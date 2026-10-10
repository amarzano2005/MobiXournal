package com.mobixournal.render

import java.io.File
import java.lang.ref.SoftReference

/**
 * The extracted outlines of the PDFs currently open, keyed by file — the [PdfTextIndexCache] of the
 * bookmark tree, and for the same reasons.
 *
 * Walking an outline is cheap next to the text layer, but mirroring a PDF-backed document across the
 * split panes asks for it twice, and the result is immutable and reusable. Entries are held
 * **softly**: an outline nobody is using is a pure cache and may be dropped under memory pressure,
 * in which case the next open simply re-extracts.
 */
object PdfOutlineCache {

    private val entries = HashMap<String, SoftReference<PdfOutline>>()

    /** The outline already extracted for [file], or null if it has not been (or has been reclaimed). */
    fun get(file: File): PdfOutline? = synchronized(entries) {
        val key = file.absolutePath
        val hit = entries[key]?.get()
        if (hit == null) entries.remove(key)
        hit
    }

    /** Record [outline] as [file]'s outline, for the other views of the same PDF. */
    fun put(file: File, outline: PdfOutline) {
        synchronized(entries) { entries[file.absolutePath] = SoftReference(outline) }
    }

    /** Drop the entry — the PDF's bytes may have been replaced (merge, re-import). */
    fun forget(file: File) {
        synchronized(entries) { entries.remove(file.absolutePath) }
    }
}
