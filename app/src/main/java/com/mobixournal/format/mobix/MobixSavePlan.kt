package com.mobixournal.format.mobix

/**
 * What a `.mobix` build produced: the entries this build wrote, and the **order the file's entries go
 * in** — which is not simply the written entries' own order.
 *
 * The two come apart for one reason: a save that repacks over the archive it was opened from may
 * **reuse** a page ([MobixPageReuse]) — an untouched page's JSON is never rebuilt, so there are no
 * bytes to list, yet the page is part of the document and has to keep its place in the archive the
 * repack assembles. So the plan *is* the list of entries to write ([entries], which is what a full
 * write hands [MobixContainer.save]), and [order] is what [MobixIncrementalSave] assembles the file
 * by, reused pages included.
 *
 * A caller with no source to reuse from — a new document, a first save, a Save As — gets [of], where
 * the two are the same list.
 */
internal class MobixSavePlan(
    /** The entries to write, `manifest.json` first. */
    val entries: List<MobixContainer.Entry>,

    /**
     * Every entry name the finished file must hold, in the order it must hold them — the reused pages
     * included, which [entries] does not carry. Each is in [entries], or in the entry source a repack
     * is copying from.
     */
    val order: List<String>,
) : List<MobixContainer.Entry> by entries {

    companion object {
        /** A plan of entries nobody reuses: the file is exactly what was written, in that order. */
        fun of(entries: List<MobixContainer.Entry>): MobixSavePlan =
            MobixSavePlan(entries, entries.map { it.name })
    }
}
