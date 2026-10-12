package com.mobixournal.format.mobix

import com.mobixournal.format.json.JsonNode
import com.mobixournal.format.json.JsonParser
import com.mobixournal.format.json.JsonWriter
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.DEFAULT_CREATOR
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.Page
import java.io.OutputStream
import java.util.UUID

/**
 * A `.mobix` **document**: the envelope (manifest, canvas), the pages, the payloads they reference, and
 * whatever this build did not model. This is the layer above the codecs — the one that knows a document
 * is an archive with an order and a set of parts, and that turns the app's own [Document] into one and
 * back.
 *
 * Three things it is responsible for, none of which belongs in a codec:
 *
 *  - **The manifest's `pages[]` is derived from what is being written**, never copied from what was
 *    read. A save recomputes each page's `sha256`, keeps an unchanged page's `modified` stamp (so a
 *    sync client does not see an untouched page as an edit — `docs/sync-and-storage.md`) and stamps
 *    only the pages whose bytes actually changed.
 *  - **Page identity is stable.** A page that arrives without an id gets one, and the id then lives in
 *    the manifest (`docs/mobix-schema-spec.md`), so a page can be reordered without becoming a
 *    different page to a merge. Its position is not its identity.
 *  - **Nothing a newer build wrote is dropped.** Entries this build does not model are kept verbatim
 *    and written back last ([preserved]), which is container rule 4, and every payload that was
 *    referenced keeps its entry even when the model never looked at it.
 *
 * Only **paginated** documents are read: an infinite-canvas one stores its content in `chunks/`, which
 * the page model cannot represent, and opening it as an empty document would let a save rewrite a
 * document in a mode the user never chose. That refusal is deliberate and is the canvas engine's to
 * lift (`docs/infinite-canvas.md`).
 *
 * Pure Kotlin, no `java.io.File`, like the rest of `format/`: the same code runs on-device and in JVM
 * unit tests.
 */
internal class MobixDocument(
    /** The envelope as read (or as built for a new document); its `pages[]` is rewritten by [encode]. */
    val manifest: MobixManifest,

    /** The geometry sidecar (`canvas.json`). */
    val canvas: MobixCanvasFile,

    /** The pages in document order, each carrying its manifest id. */
    val pages: List<Page>,

    /** What the source `.xopp` was, when this document came from one; null for a native document. */
    val bridge: MobixBridgeFile? = null,

    /** Payloads by entry name, as they were read — a caller adopts them (`docs/data-path.md`). */
    val payloads: Map<String, ByteArray> = emptyMap(),

    /** Entries this build does not model, in archive order: kept, and written back untouched. */
    val preserved: List<MobixContainer.Entry> = emptyList(),
) {

    /** The bytes of the bundled payload [name], or null when the document does not carry it. */
    fun asset(name: String): ByteArray? = payloads[name]

    /** The payload entry names, in archive order. */
    val assetNames: List<String> get() = payloads.keys.toList()

    /**
     * The archive this document is, as entries ready for [MobixContainer.save] — `manifest.json` first,
     * then `canvas.json`, the pages in document order, the assets, and the preserved entries last.
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
     */
    fun encode(nowMillis: Long, backgroundBytes: (Background) -> ByteArray? = { null }): List<MobixContainer.Entry> {
        val sink = MobixAssetSink()
        val pagesAndParts = encodePages(sink, backgroundBytes, nowMillis)
        val written = sink.entries()
        val carried = payloads.filterKeys { name -> written.none { it.name == name } }
            .map { (name, bytes) -> MobixContainer.Entry(name, bytes) }

        val envelope = mutableListOf(
            MobixContainer.Entry(
                name = MobixFormat.MANIFEST_ENTRY,
                bytes = writtenManifest(pagesAndParts.map { it.first }, nowMillis).encode()
                    .toByteArray(Charsets.UTF_8),
            ),
            MobixContainer.Entry(MobixFormat.CANVAS_ENTRY, canvas.encode().toByteArray(Charsets.UTF_8)),
        )
        // The source record is written whenever there is one, so what this document came from is still
        // known to whoever exports it back (`MobixBridgeFile`).
        bridge?.let { out ->
            envelope += MobixContainer.Entry(MobixFormat.BRIDGE_ENTRY, out.encode().toByteArray(Charsets.UTF_8))
        }

        val modelled = envelope + pagesAndParts.map { it.second } + written + carried

        // A preserved entry can only collide with a part this build writes when the two describe the
        // same slot — an orphan page entry the manifest no longer lists, while the document has a page
        // at that index. A ZIP cannot hold the name twice, and the modelled part is the document, so the
        // orphan is the one that goes; container rule 4 protects entries this build has no part for.
        val taken = modelled.map { it.name }.toSet()
        return modelled + preserved.filterNot { it.name in taken }
    }

    /** This document as a `.mobix` byte array — the shape a caller stages to a file or a URI. */
    fun toBytes(nowMillis: Long, backgroundBytes: (Background) -> ByteArray? = { null }): ByteArray =
        MobixContainer.toBytes(encode(nowMillis, backgroundBytes))

    /** Write this document as a `.mobix` on [output] (which is finished and not left open). */
    fun save(output: OutputStream, nowMillis: Long, backgroundBytes: (Background) -> ByteArray? = { null }) =
        MobixContainer.save(encode(nowMillis, backgroundBytes), output)

    /**
     * The app's document model: the pages and the title, plus the source `.xopp`'s creator, file version
     * and preview when there was one — so a file that travelled through this app is still the file its
     * owner had ([MobixBridgeFile]).
     */
    fun toDocument(): Document = Document(
        creator = bridge?.creator ?: DEFAULT_CREATOR,
        fileVersion = bridge?.fileVersion ?: "4",
        pages = pages,
        preview = bridge?.preview,
        title = manifest.title,
    )

    // ---- pages -----------------------------------------------------------------------------------

    /** Each page paired with the `pages[]` entry describing it, in document order. */
    private fun encodePages(
        sink: MobixAssetSink,
        backgroundBytes: (Background) -> ByteArray?,
        nowMillis: Long,
    ): List<Pair<MobixPart, MobixContainer.Entry>> = pages.mapIndexed { index, page ->
        val json = JsonWriter.write(MobixPageCodec.encode(page, sink, backgroundBytes))
            .toByteArray(Charsets.UTF_8)
        val name = MobixFormat.pageEntry(index + 1)
        partFor(page, index, name, MobixAssetSink.sha256(json), nowMillis) to MobixContainer.Entry(name, json)
    }

    /**
     * The `pages[]` entry for the page at [index]: its stable [Page.id] (minted when it has none), the
     * entry [name] it is written to, and a `modified` stamp that only moves when the bytes did.
     *
     * The page a previous write described is found **by id**; a page with no id falls back to the part
     * at the same index, so a document whose pages arrive without identity still keeps the ids it was
     * given rather than minting fresh ones on every save.
     */
    private fun partFor(page: Page, index: Int, name: String, sha256: String, nowMillis: Long): MobixPart {
        val before = page.id?.let { id -> manifest.pages.firstOrNull { it.id == id } }
            ?: if (page.id == null) manifest.pages.getOrNull(index) else null
        return MobixPart(
            id = page.id ?: before?.id ?: ElementId.mint(ElementId.PAGE),
            entry = name,
            modified = if (before?.sha256 == sha256) before.modified else nowMillis,
            sha256 = sha256,
        )
    }

    /** The envelope as it is written: this file's parts, this file's last-write time. */
    private fun writtenManifest(parts: List<MobixPart>, nowMillis: Long): MobixManifest = manifest.copy(
        formatVersion = MobixManifest.CURRENT_FORMAT_VERSION,
        pages = parts,
        chunks = emptyList(),
        modified = nowMillis,
    )

    companion object {

        /** The page size reported for a document with no pages yet: unstated, not a guess. */
        private const val NO_PAGE_SIZE = 0.0

        /**
         * Read the `.mobix` [archive] as a document.
         *
         * @throws MobixContainer.MalformedMobixException when the archive is typed `mobix` but is not a
         *   document this build can honestly read: a page the manifest names but the archive does not
         *   carry (a corrupt file — never skipped quietly), a `canvasMode` this build has no page list
         *   for, a `canvas.json` whose mode disagrees with the manifest's, a `formatVersion` from a
         *   newer schema, or a page entry that is not a JSON object.
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
            if (manifest.canvasMode != CanvasMode.PAGINATED) {
                throw MobixContainer.MalformedMobixException(
                    "this document is ${manifest.canvasMode}: an infinite canvas stores its content in" +
                        " chunks/, which this build cannot lay out yet (docs/infinite-canvas.md)",
                )
            }
            val pages = manifest.pages.map { pageIn(archive, it, source) }
            return MobixDocument(
                manifest = manifest,
                canvas = canvasIn(archive, manifest, pages),
                pages = pages,
                bridge = archive.text(MobixFormat.BRIDGE_ENTRY)?.let(MobixBridgeFile::decode),
                payloads = archive.entries.filter { it.name.startsWith(MobixFormat.ASSETS_DIR) }
                    .associate { it.name to it.bytes },
                preserved = preservedEntries(archive, manifest),
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
         * @param documentId the document's identity; a fresh one by default, because ingesting a file
         *   creates a document (a *re*-save of an open one keeps the id it read).
         * @param source what the `.xopp` was called, when the caller knows it.
         */
        fun of(
            document: Document,
            appVersion: String,
            nowMillis: Long,
            documentId: String = UUID.randomUUID().toString(),
            source: String? = null,
        ): MobixDocument {
            // Identity is assigned here and nowhere else: every element of every page gets an id once,
            // and every page gets one too, so the file that is written carries them from the start.
            // A revived entity already has the id the block recorded, so it keeps it.
            val pages = MobixMetaRevival.apply(document).pages.map { page ->
                val identified = ElementId.withIds(page)
                if (identified.id == null) identified.copy(id = ElementId.mint(ElementId.PAGE)) else identified
            }
            val first = pages.firstOrNull()
            return MobixDocument(
                manifest = MobixManifest.forNewDocument(
                    documentId = documentId,
                    canvasMode = CanvasMode.PAGINATED,
                    nowMillis = nowMillis,
                    title = document.title,
                    defaultPaper = first?.background,
                    appVersion = appVersion,
                ),
                canvas = MobixCanvasFile(
                    mode = CanvasMode.PAGINATED,
                    widthPt = first?.width ?: NO_PAGE_SIZE,
                    heightPt = first?.height ?: NO_PAGE_SIZE,
                ),
                pages = pages,
                bridge = MobixBridgeFile(
                    creator = document.creator,
                    fileVersion = document.fileVersion,
                    preview = document.preview,
                    source = source,
                ),
            )
        }

        // ---- reading helpers ---------------------------------------------------------------------

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
            val consumed = manifest.pages.map { it.entry }.toSet()
            return archive.entries.filterNot { entry ->
                entry.name == MobixFormat.MANIFEST_ENTRY ||
                    entry.name == MobixFormat.CANVAS_ENTRY ||
                    entry.name == MobixFormat.BRIDGE_ENTRY ||
                    entry.name.startsWith(MobixFormat.ASSETS_DIR) ||
                    entry.name in consumed
            }
        }
    }
}
