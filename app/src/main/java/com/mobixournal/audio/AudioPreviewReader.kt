package com.mobixournal.audio

import com.mobixournal.format.mobix.*
import com.mobixournal.format.model.*
import java.io.File

/** The reader and its lifetime belong to this worker, including when a tab closes during a seek. */
internal fun <T> withAudioPreviewPages(document: Document, archive: File?, checkCancelled: () -> Unit,
    block: (Document) -> T): T {
    var reader: MobixPagedReader? = null
    var positions: Map<String, Int>? = null
    val rebound = mapPageSources(document.pages) { source ->
        if (source.sourceParts() == null) source else object : PageSource {
            override val pageCount get() = source.pageCount
            override fun pageId(index: Int) = source.pageId(index)
            override fun page(index: Int): Page {
                checkCancelled()
                val file = requireNotNull(archive) { "The source archive is no longer available" }
                val own = reader ?: MobixPagedReader.open(file, cacheBudget = 1).also { reader = it }
                val ids = positions ?: own.parts.mapIndexed { i, part -> part.id to i }.toMap().also { positions = it }
                val at = requireNotNull(ids[source.pageId(index)]) { "The source page is no longer available" }
                return own.getPage(at)
            }
        }
    }
    val pages = object : AbstractList<Page>() {
        override val size get() = rebound.size
        override fun get(index: Int): Page {
            checkCancelled()
            return rebound[index]
        }
    }
    return try { block(document.copy(pages = pages)) } finally { reader?.close() }
}

internal fun AudioReferences.playableCandidate(page: Int, resolve: (String) -> File?,
    checkCancelled: () -> Unit = {}): AudioRef? {
    val checked = HashMap<String, Boolean>()
    return candidate(page) { name -> checked.getOrPut(name) {
        checkCancelled()
        try { resolve(name) != null }
        catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    } }
}

/** Background metadata reads have their own ZIP/cache and never adopt audio through the canvas reader. */
internal fun readAudioPreview(document: Document, archive: File?, index: AudioReferenceIndex,
    register: (String, String, MobixPayload) -> Unit, checkCancelled: () -> Unit = {},
    only: Collection<Int>? = null): AudioReferences {
    return withAudioPreviewPages(document, archive, checkCancelled) { safe ->
        val result = index.read(document, readBase = { safe.pages[it] }, checkCancelled = checkCancelled, only = only)
        if (archive != null) result.entries.forEach { item ->
            checkCancelled()
            val entry = item.bundledEntry ?: item.reference.filename.takeIf {
                MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.AUDIO)
            }
            if (entry != null) register(item.reference.filename, "${archive.absolutePath}:$entry",
                MobixPayload.ofArchiveEntry(archive, entry))
        }
        result
    }
}
