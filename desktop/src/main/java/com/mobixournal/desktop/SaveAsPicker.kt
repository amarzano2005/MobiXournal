package com.mobixournal.desktop

import java.awt.Frame
import java.io.File

/**
 * **The save window, asking both of its questions at once**: the name of the file and the container it is
 * written as.
 *
 * One dialog, because the picker's **type box** answers both: the three containers sit in it as `.mobix`,
 * `.xopp` and the zipped `.xopp` package (`DesktopNewDocumentFormat` — the list the Settings *Documents*
 * row shows for a new file), and the name field carries the file. The shell's native box is what made the
 * one-dialog version possible at all: `java.awt.FileDialog` ignores its filter on Windows, which is why
 * this window used to be a Swing chooser beside AWT's open dialog.
 *
 * The rule that cannot change: the container is a **document** decision, not a filename one
 * (`docs/data-path.md`), so the **type box** decides what is written. The name field is kept honest with
 * it — [saveAsRenamed] swaps a suffix the type no longer matches, and [saveAsName] gives a name typed
 * without one the suffix it is written as — but a suffix typed over the type is not a second opinion about
 * the format.
 */
internal object SaveAsPicker {

    /**
     * Show the save window for [tab] and answer the destination and the container, or null when it was
     * cancelled. It opens on the document's own container and its own folder: the common answer is "the
     * same file, somewhere else", and Save As should not make that a decision every time.
     */
    fun choose(window: Frame, tab: DesktopTab): Pair<File, DesktopNewDocumentFormat>? {
        val initial = DesktopNewDocumentFormat.ofContainer(tab.document.container)
        val folder = tab.file.absoluteFile.parentFile?.takeIf { it.isDirectory }
        val answer = DesktopFilePicker.saveFile(
            window = window,
            title = "Save the document as",
            types = DOCUMENT_SAVE_TYPES,
            proposedName = tab.file.nameWithoutExtension + initial.extension,
            startIn = folder,
            initialType = documentTypeIndexOf(initial),
        ) ?: return null
        val format = documentFormatOf(answer.typeIndex)
        val name = saveAsName(saveAsRenamed(answer.file.name, format), format)
        return File(answer.file.parentFile, name) to format
    }
}

/** The suffixes the window writes; the ones [saveAsRenamed] is allowed to replace. */
private val writtenSuffixes = setOf(".mobix", ".xopp")

/**
 * [name] as [format] writes it: the format's own suffix appended when the name does not already end with
 * it, so a name typed without one is still a file Windows recognises.
 */
internal fun saveAsName(name: String, format: DesktopNewDocumentFormat): String =
    if (name.endsWith(format.extension, ignoreCase = true)) name else name + format.extension

/**
 * The name field following the type box: a name ending in a suffix the window writes takes the new one,
 * and anything else is left as typed.
 */
internal fun saveAsRenamed(name: String, format: DesktopNewDocumentFormat): String {
    if (name.endsWith(format.extension, ignoreCase = true)) return name
    val suffix = name.substringAfterLast('.', missingDelimiterValue = "")
    if (suffix.isEmpty() || ".${suffix.lowercase()}" !in writtenSuffixes) return name
    return name.substringBeforeLast('.') + format.extension
}
