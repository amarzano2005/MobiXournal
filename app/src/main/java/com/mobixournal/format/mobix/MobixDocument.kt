package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.DEFAULT_CREATOR
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.withIdentity
import com.mobixournal.format.model.withIdentityOn
import java.io.OutputStream

/**
 * A `.mobix` **document**: the envelope (manifest, canvas), the pages, the payloads they reference, and
 * whatever this build did not model. This is the layer above the codecs — the one that knows a document
 * is an archive with an order and a set of parts, and that turns the app's own [Document] into one and
 * back.
 *
 * Three things it is responsible for, none of which belongs in a codec:
 *
 *  - **The manifest's `pages[]` is derived from what is being written**, never copied wholesale from
 *    what was read. A save recomputes each page's `sha256`, keeps an unchanged page's `modified` stamp
 *    (so a sync client does not see an untouched page as an edit — `docs/sync-and-storage.md`) and
 *    stamps only the pages whose bytes actually changed. A page a save **copies** out of the archive
 *    it came from keeps the part that archive recorded for it ([MobixPageReuse]), `sha256` included —
 *    which is the same statement, made one step earlier: the hash is of the bytes being copied.
 *  - **Page identity is stable.** A page that arrives without an id gets one, and the id then lives in
 *    the manifest (`docs/mobix-schema-spec.md`), so a page can be reordered without becoming a
 *    different page to a merge. Its position is not its identity.
 *  - **Nothing a newer build wrote is dropped.** Entries this build does not model are kept verbatim
 *    and written back last ([preserved]), which is container rule 4, and every payload that was
 *    referenced keeps its entry even when the model never looked at it.
 *
 * Both **paginated** and **infinite-canvas** documents (`INFINITE_VERTICAL`, `INFINITE_HORIZONTAL`,
 * `INFINITE_2D`) are supported: paginated documents store their content in `pages/`, while infinite
 * documents store spatial tiles in `chunks/` (`docs/infinite-canvas.md`).
 *
 * Pure Kotlin, no `java.io.File`, like the rest of `format/`: the same code runs on-device and in JVM
 * unit tests.
 */
internal class MobixDocument(
    /** The envelope as read (or as built for a new document); its `pages[]` or `chunks[]` is rewritten by [encode]. */
    val manifest: MobixManifest,

    /** The geometry sidecar (`canvas.json`). */
    val canvas: MobixCanvasFile,

    /** The pages in document order, each carrying its manifest id. Empty for infinite canvas modes. */
    val pages: List<Page> = emptyList(),

    /** The spatial chunks for infinite canvas modes, in document order. Empty for paginated mode. */
    val chunks: List<MobixChunk> = emptyList(),

    /** What the source `.xopp` was, when this document came from one; null for a native document. */
    val bridge: MobixBridgeFile? = null,

    /** Payloads by entry name, as they were read — a caller adopts them (`docs/data-path.md`). */
    val payloads: Map<String, ByteArray> = emptyMap(),

    /** Entries this build does not model, in archive order: kept, and written back untouched. */
    val preserved: List<MobixContainer.Entry> = emptyList(),

    /**
     * The streaming entry source the `assets/` payloads are **deferred** to, or null when every
     * payload was already in memory (an [Archive][MobixContainer.Archive]-based open). A document
     * opened [from a source][open] holds no asset bytes until someone asks for one ([asset]) or the
     * document is written ([encode]), which is what keeps opening a notebook with a bundled PDF or a
     * photo from inflating bytes nothing has looked at yet (`docs/data-path.md`).
     */
    private val assetEntries: MobixEntrySource? = null,
) {

    /** The `assets/` entry names the archive carries, without inflating any of them. */
    private val deferredAssetNames: List<String>
        get() = assetEntries?.entryNames?.filter { it.startsWith(MobixFormat.ASSETS_DIR) }?.toList().orEmpty()

    /** The bytes of the bundled payload [name], or null when the document does not carry it. */
    fun asset(name: String): ByteArray? = payloads[name] ?: readDeferred(name)

    /** The payload entry names, in archive order. */
    val assetNames: List<String> get() = (payloads.keys + deferredAssetNames).toList()

    /**
     * Every bundled asset as bytes, **materialising** any payload deferred to [assetEntries]. A caller
     * that has to combine documents (a merge, an asset bundler) needs the bytes and pays for them here;
     * opening alone does not.
     */
    val allPayloads: Map<String, ByteArray>
        get() = if (assetEntries == null) payloads
        else payloads + deferredAssetNames.associateWith { readDeferred(it) ?: ByteArray(0) }

    private fun readDeferred(name: String): ByteArray? {
        val entry = if (name.startsWith(MobixFormat.ASSETS_DIR)) name else "${MobixFormat.ASSETS_DIR}$name"
        return assetEntries?.readBytes(entry)
    }


    /**
     * The archive this document is, as entries ready for [MobixContainer.save] — `manifest.json` first,
     * then `canvas.json`, the pages or chunks in document order, the assets, and the preserved entries last.
     *
     * The asset sink is **one** sink for the whole document, so a picture pasted on ten pages is written
     * once (`MobixAssets`), and a payload read from a file and never inspected is still re-emitted: a
     * reference the model kept (`Background.Pdf.bundledAsset`) must not point at an entry the save
     * dropped.
     *
     * @param nowMillis the timestamp written into the manifest and into every page whose bytes changed.
     * @param backgroundBytes supplies a background's own bytes when the caller's stores can (the app's
     *   `ImageStore`/`PdfStore` do; a pure format test passes none), so a document can be written
     *   self-contained.
     * @param audioBytes supplies a recording's bytes for the sidecar name a stroke carries (the app's
     *   `AudioStore` does), so a `.mobix` carries its audio instead of pointing at a file that has to sit
     *   beside it (`docs/audio-inking-sync.md`); the stroke then names the bundled entry *and* keeps the
     *   sidecar name the `.xopp` export writes a file for. A caller with no audio passes nothing.
     * @param reuse the pages the source archive already holds ([MobixPageReuse]): each one contributes
     *   **its source part and no entry**, so its JSON is neither rebuilt nor hashed here, and the repack
     *   streams the bytes out of the file instead. The plan's parts are the manifest's own record of
     *   those pages, `sha256` included, which is what makes this exact rather than a guess.
     */
    fun encode(
        nowMillis: Long,
        backgroundBytes: (Background) -> ByteArray? = { null },
        audioBytes: (String) -> ByteArray? = { null },
        reuse: MobixPageReuse = MobixPageReuse.none,
    ): MobixSavePlan {
        val sink = MobixAssetSink()
        val parts = if (manifest.canvasMode == CanvasMode.PAGINATED) {
            encodePages(sink, backgroundBytes, audioBytes, nowMillis, reuse)
        } else {
            encodeChunks(sink, backgroundBytes, audioBytes, nowMillis)
                .map { (part, entry) -> BuiltPart(part, entry) }
        }
        val written = sink.entries()
        // Every payload the model carries is re-emitted: an asset still deferred to the source is read
        // here, so a reference the model kept can never point at an entry the save dropped.
        val carried = allPayloads.filterKeys { name -> written.none { it.name == name } }
            .map { (name, bytes) -> MobixContainer.Entry(name, bytes) }

        val envelope = mutableListOf(
            MobixContainer.Entry(
                name = MobixFormat.MANIFEST_ENTRY,
                bytes = writtenManifest(manifest, parts.map { it.part }, nowMillis).encode()
                    .toByteArray(Charsets.UTF_8),
            ),
            MobixContainer.Entry(MobixFormat.CANVAS_ENTRY, canvas.encode().toByteArray(Charsets.UTF_8)),
        )
        // The source record is written whenever there is one, so what this document came from is still
        // known to whoever exports it back (`MobixBridgeFile`).
        bridge?.let { out ->
            envelope += MobixContainer.Entry(MobixFormat.BRIDGE_ENTRY, out.encode().toByteArray(Charsets.UTF_8))
        }

        // A reused part has no entry of its own: its bytes are in the archive being repacked over, and
        // the order below is what keeps it in the file (`MobixSavePlan`).
        val modelled = envelope + parts.mapNotNull { it.entry } + written + carried

        // A preserved entry can only collide with a part this build writes when the two describe the
        // same slot — an orphan page entry the manifest no longer lists, while the document has a page
        // at that index. A ZIP cannot hold the name twice, and the modelled part is the document, so the
        // orphan is the one that goes; container rule 4 protects entries this build has no part for.
        val taken = modelled.map { it.name }.toSet()
        val preserved = preserved.filterNot { it.name in taken }
        return MobixSavePlan(
            entries = modelled + preserved,
            order = envelope.map { it.name } + parts.map { it.part.entry } +
                written.map { it.name } + carried.map { it.name } + preserved.map { it.name },
        )
    }

    /** This document as a `.mobix` byte array — the shape a caller stages to a file or a URI. */
    fun toBytes(
        nowMillis: Long,
        backgroundBytes: (Background) -> ByteArray? = { null },
        audioBytes: (String) -> ByteArray? = { null },
    ): ByteArray = MobixContainer.toBytes(encode(nowMillis, backgroundBytes, audioBytes).entries)

    /** Write this document as a `.mobix` on [output] (which is finished and not left open). */
    fun save(
        output: OutputStream,
        nowMillis: Long,
        backgroundBytes: (Background) -> ByteArray? = { null },
        audioBytes: (String) -> ByteArray? = { null },
    ) = MobixContainer.save(encode(nowMillis, backgroundBytes, audioBytes).entries, output)

    /**
     * The app's document model: the pages and the title, plus the source `.xopp`'s creator, file version
     * and preview when there was one — so a file that travelled through this app is still the file its
     * owner had ([MobixBridgeFile]) — and **this document's own identity**, which is what lets a save of
     * it be an edit rather than a duplicate ([Document.documentId]).
     */
    fun toDocument(): Document = Document(
        creator = bridge?.creator ?: DEFAULT_CREATOR,
        fileVersion = bridge?.fileVersion ?: "4",
        pages = if (pages.isNotEmpty()) pages else chunks.map { it.toPage() },
        preview = bridge?.preview,
        title = manifest.title,
        documentId = manifest.documentId,
    )

    // ---- pages -----------------------------------------------------------------------------------

    /** Each page paired with the `pages[]` entry describing it, in document order. */
    private fun encodePages(
        sink: MobixAssetSink,
        backgroundBytes: (Background) -> ByteArray?,
        audioBytes: (String) -> ByteArray?,
        nowMillis: Long,
        reuse: MobixPageReuse,
    ): List<BuiltPart> = (0 until pages.size).map { index ->
        // Read by index, never by iterating the list: an untouched page of a document opened page by
        // page is a page this save must not even *open*, and the list's own iterator would inflate and
        // decode every page of the document before this loop's body ever ran.
        reuse.reused[index]?.let { part -> return@map BuiltPart(part, entry = null) }
        val page = pages[index]
        val json = JsonWriter.write(MobixPageCodec.encode(page, sink, backgroundBytes, audioBytes))
            .toByteArray(Charsets.UTF_8)
        val name = MobixFormat.pageEntry(index + 1)
        BuiltPart(
            part = pagePartFor(manifest, page, index, name, MobixAssetSink.sha256(json), nowMillis),
            entry = MobixContainer.Entry(name, json),
        )
    }

    // ---- chunks ----------------------------------------------------------------------------------

    /** Each chunk paired with the `chunks[]` entry describing it, in document order. */
    private fun encodeChunks(
        sink: MobixAssetSink,
        backgroundBytes: (Background) -> ByteArray?,
        audioBytes: (String) -> ByteArray?,
        nowMillis: Long,
    ): List<Pair<MobixPart, MobixContainer.Entry>> = chunks.map { chunk ->
        val json = JsonWriter.write(MobixChunkCodec.encode(chunk, sink, backgroundBytes, audioBytes))
            .toByteArray(Charsets.UTF_8)
        val name = MobixFormat.chunkEntry(chunk.cx, chunk.cy)
        chunkPartFor(manifest, chunk, name, MobixAssetSink.sha256(json), nowMillis) to MobixContainer.Entry(name, json)
    }

    companion object {

        /** The page size reported for a document with no pages yet: unstated, not a guess. */
        private const val NO_PAGE_SIZE = 0.0

        /**
         * Read the `.mobix` [archive] as a document.
         *
         * @throws MobixContainer.MalformedMobixException when the archive is typed `mobix` but is not a
         *   document this build can honestly read: a page or chunk the manifest names but the archive does not
         *   carry (a corrupt file — never skipped quietly), a `canvas.json` whose mode disagrees with
         *   the manifest's, a `formatVersion` from a newer schema, or an entry that is not a JSON object.
         *
         * Whether `minAppVersion` allows a normal open is **the caller's policy**, not this layer's:
         * `manifest.requiresAppNewerThan(appVersion)` is the question, and answering it with "open it
         * read-only" rather than "refuse" is the app's call (`docs/mobix-schema-spec.md`, rule 4).
         */
        fun open(archive: MobixContainer.Archive): MobixDocument {
            val source = archive.asAssetSource()
            val manifest = MobixManifest.decode(
                text = archive.require(MobixFormat.MANIFEST_ENTRY).bytes.toString(Charsets.UTF_8),
                assets = source,
            )
            if (manifest.formatVersion > MobixManifest.CURRENT_FORMAT_VERSION) {
                throw MobixContainer.MalformedMobixException(
                    "this document is formatVersion ${manifest.formatVersion}; this build writes" +
                        " ${MobixManifest.CURRENT_FORMAT_VERSION} and refuses to read a schema it half knows",
                )
            }
            if (manifest.canvasMode == CanvasMode.PAGINATED) {
                if (manifest.chunks.isNotEmpty()) {
                    throw MobixContainer.MalformedMobixException(
                        "PAGINATED document carries chunks/ entries; a file that carries both is invalid",
                    )
                }
            } else {
                if (manifest.pages.isNotEmpty()) {
                    throw MobixContainer.MalformedMobixException(
                        "${manifest.canvasMode} document carries pages/ entries; a file that carries both is invalid",
                    )
                }
            }
            val pages = if (manifest.canvasMode == CanvasMode.PAGINATED) {
                manifest.pages.map { pageIn(archive, it, source) }
            } else {
                emptyList()
            }
            val chunks = if (manifest.canvasMode != CanvasMode.PAGINATED) {
                manifest.chunks.map { chunkIn(archive, it, source) }
            } else {
                emptyList()
            }
            return MobixDocument(
                manifest = manifest,
                canvas = canvasIn(archive, manifest, pages),
                pages = pages,
                chunks = chunks,
                bridge = archive.text(MobixFormat.BRIDGE_ENTRY)?.let(MobixBridgeFile::decode),
                payloads = archive.entries.filter { it.name.startsWith(MobixFormat.ASSETS_DIR) }
                    .associate { it.name to it.bytes },
                preserved = preservedEntries(archive, manifest),
            )
        }

        /**
         * Read the `.mobix` [source] streams, without inflating the whole archive.
         *
         * Where [open] over a [MobixContainer.Archive] has already read every entry into memory, this
         * reads the envelope (`mimetype`, `manifest.json`, `canvas.json`, the bridge) and every page,
         * and leaves the `assets/` payloads **deferred** to the source: a bundled PDF or photo is
         * inflated only when someone asks for it or the document is written, so opening a 200-page
         * notebook no longer pays for a megabyte of pictures it has not looked at (`docs/data-path.md`).
         *
         * The source is **not** closed — the caller owns it, exactly as [MobixContainer.open] leaves the
         * stream it is handed, and a caller that lets the document go must close the source itself.
         */
        fun open(source: MobixEntrySource): MobixDocument {
            val declared = source.readBytes(MobixFormat.MIMETYPE_ENTRY)?.toString(Charsets.UTF_8)?.trim()
            if (declared == null) {
                throw MobixContainer.MalformedMobixException(
                    "not a .mobix archive: it declares no ${MobixFormat.MIMETYPE_ENTRY} entry" +
                        " (a ZIP-package .xopp is a ZIP too — see MobixContainer.mimetypeOf)",
                )
            }
            if (declared != MobixFormat.MIMETYPE) {
                throw MobixContainer.MalformedMobixException(
                    "not a .mobix archive: its mimetype is \"$declared\", not \"${MobixFormat.MIMETYPE}\"",
                )
            }
            val assets = source.asAssetSource()
            val manifestText = source.readBytes(MobixFormat.MANIFEST_ENTRY)?.toString(Charsets.UTF_8)
                ?: throw MobixContainer.MalformedMobixException(
                    "${MobixFormat.MANIFEST_ENTRY} is missing: a .mobix without its manifest is not a document",
                )
            val manifest = MobixManifest.decode(manifestText, assets)
            if (manifest.formatVersion > MobixManifest.CURRENT_FORMAT_VERSION) {
                throw MobixContainer.MalformedMobixException(
                    "this document is formatVersion ${manifest.formatVersion}; this build writes" +
                        " ${MobixManifest.CURRENT_FORMAT_VERSION} and refuses to read a schema it half knows",
                )
            }
            if (manifest.canvasMode == CanvasMode.PAGINATED) {
                if (manifest.chunks.isNotEmpty()) {
                    throw MobixContainer.MalformedMobixException(
                        "PAGINATED document carries chunks/ entries; a file that carries both is invalid",
                    )
                }
            } else {
                if (manifest.pages.isNotEmpty()) {
                    throw MobixContainer.MalformedMobixException(
                        "${manifest.canvasMode} document carries pages/ entries; a file that carries both is invalid",
                    )
                }
            }
            val pages = if (manifest.canvasMode == CanvasMode.PAGINATED) {
                manifest.pages.map { pageIn(source, it, assets) }
            } else {
                emptyList()
            }
            val chunks = if (manifest.canvasMode != CanvasMode.PAGINATED) {
                manifest.chunks.map { chunkIn(source, it, assets) }
            } else {
                emptyList()
            }
            return MobixDocument(
                manifest = manifest,
                canvas = canvasIn(source, manifest, pages),
                pages = pages,
                chunks = chunks,
                bridge = source.readBytes(MobixFormat.BRIDGE_ENTRY)?.toString(Charsets.UTF_8)?.let(MobixBridgeFile::decode),
                // Every asset is left in the archive, addressed by name; nothing is read yet.
                payloads = emptyMap(),
                assetEntries = source,
                preserved = preservedEntries(source, manifest),
            )
        }

        /**
         * Read a paged `.mobix` **page by page**: the same envelope and the same document as
         * [open], whose pages are a lazy view of the reader instead of a decoded list.
         *
         * This is the open a 200-page notebook gets: the manifest answers the document's title,
         * identity, page count and page ids, a page is inflated when someone indexes it, and the
         * `assets/` payloads stay in the archive as they do on the [readBytes][open] path. The
         * reader's cache window decides how many pages are resident (see [MobixPagedReader]).
         *
         * @param pages where the page models come from — the reader's own surface by default, or a
         *   layer over it that adopts a page's bundled assets — backgrounds and recordings — as the page
         *   is read (`BundledPageAssets`).
         *   It is read through on every index, so the document holds the layer and not the pages.
         *
         * The **caller owns the reader**: it is what the pages read from, so it must outlive the
         * document and be closed by whoever opened it.
         */
        fun openPaged(
            reader: MobixPagedReader,
            pages: PageSource = PageSource.of(reader),
        ): MobixDocument {
            val source = reader.source
            // The reader settles the mimetype, the schema version and the canvas mode; a paginated
            // document carrying `chunks/` is the one corruption it does not look for, and a file
            // that carries both is invalid (the same rule [open] enforces).
            if (reader.manifest.chunks.isNotEmpty()) {
                throw MobixContainer.MalformedMobixException(
                    "PAGINATED document carries chunks/ entries; a file that carries both is invalid",
                )
            }
            return MobixDocument(
                manifest = reader.manifest,
                canvas = reader.canvas,
                pages = pages.asPageList(),
                bridge = source.readBytes(MobixFormat.BRIDGE_ENTRY)?.toString(Charsets.UTF_8)
                    ?.let(MobixBridgeFile::decode),
                // Every asset is left in the archive, addressed by name; nothing is read yet.
                payloads = emptyMap(),
                assetEntries = source,
                preserved = preservedEntries(source, reader.manifest),
            )
        }

        /**
         * Build a document from the app's model: the `.xopp` → `.mobix` half of the bridge.
         *
         * A `.xopp` has no page ids, so every page gets one here — exactly once — and from then on the
         * id is the page's. The first page's background becomes the document's `defaultPaper`, which is
         * the desktop's own rule for what a new page starts on (`docs/xopp-format.md`), and a ruling's
         * parameters travel verbatim in its `config` rather than being parsed and re-spelled.
         *
         * Nothing is bundled by this step: the source `.xopp` references its pictures by path and its
         * PDFs by a `pdf` domain, so a converted document keeps those references as they were, and the
         * caller that *can* supply the bytes passes them to [encode].
         *
         * When the `.xopp` carries a **bridge block** — the living entities a previous export flattened
         * into it — every claim that still verifies is revived here, before identity is assigned, so a
         * document that travels `.mobix` → `.xopp` → `.mobix` comes back edited rather than merely
         * redrawn ([MobixMetaRevival]).
         *
     * @param appVersion the version written as `minAppVersion` — the build that must be present to
     *   write this file safely (`docs/mobix-schema-spec.md`). The caller passes its own version, so
     *   the format layer needs no build configuration.
     * @param source what the `.xopp` was called, when the caller knows it.
     * @param reuse the pages the archive this document came from already holds ([MobixPageReuse]):
     *   they are the pages this save will copy rather than build, so identity is adopted **on the rest
     *   only** — a page nobody is going to write is a page nobody has to open. The same plan is what
     *   [encode] is given; it applies to a paginated document and is ignored for an infinite one,
     *   whose chunks are always derived from the pages.
     */
    fun of(
        document: Document,
        appVersion: String,
        nowMillis: Long,
        source: String? = null,
        canvasMode: CanvasMode = CanvasMode.PAGINATED,
        reuse: MobixPageReuse = MobixPageReuse.none,
    ): MobixDocument {
        val isPaginated = canvasMode == CanvasMode.PAGINATED
        val reuse = if (isPaginated) reuse else MobixPageReuse.none
        // Identity is the *document's* own ([withIdentity]): it mints what is missing and keeps what
        // is there, so a document that came from a `.mobix` is written with the ids it read, one
        // that came from a `.xopp` gets a set of ids that the open document adopts from then on,
        // and a re-save of either mints nothing. A revived entity already carries the id the block
        // recorded, so it keeps it too. A caller that wants a specific id (a test, a future import)
        // sets it on the document it passes rather than overruling it here. It runs over [reuse]'s
        // written pages alone when there is a plan: a copied page is not a page that needs an id.
        val revived = MobixMetaRevival.apply(document)
        val identified = if (reuse.written == null) revived.withIdentity() else revived.withIdentityOn(reuse.written)
        val documentId = requireNotNull(identified.documentId) {
            "withIdentity() mints the document id when a document has none"
        }
        val pages = identified.pages
        val first = pages.firstOrNull()
        val chunks = if (!isPaginated) {
                pages.mapIndexed { index, page ->
                    val cx = if (canvasMode == CanvasMode.INFINITE_HORIZONTAL) index else 0
                    val cy = if (canvasMode == CanvasMode.INFINITE_VERTICAL || canvasMode == CanvasMode.INFINITE_2D) index else 0
                    MobixChunk.fromPage(page, cx = cx, cy = cy)
                }
            } else {
                emptyList()
            }
            return MobixDocument(
                manifest = MobixManifest.forNewDocument(
                    documentId = documentId,
                    canvasMode = canvasMode,
                    nowMillis = nowMillis,
                    title = document.title,
                    defaultPaper = first?.background,
                    appVersion = appVersion,
                ),
                canvas = MobixCanvasFile(
                    mode = canvasMode,
                    widthPt = first?.width ?: NO_PAGE_SIZE,
                    heightPt = first?.height ?: NO_PAGE_SIZE,
                ),
                pages = if (isPaginated) pages else emptyList(),
                chunks = chunks,
                bridge = MobixBridgeFile(
                    creator = document.creator,
                    fileVersion = document.fileVersion,
                    preview = document.preview,
                    source = source,
                ),
            )
        }

        /** Create a brand-new infinite canvas document. */
        fun forInfinite(
            documentId: String,
            canvasMode: CanvasMode,
            widthPt: Double = 0.0,
            heightPt: Double = 0.0,
            chunkSizePt: Double = MobixCanvasFile.DEFAULT_CHUNK_SIZE_PT,
            chunks: List<MobixChunk> = emptyList(),
            defaultPaper: Background? = null,
            title: String? = null,
            appVersion: String = "2.0.0",
            nowMillis: Long = 0L,
        ): MobixDocument {
            require(canvasMode != CanvasMode.PAGINATED) { "forInfinite requires an infinite canvas mode, got $canvasMode" }
            return MobixDocument(
                manifest = MobixManifest.forNewDocument(
                    documentId = documentId,
                    canvasMode = canvasMode,
                    nowMillis = nowMillis,
                    title = title,
                    defaultPaper = defaultPaper,
                    appVersion = appVersion,
                ),
                canvas = MobixCanvasFile(
                    mode = canvasMode,
                    widthPt = widthPt,
                    heightPt = heightPt,
                    chunkSizePt = chunkSizePt,
                ),
                chunks = chunks,
            )
        }

        // ---- reading helpers ---------------------------------------------------------------------

        /** The page the manifest part [part] names, read on demand from a streaming [source]. */
        private fun pageIn(
            source: MobixEntrySource,
            part: MobixPart,
            assets: MobixAssetSource,
        ): Page {
            val bytes = source.readBytes(part.entry)
                ?: throw MobixContainer.MalformedMobixException("page entry ${part.entry} is missing from the archive")
            val node = JsonParser.parse(bytes.toString(Charsets.UTF_8)) as? JsonNode.Obj
                ?: throw MobixContainer.MalformedMobixException("${part.entry} is not a JSON object")
            return MobixPageCodec.decode(node, assets).copy(id = part.id)
        }

        /** The chunk the manifest part [part] names, read on demand from a streaming [source]. */
        private fun chunkIn(
            source: MobixEntrySource,
            part: MobixPart,
            assets: MobixAssetSource,
        ): MobixChunk {
            val bytes = source.readBytes(part.entry)
                ?: throw MobixContainer.MalformedMobixException("chunk entry ${part.entry} is missing from the archive")
            val node = JsonParser.parse(bytes.toString(Charsets.UTF_8)) as? JsonNode.Obj
                ?: throw MobixContainer.MalformedMobixException("${part.entry} is not a JSON object")
            val coords = MobixFormat.parseChunkEntry(part.entry) ?: (0 to 0)
            return MobixChunkCodec.decode(node, assets, fallbackCx = coords.first, fallbackCy = coords.second)
                .copy(id = part.id)
        }

        /** The geometry sidecar, read from a streaming [source]. */
        private fun canvasIn(
            source: MobixEntrySource,
            manifest: MobixManifest,
            pages: List<Page>,
        ): MobixCanvasFile {
            val text = source.readBytes(MobixFormat.CANVAS_ENTRY)?.toString(Charsets.UTF_8) ?: return MobixCanvasFile(
                mode = manifest.canvasMode,
                widthPt = pages.firstOrNull()?.width ?: NO_PAGE_SIZE,
                heightPt = pages.firstOrNull()?.height ?: NO_PAGE_SIZE,
            )
            val canvas = MobixCanvasFile.decode(text)
            if (canvas.mode != manifest.canvasMode) {
                throw MobixContainer.MalformedMobixException(
                    "canvas.json says ${canvas.mode} but the manifest says ${manifest.canvasMode}",
                )
            }
            return canvas
        }

        /** The entries a streaming read did not consume, kept verbatim and in order. */
        private fun preservedEntries(source: MobixEntrySource, manifest: MobixManifest): List<MobixContainer.Entry> {
            val consumed = (manifest.pages + manifest.chunks).map { it.entry }.toSet()
            return source.entryNames
                .filterNot { name ->
                    name == MobixFormat.MIMETYPE_ENTRY || name == MobixFormat.MANIFEST_ENTRY ||
                        name == MobixFormat.CANVAS_ENTRY || name == MobixFormat.BRIDGE_ENTRY ||
                        name.startsWith(MobixFormat.ASSETS_DIR) || name in consumed
                }
                .mapNotNull { name -> source.readBytes(name)?.let { MobixContainer.Entry(name, it) } }
        }

        /** The page the manifest part [part] names. A part with no entry in the archive is corrupt. */
        private fun pageIn(
            archive: MobixContainer.Archive,
            part: MobixPart,
            source: MobixAssetSource,
        ): Page {
            val bytes = archive.require(part.entry).bytes
            val node = JsonParser.parse(bytes.toString(Charsets.UTF_8)) as? JsonNode.Obj
                ?: throw MobixContainer.MalformedMobixException("${part.entry} is not a JSON object")
            return MobixPageCodec.decode(node, source).copy(id = part.id)
        }

        /** The chunk the manifest part [part] names. A part with no entry in the archive is corrupt. */
        private fun chunkIn(
            archive: MobixContainer.Archive,
            part: MobixPart,
            source: MobixAssetSource,
        ): MobixChunk {
            val bytes = archive.require(part.entry).bytes
            val node = JsonParser.parse(bytes.toString(Charsets.UTF_8)) as? JsonNode.Obj
                ?: throw MobixContainer.MalformedMobixException("${part.entry} is not a JSON object")
            val coords = MobixFormat.parseChunkEntry(part.entry) ?: (0 to 0)
            return MobixChunkCodec.decode(node, source, fallbackCx = coords.first, fallbackCy = coords.second)
                .copy(id = part.id)
        }

        /**
         * The geometry sidecar: `canvas.json` when the document carries one, and otherwise the
         * dimensions this build can state honestly — the paginated mode with the first page's own size.
         * A file that carries one **must agree** with the manifest about the mode: two places
         * disagreeing about where a document's content lives is a corrupt file, not a preference.
         */
        private fun canvasIn(
            archive: MobixContainer.Archive,
            manifest: MobixManifest,
            pages: List<Page>,
        ): MobixCanvasFile {
            val text = archive.text(MobixFormat.CANVAS_ENTRY) ?: return MobixCanvasFile(
                mode = manifest.canvasMode,
                widthPt = pages.firstOrNull()?.width ?: NO_PAGE_SIZE,
                heightPt = pages.firstOrNull()?.height ?: NO_PAGE_SIZE,
            )
            val canvas = MobixCanvasFile.decode(text)
            if (canvas.mode != manifest.canvasMode) {
                throw MobixContainer.MalformedMobixException(
                    "canvas.json says ${canvas.mode} but the manifest says ${manifest.canvasMode}",
                )
            }
            return canvas
        }

        /**
         * The entries this build did not consume — a future `index/`, a thumbnail, a page the manifest
         * no longer lists, anything from a newer build. Kept verbatim and in order, because opening and
         * saving a document written by a newer app must not strip it (container rule 4).
         */
        private fun preservedEntries(
            archive: MobixContainer.Archive,
            manifest: MobixManifest,
        ): List<MobixContainer.Entry> {
            val consumed = (manifest.pages + manifest.chunks).map { it.entry }.toSet()
            return archive.entries.filterNot { entry ->
                entry.name == MobixFormat.MIMETYPE_ENTRY ||
                    entry.name == MobixFormat.MANIFEST_ENTRY ||
                    entry.name == MobixFormat.CANVAS_ENTRY ||
                    entry.name == MobixFormat.BRIDGE_ENTRY ||
                    entry.name.startsWith(MobixFormat.ASSETS_DIR) ||
                    entry.name in consumed
            }
        }
    }
}
