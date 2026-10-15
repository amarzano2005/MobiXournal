package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.model.Page
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Collections
import java.util.LinkedHashMap
import java.util.zip.ZipFile

/**
 * Accessor abstraction for ZIP entries of a `.mobix` archive.
 * Allows reading the central directory / entry names without inflating payloads.
 */
internal interface MobixEntrySource : AutoCloseable {
    /** All entry names present in the archive central directory. */
    val entryNames: Set<String>

    /** Open an uncompressed stream for the entry named [name], or null if absent. */
    fun openEntry(name: String): InputStream?

    /** Read the entire uncompressed payload for entry [name], or null if absent. */
    fun readBytes(name: String): ByteArray? = openEntry(name)?.use { it.readBytes() }

    /**
     * The bytes of [name] **as the archive stores them**, or null when this source cannot hand them
     * over — an in-memory map holds inflated bytes and nothing else. A repack copies what it is given
     * verbatim instead of inflating it and deflating it again ([MobixZip]): this is what makes an
     * incremental save cheaper than a full write rather than more expensive.
     */
    fun rawEntry(name: String): MobixZip.RawEntry? = null

    companion object {
        /**
         * Create an entry source from a physical [File] using [ZipFile].
         * [ZipFile] reads the central directory at the end of the archive and only inflates entries on demand.
         */
        fun fromFile(file: File): MobixEntrySource = ZipFileEntrySource(ZipFile(file), file)

        /**
         * In-memory entry source for tests or synthetic fixtures.
         */
        fun fromMap(entries: Map<String, ByteArray>): MobixEntrySource = MapEntrySource(entries)
    }
}

private class ZipFileEntrySource(private val zip: ZipFile, file: File) : MobixEntrySource {
    override val entryNames: Set<String> = zip.entries().asSequence().map { it.name }.toSet()

    /**
     * The file's central directory, held for as long as this source is: a save copies an entry through
     * it once per unchanged part, and reading the directory per copy is what made that quadratic
     * (`MobixZip.Index`).
     */
    private val raw = MobixZip.Index(file)

    override fun openEntry(name: String): InputStream? =
        zip.getEntry(name)?.let { zip.getInputStream(it) }

    /** The file's own bytes, for a caller that wants an entry the way the archive stores it. */
    override fun rawEntry(name: String): MobixZip.RawEntry? = raw.rawEntry(name)

    override fun close() {
        zip.close()
        raw.close()
    }
}

private class MapEntrySource(private val entries: Map<String, ByteArray>) : MobixEntrySource {
    override val entryNames: Set<String> = entries.keys

    override fun openEntry(name: String): InputStream? =
        entries[name]?.let { ByteArrayInputStream(it) }

    override fun close() = Unit
}

/**
 * Decorator around [MobixEntrySource] that tracks how many times each entry has been opened or read.
 * Used in tests to verify that opening a 200-page notebook does not inflate untouched pages.
 *
 * [openCount] counts every read — an inflation *and* a raw copy, since both are a read of that entry.
 * [inflateCount] counts the inflations alone, which is the question a *save* asks: a page whose JSON was
 * never rebuilt is a page this source never opened, however often it was copied out of the file
 * (`MobixPageReuse`).
 */
internal class TrackedEntrySource(val delegate: MobixEntrySource) : MobixEntrySource {
    private val counts = Collections.synchronizedMap(mutableMapOf<String, Int>())
    private val inflates = Collections.synchronizedMap(mutableMapOf<String, Int>())

    override val entryNames: Set<String> get() = delegate.entryNames

    override fun openEntry(name: String): InputStream? {
        counts[name] = (counts[name] ?: 0) + 1
        inflates[name] = (inflates[name] ?: 0) + 1
        return delegate.openEntry(name)
    }

    /** A raw copy is still a read of that entry, so it is counted like any other. */
    override fun rawEntry(name: String): MobixZip.RawEntry? =
        delegate.rawEntry(name)?.also { counts[name] = (counts[name] ?: 0) + 1 }

    /** How many times [name] was opened/inflated. */
    fun openCount(name: String): Int = counts[name] ?: 0

    /** How many times [name] was **inflated** — a read that decoded it, not a byte copy. */
    fun inflateCount(name: String): Int = inflates[name] ?: 0

    override fun close() = delegate.close()
}

/**
 * Paged, on-demand reader for paginated `.mobix` documents.
 *
 * Rather than decompressing every page into memory upfront (which scales poorly for 200-page
 * notebooks), this reader parses only the container metadata (`mimetype`, `manifest.json`,
 * `canvas.json`) and materialises individual [Page] instances on demand into an LRU cache window.
 *
 * Pages outside the active window are evicted from memory; navigating back to an evicted page
 * re-reads its JSON entry from disk losslessly.
 *
 * @param source The underlying entry source exposing archive entries on demand.
 * @param cacheBudget The maximum number of materialised pages kept in memory simultaneously.
 */
internal class MobixPagedReader(
    val source: MobixEntrySource,
    val cacheBudget: Int = DEFAULT_CACHE_BUDGET,
) : AutoCloseable {

    val manifest: MobixManifest
    val canvas: MobixCanvasFile

    /** The asset resolver for binary payloads bundled in `assets/`. */
    val assetSource: MobixAssetSource = MobixAssetSource { name ->
        val path = if (name.startsWith(MobixFormat.ASSETS_DIR)) name else "${MobixFormat.ASSETS_DIR}$name"
        source.readBytes(path)
    }

    private val cache = object : LinkedHashMap<Int, Page>(cacheBudget, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Page>?): Boolean =
            size > cacheBudget
    }

    init {
        val mimetype = source.readBytes(MobixFormat.MIMETYPE_ENTRY)?.toString(Charsets.UTF_8)?.trim()
            ?: throw MobixContainer.MalformedMobixException("not a .mobix archive: missing mimetype entry")
        if (mimetype != MobixFormat.MIMETYPE) {
            throw MobixContainer.MalformedMobixException(
                "not a .mobix archive: mimetype is \"$mimetype\", expected \"${MobixFormat.MIMETYPE}\"",
            )
        }

        val manifestBytes = source.readBytes(MobixFormat.MANIFEST_ENTRY)
            ?: throw MobixContainer.MalformedMobixException("missing ${MobixFormat.MANIFEST_ENTRY}")
        manifest = MobixManifest.decode(manifestBytes.toString(Charsets.UTF_8), assetSource)

        if (manifest.formatVersion > MobixManifest.CURRENT_FORMAT_VERSION) {
            throw MobixContainer.MalformedMobixException(
                "formatVersion ${manifest.formatVersion} is newer than supported ${MobixManifest.CURRENT_FORMAT_VERSION}",
            )
        }
        if (manifest.canvasMode != CanvasMode.PAGINATED) {
            throw MobixContainer.MalformedMobixException(
                "canvasMode is ${manifest.canvasMode}; only PAGINATED is supported by page reader",
            )
        }

        val canvasBytes = source.readBytes(MobixFormat.CANVAS_ENTRY)
        canvas = if (canvasBytes != null) {
            MobixCanvasFile.decode(canvasBytes.toString(Charsets.UTF_8))
        } else {
            MobixCanvasFile(mode = CanvasMode.PAGINATED, widthPt = 595.0, heightPt = 842.0)
        }
    }

    /** Total number of pages declared in the document manifest. */
    val pageCount: Int get() = manifest.pages.size

    /** Stable manifest identifier of page [pageIndex], or null if index is out of bounds. */
    fun pageId(pageIndex: Int): String? = manifest.pages.getOrNull(pageIndex)?.id

    /** Manifest part descriptor for page [pageIndex], or null if index is out of bounds. */
    fun pagePart(pageIndex: Int): MobixPart? = manifest.pages.getOrNull(pageIndex)

    /** Returns true if page [pageIndex] is currently materialised in the in-memory LRU cache. */
    @Synchronized
    fun isPageMaterialized(pageIndex: Int): Boolean = cache.containsKey(pageIndex)

    /** Returns the set of page indices currently materialised in the cache. */
    @Synchronized
    fun materializedIndices(): Set<Int> = cache.keys.toSet()

    /**
     * Retrieve the materialised [Page] at [pageIndex] (0-based).
     * If the page is already cached, it is returned immediately. Otherwise, its JSON entry is
     * inflated and decoded on demand, updating the LRU cache.
     */
    @Synchronized
    fun getPage(pageIndex: Int): Page {
        require(pageIndex in 0 until pageCount) { "page index $pageIndex out of bounds (0..${pageCount - 1})" }
        cache[pageIndex]?.let { return it }

        val part = manifest.pages[pageIndex]
        val bytes = source.readBytes(part.entry)
            ?: throw MobixContainer.MalformedMobixException("page entry ${part.entry} is missing from archive")
        val node = JsonParser.parse(bytes.toString(Charsets.UTF_8)) as? JsonNode.Obj
            ?: throw MobixContainer.MalformedMobixException("${part.entry} is not a JSON object")

        val page = MobixPageCodec.decode(node, assetSource).copy(id = part.id)
        cache[pageIndex] = page
        return page
    }

    /**
     * Materialise an active sliding window of pages centered around [centerIndex] with [radius]
     * (default $\pm 2$ pages). Returns the list of pages in the window in document order.
     */
    @Synchronized
    fun ensureWindow(centerIndex: Int, radius: Int = DEFAULT_WINDOW_RADIUS): List<Page> {
        if (pageCount == 0) return emptyList()
        val start = (centerIndex - radius).coerceAtLeast(0)
        val end = (centerIndex + radius).coerceAtMost(pageCount - 1)
        return (start..end).map { getPage(it) }
    }

    /** Explicitly evicts page [pageIndex] from the memory cache. */
    @Synchronized
    fun evict(pageIndex: Int) {
        cache.remove(pageIndex)
    }

    /** Clears all materialised pages from memory. */
    @Synchronized
    fun clearCache() {
        cache.clear()
    }

    override fun close() {
        clearCache()
        source.close()
    }

    companion object {
        /** Default sliding window radius around current page ($\pm 2$ pages). */
        const val DEFAULT_WINDOW_RADIUS = 2

        /** Default in-memory page cache budget (current page $\pm 2$ = 5 pages). */
        const val DEFAULT_CACHE_BUDGET = 5

        /** Open a `.mobix` file using paged on-demand reading. */
        fun open(file: File, cacheBudget: Int = DEFAULT_CACHE_BUDGET): MobixPagedReader =
            MobixPagedReader(MobixEntrySource.fromFile(file), cacheBudget)
    }
}
