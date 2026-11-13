package com.mobixournal.desktop.io

import com.mobixournal.desktop.shell.DesktopShellState
import com.mobixournal.desktop.shell.PickerType
import com.mobixournal.format.mobix.MobixPayload
import com.mobixournal.format.mobix.carriedPayloads
import com.mobixournal.portability.PortabilityEntry
import com.mobixournal.portability.PortabilityKind
import com.mobixournal.portability.PortableCopy
import java.awt.Frame
import java.io.File
import java.util.zip.ZipFile

/**
 * **Export As ▸ .pmobix**: the window's door to `PortableCopy` (`docs/mobix-assets.md`).
 *
 * A `.mobix` may *name* what it does not carry — a PDF or a picture beside it, a recording in the app's
 * store — which is what keeps it small and what leaves it un-openable on another machine. This command
 * writes a **second** file with those references embedded, so the notebook can be handed to someone who
 * has none of the originals. The document on screen and the file it came from are untouched: this is a
 * copy, not a Save, so the tab keeps its own file, name and container.
 *
 * Its three answers are the three a reader needs: a cancelled destination (nothing happens), a failure
 * (the sentence [DesktopShellState.failure] shows, from [describeSaveFailure]), or success — after which
 * the copy's own report says what it still leaves outside itself, which is shown rather than hidden.
 *
 * Bytes come from where this window's painter gets them ([referenceFor]): the document's folder for a
 * background, the audio store for a recording. No second location is guessed, because a reference the
 * renderer cannot resolve is one the copy cannot honestly embed.
 */

/**
 * The one type an export is written as: the portable format, whose container is a `.mobix` that carries
 * its own assets. It has an extension of its own (`.pmobix`) because it is never a document to keep
 * editing — only a file to hand on.
 */
internal val PORTABLE_COPY_TYPES: List<PickerType> =
    listOf(
        PickerType(
            "Portable MobiXournal notebook (*${PortableCopy.EXTENSION})",
            listOf(PortableCopy.EXTENSION),
        ),
    )

/**
 * Write a portable copy of [state]'s active document, asking where first.
 *
 * Answers whether a copy was written and verified. A cancel and a failure both answer false; a failure
 * leaves its sentence in [DesktopShellState.failure].
 */
fun savePortableCopy(state: DesktopShellState, window: Frame?): Boolean {
    val tab = state.tabs.active ?: return false
    val document = tab.document
    val folder = tab.file.absoluteFile.parentFile
    val answer = DesktopFilePicker.saveFile(
        window = window,
        title = "Export the notebook as a portable copy",
        types = PORTABLE_COPY_TYPES,
        proposedName = tab.file.nameWithoutExtension + PortableCopy.EXTENSION,
        startIn = folder,
    ) ?: return false
    val destination = File(answer.file.absoluteFile.parentFile, portableCopyName(answer.file.name))
    return runCatching {
        PortableCopy.write(
            source = document.source,
            archiveEntries = archiveEntriesOf(document.pageSource),
            document = document.model,
            destination = destination,
            appVersion = DesktopSaves.APP_VERSION,
            nowMillis = System.currentTimeMillis(),
            openBytes = { entry ->
                referenceFor(entry, folder) { name -> document.audioStore.file(name) }?.readBytes()
            },
            carried = carriedPayloads(
                document = document.model,
                archive = document.pageSource.takeIf { it.isFile },
                suppliedAudio = { name -> document.audioStore.file(name)?.let(MobixPayload::of) },
            ),
        )
    }
        .onFailure { state.failure = describeSaveFailure(it) }
        .onSuccess { state.failure = null }
        .isSuccess
}

/** An export is always a `.pmobix`, whatever suffix the name field was left with. */
internal fun portableCopyName(name: String): String =
    if (name.endsWith(PortableCopy.EXTENSION, ignoreCase = true)) name
    else name.substringBeforeLast('.', name) + PortableCopy.EXTENSION

/**
 * The file behind one absent reference, or null when this machine does not hold it.
 *
 * A background is resolved the way its page's backdrop resolves it — against the document's own folder —
 * and a recording against the app's store, whose file names are its own ([DesktopAudioStore]). The audit
 * never offers anything else: an absolute path, a URI or a `..` traversal is `UNRECOVERABLE` there, so it
 * is never asked for here and this resolver has no reason to look outside the folder.
 */
internal fun referenceFor(
    entry: PortabilityEntry,
    folder: File?,
    audio: (String) -> File?,
): File? = when (entry.kind) {
    PortabilityKind.AUDIO -> audio(entry.reference)?.takeIf { it.isFile }
    else -> referenceFile(entry.reference, folder)?.takeIf { it.isFile }
}

/** [reference] inside [folder], or null when there is no folder to resolve it against. */
internal fun referenceFile(reference: String, folder: File?): File? {
    val path = reference.trim()
    if (path.isEmpty() || folder == null) return null
    return File(folder, path)
}

/**
 * The archive's central directory, for the audit's "is it already inside?" question.
 *
 * Empty for a document with no archive behind it — a `.xopp`, or a notebook whose staged copy the session
 * no longer holds — which makes the audit classify every reference as absent: exactly what a fresh copy
 * has to embed.
 */
internal fun archiveEntriesOf(archive: File): Set<String> =
    runCatching { ZipFile(archive).use { zip -> zip.entries().asSequence().map { it.name }.toSet() } }
        .getOrDefault(emptySet())
