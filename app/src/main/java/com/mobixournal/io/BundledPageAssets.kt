package com.mobixournal.io

import com.mobixournal.format.model.audioRef
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPart
import com.mobixournal.format.mobix.MobixPayload
import com.mobixournal.format.mobix.PageSize
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.backgroundPayloadsOf
import com.mobixournal.format.mobix.asAssetSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.Stroke
import com.mobixournal.render.BackgroundResolver
import java.io.File

/**
 * The bundled payloads of a `.mobix` read page by page: a [PageSource] layer that adopts the bytes a
 * page names **when that page is read**, and the [BackgroundResolver] the renderer asks afterwards.
 *
 * A native document keeps its pictures, its PDF and its recordings in the archive under
 * content-addressed names (`assets/img_<sha>.png`, `assets/aud_<sha>.wav`), and *which* name a page's
 * background — or a stroke's recording — is lives in that page's own JSON. Adopting them for the whole
 * document at open is therefore a walk of every page, which is exactly what a page-by-page open exists
 * to avoid. Reading page `i` adopts what page `i` names, once per asset (a PDF behind forty pages is
 * copied once, a recording behind forty strokes is copied once), into the app's own stores; the local
 * copies are keyed by the reference the document keeps, which is what the renderer looks a picture up
 * by and what the player looks a recording up by.
 *
 * A background the document only *links* — a `.mobix` may still point at a file beside it — goes
 * through [resolveLinked], the same reference arithmetic a `.xopp` uses, and a **recording** the
 * document only links is left alone: the audio store already looks one up by the name the stroke
 * carries, exactly as it does for a `.xopp`. A page whose bytes can neither be adopted nor resolved
 * draws its bare sheet, and the reference is **recorded** as it happens ([drainMissing]) rather than
 * found by walking the document — which is the cost this class exists to avoid. "Missing" and "not
 * looked at yet" stay apart because the report is made per *ask*: a page nobody has drawn has been
 * asked for nothing, and so has nothing to report.
 *
 * `pdf` is the document's background PDF, adopted up front when the archive carries one
 * ([adoptBundledPdf]) and otherwise the first page that names one: the app's model is **one**
 * background PDF per document (`OpenTab.pdfPath`, `DocumentIo.backgroundBytesOf`), so a second
 * distinct PDF asset cannot be shown anyway.
 */
internal class BundledPageAssets(
    private val delegate: PageSource,
    private val entries: MobixEntrySource,
    /** Store one PDF payload for the whole document; a record's own file, never rewritten. */
    private val storePdf: (ByteArray) -> File,
    /** Store one picture payload; the copy the renderer decodes from. */
    private val storeImage: (ByteArray) -> File,
    /** Resolve a background the document links rather than bundles. */
    private val resolveLinked: (Background) -> File?,
    /** Where a bundled recording goes, or null when the caller keeps none (a pure format test). */
    private val storeAudio: BundledAudio? = null,
) : PageSource, BackgroundResolver {

    /** Adopted pictures by the reference the document names them under; live, not a snapshot. */
    override val images: MutableMap<String, File> = LinkedHashMap()

    /** The document's background PDF, or null while no page has named one. */
    var pdf: File? = null
        private set

    /** One local copy per asset name, so a shared asset is read and written once. */
    private val byAsset = HashMap<String, File?>()

    /** Recordings already copied out, by the entry they live in — the audio half of [byAsset]. */
    private val adoptedRecordings = HashSet<String>()

    /**
     * The references this resolver was asked for and could not produce, waiting to be reported once.
     * Filled where the ask comes back empty, drained by [drainMissing] — the lazy half of the report a
     * document resolved at open makes up front (`LoadedFile.Doc.missingPdf`/`missingImage`).
     */
    private val missing = LinkedHashSet<String>()

    /** Every reference already queued for the report, so the same one is never queued twice. */
    private val reported = HashSet<String>()

    /** Assets the archive does **not** hold: the negative cache `byAsset` cannot keep (a null value). */
    private val missingAssets = HashSet<String>()

    private val assets = entries.asAssetSource()

    // --- PageSource ------------------------------------------------------------------------------

    override val pageCount: Int get() = delegate.pageCount

    override fun pageId(index: Int): String? = delegate.pageId(index)

    override fun pageSize(index: Int): PageSize = delegate.pageSize(index)

    override fun idIndex(): Map<String, Int> = delegate.idIndex()

    /**
     * Forwarded so a save can tell which pages this layer's reads came from (`MobixPageReuse`): the
     * pages are the reader's own — this class adopts a page's payloads and hands the page on — so the
     * parts the reader decoded them from are still its answer to give.
     */
    override fun sourceParts(): List<MobixPart>? = delegate.sourceParts()

    /** A page read adopts the payloads that page names — the only place the archive says which. */
    override fun page(index: Int): Page = delegate.page(index).also { page ->
        file(page.background)
        adoptRecordings(page)
    }

    override fun close() = delegate.close()

    // --- BackgroundResolver ----------------------------------------------------------------------

    /** The local copy of [background]'s bytes, adopting it now if this is the first ask. */
    override fun file(background: Background): File? = when (background) {
        is Background.Solid -> null
        is Background.Pdf -> pdf ?: adopt(background)?.also { pdf = it }
        is Background.Pixmap -> background.filename
            .takeIf(String::isNotEmpty)
            ?.let { reference -> images[reference] ?: adopt(background)?.also { images[reference] = it } }
    }

    // --- audio -----------------------------------------------------------------------------------

    /**
     * Copy out the recordings this page's strokes replay from, under the name the stroke carries —
     * what makes a `.mobix` play with no `.wav` beside it (`docs/audio-inking-sync.md`).
     *
     * Which entry a stroke's audio lives in is the model's own [Stroke.bundledAudio]; a stroke that
     * carries only an `assets/aud_…` name in `fn` (the shape this app wrote before it kept the two
     * apart, and the shape the schema example shows) is read as both, which is why the name itself is
     * tested here rather than trusted.
     */
    private fun adoptRecordings(page: Page) {
        val store = storeAudio ?: return
        for (layer in page.layers) {
            for (element in layer.elements) {
                val stroke = element as? Stroke ?: continue
                val reference = stroke.audioRef() ?: continue
                val entry = stroke.bundledAudio
                    ?: reference.filename.takeIf { MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.AUDIO) }
                    ?: continue
                if (entry in adoptedRecordings) continue
                // Streamed to the store: a lecture's recording is never held as an array on open.
                assets.open(entry)?.use { source ->
                    adoptedRecordings += entry
                    store.adopt(reference.filename, source)
                }
            }
        }
    }

    // --- assets ----------------------------------------------------------------------------------

    /**
     * Adopt the archive's own PDF background up front: a `.mobix` that bundles one names a
     * `assets/img_<sha>.pdf` entry, and the asset is unambiguous because the model holds one PDF per
     * document. It costs one asset read and **no** page read, which is what lets a PDF-backed
     * notebook show its background on the first frame as well as on a later one.
     */
    fun adoptBundledPdf() {
        if (pdf != null) return
        val asset = entries.entryNames.firstOrNull {
            it.startsWith(MobixFormat.ASSETS_DIR) && it.endsWith(".pdf")
        } ?: return
        pdf = byAsset.getOrPut(asset) { assets.bytes(asset)?.let(storePdf) }
    }

    /**
     * Adopt [background]: its bundled asset when it has one, otherwise its linked reference. A page
     * whose background can neither be adopted nor resolved is drawn as its bare sheet and **recorded**,
     * so the ask that came back empty is reported instead of passing in silence ([drainMissing]).
     *
     * The ask is made for **every frame** that draws the page (`BackgroundResolver.file`), so both
     * answers are cached: an asset that was read is a file, and an asset that was not read is in
     * [missingAssets] so the archive is not consulted again — and, since a `LinkedHashMap` cannot hold a
     * null value, that set is what keeps the entry from being read once per frame for a document that
     * never had it. [recordMissing] then makes the report itself once-per-reference rather than
     * once-per-ask.
     */
    private fun adopt(background: Background): File? {
        val asset = when (background) {
            is Background.Pdf -> background.bundledAsset
            is Background.Pixmap -> background.bundledAsset
            is Background.Solid -> null
        }
        if (asset == null) {
            val linked = resolveLinked(background)
            if (linked == null) recordMissing(referenceOf(background, asset = null))
            return linked
        }
        if (asset in missingAssets) return null
        return byAsset.getOrPut(asset) {
            assets.bytes(asset)?.let { bytes ->
                if (background is Background.Pdf) storePdf(bytes) else storeImage(bytes)
            } ?: run {
                // The archive does not hold the entry the page names: nothing to adopt, and this is
                // what the user is told about once a page it belongs to is drawn.
                missingAssets += asset
                recordMissing(referenceOf(background, asset))
                null
            }
        }
    }

    // --- the lazy report ---------------------------------------------------------------------------

    /**
     * The backgrounds asked for and not found, **drained**: each reference comes back once, and never
     * again — the same page is asked for on every frame it is drawn, and a message per frame is the
     * same as no message at all.
     *
     * What a caller does with it is a message, not a repair: the bytes are not in the archive and no
     * amount of asking produces them. Reporting it is the difference between a blank page the user can
     * explain (a file that lost its picture, a share that dropped it) and a blank page that looks like a
     * bug.
     */
    override fun drainMissing(): List<String> = missing.toList().also { missing.clear() }

    /** Queue [reference] for the report, once: what was already said is never said again. */
    private fun recordMissing(reference: String) {
        if (reported.add(reference)) missing += reference
    }

    /** How a missing background is named to the user: the entry when the page names one, its own
     * reference otherwise, and something honest when it names neither (an embedded PDF with no entry). */
    private fun referenceOf(background: Background, asset: String?): String = when (background) {
        is Background.Pdf -> asset ?: background.filename ?: "the document's background PDF"
        is Background.Pixmap -> asset ?: background.filename.ifEmpty { "a picture" }
        is Background.Solid -> "a background"
    }
}
