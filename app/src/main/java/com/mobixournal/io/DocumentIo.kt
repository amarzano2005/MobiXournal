package com.mobixournal.io

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.mobixournal.format.model.audioRef
import com.mobixournal.format.FileKind
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixConflictDetector
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixMergeEngine
import com.mobixournal.format.mobix.MobixPageReuse
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.MobixPayload
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.asAssetSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.withIdentity
import com.mobixournal.render.PdfMerger
import com.mobixournal.render.TextPdfGenerator
import com.mobixournal.render.Uncompilable
import java.io.File
import java.util.zip.ZipFile


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
     * What this session knows about the `.mobix` files it opened: where each was read from, how it is
     * shaped, and the page readers still backing it ([MobixOpenSessions]). It is what makes a save an
     * edit of the file it came from rather than a fresh archive, and it is released by the same sweep
     * that prunes the stores below ([prunePagedOpens]).
     */
    /**
     * What this session knows about the `.mobix` files it opened — its own record of opens, read by a
     * save ([canvasGeometryOf][com.mobixournal.io.canvasGeometryOf] answers the *canvas* from it, which
     * is why it is reachable from outside this class).
     */
    internal val openSessions = MobixOpenSessions()

    /**
     * Close the page readers behind documents the session no longer holds. Called with the ids every
     * open tab and live canvas still carries, so a document being shown, mirrored or kept in the
     * background keeps its reader and only the ones nothing points at are released.
     */
    fun prunePagedOpens(liveDocumentIds: Collection<String?>) = openSessions.prune(liveDocumentIds)

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
            openSessions.remember(mobixDoc.manifest.documentId, staged, source?.toString())
            // …and how it is shaped, so the save writes the same shape back: the canvas mode, and — for
            // a chunked document — the coordinates each part sits at (read out of the manifest's own
            // entry names, which costs no page read).
            openSessions.shape(mobixDoc)
            // …and that the reader backing it has to stay open for as long as the session holds it.
            openSessions.keep(mobixDoc.manifest.documentId, reader)
            val transcript = entries.readBytes(MobixFormat.TRANSCRIPT_ENTRY)?.let {
                com.mobixournal.audio.TranscriptionCodec.decode(String(it, Charsets.UTF_8))
            }

            LoadedFile.Doc(
                document = mobixDoc.toDocument(),
                pdf = backgrounds.pdf,
                format = SaveFormat.MOBIX,
                images = backgrounds.images,
                needsNewerApp = mobixDoc.manifest.requiresAppNewerThan(appVersion),
                conflictNotification = conflictNote,
                backgrounds = backgrounds,
                canvasMode = mobixDoc.manifest.canvasMode,
                transcription = transcript,
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
    fun identifyForSave(
        document: Document,
        target: Uri?,
        /** See [encode]: the geometry a document this session never opened is written in. */
        canvasMode: CanvasMode = CanvasMode.PAGINATED,
    ): Document {
        val source = openSessions.sourceFor(document, target)
            ?: return document.withIdentity()
        return runCatching {
            entrySources(source.staged).use { from ->
                MobixPageReuse.of(document.pages, from, openSessions.canvasModeOf(document, canvasMode))
                    .identified(document)
            }
        }.getOrElse { document.withIdentity() }
    }

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
     *
     * @param canvasMode The canvas geometry [document] is written in **when this session never opened
     *   it** — a document created in the editor, whose mode the tab carries ([OpenTab.canvasMode], from
     *   `AppSettings.newDocumentCanvas`), or one that came in as a `.xopp`. A document this session *did*
     *   open keeps the mode its own file declares, because a chunked `.mobix` written back as a
     *   paginated one is the rewrite `docs/mobix-format.md` refuses a document to avoid
     *   ([MobixOpenSessions.canvasModeOf]). Ignored by every format but [SaveFormat.MOBIX], which is the
     *   only one with a chunk container.
     */
    fun encode(
        document: Document,
        pdf: File?,
        format: SaveFormat,
        target: Uri? = null,
        images: Map<String, File> = emptyMap(),
        /**
         * The **file** the recording a stroke's `fn` names lives in, or null when the store has none.
         * A file rather than its bytes: the recording is bundled by streaming it into the archive, so a
         * long lecture is never a `ByteArray` on the way to disk ([MobixPayload]).
         */
        audioFiles: (String) -> File? = { null },
        canvasMode: CanvasMode = CanvasMode.PAGINATED,
        transcription: com.mobixournal.audio.AudioTranscription? = null,
    ): Encoded {
        val out = staging.newFile("save")
        // The three formats are the core's own writers; what this class adds is the staging file and the
        // four answers only an open session has — the shape a chunked document must keep, the archive a
        // repack reads from, the target a reference is relativised against.
        val uncompiled = out.outputStream().use { output ->
            DocumentEncoder.write(
                document = document,
                format = format,
                target = target?.let { UriSaveTarget(it, displayName(it, "document"), refs) },
                out = output,
                appVersion = appVersion,
                shape = saveShapeOf(document, target, canvasMode),
                pdf = pdf,
                images = images,
                audioFiles = audioFiles,
                extraEntries = transcription?.let { transcript ->
                    mapOf(
                        MobixFormat.TRANSCRIPT_ENTRY to MobixPayload.of(
                            com.mobixournal.audio.TranscriptionCodec.encode(transcript)
                                .toByteArray(Charsets.UTF_8),
                        ),
                    )
                } ?: emptyMap(),
                entrySource = entrySources,
            )
        }
        return Encoded(out, uncompiled)
    }

    /**
     * How this save has to be **shaped**, which no page can answer: the container an open document
     * declared (a chunked `.mobix` stays chunked, at its own coordinates, and its board geometry is what
     * a `.xopp` export slices by), the archive a whole-archive write reads still-referenced `assets/`
     * entries out of, and the archive to repack over when the save lands back on the file it came from.
     *
     * Asked here rather than inside the encoder because every answer is the **session's** — a document
     * this app never opened has none of them, and the defaults the encoder then uses are the honest ones
     * for a new document: paged, whole archive, every page built.
     */
    private fun saveShapeOf(
        document: Document,
        target: Uri?,
        canvasMode: CanvasMode,
    ): DocumentEncoder.SaveShape = DocumentEncoder.SaveShape(
        mode = openSessions.canvasModeOf(document, canvasMode),
        chunkCoords = openSessions.chunkCoordsOf(document),
        canvas = openSessions.canvasOf(document),
        geometry = canvasGeometryOf(document, canvasMode),
        carriedFrom = openSessions.stagedOf(document),
        repackOver = openSessions.sourceFor(document, target)?.staged,
    )

    /**
     * What a write produced: the staged file to push across, and — for a `.xopp` — what the synthesis
     * could not draw into it. A `.mobix` keeps everything, so its list is always empty.
     */
    class Encoded(val file: File, val uncompiled: List<Uncompilable> = emptyList())


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
