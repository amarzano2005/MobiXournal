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
internal data class MobixMergeResult(
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
 */
internal object MobixMergeEngine {

    /**
     * Merge [local] and [remote] documents against an optional [base] ancestor.
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
    ): MobixMergeResult {
        require(local.manifest.canvasMode == CanvasMode.PAGINATED && remote.manifest.canvasMode == CanvasMode.PAGINATED) {
            "Only PAGINATED documents are supported for merge"
        }

        val pageResolution = resolveMergedPages(local, remote, base)
        val mergedPayloads = LinkedHashMap<String, ByteArray>().apply {
            putAll(local.allPayloads)
            putAll(remote.allPayloads)
        }
        val mergedPreserved = unionPreserved(local, remote, pageResolution.pages, mergedPayloads.keys)

        val mergedDoc = MobixDocument(
            manifest = local.manifest.copy(
                title = local.manifest.title ?: remote.manifest.title,
                modified = nowMillis,
            ),
            canvas = local.canvas,
            pages = pageResolution.pages,
            bridge = local.bridge ?: remote.bridge,
            payloads = mergedPayloads,
            preserved = mergedPreserved,
        )

        return MobixMergeResult(
            mergedDocument = mergedDoc,
            adoptedRemotePages = pageResolution.adoptedRemote,
            adoptedLocalPages = pageResolution.adoptedLocal,
            unchangedPages = pageResolution.unchanged,
            elementMergedPages = pageResolution.elementMerged,
            mergedElementCount = pageResolution.totalMergedElements,
        )
    }

    private data class PageResolution(
        val pages: List<Page>,
        val adoptedRemote: Int,
        val adoptedLocal: Int,
        val unchanged: Int,
        val elementMerged: Int,
        val totalMergedElements: Int,
    )

    private fun resolveMergedPages(
        local: MobixDocument,
        remote: MobixDocument,
        base: MobixDocument?,
    ): PageResolution {
        val basePartsById = base?.manifest?.pages?.associateBy { it.id }.orEmpty()
        val localPartsById = local.manifest.pages.associateBy { it.id }
        val remotePartsById = remote.manifest.pages.associateBy { it.id }

        // Each side is read through the page surface: the ids come from the manifest and a page
        // model is materialised only by the branch that answers with it.
        val localSide = PageSide(PageSource.of(local.pages))
        val remoteSide = PageSide(PageSource.of(remote.pages))

        val allPageIds = LinkedHashSet<String>()
        local.manifest.pages.forEach { allPageIds.add(it.id) }
        remote.manifest.pages.forEach { allPageIds.add(it.id) }

        var adoptedRemote = 0
        var adoptedLocal = 0
        var unchanged = 0
        var elementMerged = 0
        var totalMergedElements = 0
        val mergedPages = mutableListOf<Page>()

        for (id in allPageIds) {
            val resolved = resolvePage(
                id = id,
                localPart = localPartsById[id],
                remotePart = remotePartsById[id],
                basePart = basePartsById[id],
                local = localSide,
                remote = remoteSide,
            ) ?: continue

            mergedPages.add(resolved.page)
            when (resolved.outcome) {
                PageOutcome.ADOPTED_REMOTE -> adoptedRemote++
                PageOutcome.ADOPTED_LOCAL -> adoptedLocal++
                PageOutcome.UNCHANGED -> unchanged++
                PageOutcome.ELEMENT_MERGED -> {
                    elementMerged++
                    totalMergedElements += resolved.mergedElements
                }
                PageOutcome.TAKEN_AS_IS -> Unit
            }
        }

        return PageResolution(
            pages = mergedPages,
            adoptedRemote = adoptedRemote,
            adoptedLocal = adoptedLocal,
            unchanged = unchanged,
            elementMerged = elementMerged,
            totalMergedElements = totalMergedElements,
        )
    }

    /**
     * Resolve one page id against the two sides, materialising only the page models the answered
     * branch needs, and say how it was resolved so the caller can count it.
     *
     * @return the resolved page and its disposition, or null when neither side can produce a model
     *   for [id] (the id then drops out of the merge, as it did before the surface existed).
     */
    private fun resolvePage(
        id: String,
        localPart: MobixPart?,
        remotePart: MobixPart?,
        basePart: MobixPart?,
        local: PageSide,
        remote: PageSide,
    ): ResolvedPage? = when {
        // Only remote declares the page: it is new on that side.
        localPart == null -> remote.pageById(id)?.let { ResolvedPage(it, PageOutcome.ADOPTED_REMOTE) }
        // Only local declares it.
        remotePart == null -> local.pageById(id)?.let { ResolvedPage(it, PageOutcome.ADOPTED_LOCAL) }
        // Byte-identical on both sides: one model answers for both.
        localPart.sha256 == remotePart.sha256 ->
            (local.pageById(id) ?: remote.pageById(id))?.let { ResolvedPage(it, PageOutcome.UNCHANGED) }
        // Local still matches the ancestor, so remote's page is adopted wholesale.
        basePart?.sha256 == localPart.sha256 ->
            remote.pageById(id)?.let { ResolvedPage(it, PageOutcome.ADOPTED_REMOTE) }
        // Remote still matches the ancestor, so local's page is adopted wholesale.
        basePart?.sha256 == remotePart.sha256 ->
            local.pageById(id)?.let { ResolvedPage(it, PageOutcome.ADOPTED_LOCAL) }
        // Both sides changed the page: merge their elements.
        else -> {
            val localPage = local.pageById(id)
            val remotePage = remote.pageById(id)
            if (localPage != null && remotePage != null) {
                val (mergedPage, count) = mergePageElements(localPage, remotePage)
                ResolvedPage(mergedPage, PageOutcome.ELEMENT_MERGED, count)
            } else {
                (localPage ?: remotePage)?.let { ResolvedPage(it, PageOutcome.TAKEN_AS_IS) }
            }
        }
    }

    /**
     * One side of the merge, read page by page through [PageSource].
     *
     * The id index is built from the ids alone ([PageSource.idIndex]), so constructing a side
     * materialises nothing, and the source is owned by whoever opened it — the merge only borrows it.
     */
    private class PageSide(private val source: PageSource) {
        private val indexById = source.idIndex()

        /** The page model whose stable id is [id], materialised on demand, or null when absent. */
        fun pageById(id: String): Page? = indexById[id]?.let { source.page(it) }
    }

    /** How a page was resolved, so the caller counts outcomes without re-inspecting the page. */
    private enum class PageOutcome { ADOPTED_REMOTE, ADOPTED_LOCAL, UNCHANGED, ELEMENT_MERGED, TAKEN_AS_IS }

    /** A resolved page and what was done to it; [mergedElements] is set only by an element merge. */
    private data class ResolvedPage(
        val page: Page,
        val outcome: PageOutcome,
        val mergedElements: Int = 0,
    )

    private fun unionPreserved(
        local: MobixDocument,
        remote: MobixDocument,
        mergedPages: List<Page>,
        payloadKeys: Set<String>,
    ): List<MobixContainer.Entry> {
        val knownNames = (mergedPages.indices.map { MobixFormat.pageEntry(it + 1) } +
            setOf(MobixFormat.MANIFEST_ENTRY, MobixFormat.CANVAS_ENTRY, MobixFormat.BRIDGE_ENTRY) +
            payloadKeys).toSet()
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
