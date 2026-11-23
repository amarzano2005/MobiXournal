package com.mobixournal.render

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Thread-safe bounded LRU cache of recognized [HandwrittenWord]s keyed by a deterministic
 * 64-bit hash of each [InkLine]'s strokes.
 *
 * Unchanged lines across edits or reopened documents resolve in O(1) without calling the platform
 * recognizer; optional binary persistence ([saveTo] / [loadFrom]) preserves readings across restarts.
 */
class HandwritingLineCache(private val maxEntries: Int = 2048) {

    private val lock = Any()
    private val entries = object : LinkedHashMap<Long, List<HandwrittenWord>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<HandwrittenWord>>?): Boolean =
            size > maxEntries
    }

    /** Number of cached lines currently held in memory. */
    val size: Int
        get() = synchronized(lock) { entries.size }

    /** Clears all cached line readings. */
    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    /**
     * Indexes all pages in [doc], reusing cached line readings where the line's strokes are
     * unchanged and only invoking [engine] on uncached lines.
     */
    fun indexDocument(
        doc: Document,
        onProgress: ((Int, Int) -> Unit)? = null,
        isCancelled: () -> Boolean = { false },
        engine: InkWordEngine = InkEngines.platform,
    ): HandwritingIndex {
        val total = doc.pages.size
        val pages = ArrayList<List<HandwrittenWord>>(total)
        for (pageIndex in 0 until total) {
            if (isCancelled()) return HandwritingIndex(emptyList())
            onProgress?.invoke(pageIndex + 1, total)
            pages.add(recognizePageCached(doc.pages[pageIndex], isCancelled, engine))
        }
        return HandwritingIndex(pages)
    }

    /** Recognizes [page] line-by-line, reading from cache on hit and storing on miss. */
    fun recognizePageCached(
        page: Page,
        isCancelled: () -> Boolean = { false },
        engine: InkWordEngine = InkEngines.platform,
    ): List<HandwrittenWord> {
        val lines = HandwritingRecognizer.lines(page, isCancelled)
        if (lines.isEmpty() || isCancelled()) return emptyList()
        val out = ArrayList<HandwrittenWord>()
        for (line in lines) {
            if (isCancelled()) return emptyList()
            val key = stableLineKey(line.words)
            val cached = synchronized(lock) { entries[key] }
            if (cached != null) {
                out.addAll(cached)
            } else {
                val recognized = HandwritingRecognizer.recognize(line, engine, isCancelled)
                if (!isCancelled()) {
                    synchronized(lock) { entries[key] = recognized }
                    out.addAll(recognized)
                }
            }
        }
        return out
    }

    /** Persists the current cache entries to [file] in a compact binary format. */
    fun saveTo(file: File) {
        val snapshot: List<Pair<Long, List<HandwrittenWord>>> = synchronized(lock) {
            entries.entries.map { it.key to it.value }
        }
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            DataOutputStream(BufferedOutputStream(tmp.outputStream())).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(FORMAT_VERSION)
                out.writeInt(snapshot.size)
                for ((key, words) in snapshot) {
                    out.writeLong(key)
                    out.writeInt(words.size)
                    for (w in words) {
                        out.writeUTF(w.text)
                        out.writeDouble(w.bounds.left)
                        out.writeDouble(w.bounds.top)
                        out.writeDouble(w.bounds.right)
                        out.writeDouble(w.bounds.bottom)
                        out.writeDouble(w.confidence)
                        out.writeInt(w.candidates.size)
                        for (c in w.candidates) out.writeUTF(c)
                    }
                }
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
        }
    }

    /** Loads previously persisted cache entries from [file], ignoring corrupt or mismatched files. */
    fun loadFrom(file: File) {
        if (!file.isFile) return
        runCatching {
            DataInputStream(BufferedInputStream(file.inputStream())).use { inp ->
                if (inp.readInt() != MAGIC || inp.readInt() != FORMAT_VERSION) return
                val count = inp.readInt().coerceIn(0, maxEntries)
                val loaded = ArrayList<Pair<Long, List<HandwrittenWord>>>(count)
                repeat(count) {
                    val key = inp.readLong()
                    val wordCount = inp.readInt().coerceIn(0, 512)
                    val words = ArrayList<HandwrittenWord>(wordCount)
                    repeat(wordCount) {
                        val text = inp.readUTF()
                        val bounds = Bounds(inp.readDouble(), inp.readDouble(), inp.readDouble(), inp.readDouble())
                        val confidence = inp.readDouble()
                        val candCount = inp.readInt().coerceIn(0, 16)
                        val cands = ArrayList<String>(candCount)
                        repeat(candCount) { cands.add(inp.readUTF()) }
                        words.add(HandwrittenWord(text, bounds, confidence, cands))
                    }
                    loaded.add(key to words)
                }
                synchronized(lock) {
                    for ((k, v) in loaded) entries.putIfAbsent(k, v)
                }
            }
        }
    }

    companion object {
        private const val MAGIC = 0x4D484943 // "MHIC"
        private const val FORMAT_VERSION = 1

        /** Deterministic 64-bit FNV-1a hash of [words] across app restarts. */
        fun stableLineKey(words: List<List<Stroke>>): Long {
            var hash = -0x340d631b7bdddcdbL
            fun mix(v: Long) {
                hash = (hash xor v) * 0x100000001b3L
            }
            mix(words.size.toLong())
            for (word in words) {
                mix(word.size.toLong())
                for (stroke in word) {
                    mix(stroke.tool.ordinal.toLong())
                    mix(stroke.points.size.toLong())
                    for (pt in stroke.points) {
                        mix(java.lang.Double.doubleToLongBits(pt.x))
                        mix(java.lang.Double.doubleToLongBits(pt.y))
                    }
                }
            }
            return hash
        }
    }
}
