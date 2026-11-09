package com.mobixournal.audio

import com.mobixournal.format.model.*
import java.lang.ref.WeakReference

data class IndexedAudio(val page: Int, val reference: AudioRef, val bundledEntry: String?)

/**
 * The recordings [page]'s strokes replay from, in stroke order, one per file.
 *
 * The single mapping both the index below and the chrome's own page-local discovery use, so “which
 * recording was this stroke drawn over” cannot be spelled two ways. It reads the page's model and
 * touches nothing else — no archive, no file.
 */
fun audioReferences(page: Page): List<AudioRef> =
    page.layers.asSequence().flatMap { it.elements.asSequence() }.filterIsInstance<Stroke>()
        .mapNotNull { it.audioRef() }.distinctBy { it.filename }.toList()

class AudioReferences(val entries: List<IndexedAudio>) {
    fun candidate(page: Int, available: (String) -> Boolean): AudioRef? =
        entries.asSequence().filter { it.page == page }.map { it.reference }.firstOrNull { available(it.filename) }
            ?: entries.asSequence().map { it.reference }.firstOrNull { available(it.filename) }?.copy(startMs = 0)

    fun firstPage(filename: String): Int? {
        fun bare(value: String) = value.substringAfterLast('/').substringAfterLast('\\')
        return entries.firstOrNull { bare(it.reference.filename).equals(bare(filename), ignoreCase = true) }?.page
    }
}

/**
 * Worker-owned compact metadata: edits/undo revisit only changed overlays, never the unchanged lazy base.
 *
 * A read is **page-scoped** when the caller names the pages it cares about ([only]): a sidecar sweep
 * asks for the page in front, so turning a page — or committing a stroke, which is a new document
 * identity — never inflates the rest of a `.mobix`. What a read answers is the cache, so every page an earlier
 * read visited stays indexed and the document-wide fallback ("some recording in this document") fills
 * in as the user browses; a caller that genuinely needs the whole document asks for it with [only] null.
 */
class AudioReferenceIndex {
    private var base: WeakReference<List<Page>>? = null
    private var previousEdits = emptySet<Int>()
    private val edited = HashMap<Int, WeakReference<Page>>()
    private val pages = LinkedHashMap<Int, List<IndexedAudio>>()

    @Synchronized fun read(document: Document, readBase: (Int) -> Page = { document.pages[it] },
        checkCancelled: () -> Unit = {}, only: Collection<Int>? = null): AudioReferences {
        val overlay = document.pages as? EditedPages
        val nextBase = overlay?.basePages ?: document.pages
        val edits = overlay?.editedIndices.orEmpty()
        if (base?.get() !== nextBase) {
            pages.clear(); edited.clear(); previousEdits = emptySet(); base = WeakReference(nextBase)
        }
        for (index in previousEdits + edits) {
            checkCancelled()
            val page = if (index in edits) document.pages[index] else null
            if (edited[index]?.get() !== page) pages.remove(index)
        }
        edited.clear()
        for (index in edits) edited[index] = WeakReference(document.pages[index])
        previousEdits = edits.toSet()
        val wanted = only?.filter { it in document.pages.indices } ?: document.pages.indices
        for (index in wanted) {
            checkCancelled()
            pages.getOrPut(index) {
                val page = if (index in edits) document.pages[index] else readBase(index)
                page.layers.asSequence().flatMap { it.elements.asSequence() }.filterIsInstance<Stroke>()
                    .mapNotNull { stroke -> stroke.audioRef()?.let { IndexedAudio(index, it, stroke.bundledAudio) } }
                    .distinctBy { it.reference.filename }.toList()
            }
        }
        return AudioReferences(pages.values.flatten())
    }
}
