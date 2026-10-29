package com.mobixournal.io

import com.mobixournal.format.SaveFormat
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
