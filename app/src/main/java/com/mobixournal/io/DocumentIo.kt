package com.mobixournal.io

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.mobixournal.format.FileKind
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.MobixConflictDetector
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixMergeEngine
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.render.ATTACH_DOMAIN
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
) {

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
     * Open a native `.mobix` document ([MobixContainer.open] decides whether it *is* one).
     *
     * Its bundled backgrounds are adopted into the two stores on the way, because a `.mobix` is
     * self-contained by design: the bytes in the archive are the background, and a pixmap is keyed by
     * the reference the document carries (which is what the renderer looks one up by). A background the
     * document only *references* — a native document may still point at a file beside it — is resolved
     * exactly as a gzip `.xopp`'s is, so neither kind comes up blank when it need not.
     */
    private fun mobix(staged: File, name: String, source: Uri?): LoadedFile.Doc {
        // A streaming open: the envelope and the pages are read, the `assets/` payloads are left in the
        // archive and inflated only if a background is adopted from them or the document is written.
        // Nothing keeps this source past the call — the open document holds pages, not the MobixDocument
        // that was read from it (see docs/data-path.md, and MobixDocument.open(source)).
        val entries = MobixEntrySource.fromFile(staged)
        return try {
            var mobixDoc = MobixDocument.open(entries)
            val conflictNote = checkForConflictSiblingAndMerge(mobixDoc, staged, name, source)?.also {
                mobixDoc = it.first
            }?.second

            val document = mobixDoc.toDocument()
            val adopted = adoptBundledBackgrounds(document, entries)
            val pdf = adopted.pdf ?: refs.resolvePdfBackground(document, name, source)
            val images = adopted.images + refs.resolvePixmapBackgrounds(document, name, source)
            val references = refs.pixmapReferences(document)
            val needsNewerApp = mobixDoc.manifest.requiresAppNewerThan(appVersion)
            LoadedFile.Doc(
                document,
                pdf,
                SaveFormat.MOBIX,
                missingPdf = pdf == null && document.pages.any { it.background is Background.Pdf },
                images = images,
                missingImage = references.any { it !in images },
                needsNewerApp = needsNewerApp,
                conflictNotification = conflictNote,
            )
        } finally {
            entries.close()
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

    /** What a `.mobix`'s bundled backgrounds became: the local copies, and whether one was unreadable. */
    private class BundledBackgrounds(
        val pdf: File?,
        val images: Map<String, File>,
        val missing: Boolean,
    )

    /**
     * Copy every background a `.mobix` **bundles** out of the archive into the stores, once per asset:
     * a document with the same PDF behind forty pages carries one entry and gets one local copy.
     *
     * An asset the archive does not actually hold is reported rather than skipped in silence — the
     * pages behind it would render blank, and "file not found" and "file empty" are different things
     * to try to explain to a user.
     */
    private fun adoptBundledBackgrounds(
        document: Document,
        entries: MobixEntrySource,
    ): BundledBackgrounds {
        // One local copy per asset, so the same PDF behind forty pages is read and written once.
        val byAsset = HashMap<String, File?>()
        val images = LinkedHashMap<String, File>()
        var pdf: File? = null
        var missing = false
        for (page in document.pages) {
            val background = page.background
            val asset = when (background) {
                is Background.Pdf -> background.bundledAsset
                is Background.Pixmap -> background.bundledAsset
                is Background.Solid -> null
            } ?: continue
            val file = byAsset.getOrPut(asset) { entries.readBytes(asset)?.let { store(background, it) } }
            if (file == null) {
                missing = true
                continue
            }
            when (background) {
                is Background.Pdf -> pdf = file
                // Keyed by the reference the document keeps: that is what the renderer looks a
                // background picture up by (`openBackgroundImage`), not the asset name it arrived under.
                is Background.Pixmap -> images[background.filename] = file
                is Background.Solid -> Unit
            }
        }
        return BundledBackgrounds(pdf, images, missing)
    }

    /**
     * Put bundled [bytes] where a background of this kind lives: the PDF store for a PDF (one file for
     * the whole document) or the image store for a picture — keyed, at the call site, by the reference
     * the document keeps.
     */
    private fun store(background: Background, bytes: ByteArray): File =
        if (background is Background.Pdf) {
            pdfStore.newFile().also { file -> file.outputStream().use { it.write(bytes) } }
        } else {
            imageStore.copyIn(bytes.inputStream())
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
     * Serialise [document] into a local scratch file in [format], returning it for [stageOut]. On a
     * slow or flaky remote share, serialising straight down the wire risks leaving a half-written
     * `.xopp` behind, so the finished bytes are always pushed across in one later pass.
     *
     * - [SaveFormat.ORIGINAL] — gzip XML, PDF background linked by path/URI, made **relative** to
     *   [target] whenever the PDF sits in the same folder ([portableReference]).
     * - [SaveFormat.ZIPPED] — a ZIP package with [pdf] embedded (`domain="attach"`, `bg.pdf`).
     * - [SaveFormat.MOBIX] — this app's own archive, where [pdf] and [images] are **bundled** into it:
     *   the format is self-contained by design, so a background the stores hold a copy of travels with
     *   the file instead of being linked (see [backgroundBytesOf]).
     */
    fun encode(
        document: Document,
        pdf: File?,
        format: SaveFormat,
        target: Uri? = null,
        images: Map<String, File> = emptyMap(),
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
                    // The document's own metadata, not a fresh identity: a `.mobix` opened and saved
                    // again is the same document ([Document.documentId]).
                    output.write(
                        MobixDocument.of(
                            document = document,
                            appVersion = appVersion,
                            nowMillis = now,
                            source = target?.let { displayName(it, "document") },
                        ).toBytes(nowMillis = now, backgroundBytes = backgroundBytesOf(pdf, images)),
                    )
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
