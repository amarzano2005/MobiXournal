package com.mobixournal.format.mobix

import com.mobixournal.format.model.Element
import com.mobixournal.format.model.ElementId
import com.mobixournal.format.model.Layer
import com.mobixournal.format.model.Page

/**
 * Result details of a three-way document merge operation.
 *
 * @property mergedDocument The resulting unified [MobixDocument].
 * @property adoptedRemotePages Number of pages taken wholesale from remote because only remote modified them.
 * @property adoptedLocalPages Number of pages retained wholesale from local because only local modified them.
 * @property unchangedPages Number of pages that were identical across local and remote.
 * @property elementMergedPages Number of pages where concurrent edits were merged at the element level.
 * @property mergedElementCount Total number of elements resolved during element-level page merges.
 */
data class MobixMergeResult(
    val mergedDocument: MobixDocument,
    val adoptedRemotePages: Int,
    val adoptedLocalPages: Int,
    val unchangedPages: Int,
    val elementMergedPages: Int,
    val mergedElementCount: Int,
) {
    /** Whether the merge incorporated any modifications from the remote document. */
    val hasRemoteChanges: Boolean
        get() = adoptedRemotePages > 0 || elementMergedPages > 0
}

/**
 * One side of a merge — what the merge needs of a document: the envelope that settles which page is
 * which, and a **page surface** it reads a page model from only when a branch needs one.
 *
 * That is the whole point of the type: the manifest already carries each page's `id`, `sha256` and
 * `modified`, so a page that only one side changed is *adopted by part* — the merge records which
 * side's page the merged document should answer with and never decodes it. A `.mobix` opened page by
 * page ([MobixDocument.openPaged]) therefore merges without inflating the pages nobody compared.
 */
class MobixMergeSide private constructor(
    val manifest: MobixManifest,
    val canvas: MobixCanvasFile,
    val bridge: MobixBridgeFile?,
    val preserved: List<MobixContainer.Entry>,
    val pageSource: PageSource,
    private val payloads: () -> Map<String, ByteArray>,
) {

    /** Id → page index, built from the source's ids alone: constructing a side materialises nothing. */
    private val indexById: Map<String, Int> by lazy { pageSource.idIndex() }

    /** How many pages this side declares. */
    val pageCount: Int get() = pageSource.pageCount

    /** The index of the page whose stable id is [id], or null when this side does not have it. */
    fun indexOf(id: String): Int? = indexById[id]

    /** The page at [index], materialised on demand. */
    fun pageAt(index: Int): Page = pageSource.page(index)

    /** Every bundled payload this side carries — the bytes a merged document has to keep. */
    val allPayloads: Map<String, ByteArray> get() = payloads()

    companion object {
        /**
         * The side of an open document. Its pages are read through [PageSource.of], which finds a
         * page-by-page reader when the document was opened that way ([LazyPages]) and answers from
         * the in-memory list otherwise.
         */
        fun of(document: MobixDocument): MobixMergeSide = MobixMergeSide(
            manifest = document.manifest,
            canvas = document.canvas,
            bridge = document.bridge,
            preserved = document.preserved,
            pageSource = PageSource.of(document.pages),
            payloads = { document.allPayloads },
        )
    }
}

/**
 * Three-way merge engine for `.mobix` documents (`docs/sync-and-storage.md`).
 *
 * Resolves concurrent multi-device edits (e.g. tablet vs desktop on Nextcloud/Syncthing)
 * without data loss:
 *
 * 1. **Page-level comparison**:
 *    - Each page is tracked by stable `id` and `sha256` in `manifest.json`.
 *    - If Page $A$ was modified only on Local, retain Local's page.
 *    - If Page $B$ was modified only on Remote, adopt Remote's page.
 *    - If a new page was added on Remote, append it; if added on Local, retain it.
 *
 * 2. **Element-level CRDT merge (same-page collision)**:
 *    - When the exact same page was modified concurrently on both Local and Remote:
 *      - Elements are identified by stable [Element.id].
 *      - Distinct elements are unioned (both sets of strokes/text/drawings are retained).
 *      - For identical element IDs with divergent content, remote or latest element is adopted.
 *
 * **Only the pages the manifest says diverged are ever decoded.** A branch that takes one side's
 * page as a whole records *which* page that is rather than reading it (`MobixMergeSide`), so the
 * merged document is built from the manifest's parts plus the pages that genuinely had to be
 * compared — one page in a one-page conflict, and none in a merge of documents that only added and
 * removed pages.
 */
object MobixMergeEngine {

    /**
     * Merge two open [local] and [remote] documents against an optional [base] ancestor.
     * When [base] is null, two-way diff comparison against local hashes is used.
     *
     * @param local The local document opened or being saved on this device.
     * @param remote The remote/conflict document found on disk or cloud sync.
     * @param base The common ancestor document if known, or null for two-way diff.
     * @param nowMillis Timestamp for the resulting merged manifest.
     * @return [MobixMergeResult] with the merged document and resolution statistics.
     */
    fun merge(
        local: MobixDocument,
        remote: MobixDocument,
        base: MobixDocument? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): MobixMergeResult = merge(
        local = MobixMergeSide.of(local),
        remote = MobixMergeSide.of(remote),
        base = base,
        nowMillis = nowMillis,
    )

    /**
     * Merge two **sides** — the shape a caller uses when a document was opened page by page and only
     * the pages that must be compared should be read (`MobixMergeSide`). The sides are borrowed, not
     * owned: whatever backs their page surfaces must outlive the merged document, which answers a
     * page nobody compared by reading it from the side that has it.
     */
    fun merge(
        local: MobixMergeSide,
        remote: MobixMergeSide,
        base: MobixDocument? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): MobixMergeResult {
        require(local.manifest.canvasMode == CanvasMode.PAGINATED && remote.manifest.canvasMode == CanvasMode.PAGINATED) {
            "Only PAGINATED documents are supported for merge"
        }

        val pagePlan = planPages(local, remote, base)
        val mergedPayloads = LinkedHashMap<String, ByteArray>().apply {
            putAll(local.allPayloads)
            putAll(remote.allPayloads)
        }
        val mergedPreserved = unionPreserved(local, remote, pagePlan.pages.size, mergedPayloads.keys)

        val mergedDoc = MobixDocument(
            manifest = local.manifest.copy(
                title = local.manifest.title ?: remote.manifest.title,
                modified = nowMillis,
            ),
            canvas = local.canvas,
            pages = pagePlan.pages,
            bridge = local.bridge ?: remote.bridge,
            payloads = mergedPayloads,
            preserved = mergedPreserved,
        )

        return MobixMergeResult(
            mergedDocument = mergedDoc,
            adoptedRemotePages = pagePlan.adoptedRemotePages,
            adoptedLocalPages = pagePlan.adoptedLocalPages,
            unchangedPages = pagePlan.unchangedPages,
            elementMergedPages = pagePlan.elementMergedPages,
            mergedElementCount = pagePlan.mergedElementCount,
        )
    }

    /** The merged page list and how each page was resolved. */
    private class PagePlan(
        val pages: List<Page>,
        val adoptedRemotePages: Int,
        val adoptedLocalPages: Int,
        val unchangedPages: Int,
        val elementMergedPages: Int,
        val mergedElementCount: Int,
    )

    /**
     * Walk the union of the two sides' page ids in document order and decide, per page, which side
     * answers it — **materialising only the pages both sides changed**, which are the only ones whose
     * elements have to be merged.
     */
    private fun planPages(
        local: MobixMergeSide,
        remote: MobixMergeSide,
        base: MobixDocument?,
    ): PagePlan {
        val basePartsById = base?.manifest?.pages?.associateBy { it.id }.orEmpty()
        val localPartsById = local.manifest.pages.associateBy { it.id }
        val remotePartsById = remote.manifest.pages.associateBy { it.id }

        val allPageIds = LinkedHashSet<String>()
        local.manifest.pages.forEach { allPageIds.add(it.id) }
        remote.manifest.pages.forEach { allPageIds.add(it.id) }

        val ids = mutableListOf<String>()
        val readers = mutableListOf<() -> Page>()
        var adoptedRemotePages = 0
        var adoptedLocalPages = 0
        var unchangedPages = 0
        var elementMergedPages = 0
        var mergedElementCount = 0

        for (id in allPageIds) {
            val planned = planPage(
                id = id,
                localPart = localPartsById[id],
                remotePart = remotePartsById[id],
                basePart = basePartsById[id],
                local = local,
                remote = remote,
            ) ?: continue

            ids += id
            readers += planned.read
            when (planned.outcome) {
                PageOutcome.ADOPTED_REMOTE -> adoptedRemotePages++
                PageOutcome.ADOPTED_LOCAL -> adoptedLocalPages++
                PageOutcome.UNCHANGED -> unchangedPages++
                PageOutcome.ELEMENT_MERGED -> {
                    elementMergedPages++
                    mergedElementCount += planned.mergedElements
                }
            }
        }

        return PagePlan(
            pages = MergedPageSource(ids, readers).asPageList(),
            adoptedRemotePages = adoptedRemotePages,
            adoptedLocalPages = adoptedLocalPages,
            unchangedPages = unchangedPages,
            elementMergedPages = elementMergedPages,
            mergedElementCount = mergedElementCount,
        )
    }

    /**
     * Resolve one page id against the two sides, answering with a **reader** for the page and how it
     * was resolved, so the caller counts outcomes without re-inspecting the page.
     *
     * @return the planned page, or null when neither side can produce a page for [id] (the id then
     *   drops out of the merge, as it did before).
     */
    private fun planPage(
        id: String,
        localPart: MobixPart?,
        remotePart: MobixPart?,
        basePart: MobixPart?,
        local: MobixMergeSide,
        remote: MobixMergeSide,
    ): PlannedPage? = when {
        // Only remote declares the page: it is new on that side.
        localPart == null -> remote.indexOf(id)?.let {
            PlannedPage({ remote.pageAt(it) }, PageOutcome.ADOPTED_REMOTE)
        }
        // Only local declares it.
        remotePart == null -> local.indexOf(id)?.let {
            PlannedPage({ local.pageAt(it) }, PageOutcome.ADOPTED_LOCAL)
        }
        // Byte-identical on both sides: one side answers for both, and neither is decoded here.
        localPart.sha256 == remotePart.sha256 -> {
            val fromLocal = local.indexOf(id)
            val index = fromLocal ?: remote.indexOf(id)
            index?.let {
                val side = if (fromLocal != null) local else remote
                PlannedPage({ side.pageAt(it) }, PageOutcome.UNCHANGED)
            }
        }
        // Local still matches the ancestor, so remote's page is adopted wholesale.
        basePart?.sha256 == localPart.sha256 -> remote.indexOf(id)?.let {
            PlannedPage({ remote.pageAt(it) }, PageOutcome.ADOPTED_REMOTE)
        }
        // Remote still matches the ancestor, so local's page is adopted wholesale.
        basePart?.sha256 == remotePart.sha256 -> local.indexOf(id)?.let {
            PlannedPage({ local.pageAt(it) }, PageOutcome.ADOPTED_LOCAL)
        }
        // Both sides changed the page: these are the pages that must be read, and merged.
        else -> {
            val localIndex = local.indexOf(id)
            val remoteIndex = remote.indexOf(id)
            if (localIndex != null && remoteIndex != null) {
                val localPage = local.pageAt(localIndex)
                val remotePage = remote.pageAt(remoteIndex)
                val (mergedPage, count) = mergePageElements(localPage, remotePage)
                PlannedPage({ mergedPage }, PageOutcome.ELEMENT_MERGED, mergedElements = count)
            } else {
                // One side's manifest names the page but its surface cannot produce it (a corrupt
                // archive): the other side answers, which is what the eager merge did too.
                val (side, index) = if (localIndex != null) local to localIndex else remote to remoteIndex
                index?.let { PlannedPage({ side.pageAt(it) }, PageOutcome.UNCHANGED) }
            }
        }
    }

    /** How a page was resolved, so the caller counts outcomes without re-inspecting the page. */
    private enum class PageOutcome { ADOPTED_REMOTE, ADOPTED_LOCAL, UNCHANGED, ELEMENT_MERGED }

    /** A resolved page as a deferred read; [mergedElements] is set only by an element merge. */
    private class PlannedPage(
        val read: () -> Page,
        val outcome: PageOutcome,
        val mergedElements: Int = 0,
    )

    /**
     * The merged document's pages: the ids the merge settled, each read from the side that answers
     * it, only when someone asks for that page.
     */
    private class MergedPageSource(
        private val ids: List<String>,
        private val readers: List<() -> Page>,
    ) : PageSource {

        override val pageCount: Int get() = readers.size

        override fun pageId(index: Int): String? = ids.getOrNull(index)

        override fun page(index: Int): Page {
            require(index in readers.indices) { "page index $index out of bounds (0..${readers.size - 1})" }
            return readers[index]()
        }
    }

    private fun unionPreserved(
        local: MobixMergeSide,
        remote: MobixMergeSide,
        pageCount: Int,
        payloadKeys: Set<String>,
    ): List<MobixContainer.Entry> {
        val knownNames = (0 until pageCount).map { MobixFormat.pageEntry(it + 1) } +
            setOf(
                MobixFormat.MIMETYPE_ENTRY,
                MobixFormat.MANIFEST_ENTRY,
                MobixFormat.CANVAS_ENTRY,
                MobixFormat.BRIDGE_ENTRY,
            ) +
            payloadKeys
        return (local.preserved + remote.preserved).distinctBy { it.name }.filterNot { it.name in knownNames }
    }

    /**
     * Merge two divergent versions of the same [Page] by unioning distinct elements
     * and resolving conflicting element IDs.
     */
    private fun mergePageElements(localPage: Page, remotePage: Page): Pair<Page, Int> {
        // Collect all layers by name or position
        val maxLayers = maxOf(localPage.layers.size, remotePage.layers.size)
        val mergedLayers = mutableListOf<Layer>()
        var totalElements = 0

        for (i in 0 until maxLayers) {
            val localLayer = localPage.layers.getOrNull(i)
            val remoteLayer = remotePage.layers.getOrNull(i)

            val localElements = localLayer?.elements.orEmpty()
            val remoteElements = remoteLayer?.elements.orEmpty()

            val elementsById = LinkedHashMap<String, Element>()
            val unnamedElements = mutableListOf<Element>()

            // Add local elements first
            for (elem in localElements) {
                val id = ElementId.idOf(elem)
                if (id != null) elementsById[id] = elem else unnamedElements.add(elem)
            }

            // Union remote elements (remote elements with new ID are added; matching IDs overwrite/LWW)
            for (elem in remoteElements) {
                val id = ElementId.idOf(elem)
                if (id != null) elementsById[id] = elem else unnamedElements.add(elem)
            }

            val layerElements = elementsById.values.toList() + unnamedElements
            totalElements += layerElements.size

            val layerName = localLayer?.name ?: remoteLayer?.name
            mergedLayers.add(
                Layer(
                    elements = layerElements,
                    name = layerName,
                    extraAttrs = localLayer?.extraAttrs ?: remoteLayer?.extraAttrs ?: linkedMapOf(),
                ),
            )
        }

        val mergedPage = localPage.copy(layers = mergedLayers)
        return mergedPage to totalElements
    }
}
