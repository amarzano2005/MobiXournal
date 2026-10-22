package com.mobixournal.io

import com.mobixournal.format.PdfReference
import com.mobixournal.format.SaveFormat
import com.mobixournal.format.Xopp
import com.mobixournal.format.XoppZip
import com.mobixournal.format.mobix.CanvasMode
import com.mobixournal.format.mobix.MobixCanvasFile
import com.mobixournal.format.mobix.MobixContainer
import com.mobixournal.format.mobix.MobixDocument
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixIncrementalSave
import com.mobixournal.format.mobix.MobixPayload
import com.mobixournal.format.mobix.backgroundPayloadsOf
import com.mobixournal.format.mobix.carriedPayloads
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.render.ABSOLUTE_DOMAIN
import com.mobixournal.render.ATTACH_DOMAIN
import com.mobixournal.render.CanvasGeometry
import com.mobixournal.render.SynthesisResult
import com.mobixournal.render.Uncompilable
import com.mobixournal.render.XoppSynthesis
import com.mobixournal.render.documentWithPdfDomain
import com.mobixournal.render.documentWithPdfReference
import com.mobixournal.render.documentWithPixmapAttachments
import com.mobixournal.render.sheetDocument
import java.io.File
import java.io.OutputStream

/**
 * The document I/O that **writes**, without Android: one entry point that serialises a [Document] in
 * one of the three containers this project reads back — gzip `.xopp`, self-contained ZIP `.xopp`, and
 * native `.mobix`.
 *
 * It exists because everything that writes bytes already lived in `mobix-core`
 * ([Xopp.save][com.mobixournal.format.Xopp.save], [XoppZip.save][com.mobixournal.format.XoppZip.save],
 * `MobixContainer.save`, `MobixIncrementalSave`, `XoppSynthesis`), and only the *shape of the target*
 * kept the whole save in the Android module: `DocumentIo.encode` opened a scratch file through SAF's
 * staging area, asked its session for the container an open document declared, and relativised
 * background references against a `content://` URI. Three questions, asked here through three small
 * seams — [SaveTarget], [SaveShape] and the entry-source factory — so the desktop window can write a
 * document it opened, and the Android app keeps its own answers through the same door.
 *
 * What stays in the app, deliberately: the scratch file (`UriStaging` — a save is staged locally and
 * pushed across in one later pass, so a dropped link cannot leave half a `.xopp` on a remote share),
 * the stores a background's bytes come from, and the `content://` relativisation. This class takes the
 * document, the bytes' files and where they are going; it does not know what a URI is.
 */
object DocumentEncoder {

    /**
     * Where a save is going, as far as the **document** can see: the name the file will carry (the
     * `.xopp` bridge block records it) and the reference arithmetic its folder implies.
     *
     * The two implementations are the two worlds this project saves into: a plain [File]
     * ([FileSaveTarget], what the desktop writes) and the app's `content://` target, which relativises
     * against a SAF document id and therefore stays in the app.
     */
    interface SaveTarget {
        /** The name the file will carry, or null when the destination cannot name it (an opaque id). */
        val name: String?

        /**
         * [document] with its background references made **portable** for this target: relative when
         * the background sits in the document's own folder, unchanged otherwise. A relative reference
         * is the only form that survives the file being copied to another machine.
         */
        fun portable(document: Document): Document
    }

    /**
     * A save onto the filesystem itself — the desktop window's only destination, and what a
     * `file://` document in the app already is.
     *
     * A background that sits beside [file] is written **relative** to it (desktop Xournal++ resolves
     * a relative reference against the `.xopp`'s own folder), which is what keeps a document and its
     * PDF together when the pair is copied or zipped. A background elsewhere — another volume, a
     * `content://` URI, an unresolvable path — keeps its absolute reference: a relative one that
     * resolves to nothing would be worse than an absolute one that at least works here.
     */
    class FileSaveTarget(val file: File) : SaveTarget {

        override val name: String get() = file.name

        override fun portable(document: Document): Document = relativeReferences(document, file)
    }

    /**
     * How a write has to be shaped, which no page can answer: the container an open document declared
     * (a chunked `.mobix` must stay chunked, at its own coordinates), the board geometry a `.xopp`
     * export slices by, and the two files a repack reads from.
     *
     * The defaults are a **new** paginated document saved to a destination nothing was opened from:
     * every page is built and a whole archive is written.
     *
     * @property mode The canvas geometry the document is written in.
     * @property chunkCoords The occupied cells a chunked write names its entries after, in the
     *   document's own order; null for a paginated document.
     * @property canvas The `canvas.json` an opened board declared, so a rewrite keeps its paper and
     *   chunk size rather than inventing them.
     * @property geometry The board to slice into sheets for a `.xopp` write; null for anything that is
     *   not a board (see [CanvasGeometry]).
     * @property carriedFrom The archive a **whole-archive** write reads still-referenced `assets/`
     *   entries out of (`carriedPayloads`) — the staged copy of the file the document was opened
     *   from, whether or not the save lands back on it.
     * @property repackOver The archive to **repack over**: set only when the save lands back on the
     *   file it was opened from, so unchanged entries stream out of it and untouched pages are not
     *   rebuilt (`MobixIncrementalSave`).
     */
    data class SaveShape(
        val mode: CanvasMode = CanvasMode.PAGINATED,
        val chunkCoords: List<Pair<Int, Int>>? = null,
        val canvas: MobixCanvasFile? = null,
        val geometry: CanvasGeometry? = null,
        val carriedFrom: File? = null,
        val repackOver: File? = null,
    )

    /**
     * Serialise [document] into [out] as [format], returning what a `.xopp` could not draw (a `.mobix`
     * keeps everything, so its list is always empty).
     *
     * - [SaveFormat.ORIGINAL] — gzip XML, the background PDF linked by a path or URI made relative to
     *   [target] whenever the two share a folder.
     * - [SaveFormat.ZIPPED] — a ZIP package with [pdf] embedded (`domain="attach"`, `bg.pdf`) and each
     *   picture carried as a numbered entry.
     * - [SaveFormat.MOBIX] — the native archive, with [pdf], [images] and the recordings [audioFiles]
     *   supplies **bundled** into it: the format is self-contained by design, so a background or a
     *   recording the caller holds a copy of travels with the file instead of being linked.
     *
     * A `.xopp` write synthesises living entities into ink first ([XoppSynthesis]): that format has
     * nowhere to keep a recipe, so a table is written as the grid it draws. A board is sliced into the
     * pages its tiles add up to ([sheetDocument]) for the same reason — `.xopp` has no chunk container
     * — while a `.mobix` keeps its chunks wherever it is saved.
     *
     * @param appVersion This build's version, written into a `.mobix` as `minAppVersion`; the caller
     *   owns the one source of truth for it (the app reads it from `BuildConfig`).
     * @param extraEntries Entries to add to a `.mobix` beside the ones the document implies — the app's
     *   transcription sidecar, which is not part of the document model.
     * @param entrySource How a `.mobix` reads its repack source; a test substitutes a counting one to
     *   prove that a save rebuilt no page.
     */
    fun write(
        document: Document,
        format: SaveFormat,
        target: SaveTarget?,
        out: OutputStream,
        appVersion: String,
        shape: SaveShape = SaveShape(),
        pdf: File? = null,
        images: Map<String, File> = emptyMap(),
        /**
         * The **file** the recording a stroke's `fn` names lives in, or null when the caller holds
         * none. A file rather than its bytes: the recording is bundled by streaming it into the
         * archive, so a long lecture is never a `ByteArray` on the way to disk ([MobixPayload]).
         */
        audioFiles: (String) -> File? = { null },
        extraEntries: Map<String, MobixPayload> = emptyMap(),
        entrySource: (File) -> MobixEntrySource = MobixEntrySource::fromFile,
    ): List<Uncompilable> = when (format) {
        SaveFormat.ORIGINAL -> {
            val ink = ink(document, shape)
            Xopp.save(target?.portable(ink.document) ?: ink.document, out)
            ink.uncompiled
        }
        SaveFormat.ZIPPED -> {
            val ink = ink(document, shape)
            // Pictures ride inside the package as numbered entries, so a ZIP save is self-contained
            // for pixmap backgrounds exactly as it already is for the PDF.
            val bundled = documentWithPixmapAttachments(ink.document) { images[it] }
            XoppZip.save(
                if (pdf != null) documentWithPdfDomain(bundled.document, ATTACH_DOMAIN) else bundled.document,
                pdf,
                out,
                bundled.entries,
            )
            ink.uncompiled
        }
        SaveFormat.MOBIX -> {
            writeMobix(
                document = document,
                target = target,
                shape = shape,
                pdf = pdf,
                images = images,
                audioFiles = audioFiles,
                extraEntries = extraEntries,
                appVersion = appVersion,
                entrySource = entrySource,
                output = out,
            )
            emptyList()
        }
    }

    /**
     * The document a `.xopp` write may encode: living entities cooked into ink first, because `.xopp`
     * has nowhere to keep a recipe — a table is a grid of rules and cells, a plot is its curves drawn,
     * a component is its symbol's strokes — and whatever this build cannot draw comes back for the
     * caller to tell the user about.
     *
     * It also slices a **board** into the pages its tiles add up to ([sheetDocument]): that is the
     * export's own conversion, not the document's, so a chunked `.mobix` keeps its chunks wherever it
     * is saved and the pages exist only in the `.xopp` being written. It runs on every `.xopp` write
     * rather than only the ones whose document is a `.mobix`: an ink-only document is the identity
     * under synthesis (nothing to cook, and it keeps whatever bridge block it already carried), so
     * there is one door to the writer and no way to reach it un-flattened.
     */
    private fun ink(document: Document, shape: SaveShape): SynthesisResult =
        XoppSynthesis.synthesize(
            sheetDocument(document, shape.geometry?.takeIf { it.isBoard }),
            documentId = document.documentId,
        )

    /**
     * The `.mobix` arm: one whole-archive write, or a repack over the file the document was opened
     * from. The audio seam is memoised **once per write** — a page with four hundred strokes that
     * replay from one lecture asks for it four hundred times.
     */
    private fun writeMobix(
        document: Document,
        target: SaveTarget?,
        shape: SaveShape,
        pdf: File?,
        images: Map<String, File>,
        audioFiles: (String) -> File?,
        extraEntries: Map<String, MobixPayload>,
        appVersion: String,
        entrySource: (File) -> MobixEntrySource,
        output: OutputStream,
    ) {
        val now = System.currentTimeMillis()
        val backgrounds = backgroundPayloadsOf(pdf, images)
        val payloads = HashMap<String, MobixPayload?>()
        fun audioPayload(name: String): MobixPayload? {
            if (payloads.containsKey(name)) return payloads[name]
            val payload = audioFiles(name)?.takeIf(File::isFile)?.let(MobixPayload::of)
            payloads[name] = payload
            return payload
        }
        // The document's own metadata, not a fresh identity: a `.mobix` opened and saved again is the
        // same document (`Document.documentId`). A whole-archive write builds every page from the
        // model, so a page that names `assets/…` writes that reference back — carried from the archive
        // this document was opened from, or the new file names an entry it does not have.
        val carried = carriedPayloads(document, shape.carriedFrom, ::audioPayload).toMutableMap()
        carried.putAll(extraEntries)
        val bridgeSource = target?.name
        if (shape.repackOver == null) {
            val mobix = MobixDocument.of(
                document = document,
                appVersion = appVersion,
                nowMillis = now,
                source = bridgeSource,
                payloads = carried,
                canvasMode = shape.mode,
                chunkCoords = shape.chunkCoords,
                canvas = shape.canvas,
            )
            MobixContainer.save(mobix.encode(now, backgrounds, ::audioPayload).entries, output)
        } else {
            entrySource(shape.repackOver).use { from ->
                MobixIncrementalSave.saveOver(
                    source = from,
                    document = document,
                    appVersion = appVersion,
                    nowMillis = now,
                    output = output,
                    backgrounds = backgrounds,
                    audio = ::audioPayload,
                    bridgeSource = bridgeSource,
                    canvasMode = shape.mode,
                    chunkCoords = shape.chunkCoords,
                    canvas = shape.canvas,
                    payloads = carried,
                )
            }
        }
    }

    /**
     * [document] with a background reference rewritten relative to a document saved at [target], for
     * every reference that sits in the same folder — and nothing else changed.
     *
     * An attached reference is left alone: its `domain` says the bytes travel inside the file. One that
     * is already relative and resolves to nothing below the target's folder comes back as it was read, so
     * a desktop-authored document (a Windows path under `domain="absolute"`, a relative path a Linux
     * Xournal++ wrote) round-trips unchanged.
     */
    private fun relativeReferences(document: Document, target: File): Document {
        val path = target.absoluteFile.invariantSeparatorsPath
        val pdf = document.pages.firstNotNullOfOrNull { it.background as? Background.Pdf }
        val withPdf = pdf?.let { background ->
            val ref = background.filename?.takeIf(String::isNotEmpty) ?: return@let null
            // An attached reference's bytes travel inside the file; anything else is a path that may or
            // may not sit beside the document, and only the arithmetic below can say. A reference that
            // is *already* relative lands here too and comes back changed only when it resolves below
            // the target's folder — so a document written on another machine keeps its own answer.
            if (background.domain == ATTACH_DOMAIN) return@let null
            PdfReference.relativeReference(path, ref)?.let { relative ->
                // Desktop Xournal++ has no `relative` domain: a relative reference rides under
                // `domain="absolute"` and is resolved against the `.xopp`'s own folder.
                documentWithPdfReference(document, relative, ABSOLUTE_DOMAIN)
            }
        } ?: document
        if (withPdf.pages.none { it.background is Background.Pixmap }) return withPdf
        val pages = withPdf.pages.map { page ->
            val background = page.background as? Background.Pixmap ?: return@map page
            val ref = background.filename.takeIf(String::isNotEmpty) ?: return@map page
            if (background.domain == ATTACH_DOMAIN) return@map page
            val relative = PdfReference.relativeReference(path, ref) ?: return@map page
            page.copy(background = background.copy(filename = relative))
        }
        return withPdf.copy(pages = pages)
    }
}
