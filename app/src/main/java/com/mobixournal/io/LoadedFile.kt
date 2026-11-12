package com.mobixournal.io

import com.mobixournal.format.SaveFormat
import com.mobixournal.format.mobix.MobixOpenOutcome
import com.mobixournal.format.mobix.MobixOpenVerdict
import com.mobixournal.format.model.Document
import com.mobixournal.render.BackgroundResolver
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
        /**
         * What this build may do with the document, decided once at open
         * (`MobixCapabilities.evaluate`) — the shell presents this instead of asking the manifest again.
         * A `.xopp` can declare nothing, so it is a full edit; so is a `.mobix` whose declaration this
         * build satisfies.
         */
        val verdict: MobixOpenVerdict = MobixOpenVerdict(MobixOpenOutcome.FULL_EDIT),
        /** Non-null message when a sync conflict sibling was detected and merged into this document. */
        val conflictNotification: String? = null,
        /**
         * How a page's background becomes a local file when the page is read, for a `.mobix` opened
         * page by page (`BundledPageAssets`) — the tab and the canvas both have to carry it, since
         * the pages it answers for are only read later. Null for a document whose backgrounds were
         * resolved at open: a `.xopp`, or a `.mobix` opened whole.
         */
        val backgrounds: BackgroundResolver? = null,
        /**
         * What this `.mobix` says about the bytes it names but does not carry (`PortabilityAudit`), or
         * null until someone asked (`withPortability`) and for a `.xopp`.
         *
         * The shell reports it **once per open** — a document-wide sentence instead of a toast per page —
         * and the per-page notice a lazy background resolution raises is folded into it when this is
         * present (`MainActivityDocuments.applyLoadedDoc`).
         */
        val portability: com.mobixournal.portability.PortabilityReport? = null,
        /**
         * How to produce [portability] — **never** during the open.
         *
         * Answering it walks every page through the archive (`PortabilityAudit`), which is the one cost
         * the page-by-page open exists to avoid: doing it inside `DocumentIo.read` made a 200-page
         * notebook inflate all of its pages before the first frame. The open flow therefore calls this on
         * the worker that fetched the file, after the bytes have landed (`withPortability`).
         */
        val auditPortability: (() -> com.mobixournal.portability.PortabilityReport?)? = null,
    ) : LoadedFile
}

/**
 * Answer a `.mobix`'s [LoadedFile.Doc.portability] **on this thread** — the open's own worker, once the
 * bytes have landed.
 *
 * Deliberately not part of `DocumentIo.read`: the audit inflates every page, and a page-by-page open whose
 * promise is "opening a two-hundred-page notebook reads no page" cannot pay that before the first frame
 * (`PagedMobixOpenTest`, `MobixPagedOpenBenchmarkTest`). Everything else — a `.xopp`, a document that was
 * already audited, an open whose analysis fails — comes back unchanged.
 */
fun LoadedFile.withPortability(): LoadedFile {
    if (this !is LoadedFile.Doc || portability != null) return this
    val report = auditPortability?.invoke() ?: return this
    return copy(portability = report)
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
