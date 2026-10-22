package com.mobixournal.format.mobix

import com.mobixournal.format.model.Background
import com.mobixournal.format.model.Document
import com.mobixournal.format.model.Stroke
import com.mobixournal.format.model.audioRef
import java.io.File
import java.util.zip.ZipFile

/*
 * The two halves of a `.mobix` write that are neither the codec nor the app: **which bytes a page's
 * payload is** (a store's file, hashed once) and **which entries a rewrite must still carry** (read out
 * of the archive the document was opened from). Both are pure — a `File`, a `ZipFile`, the model — so
 * they live here and a writer without Android ([com.mobixournal.io.DocumentEncoder]) can use them.
 */

/**
 * How a `.mobix` save finds a background's bytes: the PDF store's copy for a PDF, the image store's
 * for a picture. Memoised per file.
 */
fun backgroundPayloadsOf(pdf: File?, images: Map<String, File>): (Background) -> MobixPayload? {
    // One payload per file, so a PDF shared by forty pages is hashed — and written — once, and is
    // never held as a `ByteArray`: a background can be a 100 MB scan.
    val read = HashMap<String, MobixPayload?>()
    fun payloadOf(file: File?): MobixPayload? = file?.takeIf { it.isFile }
        ?.let { read.getOrPut(it.path) { MobixPayload.of(it) } }
    return { background ->
        when (background) {
            is Background.Pdf -> payloadOf(pdf)
            is Background.Pixmap -> payloadOf(images[background.filename])
            is Background.Solid -> null
        }
    }
}

/**
 * The `assets/` entries a **source-less** `.mobix` write still has to carry, read out of the archive
 * the document was opened from — **as streams** ([MobixPayload.ofArchiveEntry]), so a bundled
 * recording or PDF is copied into the new file without passing through the heap.
 *
 * A save that repacks over its own archive needs none of this: an entry whose bytes did not change is
 * streamed out of that file verbatim (`MobixIncrementalSave`). But a Save As, or a first save of a
 * document whose staged copy is gone, writes a fresh archive from the model — and the model keeps the
 * *names* of the payloads it was read with (`Background.Pdf.bundledAsset`, `Stroke.bundledAudio`)
 * precisely so a rewrite cannot orphan them. The bytes those names point at are only adopted into the
 * app's stores when a page is read (`BundledPageAssets`), so a page nothing has drawn yet has no local
 * copy and the stores answer nothing for it. Left alone, the written page would name
 * `assets/img_<sha>.png` with no such entry in the archive: the picture, the bundled PDF or the
 * recording would be silently gone from the new file.
 *
 * So the entries the document still *references* are read back out of the staged copy and handed to
 * the writer as payloads — a **pass of entry names**, not of bytes: which of them the archive holds
 * comes from its central directory, and each one is opened only if the write asks for it. A document
 * whose staged copy the session no longer holds has nothing to read and yields nothing — the honest
 * answer, reported to the user where a page needs a background it cannot get
 * (`report-a-mobix-page-whose-background-is-missing`).
 */
fun carriedPayloads(
    document: Document,
    archive: File?,
    suppliedAudio: (String) -> MobixPayload?,
): Map<String, MobixPayload> {
    if (archive == null) return emptyMap()
    val referenced = referencedAssets(document, suppliedAudio)
    if (referenced.isEmpty()) return emptyMap()
    val present = runCatching {
        ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
    }.getOrNull() ?: return emptyMap()
    return referenced.filter { it in present }
        .associateWith { name -> MobixPayload.ofArchiveEntry(archive, name) }
}

/**
 * The `assets/` entry names the model itself still carries: a page's bundled background and a stroke's
 * bundled recording. A stroke that names an asset through its `fn` instead — the shape this app wrote
 * before it kept the two apart — counts as one too, which is the same rule the adoption step reads it
 * by (`BundledPageAssets`).
 *
 * A recording the app's store still holds travels as **itself**, added by content hash while the page
 * is encoded ([suppliedAudio]); carrying the archive's copy as well would write the same bytes twice,
 * and reading it out would be the allocation this path avoids. It is carried only when nothing else
 * can supply it.
 */
fun referencedAssets(
    document: Document,
    suppliedAudio: (String) -> MobixPayload?,
): Set<String> {
    val names = LinkedHashSet<String>()
    for (page in document.pages) {
        when (val background = page.background) {
            is Background.Pdf -> background.bundledAsset?.let(names::add)
            is Background.Pixmap -> background.bundledAsset?.let(names::add)
            is Background.Solid -> Unit
        }
        for (layer in page.layers) {
            for (element in layer.elements) {
                val stroke = element as? Stroke ?: continue
                val reference = stroke.audioRef()
                if (reference != null && suppliedAudio(reference.filename) != null) continue
                stroke.bundledAudio?.let(names::add)
                reference?.filename
                    ?.takeIf { MobixFormat.isAssetEntry(it, MobixFormat.AssetKind.AUDIO) }
                    ?.let(names::add)
            }
        }
    }
    return names
}
