package com.mobixournal.desktop

import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Java's own dialogs: the fallback a host without the shell's surface still gets.
 *
 * Open stays `java.awt.FileDialog`, which is the platform's own picker on macOS and Linux; **save** and
 * **folder** use Swing's chooser, because a type box is what decides a document's container and a folder is
 * a question `FileDialog` cannot ask at all. Its type box is the one the window had before the native
 * picker arrived, so Save As behaves the same on a Linux host as it always did.
 */
internal object DesktopFilePickerJava {

    /** A file to read, with the listing narrowed to [types] on the hosts that honour a filter. */
    fun open(window: Frame?, title: String, types: List<PickerType>, startIn: File?): File? = onEventThread {
        val dialog = FileDialog(window, title, FileDialog.LOAD)
        startIn?.let { folder -> dialog.directory = folder.absolutePath }
        // A filter that hides a readable document is worse than one that lists it: the loader decides by
        // the file's own bytes, and this is a convenience wherever it is honoured.
        dialog.setFilenameFilter { _, name -> types.isEmpty() || types.any { it.matches(name) } }
        dialog.isVisible = true
        val name = dialog.file
        if (name == null) null else File(dialog.directory ?: "", name)
    }

    /** A destination to write, with the type box that names the container. */
    fun save(
        window: Frame?,
        title: String,
        types: List<PickerType>,
        proposedName: String,
        startIn: File?,
        initialType: Int,
    ): SaveAnswer? = onEventThread {
        val chooser = chooser(title, startIn)
        chooser.dialogType = JFileChooser.SAVE_DIALOG
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        // The rows are the only answers: an "all files" entry would name no container at all.
        chooser.isAcceptAllFileFilterUsed = false
        val filters = types.map { type ->
            FileNameExtensionFilter(type.label, *type.extensions.map { it.removePrefix(".") }.toTypedArray())
        }
        for (filter in filters) chooser.addChoosableFileFilter(filter)
        val initial = filters.getOrNull(initialType) ?: filters.firstOrNull()
        if (initial != null) chooser.fileFilter = initial
        chooser.selectedFile = File(startIn ?: chooser.currentDirectory, proposedName)
        if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) return@onEventThread null
        val file = chooser.selectedFile ?: return@onEventThread null
        SaveAnswer(file, filters.indexOfFirst { it === chooser.fileFilter }.coerceAtLeast(0))
    }

    /** A folder to write a set of files into. */
    fun folder(window: Frame?, title: String, startIn: File?): File? = onEventThread {
        val chooser = chooser(title, startIn)
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) null else chooser.selectedFile
    }

    private fun chooser(title: String, startIn: File?): JFileChooser =
        (if (startIn != null) JFileChooser(startIn) else JFileChooser()).apply { dialogTitle = title }

    /** Run [block] on the event thread — the one thread a Swing dialog may be shown from. */
    private fun <T> onEventThread(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return block()
        var answer: T? = null
        var failure: Throwable? = null
        EventQueue.invokeAndWait { runCatching(block).onSuccess { answer = it }.onFailure { failure = it } }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return answer as T
    }
}
