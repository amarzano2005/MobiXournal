package com.mobixournal.ui

import com.mobixournal.format.model.OutlineTree
import com.mobixournal.format.model.Page
import com.mobixournal.render.OutlineResolution
import com.mobixournal.render.OutlineRow
import com.mobixournal.render.PdfOutline
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One successful projection per document state. Weak keys never keep a closed document/reader alive. */
internal class BookmarkRowsCache {
    private class Snapshot(pages: List<Page>, tree: OutlineTree, pdf: PdfOutline?, val rows: List<OutlineRow>) {
        val pages = WeakReference(pages)
        val tree = WeakReference(tree)
        val pdf = WeakReference(pdf)
    }

    @Volatile private var snapshot: Snapshot? = null

    fun cached(pages: List<Page>, tree: OutlineTree, pdf: PdfOutline?): List<OutlineRow>? = snapshot?.takeIf {
        it.pages.get() === pages && it.tree.get() === tree && it.pdf.get() === pdf
    }?.rows

    suspend fun load(pages: List<Page>, tree: OutlineTree, pdf: PdfOutline?): List<OutlineRow> {
        cached(pages, tree, pdf)?.let { return it }
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            val rows = OutlineResolution.rowsOf(tree, pdf, pages) { context.ensureActive() }
            context.ensureActive()
            snapshot = Snapshot(pages, tree, pdf, rows)
            rows
        }
    }
}
