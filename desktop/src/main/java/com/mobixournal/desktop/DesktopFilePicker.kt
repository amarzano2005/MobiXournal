package com.mobixournal.desktop

import java.awt.Frame
import java.io.File

/**
 * The window's **system file dialogs**, one seam for every question it asks about a file.
 *
 * On Windows it asks the **shell's own dialog** (`DesktopFilePickerWindows`: `IFileOpenDialog` /
 * `IFileSaveDialog` through JNA): the modern Explorer surface with breadcrumbs and a search box, a real
 * **type box** for Save As, and a **folder** mode. None of those three is reachable through
 * `java.awt.FileDialog`, which is why Save As had to be a Swing chooser and the page exports had no way to
 * ask where to write. Everywhere else — and whenever the shell refuses — it falls back to Java's own
 * dialogs ([DesktopFilePickerJava]), so a host without the COM surface still asks the same questions.
 *
 * The two answers are different on purpose: a **cancel** is `null`, an answer that writes nothing, while a
 * host that could not show the native dialog is a **failure**, and the seam then asks with Java's dialog
 * rather than reporting a cancel nobody gave.
 */
internal object DesktopFilePicker {

    /** A document (or anything else) to read: the shell's Open dialog, or Java's. */
    fun openFile(window: Frame?, title: String, types: List<PickerType>, startIn: File? = null): File? {
        if (DesktopFilePickerWindows.available) {
            val native = DesktopFilePickerWindows.open(title, types, startIn)
            if (native.isSuccess) return native.getOrNull()
        }
        return DesktopFilePickerJava.open(window, title, types, startIn)
    }

    /**
     * A destination to write: `proposedName` sits in the name field, `types` are the rows of the type box,
     * and the answer carries the row that decided the container ([SaveAnswer]).
     */
    fun saveFile(
        window: Frame?,
        title: String,
        types: List<PickerType>,
        proposedName: String,
        startIn: File? = null,
        initialType: Int = 0,
    ): SaveAnswer? {
        if (DesktopFilePickerWindows.available) {
            val native = DesktopFilePickerWindows.save(title, types, startIn, proposedName, initialType)
            if (native.isSuccess) return native.getOrNull()
        }
        return DesktopFilePickerJava.save(window, title, types, proposedName, startIn, initialType)
    }

    /** A **folder** to write a set of files into — the one question AWT could not ask at all. */
    fun chooseFolder(window: Frame?, title: String, startIn: File? = null): File? {
        if (DesktopFilePickerWindows.available) {
            val native = DesktopFilePickerWindows.folder(title, startIn)
            if (native.isSuccess) return native.getOrNull()
        }
        return DesktopFilePickerJava.folder(window, title, startIn)
    }
}
