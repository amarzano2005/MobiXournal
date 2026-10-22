package com.mobixournal.format.mobix

import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.Page

/**
 * The `pages[]`/`chunks[]` entries a save writes — the half of a `.mobix` write that is *bookkeeping*
 * rather than bytes: which part each page is, what it is called, and when it last changed.
 *
 * Split out of [MobixDocument] because it is the one part of that file that is pure: every function here
 * answers from a manifest, a page and a hash, so what a save records about a part can be read — and
 * tested — without an archive, a source or a document around it.
 *
 * Two rules live here, and both exist so that *saving a document twice looks like saving it twice*:
 *
 *  - **A part's identity is stable.** A page that already has an [ElementId.PAGE] keeps it, and a page
 *    that arrives without one gets it minted here — matched to the part the previous manifest wrote for
 *    that page, found **by id** (a page with no id falls back to the part at the same index), so a page
 *    can be reordered without becoming a different page to a merge (`docs/mobix-format.md`).
 *  - **A part's `modified` stamp only moves when its bytes did.** A page whose `sha256` is what the
 *    previous write recorded is untouched, and a sync client reading the manifest has to see that — an
 *    untouched page restamped with the save time reads as an edit on another device.
 */
internal class BuiltPart(
    /** The `pages[]`/`chunks[]` entry the manifest records for this part. */
    val part: MobixPart,

    /**
     * The entry this build produced for it, or **null when the source archive already holds it**
     * ([MobixPageReuse]) — the one case where a part is written without its bytes being built.
     */
    val entry: MobixContainer.Entry?,
)

/**
 * The `pages[]` entry for [page] at [index]: its stable [Page.id] (minted when it has none), the entry
 * [name] it is written to, and a `modified` stamp that only moves when the bytes did.
 *
 * [previous] is the manifest of the archive this document came from (or the document's own, for a save
 * that has no source): the part the last write recorded for the same page is what says whether anything
 * changed. The page a previous write described is found **by id**; a page with no id falls back to the
 * part at the same index, so a document whose pages arrive without identity still keeps the ids it was
 * given rather than minting fresh ones on every save.
 */
internal fun pagePartFor(
    previous: MobixManifest,
    page: Page,
    index: Int,
    name: String,
    sha256: String,
    nowMillis: Long,
): MobixPart {
    val before = page.id?.let { id -> previous.pages.firstOrNull { it.id == id } }
        ?: if (page.id == null) previous.pages.getOrNull(index) else null
    return MobixPart(
        id = page.id ?: before?.id ?: ElementId.mint(ElementId.PAGE),
        entry = name,
        modified = if (before?.sha256 == sha256) before.modified else nowMillis,
        sha256 = sha256,
    )
}

/**
 * The `chunks[]` entry for [chunk] — the infinite-canvas form of [pagePartFor], with the same rules: a
 * stable id, and a `modified` stamp that only moves when the bytes did. A chunk is matched by id, and
 * by the entry it is written to when it has none (its position is a coordinate, not an index).
 */
internal fun chunkPartFor(
    previous: MobixManifest,
    chunk: MobixChunk,
    name: String,
    sha256: String,
    nowMillis: Long,
): MobixPart {
    val before = chunk.id?.let { id -> previous.chunks.firstOrNull { it.id == id } }
        ?: previous.chunks.firstOrNull { it.entry == name }
    return MobixPart(
        id = chunk.id ?: before?.id ?: ElementId.mint(ElementId.CHUNK),
        entry = name,
        modified = if (before?.sha256 == sha256) before.modified else nowMillis,
        sha256 = sha256,
    )
}

/**
 * [manifest] as the envelope of the file about to be written: these [parts], this build's schema, and
 * [nowMillis] as the document's last-write time. Everything else — the document id, its creation time,
 * its title, its default paper, the fields this build does not model — is carried as it was read.
 */
internal fun writtenManifest(
    manifest: MobixManifest,
    parts: List<MobixPart>,
    nowMillis: Long,
): MobixManifest = manifest.copy(
    formatVersion = MobixManifest.CURRENT_FORMAT_VERSION,
    pages = if (manifest.canvasMode == CanvasMode.PAGINATED) parts else emptyList(),
    chunks = if (manifest.canvasMode != CanvasMode.PAGINATED) parts else emptyList(),
    modified = nowMillis,
)
