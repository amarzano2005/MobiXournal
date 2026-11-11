package com.mobixournal

import android.net.Uri
import com.mobixournal.format.mobix.MobixPayload
import com.mobixournal.format.mobix.PageSource
import com.mobixournal.format.mobix.carriedPayloads
import com.mobixournal.portability.PortableCopy
import java.io.File
import java.util.zip.ZipFile

/**
 * **Save portable copy** on the tablet: the desk window's command, with SAF in place of a file dialog.
 *
 * The same three steps as `PortableCopy` describes, and the same promise — the copy is a normal `.mobix`
 * with the references embedded, verified on the way out, and the open document is untouched. What differs
 * is where the bytes come from: a background resolves through the same reference arithmetic the renderer
 * uses ([DocumentIo.portableReference]), a recording through the audio store, and the archive the notebook
 * was opened from is staged once to answer "is this already inside?" and to carry forward the entries the
 * model names but has not drawn (`carriedPayloads`).
 *
 * The write goes to a cache file and is staged onto the picked `content://` URI only once it has been
 * written and verified, so a failed or cancelled copy leaves nothing half-written in the user's folder.
 */
internal fun MainActivity.savePortableCopy(uri: Uri) {
    val tab = tabs.active ?: return
    if (!tab.hydrated || tab.opening) {
        toast("Wait for the document to finish opening")
        return
    }
    if (tab.isReadOnly) {
        toast("Document is read-only")
        return
    }
    snapshotActiveTab()
    val open = pane
    val document = open.surface?.takeIf { open.tabs.active?.id == tab.id }?.toDocument() ?: tab.document
    val source = tab.uri?.let(Uri::parse)
    val name = tab.title.ifBlank { "notes" }
    inBackground("Writing a portable copy.", {
        val scratch = File.createTempFile("portable-", ".mobix", cacheDir)
        val staged = stageArchive(source)
        try {
            val written = PortableCopy.write(
                source = PageSource.of(document.pages),
                archiveEntries = archiveEntries(staged),
                document = document,
                destination = scratch,
                appVersion = BuildConfig.VERSION_NAME,
                nowMillis = System.currentTimeMillis(),
                openBytes = { entry ->
                    io.portableReference(entry, name, source)?.use { it.readBytes() }
                        ?: audio.recordingFile(entry.reference)?.takeIf { it.isFile }?.readBytes()
                },
                carried = carriedPayloads(
                    document = document,
                    archive = staged,
                    suppliedAudio = { recording -> audio.recordingFile(recording)?.let(MobixPayload::of) },
                ),
            )
            io.stageOut(scratch, uri)
            // What the copy still leaves outside itself, in the words the reader needs.
            written.remaining.size to written.embedded.size
        } finally {
            scratch.delete()
            staged?.delete()
        }
    }) { result ->
        result.onFailure { toast("Portable copy failed: ${it.message}") }
            .onSuccess { (left, embedded) ->
                toast(
                    when {
                        left == 0 -> "Portable copy saved: everything is inside it ($embedded embedded)"
                        embedded == 0 -> "Portable copy saved: $left reference(s) still point outside it"
                        else -> "Portable copy saved: $embedded embedded, $left still outside"
                    },
                )
            }
    }
}

/** The name a portable copy opens on: the document's own, marked, and always a `.mobix`. */
internal fun MainActivity.suggestedPortableCopyName(): String =
    suggestedSaveName().substringBeforeLast('.') + "-portable.mobix"

/**
 * The document's bytes copied into the cache, so the archive can be read twice — its central directory
 * and, entry by entry, the payloads the model names but this session never drew. Null when the document
 * has no file behind it yet (a new tab), which simply means there is nothing to carry.
 */
private fun MainActivity.stageArchive(source: Uri?): File? {
    source ?: return null
    return runCatching {
        File.createTempFile("portable-src-", ".mobix", cacheDir).also { target ->
            contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            }
        }
    }.getOrNull()
}

/** The archive's central directory, or none: the audit then calls every reference absent. */
private fun archiveEntries(archive: File?): Set<String> {
    archive ?: return emptySet()
    return runCatching { ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toSet() } }
        .getOrDefault(emptySet())
}
