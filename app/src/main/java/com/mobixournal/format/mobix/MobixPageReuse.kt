package com.mobixournal.format.mobix

import com.mobixournal.format.model.Document
import com.mobixournal.format.model.EditedPages
import com.mobixournal.format.model.Page
import com.mobixournal.format.model.withIdentity
import com.mobixournal.format.model.withIdentityOn

/**
 * Which of a document's pages a save will **write**, and the part the archive it is repacking over
 * already holds for each of the rest.
 *
 * This is the second half of the incremental save ([MobixIncrementalSave]): the diff there decides
 * *which entries are written* by comparing their bytes against the hashes the source's manifest
 * recorded, which is exact but costs a page its JSON — an untouched page is serialised and hashed only
 * to be thrown away. A page that is still **the source archive's own page** needs none of that: the
 * bytes are already in the file under a name the manifest states, so the entry is copied and the page
 * is never built at all.
 *
 * **Where the answer comes from.** Not from a dirty flag — a flag is the second copy of an answer that
 * drifts the first time an edit forgets to set it ([PageWindow] keeps one for its own reasons, and it
 * is deliberately *not* what this reads). It comes from the shape of the document's page list: a
 * `.mobix` opened page by page holds the reader's own view ([LazyPages], over a `PageSource` whose
 * [PageSource.sourceParts] is the manifest's page list), and an edit replaces one index of it through
 * [EditedPages]. So **an index the overlay did not replace is the source's page at that index** — it
 * is `pages[i]`, read through the very source that decoded it, and nothing has touched it since. The
 * part's `sha256` is then the hash of the bytes that page was decoded from, which is what the new
 * manifest must record for it.
 *
 * **When it does not apply**, and the page is rebuilt exactly as before:
 *
 *  - the page list is no longer a source's view — a `.xopp` or a new document (its pages are the
 *    model's own), or a document whose list was replaced wholesale (a page inserted or removed, a
 *    load, undo/redo), which materialises it;
 *  - the index moved: the source's part for it names a different entry than the index writes, so the
 *    bytes would have to be copied *to another name* ([MobixFormat.pageEntry]), which this does not
 *    do — a renamed page is a page this build rewrites;
 *  - the archive no longer holds that entry, or the source has no manifest at all.
 *
 * Nothing here is trusted blindly by the writer either: [MobixIncrementalSave.repack] streams the
 * reused entries out of the source, so a plan that named an entry the source lacks would fail loudly
 * at the repack. [of] refuses to build such a plan in the first place.
 */
internal class MobixPageReuse private constructor(
    /** Page index → the part the source archive already holds for exactly that page. */
    val reused: Map<Int, MobixPart>,

    /**
     * The indices a save must build, or null when it must build every page — the no-source case. This
     * is the list identity is adopted over ([com.mobixournal.format.model.withIdentityOn]), because a
     * page that is copied is a page no id has to be minted into.
     */
    val written: Set<Int>?,
) {

    /**
     * [document] as its write needs it: the identity adopted over the pages **this save writes**, and
     * over nothing else.
     *
     * A page the file already holds is a page no id has to be minted into — the ids are in the file and
     * the model read them from it — so asking that page whether it has one is what would open it: the
     * whole-document form ([withIdentity]) walks every page, so on a two-hundred-page notebook a save
     * would inflate the notebook to be told it has nothing to do (`docs/data-path.md` → *The page
     * window*).
     *
     * This is the **single** place that choice is made, because two callers need the same one: the write
     * takes it on the copy it encodes ([MobixDocument.of]) and the editor takes it on the model it is
     * about to snapshot (`DocumentIo.identifyForSave`, which asks [of] the same question). Made twice,
     * the two would drift, and a page identified on one side of a save and not the other is exactly the
     * fresh-id-per-save that [withIdentityOn] exists to prevent.
     *
     * The document's own id is adopted either way, since a `.mobix` manifest needs one.
     */
    fun identified(document: Document): Document =
        if (written == null) document.withIdentity() else document.withIdentityOn(written)

    companion object {

        /** No page is reused: a save with nothing to repack over writes the whole document. */
        val none = MobixPageReuse(emptyMap(), null)

        /**
         * The pages of [pages] that [source] already holds, keyed by index.
         *
         * @param source the archive the save is about to repack over — the same bytes the pages were
         *   read from. Its entry names are what the reuse is checked against: an entry that is not
         *   there cannot be copied.
         */
        fun of(
            pages: List<Page>,
            source: MobixEntrySource,
            /** The mode the write will claim: it decides what a part's entry name has to be. */
            canvasMode: CanvasMode = CanvasMode.PAGINATED,
        ): MobixPageReuse {
            val (base, edited) = sourceView(pages) ?: return none
            val parts = base.backing.sourceParts() ?: return none
            val reusable = LinkedHashMap<Int, MobixPart>()
            for (index in 0 until pages.size) {
                if (index in edited) continue
                val part = parts.getOrNull(index) ?: continue
                // The page has to be exactly where it was: a part that names some other entry would
                // have to be copied to this index's name, which is a rewrite, not a copy.
                if (!entryMatches(part, index, canvasMode)) continue
                if (part.entry !in source.entryNames) continue
                reusable[index] = part
            }
            if (reusable.isEmpty()) return none
            return MobixPageReuse(reusable, (0 until pages.size).toSet() - reusable.keys)
        }

        /**
         * Whether the entry a write gives the page at [index] is the entry [part] already holds.
         *
         * A **paginated** document names a page by its position (`pages/page-0007.json`), so the name
         * has to be this index's own. A **chunk** is named by its coordinates, and a chunk's place in
         * the manifest is not its place on the board — which is why a chunked write takes its
         * coordinates from the source's own manifest ([MobixDocument.of] `chunkCoords`) instead of
         * deriving them: an index the overlay did not replace is therefore still exactly the entry the
         * source holds, and anything that is not a chunk entry at all is a rewrite, not a copy.
         */
        private fun entryMatches(part: MobixPart, index: Int, canvasMode: CanvasMode): Boolean =
            when (canvasMode) {
                CanvasMode.PAGINATED -> part.entry == MobixFormat.pageEntry(index + 1)
                CanvasMode.INFINITE_VERTICAL,
                CanvasMode.INFINITE_HORIZONTAL,
                CanvasMode.INFINITE_2D,
                -> MobixFormat.parseChunkEntry(part.entry) != null
            }

        /**
         * [pages] as the source's own list plus the indices an edit replaced, or null when the list is
         * not a source's view at all.
         */
        private fun sourceView(pages: List<Page>): Pair<LazyPages, Set<Int>>? {
            val edited = pages as? EditedPages
            val base = edited?.basePages ?: pages
            return if (base is LazyPages) base to (edited?.editedIndices ?: emptySet()) else null
        }
    }
}
