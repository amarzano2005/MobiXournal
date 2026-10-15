package com.mobixournal.io

import com.mobixournal.audio.audioRef
import com.mobixournal.format.mobix.MobixEntrySource
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.PageSize
import com.mobixournal.format.mobix.PageSource
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
 * carries, exactly as it does for a `.xopp`. A page whose bytes can neither be adopted nor resolved is
 * simply left without a picture: those pages draw their sheet, and "missing" is not distinguishable
 * from "not looked at yet" without walking the document, which is the cost this class exists to avoid.
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

    private val assets = entries.asAssetSource()

    // --- PageSource ------------------------------------------------------------------------------

    override val pageCount: Int get() = delegate.pageCount

    override fun pageId(index: Int): String? = delegate.pageId(index)

    override fun pageSize(index: Int): PageSize = delegate.pageSize(index)

    override fun idIndex(): Map<String, Int> = delegate.idIndex()

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
                val bytes = assets.bytes(entry) ?: continue
                adoptedRecordings += entry
                store.adopt(reference.filename, bytes)
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

    /** Adopt [background]: its bundled asset when it has one, otherwise its linked reference. */
    private fun adopt(background: Background): File? {
        val asset = when (background) {
            is Background.Pdf -> background.bundledAsset
            is Background.Pixmap -> background.bundledAsset
            is Background.Solid -> null
        } ?: return resolveLinked(background)
        return byAsset.getOrPut(asset) {
            assets.bytes(asset)?.let { bytes ->
                if (background is Background.Pdf) storePdf(bytes) else storeImage(bytes)
            }
        }
    }
}
