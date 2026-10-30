package com.mobixournal.desktop

import java.io.File

/**
 * One row of a file dialog's **type box**: the label a person reads and the suffixes it accepts.
 *
 * The picker speaks this rather than a Swing filter because both the shell's dialog and Java's fallback
 * take a label plus a list of `*.ext` patterns — and because a **save** row has to map back to the
 * container the window writes (`DesktopNewDocumentFormat`, `docs/data-path.md`).
 */
internal data class PickerType(val label: String, val extensions: List<String>) {

    /** The suffix the dialog fills in when a name has none — the first, without its dot. */
    val defaultExtension: String get() = extensions.first().removePrefix(".")

    /** Whether [name] carries one of the suffixes this row accepts. */
    fun matches(name: String): Boolean = extensions.any { name.endsWith(it, ignoreCase = true) }
}

/** The `*.ext` pattern a shell dialog wants, from a stored `.ext`. */
internal fun filterOf(extension: String): String = "*." + extension.removePrefix(".")

/** What a save answered: the file the reader named and the **row** of the type box that decided it. */
internal data class SaveAnswer(val file: File, val typeIndex: Int)

/** The three containers the window writes, in the order its type box lists them. */
internal val DOCUMENT_SAVE_TYPES: List<PickerType> =
    DesktopNewDocumentFormat.entries.map { PickerType(it.label, listOf(it.extension)) }

/**
 * One row for both readable documents.
 *
 * `java.awt.FileDialog` ignores its filter on Windows, so the window could only ever test the suffix
 * itself; the shell's box filters for real, and one row covering both containers keeps a `.xopp` visible
 * while the reader is looking for a `.mobix`.
 */
internal val DOCUMENT_OPEN_TYPES: List<PickerType> =
    listOf(PickerType("MobiXournal and Xournal++ (*.mobix, *.xopp)", listOf(".mobix", ".xopp")))

/** A PDF, for the import and for the export that writes one. */
internal val PDF_TYPES: List<PickerType> = listOf(PickerType("PDF document (*.pdf)", listOf(".pdf")))

/** The settings backup's one file, whose suffix is the stamp its import reads. */
internal val BACKUP_TYPES: List<PickerType> =
    listOf(PickerType("MobiXournal settings (*.properties)", listOf(DesktopSettingsBackup.EXTENSION)))

/** The container a save row names; an answer outside the list is the default, never a crash. */
internal fun documentFormatOf(typeIndex: Int): DesktopNewDocumentFormat =
    DesktopNewDocumentFormat.entries.getOrNull(typeIndex) ?: DesktopNewDocumentFormat.DEFAULT

/** The row of [DOCUMENT_SAVE_TYPES] that writes [format] — the picker opens on the document's own. */
internal fun documentTypeIndexOf(format: DesktopNewDocumentFormat): Int =
    DesktopNewDocumentFormat.entries.indexOf(format).coerceAtLeast(0)
