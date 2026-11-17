package com.mobixournal.format.model

/** A structural page view can replace its lazy base without reading it or changing its edits. */
interface PageListView {
    fun rebase(transform: (List<Page>) -> List<Page>): List<Page>
    /** Untouched page provenance, so count/id/size queries do not decode the lazy base. */
    fun sourcePage(index: Int): Pair<List<Page>, Int>? = null
}

/** Insertion/deletion provenance survives background reader replacement and undo snapshots. */
class SplicedPages(private val base: List<Page>, private val at: Int, private val inserted: Page?) :
    AbstractList<Page>(), PageListView {
    override val size get() = base.size + if (inserted == null) -1 else 1
    override fun get(index: Int): Page {
        if (index !in indices) throw IndexOutOfBoundsException("page index $index")
        return when { inserted == null -> base[if (index < at) index else index + 1]
            index < at -> base[index]
            index == at -> inserted
            else -> base[index - 1] }
    }
    override fun rebase(transform: (List<Page>) -> List<Page>): List<Page> = SplicedPages(transform(base), at, inserted)
    override fun sourcePage(index: Int): Pair<List<Page>, Int>? = when {
        index !in indices -> null
        inserted == null -> base to if (index < at) index else index + 1
        index == at -> null
        else -> base to if (index < at) index else index - 1
    }
}
