package com.mobixournal.portability

import com.mobixournal.format.mobix.MobixAssetSink
import com.mobixournal.format.mobix.MobixDigests
import com.mobixournal.format.mobix.MobixFormat
import com.mobixournal.format.mobix.MobixPagedReader
import com.mobixournal.format.mobix.MobixSafetyCaps
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ImageElement
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.TexImageElement
import com.mobixournal.format.model.TextElement
import com.mobixournal.format.model.audioRef

/**
 * Whether the bytes a reference names are **inside the document**.
 *
 * Three answers, and the difference between the last two is the one a user feels:
 *
 *  - [EMBEDDED] — the payload is in the file (a bundled `assets/` entry, or bytes the model already
 *    holds); nothing has to be found for the document to be complete.
 *  - [REFERENCED_ABSENT] — a path, URI or sidecar name with no bytes in the archive. This is a legal
 *    shape (`docs/mobix-assets.md`: a background may keep a file reference instead of bundling its
 *    bytes), and it is what a portable copy can embed when the file is still reachable.
 *  - [UNRECOVERABLE] — a corrupt document (a name in `assets/` shape whose entry is missing), or a
 *    reference that points outside the document's own folder: an absolute path, a URI, a `..`
 *    traversal. Nothing a caller can do makes those bytes part of this document.
 */
enum class PortabilityStatus {
    EMBEDDED,
    REFERENCED_ABSENT,
    UNRECOVERABLE,
}

/**
 * What kind of payload a reference names — the vocabulary of `docs/mobix-assets.md`, kept as an enum
 * so a report can be grouped and a UI can say "two pictures" rather than listing file names.
 *
 * [FONT] has no producer today: a `TextElement.font` is a **name of a face**, and nothing in a
 * `.mobix` carries font bytes (`docs/mobix-elements.md`), so a font name is a rendering hint rather
 * than a reference and the audit emits no entry for it. The constant is here so the classification is
 * exhaustive the day a bundled face becomes a payload kind.
 */
enum class PortabilityKind(val label: String) {
    IMAGE("image"),
    TEX_IMAGE("texImage"),
    PIXMAP("pixmap"),
    PDF("pdf"),
    AUDIO("audio"),
    FONT("font"),
}

/**
 * One reference a document makes, with the evidence a caller needs to act on it: what kind of payload
 * it is, the reference as the document writes it, and **where** it was found (page and element).
 *
 * [dedupeKey] is stable across pages and surfaces: the same bundled picture behind four backgrounds is
 * four entries with one key, so a caller groups by reference instead of by page — which is what a
 * status band that says "3 parts are not in this file" needs, and what the portable-copy flow walks.
 */
data class PortabilityEntry(
    val status: PortabilityStatus,
    val kind: PortabilityKind,
    /** The reference: an `assets/…` entry name, or the path/URI/sidecar name the document carries. */
    val reference: String,
    /** The 0-based page the reference was found on. */
    val pageIndex: Int,
    /** The element that made the reference, or null for a page background. */
    val elementId: String? = null,
) {
    /** One key per distinct reference, so a report of two hundred pages is grouped in one pass. */
    val dedupeKey: String get() = "${kind.label}:$reference"
}

/**
 * What [PortabilityAudit] found: the document it read, how many references fall in each status, and
 * every entry in page order.
 *
 * [isPortable] is the whole-document answer ("every reference this document makes is inside it");
 * [missing] is what a status band lists. The report is evidence, not a repair: it resolves, copies and
 * downloads nothing, and the document it was built from is untouched.
 */
data class PortabilityReport(
    val documentId: String?,
    val counts: Map<PortabilityStatus, Int>,
    val entries: List<PortabilityEntry>,
) {
    /** True when every reference this document makes is inside it. */
    val isPortable: Boolean get() = entries.all { it.status == PortabilityStatus.EMBEDDED }

    /** The entries that are not embedded, in page order — what a caller reports to the user. */
    val missing: List<PortabilityEntry> get() = entries.filter { it.status != PortabilityStatus.EMBEDDED }

    /** The entries grouped by reference ([PortabilityEntry.dedupeKey]), in first-seen order. */
    fun byReference(): Map<String, List<PortabilityEntry>> =
        entries.groupByTo(LinkedHashMap()) { it.dedupeKey }

    companion object {
        /** A report over [entries], counting each status — zero included, so a caller may index it. */
        fun of(documentId: String?, entries: List<PortabilityEntry>): PortabilityReport = PortabilityReport(
            documentId = documentId,
            counts = PortabilityStatus.entries.associateWith { status -> entries.count { it.status == status } },
            entries = entries,
        )
    }
}

/**
 * **Classify every reference an opened document makes** — the model behind the "this file is not
 * self-contained" report every shell shows, and the input a portable copy is built from.
 *
 * It walks the document through [PageSource], so a 200-page `.mobix` is read one page at a time and
 * each page exactly once; nothing else is materialised. The archive's entry **names** come from the
 * caller (a reader's central directory), which is why the audit can answer "is the entry present?"
 * without inflating a single payload: opening a picture to look at it would defeat the point of a
 * report that runs when a file is opened.
 *
 * That is also the limit stated plainly: a name in `assets/` shape is trusted to claim its digest
 * (`MobixDigests` refuses bytes that do not hash to their name when a page *draws* them), so a payload
 * whose bytes were damaged reads as [PortabilityStatus.EMBEDDED] here. The audit answers **whether
 * bytes are inside the file**, not whether those bytes are good — `MobixAssetValidator` is the
 * separate, off-line answer to the second question.
 *
 * What it does not interpret: an element family this build preserves verbatim ([RawElement] in JSON
 * syntax) is opaque, so references inside it are not classified; a `.xopp`-carried element has no
 * native payload reference to classify. Both stay a documented gap rather than a guess.
 */
object PortabilityAudit {

    /**
     * Audit [source] against the archive entry names in [entries].
     *
     * [entries] is the archive's central directory (`MobixEntrySource.entryNames`), never a page's
     * reference list: the presence of a payload is a fact of the file, not of the model.
     */
    fun audit(
        source: PageSource,
        documentId: String?,
        entries: Set<String>,
        caps: MobixSafetyCaps = MobixSafetyCaps.Default,
    ): PortabilityReport {
        // The same first check the reader makes: a report is not a reason to trust a directory.
        caps.refuseBeyondEntryCount(entries.size)
        val found = ArrayList<PortabilityEntry>()
        for (index in 0 until source.pageCount) {
            val page = source.page(index)
            found += referencesOf(page.background, index, entries)
            for (layer in page.layers) {
                for (element in layer.elements) {
                    found += referencesOf(element, index, entries)
                }
            }
        }
        return PortabilityReport.of(documentId, found)
    }

    /**
     * Audit an already-open `.mobix` [reader]: its manifest answers the document id, its directory the
     * entry names, and its pages are read through [PageSource] — so this opens no payload and reads no
     * page the report does not name.
     */
    fun audit(reader: MobixPagedReader): PortabilityReport =
        audit(PageSource.of(reader), reader.manifest.documentId, reader.source.entryNames, reader.caps)

    /** Audit a document the caller holds in memory (a `.xopp`, a fresh document) or as a page view. */
    fun audit(
        document: Document,
        entries: Set<String> = emptySet(),
        caps: MobixSafetyCaps = MobixSafetyCaps.Default,
    ): PortabilityReport = audit(PageSource.of(document), document.documentId, entries, caps)

    // ---- backgrounds -----------------------------------------------------------------------------

    /**
     * A background's reference: the bundled entry when the document carries one (that is where the
     * bytes are), the file reference otherwise. Reporting both would call every self-contained file
     * non-portable because of the bridge reference that is kept beside a bundle on purpose.
     */
    private fun referencesOf(
        background: Background,
        pageIndex: Int,
        entries: Set<String>,
    ): List<PortabilityEntry> = when (background) {
        is Background.Solid -> emptyList()
        is Background.Pixmap -> listOfNotNull(
            referenceOf(PortabilityKind.PIXMAP, background.bundledAsset ?: background.filename, pageIndex, null, entries),
        )
        is Background.Pdf -> listOfNotNull(
            referenceOf(PortabilityKind.PDF, background.bundledAsset ?: background.filename, pageIndex, null, entries),
        )
    }

    // ---- elements --------------------------------------------------------------------------------

    /**
     * An element's references, per family: a picture's bytes are in the model (the codec refuses a page
     * whose `asset` is missing), a formula's rendered PNG is optional, and a recording is named either
     * by the bundled entry or by the sidecar `fn` it replayed from.
     */
    private fun referencesOf(
        element: Element,
        pageIndex: Int,
        entries: Set<String>,
    ): List<PortabilityEntry> = when (element) {
        is ImageElement -> listOf(embedded(PortabilityKind.IMAGE, imageName(element.data), pageIndex, element.id))
        is TexImageElement -> element.data
            ?.let { listOf(embedded(PortabilityKind.TEX_IMAGE, texImageName(it), pageIndex, element.id)) }
            .orEmpty()
        is Stroke -> listOfNotNull(
            referenceOf(PortabilityKind.AUDIO, element.bundledAudio ?: element.audioRef()?.filename, pageIndex, element.id, entries),
        )
        // A desktop text box can carry the same `fn`/`ts` audio pair a stroke does
        // (`docs/xopp-format.md`), and the `.mobix` codec keeps it in `attrs` — so it is a reference,
        // and a `.mobix` never bundles a text box's recording.
        is TextElement -> listOfNotNull(
            referenceOf(PortabilityKind.AUDIO, element.extraAttrs[AUDIO_FILENAME], pageIndex, element.id, entries),
        )
        // A preserved (unmodelled) element is opaque: its body is not this build's JSON to interpret.
        else -> emptyList()
    }

    // ---- classification --------------------------------------------------------------------------

    /**
     * Classify one raw reference: blank is no reference at all; an `assets/` shape is a bundled entry
     * and is present or the document is corrupt; anything else is a file reference, and either inside
     * the document's folder (findable, so embeddable by a portable copy) or outside it.
     */
    private fun referenceOf(
        kind: PortabilityKind,
        raw: String?,
        pageIndex: Int,
        elementId: String?,
        entries: Set<String>,
    ): PortabilityEntry? {
        val reference = raw?.trim().orEmpty()
        if (reference.isEmpty()) return null
        val entry = assetEntry(reference)
        if (entry != null) {
            val status = if (entry in entries) PortabilityStatus.EMBEDDED else PortabilityStatus.UNRECOVERABLE
            return PortabilityEntry(status, kind, entry, pageIndex, elementId)
        }
        val status =
            if (leavesDocumentFolder(reference)) PortabilityStatus.UNRECOVERABLE
            else PortabilityStatus.REFERENCED_ABSENT
        return PortabilityEntry(status, kind, reference, pageIndex, elementId)
    }

    /**
     * The archive entry name [reference] claims to be, or null when it is not in `assets/` shape.
     *
     * A name in that shape is the **content's own hash** (`docs/mobix-assets.md`), which is what makes
     * "the bytes are in this file" answerable from the directory alone; a bare `img_<sha>.png` and a
     * full `assets/img_<sha>.png` are the same entry, as they are everywhere else in the format.
     */
    private fun assetEntry(reference: String): String? {
        val shaped = MobixFormat.AssetKind.entries.any { MobixFormat.isAssetEntry(reference, it) }
        if (!shaped) return null
        val full = if (reference.startsWith(MobixFormat.ASSETS_DIR)) {
            reference
        } else {
            MobixFormat.ASSETS_DIR + reference
        }
        // The grammar is `MobixDigests`'s to state: a name it would not verify bytes against is not an
        // entry a reader could have resolved either.
        return full.takeIf { MobixDigests.digestIn(it) != null }
    }

    /**
     * Whether [reference] points somewhere a document's own folder does not contain: an absolute path,
     * a URI, or a `..` segment. Those bytes can never become part of this document, so they are
     * reported as [PortabilityStatus.UNRECOVERABLE] rather than as something to look for.
     */
    private fun leavesDocumentFolder(reference: String): Boolean =
        "://" in reference ||
            reference.startsWith("content:") ||
            reference.startsWith("file:") ||
            reference.startsWith("/") ||
            reference.startsWith("\\") ||
            WINDOWS_ABSOLUTE.containsMatchIn(reference) ||
            reference.split('/', '\\').any { it == ".." }

    /** A reference whose bytes the document itself holds, addressed by the name a writer would give it. */
    private fun embedded(
        kind: PortabilityKind,
        name: String,
        pageIndex: Int,
        elementId: String?,
    ): PortabilityEntry = PortabilityEntry(PortabilityStatus.EMBEDDED, kind, name, pageIndex, elementId)

    /** A picture element's entry name: the hash of its bytes, in the container they arrived in. */
    private fun imageName(bytes: ByteArray): String = MobixFormat.assetEntry(
        MobixAssetSink.sha256(bytes),
        MobixFormat.AssetKind.IMAGE,
        MobixAssetSink.suffixFor(bytes),
    )

    /** A formula's rendered PNG is always a PNG, whatever the source image arrived as (`MobixElementCodec`). */
    private fun texImageName(bytes: ByteArray): String =
        MobixFormat.assetEntry(MobixAssetSink.sha256(bytes), MobixFormat.AssetKind.IMAGE, "png")

    /** The `<stroke>`/`<text>` attribute naming an audio sidecar (`docs/xopp-format.md`). */
    private const val AUDIO_FILENAME = "fn"

    /** A drive-letter absolute path (`C:\…`, `C:/…`), which a document's folder never contains. */
    private val WINDOWS_ABSOLUTE = Regex("^[A-Za-z]:[\\\\/]")
}
