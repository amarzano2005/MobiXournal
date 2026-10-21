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
interface MobixEntrySource : AutoCloseable {
    /** All entry names present in the archive central directory. */
    val entryNames: Set<String>

    /** Open an uncompressed stream for the entry named [name], or null if absent. */
    fun openEntry(name: String): InputStream?

    /** Read the entire uncompressed payload for entry [name], or null if absent. */
    fun readBytes(name: String): ByteArray? = openEntry(name)?.use { it.readBytes() }

    /**
     * The entry [name] **as the archive stores it**, or null when this source cannot hand it over — an
     * in-memory map holds inflated bytes and nothing else. A repack copies what it is given verbatim
     * instead of inflating it and deflating it again, which is what makes an incremental save cheaper
     * than a full write rather than more expensive ([MobixIncrementalSave]).
     *
     * A decorator source **must forward it**, or everything it wraps silently loses that path.
     */
    fun rawEntry(name: String): MobixRawEntry? = null

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
    override fun rawEntry(name: String): MobixRawEntry? = raw.rawEntry(name)

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
    override fun rawEntry(name: String): MobixRawEntry? =
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
 * **A chunked document reads the same way** ([infinite-canvas.md](docs/infinite-canvas.md)): the parts
 * a `.mobix` declares are its `pages[]` for a paginated document and its `chunks[]` for an infinite
 * one, and the two differ only in how a part becomes a [Page] — a chunk decodes through
 * [MobixChunkCodec] and hands back the page it stands for, which is the shape the rest of the app
 * works in. The coordinates stay where they are (the entry name, and the manifest's own record of it),
 * and are read out of the entry name rather than assumed, so a document written onto a 2D board comes
 * back at its own coordinates. [isPaginated] is the one place the two modes are told apart.
 *
 * @param source The underlying entry source exposing archive entries on demand.
 * @param cacheBudget The maximum number of materialised pages kept in memory simultaneously.
 */
class MobixPagedReader(
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
        // A file that carries both is invalid, whichever mode it claims (`MobixDocument.open` is the
        // other half of this rule, for the whole-archive read).
        if (isPaginated && manifest.chunks.isNotEmpty()) {
            throw MobixContainer.MalformedMobixException(
                "PAGINATED document carries chunks/ entries; a file that carries both is invalid",
            )
        }
        if (!isPaginated && manifest.pages.isNotEmpty()) {
            throw MobixContainer.MalformedMobixException(
                "${manifest.canvasMode} document carries pages/ entries; a file that carries both is invalid",
            )
        }

        val canvasBytes = source.readBytes(MobixFormat.CANVAS_ENTRY)
        canvas = if (canvasBytes != null) {
            MobixCanvasFile.decode(canvasBytes.toString(Charsets.UTF_8))
        } else {
            MobixCanvasFile(mode = CanvasMode.PAGINATED, widthPt = 595.0, heightPt = 842.0)
        }
    }

    /** True when the content is `pages/`; false when it is `chunks/` ([CanvasMode]). */
    val isPaginated: Boolean get() = manifest.canvasMode == CanvasMode.PAGINATED

    /**
     * The parts this document declares, in document order: `pages[]` for a paginated document,
     * `chunks[]` for an infinite one. Counting, ids and the save's reuse plan all read this — which is
     * what makes "a chunk is a page" a fact of one list rather than a special case in each of them.
     */
    val parts: List<MobixPart> get() = if (isPaginated) manifest.pages else manifest.chunks

    /** Total number of pages (or chunks) declared in the document manifest. */
    val pageCount: Int get() = parts.size

    /** Stable manifest identifier of page [pageIndex], or null if index is out of bounds. */
    fun pageId(pageIndex: Int): String? = parts.getOrNull(pageIndex)?.id

    /** Manifest part descriptor for page [pageIndex], or null if index is out of bounds. */
    fun pagePart(pageIndex: Int): MobixPart? = parts.getOrNull(pageIndex)

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

        val part = parts[pageIndex]
        val bytes = source.readBytes(part.entry)
            ?: throw MobixContainer.MalformedMobixException("page entry ${part.entry} is missing from archive")
        val node = JsonParser.parse(bytes.toString(Charsets.UTF_8)) as? JsonNode.Obj
            ?: throw MobixContainer.MalformedMobixException("${part.entry} is not a JSON object")

        val page = pageOf(part, node)
        cache[pageIndex] = page
        return page
    }

    /**
     * The [Page] a part stands for: a page entry decodes as itself, a chunk entry decodes as the page
     * it holds. Its coordinates are read out of the entry name (the only place a chunk has any), so a
     * chunk that arrives under a name nothing can parse keeps its position in the manifest order and
     * no invented coordinates.
     */
    private fun pageOf(part: MobixPart, node: JsonNode.Obj): Page = if (isPaginated) {
        MobixPageCodec.decode(node, assetSource).copy(id = part.id)
    } else {
        val (cx, cy) = MobixFormat.parseChunkEntry(part.entry) ?: (0 to 0)
        MobixChunkCodec.decode(node, assetSource, fallbackCx = cx, fallbackCy = cy).toPage().copy(id = part.id)
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
