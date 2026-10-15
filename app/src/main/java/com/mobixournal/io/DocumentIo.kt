package com.mobixournal.io

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.mobixournal.audio.audioRef
import com.mobixournal.format.FileKind
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.MobixConflictDetector
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixIncrementalSave
import com.mobixournal.format.mobix.MobixMergeEngine
import com.mobixournal.format.mobix.MobixPageReuse
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.asAssetSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.withIdentity
import com.mobixournal.render.ATTACH_DOMAIN
import com.mobixournal.render.BackgroundResolver
import com.mobixournal.render.PdfMerger
import com.mobixournal.render.SynthesisResult
import com.mobixournal.render.TextPdfGenerator
import com.mobixournal.render.Uncompilable
import com.mobixournal.render.XoppSynthesis
import com.mobixournal.render.documentWithPdfDomain
import com.mobixournal.render.documentWithPixmapAttachments
import java.io.File

/**
 * What a staged file turned out to hold. [DocumentIo.read] sniffs the container and returns one of
 * these; resolving it onto a canvas (rasterising the PDF, loading the document) is the caller's job,
 * so everything here stays free of Android view code and unit-testable.
 */
sealed interface LoadedFile {
    /**
     * A raw PDF, which becomes a fresh annotatable document over its pages. [generated] marks one we
     * typeset ourselves from a text file: it exists only in the cache, so the saved `.xopp` has to
     * carry the bytes with it ([SaveFormat.ZIPPED]) rather than link a path that will be swept.
     */
    data class Pdf(val file: File, val generated: Boolean = false) : LoadedFile

    /**
     * A raster image ([FileKind.IMAGE]), staged locally. It becomes a one-page document with the
     * picture as that page's pixmap background — the only shape the `.xopp` format has for an image
     * — so, like a generated PDF, the caller resolves it rather than parsing anything here.
     */
    data class Image(val file: File, val name: String) : LoadedFile

    /**
     * A parsed `.xopp`. [pdf] is the local copy of its background PDF, if it had one we could
     * reach; [missingPdf] says the document referenced a background we could *not* resolve, so
     * those pages will come up blank.
     */
    data class Doc(
        val document: Document,
        val pdf: File?,
        val format: SaveFormat,
        val missingPdf: Boolean = false,
        /**
         * The local copy of each `pixmap` background's picture, keyed by the reference the document
         * carries. The document's own references are left as they were read (so a plain Save writes
         * them back unchanged) and the renderer decodes through this side table instead.
         */
        val images: Map<String, File> = emptyMap(),
        /** True when some pixmap background named a picture we could not reach. */
        val missingImage: Boolean = false,
        /** True when a .mobix manifest requires a newer app version than this build (minAppVersion). */
        val needsNewerApp: Boolean = false,
        /** Non-null message when a sync conflict sibling was detected and merged into this document. */
        val conflictNotification: String? = null,
        /**
         * How a page's background becomes a local file when the page is read, for a `.mobix` opened
         * page by page (`BundledPageAssets`) — the tab and the canvas both have to carry it, since
         * the pages it answers for are only read later. Null for a document whose backgrounds were
         * resolved at open: a `.xopp`, or a `.mobix` opened whole.
         */
        val backgrounds: BackgroundResolver? = null,
    ) : LoadedFile
}

/**
 * The user-set bounds on what document I/O may spend on disk and on a single import (Settings →
 * Storage). Kept as a plain value here rather than reaching for `AppSettings`, so the I/O layer stays
 * independent of the UI one.
 */
data class StorageLimits(
    /** Largest plain-text file that may be typeset into a background PDF (see [TextImport]). */
    val textImportBytes: Long = Long.MAX_VALUE,
    /** Byte budget for the generated/background PDF cache (see [PdfStore.prune]). */
    val pdfCacheBytes: Long = PdfStore.UNLIMITED,
    /** Byte budget for the pixmap-background copies (see [ImageStore.prune]). */
    val imageCacheBytes: Long = ImageStore.UNLIMITED,
)

/**
 * Owns everything between a `content://` URI and a document the canvas can show: the staging area,
 * the two background-PDF stores, and the read/encode/merge steps in between.
 *
 * This is document **I/O policy**, deliberately kept out of the activity, which is left with intent
 * plumbing and the Compose host. Nothing here touches a view or the UI thread — callers run [read],
 * [encode] and [merge] on a worker (see `MainActivity.inBackground`) and apply the result themselves.
 */
class DocumentIo(
    private val resolver: ContentResolver,
    cacheDir: File,
    filesDir: File,
    /**
     * This build's version, written into a `.mobix` as `minAppVersion` — the version that must be
     * present to write the document safely (`docs/mobix-schema-spec.md`). Passed in rather than read
     * from `BuildConfig`, so this layer stays testable and the caller owns the one source of truth.
     */
    private val appVersion: String,
    /**
     * Typesets a plain-text file into a background PDF ([TextImport]). Injected because it needs the
     * bundled fonts (and so an `AssetManager`); when absent, text files are simply unreadable.
     */
    textPdf: TextPdfGenerator? = null,
    /**
     * Where the recordings a `.mobix` bundles are adopted on open, so a stroke replays from the file
     * that has no sidecar beside it ([BundledAudio], implemented by the app's audio store). Injected
     * rather than answered from a path, because where recordings live is the audio layer's business.
     * Null in a caller that keeps no recordings (a format-only test).
     */
    private val audio: BundledAudio? = null,
) {

    /**
     * How a `.mobix` on disk is read — for an **open** and for the repack an incremental save does over
     * the file it was opened from: the file-backed entry source on device, a counting one in a test
     * ([MobixEntrySource] is internal, so this is reachable through the internal constructor below) —
     * which is what makes "the open inflated no page" and "the save rebuilt no page" assertions.
     */
    private var entrySources: (File) -> MobixEntrySource = MobixEntrySource::fromFile

    /** The same document I/O with the entry source supplied — the test seam for the counters. */
    internal constructor(
        resolver: ContentResolver,
        cacheDir: File,
        filesDir: File,
        appVersion: String,
        textPdf: TextPdfGenerator?,
        entrySources: (File) -> MobixEntrySource,
        audio: BundledAudio? = null,
    ) : this(resolver, cacheDir, filesDir, appVersion, textPdf, audio) {
        this.entrySources = entrySources
    }

    /** Staging for document bytes, so slow remote (SSHFS/FTP/cloud) URIs never block the canvas. */
    private val staging = UriStaging(resolver, File(cacheDir, STAGING_DIR))

    /**
     * Where a document's background PDF is kept while it is open — one file per document, never
     * rewritten (see [PdfStore]). Lives in the cache: a tab whose copy the OS reclaims falls back to
     * blank backgrounds, which the caller already handles.
     */
    private val pdfStore = PdfStore(File(cacheDir, PDF_DIR))

    /**
     * Where a *merged* background PDF is kept ([merge]). In `filesDir` rather than the cache, because
     * a plain Save records this path in the document and it has to still resolve on reopen.
     */
    private val joinedPdfStore = PdfStore(File(filesDir, JOINED_PDF_DIR))

    /**
     * Where the pictures behind `pixmap` backgrounds are kept while a document is open (see
     * [ImageStore]). In the cache like [pdfStore]: a copy the OS reclaims costs a blank page
     * background, which the renderer already handles, and the document's own reference is untouched.
     */
    private val imageStore = ImageStore(File(cacheDir, IMAGE_DIR))

    /** Text → PDF, cached in [pdfStore] by content. Null when no font-backed generator was supplied. */
    private val textImport = textPdf?.let { TextImport(pdfStore, it) }

    /** Reference resolution for import and save — the Uri/path arithmetic extracted into its own class. */
    private val refs = DocumentReferenceResolver(resolver, pdfStore, imageStore)

    /**
     * Where each open `.mobix` was read from, by document id: the staged copy the session keeps, and
     * the URI it came in through. A save that lands back on that same URI repacks over these bytes
     * instead of writing the whole archive again ([MobixIncrementalSave]) — which is what makes a save
     * on a long notebook cost a page rather than the notebook.
     */
    private val mobixSources = HashMap<String, MobixSource>()

    /** An open `.mobix`'s own bytes: the staged copy, and the URI it was opened through. */
    private class MobixSource(val staged: File, val uri: String?)

    /**
     * The page readers of the `.mobix` documents opened page by page, by the document they back.
     *
     * A paged document reads its pages **through** its reader ([MobixDocument.openPaged]), so the
     * reader has to outlive the open call and be closed when the session lets the document go —
     * which is what [prunePagedOpens] does, from the same sweep that prunes the background stores.
     */
    private val pagedOpens = LinkedHashMap<String, MutableList<MobixPagedReader>>()

    /**
     * Close the page readers behind documents the session no longer holds. Called with the ids every
     * open tab and live canvas still carries, so a document being shown, mirrored or kept in the
     * background keeps its reader and only the ones nothing points at are released.
     */
    fun prunePagedOpens(liveDocumentIds: Collection<String?>) {
        val live = liveDocumentIds.filterNotNull().toSet()
        for (id in pagedOpens.keys.filterNot { it in live }) {
            pagedOpens.remove(id)?.forEach { reader -> runCatching { reader.close() } }
        }
    }

    /**
     * The two storage caps the user sets in Settings → Storage, pushed in by the activity whenever
     * settings change. Held as a field rather than a constructor argument because this object
     * outlives any one settings value; unset, both are effectively unlimited.
     */
    var limits: StorageLimits = StorageLimits()

    // --- transfers ------------------------------------------------------------------------------

    /** Copy the bytes at [uri] down into a local scratch file. Slow; run it off the UI thread. */
    fun stageIn(uri: Uri, name: String): File = staging.stageIn(uri, name)

    /** Push a finished local [file] out to [uri]. Slow; run it off the UI thread. */
    fun stageOut(file: File, uri: Uri) = staging.stageOut(file, uri)

    /** Hold onto [uri] across restarts, so a later plain Save can write back to it. */
    fun persist(uri: Uri) = staging.persist(uri)

    /** True when we still hold a persisted *write* grant on [uri]. */
    fun isWritable(uri: Uri): Boolean = staging.isWritable(uri)

    /** The file name behind a `content://` URI, for a tab's label. Falls back to [fallback]. */
    fun displayName(uri: Uri, fallback: String): String = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf(String::isNotBlank) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: fallback

    // --- reading --------------------------------------------------------------------------------

    /**
     * Read a staged local copy into a [LoadedFile], sniffing the container by its leading bytes and
     * never by extension: the picker is unfiltered and SAF gives us `content://` URIs with no
     * reliable suffix. The verdict also fixes the sticky save format, so a reopened ZIP keeps saving
     * ZIPPED and a gzip `.xopp` keeps saving gzip.
     */
    fun read(staged: File, name: String = staged.name, source: Uri? = null): LoadedFile {
        val kind = staged.inputStream().use { FileKind.sniff(it.buffered()) }
        if (kind == FileKind.PDF) return LoadedFile.Pdf(staged)
        // An image is likewise not a document to parse: it is handed back for the caller to hang on
        // a page as a pixmap background (see [LoadedFile.Image]).
        if (kind == FileKind.IMAGE) return LoadedFile.Image(staged, name)
        // A text file has no `.xopp` representation of its own, so it is typeset into a PDF and then
        // opened as one — every PDF path downstream (backgrounds, page building, text selection)
        // works unchanged from here (see [TextImport]).
        if (kind == FileKind.TEXT) {
            val generator = textImport ?: error("no text generator configured")
            return LoadedFile.Pdf(
                generator.pdfFor(staged, name, limits.textImportBytes),
                generated = true,
            )
        }
        return staged.inputStream().use { raw ->
            val input = raw.buffered()
            when (kind) {
                // Both of these are ZIPs and both start `PK\x03\x04`, so the bytes settle nothing:
                // the `mimetype` entry decides, and it is read from its own view of the file so the
                // stream this branch is holding is untouched either way.
                FileKind.ZIP -> if (isMobix(staged)) {
                    mobix(staged, name, source)
                } else {
                    // A ZIP package carries its PDF inside, so the background needs no sibling file.
                    // A ZIP package's pictures ride inside it too, keyed by the entry name an attached
                    // `pixmap` background points at, so they resolve without touching the filesystem.
                    XoppZip.open(input, pdfStore::newFile, imageStore::newFile)
                        .let { (doc, pdf, images) ->
                            LoadedFile.Doc(
                                doc,
                                pdf,
                                SaveFormat.ZIPPED,
                                images = images,
                                missingImage = refs.pixmapReferences(doc).any { it !in images },
                            )
                        }
                }
                FileKind.GZIP -> linked(Xopp.open(input), name, source)
                // Desktop Xournal++ can write plain XML; accept it and save it back compressed.
                FileKind.XML -> linked(Xopp.parseXml(input.reader(Charsets.UTF_8).readText()), name, source)
                else -> error("unrecognised file type")
            }
        }
    }

    /**
     * Whether the ZIP at [staged] is a native `.mobix` rather than a ZIP-package `.xopp`. Read from its
     * own view of the file: the caller is mid-way through another stream over the same bytes.
     */
    private fun isMobix(staged: File): Boolean =
        staged.inputStream().use { MobixContainer.isMobix(it.buffered()) }

    /**
     * Open a native `.mobix` document page by page ([MobixPagedReader] decides whether it *is* one).
     *
     * Its bundled backgrounds are adopted a **page at a time**, because a `.mobix` is self-contained by
     * design and *which* asset a page names is written in that page: `BundledPageAssets` reads page `i`
     * and adopts what page `i` names, keyed by the reference the document carries (what the renderer
     * looks one up by), so the open costs no page read and no asset read. A background the document only
     * *references* — a native document may still point at a file beside it — is resolved exactly as a
     * gzip `.xopp`'s is; the archive's own PDF is adopted up front, since the model holds one background
     * PDF per document.
     */
    private fun mobix(staged: File, name: String, source: Uri?): LoadedFile.Doc {
        // A **paged** open: the envelope and the manifest are read, a page is inflated when someone
        // asks for it, and the `assets/` payloads stay in the archive until a page names one. The
        // reader therefore outlives this call — the document reads its pages through it — and is
        // closed when the session lets the document go ([prunePagedOpens]).
        val entries = entrySources(staged)
        val reader = try {
            MobixPagedReader(entries)
        } catch (e: Throwable) {
            entries.close()
            throw e
        }
        // Backgrounds and recordings are adopted page by page, from the page that names them, instead
        // of walking every page at open (`BundledPageAssets`).
        val backgrounds = BundledPageAssets(
            delegate = PageSource.of(reader),
            entries = entries,
            storePdf = { bytes -> pdfStore.newFile().also { it.writeBytes(bytes) } },
            storeImage = { bytes -> imageStore.copyIn(bytes.inputStream()) },
            resolveLinked = { refs.resolveBackground(it, name, source) },
            storeAudio = audio,
        )
        return try {
            var mobixDoc = MobixDocument.openPaged(reader, backgrounds)
            backgrounds.adoptBundledPdf()

            val conflictNote = checkForConflictSiblingAndMerge(mobixDoc, staged, name, source)?.also {
                mobixDoc = it.first
            }?.second

            // Remember what this document was read from, so the next save can repack over it. Keyed by
            // the document's own id, which is what a save of it carries ([Document.documentId]).
            mobixSources[mobixDoc.manifest.documentId] = MobixSource(staged, source?.toString())
            // …and that the reader backing it has to stay open for as long as the session holds it.
            pagedOpens.getOrPut(mobixDoc.manifest.documentId) { mutableListOf() } += reader

            LoadedFile.Doc(
                document = mobixDoc.toDocument(),
                pdf = backgrounds.pdf,
                format = SaveFormat.MOBIX,
                images = backgrounds.images,
                needsNewerApp = mobixDoc.manifest.requiresAppNewerThan(appVersion),
                conflictNotification = conflictNote,
                backgrounds = backgrounds,
            )
        } catch (e: Throwable) {
            reader.close()
            throw e
        }
    }

    /**
     * Checks if a conflict companion file exists beside [staged] or [source].
     * If found, performs a three-way merge into [baseDoc], archives the conflict file,
     * and returns a pair of (mergedDoc, notificationString).
     */
    private fun checkForConflictSiblingAndMerge(
        baseDoc: MobixDocument,
        staged: File,
        name: String,
        source: Uri?,
    ): Pair<MobixDocument, String>? {
        // Probe sibling files on disk if staged file parent directory is accessible
        val parentDir = staged.parentFile
        val localSiblings = parentDir?.list()?.toList().orEmpty()
        val conflictSiblingName = MobixConflictDetector.findConflictSiblings(name, localSiblings).firstOrNull()

        var remoteDoc: MobixDocument? = null
        var conflictFileToArchive: File? = null

        if (conflictSiblingName != null && parentDir != null) {
            val conflictFile = File(parentDir, conflictSiblingName)
            if (conflictFile.exists()) {
                runCatching {
                    val conflictArchive = conflictFile.inputStream().use { MobixContainer.open(it.buffered()) }
                    remoteDoc = MobixDocument.open(conflictArchive)
                    conflictFileToArchive = conflictFile
                }
            }
        }

        // Also check via SAF / reference resolver if not found on local directory
        if (remoteDoc == null && source != null) {
            // Find conflict via openSiblingStream if potential sibling name matches
            val probedName = MobixConflictDetector.findConflictSiblings(name, listOf(name)).firstOrNull()
            if (probedName != null) {
                refs.openSiblingStream(source, probedName)?.use { input ->
                    runCatching {
                        val conflictArchive = MobixContainer.open(input.buffered())
                        remoteDoc = MobixDocument.open(conflictArchive)
                    }
                }
            }
        }

        val remote = remoteDoc ?: return null
        val mergeResult = MobixMergeEngine.merge(local = baseDoc, remote = remote)
        if (!mergeResult.hasRemoteChanges) return null

        // Safely archive the conflict file by renaming it to .bak
        conflictFileToArchive?.let { file ->
            val backup = File(file.parentFile, "${file.name}.conflict-merged.bak")
            file.renameTo(backup)
        }

        val note = buildString {
            append("Sync conflict resolved: ")
            if (mergeResult.adoptedRemotePages > 0) append("${mergeResult.adoptedRemotePages} remote page(s) adopted. ")
            if (mergeResult.elementMergedPages > 0) append("${mergeResult.elementMergedPages} page(s) merged at element level.")
        }.trim()

        return mergeResult.mergedDocument to note
    }

    /**
     * Resolve the PDF background a gzip/XML document links to, so a saved project reopens with its
     * background intact. Only the first PDF-backed page carries the reference (import convention).
     */
    private fun linked(doc: Document, name: String, source: Uri?): LoadedFile.Doc {
        val pdf = refs.resolvePdfBackground(doc, name, source)
        val references = refs.pixmapReferences(doc)
        val images = refs.resolvePixmapBackgrounds(doc, name, source)
        val bg = doc.pages.firstNotNullOfOrNull { it.background as? Background.Pdf }
        val ref = bg?.filename
        return LoadedFile.Doc(
            doc,
            pdf,
            SaveFormat.ORIGINAL,
            missingPdf = ref != null && pdf == null,
            images = images,
            missingImage = references.any { it !in images },
        )
    }

    // --- PDFs -----------------------------------------------------------------------------------

    /** Take a local PDF into the store as this document's own never-rewritten copy. */
    fun adoptPdf(source: File): File {
        val out = pdfStore.newFile()
        source.inputStream().use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    /**
     * Take a local picture into the image store, so a freshly imported image keeps rendering (and
     * can be bundled into a ZIP save) after the staging copy is swept and whatever grant we opened
     * it under expires. The document still references the original.
     */
    fun adoptImage(source: File): File = imageStore.copyIn(source.inputStream())

    /**
     * Merge [existing] and [incoming] into one joined background PDF — the only shape a `.xopp` can
     * represent, since it holds a single background reference. The joined file lands in `filesDir`
     * (not the cache) so the link a plain Save records survives; successive appends get fresh names,
     * so a merge never writes the file it is reading.
     */
    fun merge(existing: File, incoming: File): File =
        PdfMerger.join(existing, incoming, joinedPdfStore.newFile())

    /**
     * Drop the background PDFs nothing refers to any more. Each open allocates its own file, so
     * without this sweep the folders would grow with every document opened.
     *
     * The cache store is additionally held to [StorageLimits.pdfCacheBytes], and the image store to
     * [StorageLimits.imageCacheBytes], so a session that opens many large PDFs or pictures can't sit
     * on hundreds of megabytes of cache between sweeps. The *joined* store is not: a saved `.xopp`
     * links those by path, so deleting one would break a document on disk.
     */
    fun prune(live: Collection<String?>, liveImages: Collection<String?> = emptyList()) {
        pdfStore.prune(live, limits.pdfCacheBytes)
        joinedPdfStore.prune(live)
        imageStore.prune(liveImages, limits.imageCacheBytes)
    }

    // --- writing --------------------------------------------------------------------------------

    /**
     * [document] as the `.mobix` save of it to [target] will write it: the **identity** that write
     * needs, and nothing else changed.
     *
     * A `.mobix` names its pages and elements, and those names have to be the *model's* own before the
     * bytes are written — minting them inside the writer leaves the open document unidentified, and the
     * next save hands out a fresh set, which a merge or a sync client reads as the whole page replaced
     * (`format/model/DocumentIdentity.kt`). Identity is therefore adopted *before* the document is
     * snapshotted, which is what this is for, and a page the archive being repacked over already holds
     * is **not** asked: its ids came with the file, the save copies them back verbatim, and asking would
     * inflate it — which on a two-hundred-page notebook is the notebook ([MobixPageReuse], consulted
     * here exactly as the save itself will consult it).
     *
     * A save with no archive behind it — a new document, a first save, a Save As to another target —
     * reuses nothing and so identifies every page, which is what today's whole-document adoption does;
     * so does a caller whose source will not open, since the encode that follows reports that failure
     * where a save can show it (and identity is the one thing here that must not fail the save).
     */
    fun identifyForSave(document: Document, target: Uri?): Document {
        val source = mobixSourceFor(document, target)
            ?: return document.withIdentity()
        return runCatching {
            entrySources(source.staged).use { from ->
                MobixPageReuse.of(document.pages, from).identified(document)
            }
        }.getOrElse { document.withIdentity() }
    }

    /**
     * The archive a save of [document] to [target] repacks over, or null when there is none: a
     * `.mobix` written back to the very file it was opened from is the one case that can copy anything,
     * so anything else — a new document, a first save, a Save As — writes the whole archive.
     *
     * Keyed by the *incoming* document's id: a document that never was a `.mobix` has none, and one
     * that [MobixDocument.of] was about to mint has no source either. The target has to match the URI
     * the document came in through **as a string**, because that is what a session remembers it as
     * ([mobixSources]) and what SAF hands back for the same file.
     */
    private fun mobixSourceFor(document: Document, target: Uri?): MobixSource? =
        document.documentId?.let { mobixSources[it] }
            ?.takeIf { it.staged.isFile && it.uri != null && it.uri == target?.toString() }

    /**
     * Serialise [document] into a local scratch file in [format], returning it for [stageOut]. On a
     * slow or flaky remote share, serialising straight down the wire risks leaving a half-written
     * `.xopp` behind, so the finished bytes are always pushed across in one later pass.
     *
     * - [SaveFormat.ORIGINAL] — gzip XML, PDF background linked by path/URI, made **relative** to
     *   [target] whenever the PDF sits in the same folder ([portableReference]).
     * - [SaveFormat.ZIPPED] — a ZIP package with [pdf] embedded (`domain="attach"`, `bg.pdf`).
     * - [SaveFormat.MOBIX] — this app's own archive, where [pdf], [images] and the recordings
     *   [audio] supplies are **bundled** into it: the format is self-contained by design, so a
     *   background or a recording the stores hold a copy of travels with the file instead of being
     *   linked (see [backgroundBytesOf]).
     */
    fun encode(
        document: Document,
        pdf: File?,
        format: SaveFormat,
        target: Uri? = null,
        images: Map<String, File> = emptyMap(),
        /** The bytes of the recording a stroke's `fn` names, or null when the store has none. */
        audio: (String) -> ByteArray? = { null },
    ): Encoded {
        val out = staging.newFile("save")
        var uncompiled: List<Uncompilable> = emptyList()
        out.outputStream().use { output ->
            when (format) {
                SaveFormat.ORIGINAL -> {
                    val ink = ink(document)
                    uncompiled = ink.uncompiled
                    Xopp.save(refs.portableReference(ink.document, target), output)
                }
                SaveFormat.MOBIX -> {
                    val now = System.currentTimeMillis()
                    // Repack over the file this document was opened from when the save lands back on
                    // it: the entries whose bytes did not change stream out of that archive instead of
                    // being deflated again, and a page nothing touched is not even built — its bytes are
                    // copied out of the file it is still holding ([MobixPageReuse]). Everywhere else — a
                    // new document, a first save, a Save As to another target — there is no source to
                    // copy from, so every page is built and the whole archive is written.
                    val source = mobixSourceFor(document, target)
                    val backgroundBytes = backgroundBytesOf(pdf, images)
                    if (source == null) {
                        // The document's own metadata, not a fresh identity: a `.mobix` opened and saved
                        // again is the same document ([Document.documentId]).
                        val mobix = MobixDocument.of(
                            document = document,
                            appVersion = appVersion,
                            nowMillis = now,
                            source = target?.let { displayName(it, "document") },
                            // A whole-archive write builds every page from the model, so a page that
                            // names `assets/…` writes that reference back. Carry the bytes it points
                            // at out of the archive this document was opened from, or the new file
                            // names an entry it does not have ([carriedAssets]).
                            payloads = carriedAssets(document),
                        )
                        MobixContainer.save(mobix.encode(now, backgroundBytes, audio).entries, output)
                    } else {
                        entrySources(source.staged).use { from ->
                            MobixIncrementalSave.saveOver(
                                source = from,
                                document = document,
                                appVersion = appVersion,
                                nowMillis = now,
                                output = output,
                                backgroundBytes = backgroundBytes,
                                audioBytes = audio,
                                bridgeSource = target?.let { displayName(it, "document") },
                            )
                        }
                    }
                }
                SaveFormat.ZIPPED -> {
                    val ink = ink(document)
                    uncompiled = ink.uncompiled
                    // Pictures ride inside the package as numbered entries, so a ZIP save is
                    // self-contained for pixmap backgrounds exactly as it already is for the PDF.
                    val bundled = documentWithPixmapAttachments(ink.document) { images[it] }
                    XoppZip.save(
                        if (pdf != null) documentWithPdfDomain(bundled.document, ATTACH_DOMAIN)
                        else bundled.document,
                        pdf,
                        output,
                        bundled.entries,
                    )
                }
            }
        }
        return Encoded(out, uncompiled)
    }

    /**
     * The document a `.xopp` write may encode: living entities are **cooked into ink** first
     * ([XoppSynthesis]), because `.xopp` has nowhere to keep a recipe — a table is a grid of rules and
     * cells, a plot is its curves drawn, a component is its symbol's strokes — and whatever this build
     * cannot draw comes back in [SynthesisResult.uncompiled] for the caller to tell the user about.
     *
     * It runs on every `.xopp` write rather than only on the ones whose document is a `.mobix`: an
     * ink-only document is the identity under synthesis (nothing to cook, and it keeps whatever bridge
     * block it already carried), so there is one door to the writer and no way to reach it un-flattened.
     */
    private fun ink(document: Document): SynthesisResult =
        XoppSynthesis.synthesize(document, documentId = document.documentId)

    /**
     * What a write produced: the staged file to push across, and — for a `.xopp` — what the synthesis
     * could not draw into it. A `.mobix` keeps everything, so its list is always empty.
     */
    class Encoded(val file: File, val uncompiled: List<Uncompilable> = emptyList())

    /**
     * The `assets/` entries a **source-less** `.mobix` write still has to carry, read out of the
     * archive this document was opened from.
     *
     * A save that repacks over its own archive needs none of this: an entry whose bytes did not change
     * is streamed out of that file verbatim ([MobixIncrementalSave]). But a Save As, or a first save of
     * a document whose staged copy is gone, writes a fresh archive from the model — and the model keeps
     * the *names* of the payloads it was read with ([Background.Pdf.bundledAsset],
     * [Stroke.bundledAudio]) precisely so a rewrite cannot orphan them. The bytes those names point at
     * are only adopted into the app's stores when a page is read ([`BundledPageAssets`]), so a page
     * nothing has drawn yet has no local copy and the stores answer nothing for it. Left alone, the
     * written page would name `assets/img_<sha>.png` with no such entry in the archive: the picture, the
     * bundled PDF or the recording would be silently gone from the new file.
     *
     * So the entries the document still *references* are read back out of the staged copy and handed to
     * the writer as payloads. Reading them is the only pass over the pages that costs anything, and it
     * is a pass this path is already making: every page is being built from the model anyway. A document
     * whose staged copy the session no longer holds has nothing to read and yields nothing — the honest
     * answer, reported to the user where a page needs a background it cannot get
     * (`report-a-mobix-page-whose-background-is-missing`).
     */
    private fun carriedAssets(document: Document): Map<String, ByteArray> {
        val source = document.documentId?.let { mobixSources[it] }?.takeIf { it.staged.isFile }
            ?: return emptyMap()
        val referenced = referencedAssets(document)
        if (referenced.isEmpty()) return emptyMap()
        return entrySources(source.staged).use { entries ->
            val assets = entries.asAssetSource()
            referenced.mapNotNull { name -> assets.bytes(name)?.let { bytes -> name to bytes } }.toMap()
        }
    }

    /**
     * The `assets/` entry names the model itself still carries: a page's bundled background and a
     * stroke's bundled recording. A stroke that names an asset through its `fn` instead — the shape
     * this app wrote before it kept the two apart — counts as one too, which is the same rule the
     * adoption step reads it by ([`BundledPageAssets`]).
     */
    private fun referencedAssets(document: Document): Set<String> {
        val names = LinkedHashSet<String>()
        for (page in document.pages) {
            when (val background = page.background) {
                is Background.Pdf -> background.bundledAsset?.let(names::add)
                is Background.Pixmap -> background.bundledAsset?.let(names::add)
                is Background.Solid -> Unit
            }
            for (layer in page.layers) {
                for (element in layer.elements) {
                    val stroke = element as? Stroke ?: continue
                    stroke.bundledAudio?.let(names::add)
                    stroke.audioRef()?.filename
                        ?.takeIf { MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.AUDIO) }
                        ?.let(names::add)
                }
            }
        }
        return names
    }

    /**
     * How a `.mobix` save finds a background's bytes: the PDF store's copy for a PDF, the image store's
     * for a picture.
     *
     * Memoised per file, because the answer is asked once per *page*: a forty-page document over one
     * PDF would otherwise read that PDF forty times on the way to storing it once
     * ([MobixAssetSink] names a payload by the hash of its bytes, so the archive holds one entry either
     * way — the reads are the waste).
     */
    private fun backgroundBytesOf(pdf: File?, images: Map<String, File>): (Background) -> ByteArray? {
        val read = HashMap<String, ByteArray?>()
        fun bytesOf(file: File?): ByteArray? = file?.takeIf { it.isFile }
            ?.let { read.getOrPut(it.path) { it.readBytes() } }
        return { background ->
            when (background) {
                is Background.Pdf -> bytesOf(pdf)
                is Background.Pixmap -> bytesOf(images[background.filename])
                is Background.Solid -> null
            }
        }
    }

    private companion object {
        /** Cache subfolder holding the local staging copies of documents in transit (see [UriStaging]). */
        const val STAGING_DIR = "staging"

        /** Cache subfolder holding one background PDF per open document (see [PdfStore]). */
        const val PDF_DIR = "pdf"

        /** Cache subfolder holding the local copies of pixmap background pictures (see [ImageStore]). */
        const val IMAGE_DIR = "images"

        /** `filesDir` subfolder holding merged background PDFs, which saved documents link to. */
        const val JOINED_PDF_DIR = "pdf-joined"
    }
}
