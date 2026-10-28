package com.mobixournal.desktop

import java.awt.EventQueue
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * **The save window, asking both of its questions at once**: the name of the file and the container it is
 * written as.
 *
 * The system dialog the window uses for *open* cannot carry more than a name — on Windows AWT's
 * `FileDialog` ignores its filter entirely — so Save As used to ask the container in a card of its own and
 * then hand the native dialog nothing but a suffix to remember. Two dialogs for one save is the wrong way
 * round: a save window with a **type box** is what every other program on the machine shows, so the
 * window uses one, and the three containers sit in it as `.mobix`, `.xopp` and the zipped `.xopp` package
 * (`DesktopNewDocumentFormat` — the list the Settings *Documents* row shows for a new file).
 *
 * What cannot change is the rule: the container is a **document** decision, not a filename one
 * (`docs/data-path.md`), so the **type box** decides what is written. The name field is kept honest with
 * it — [saveAsRenamed] swaps a suffix the type no longer matches as the box changes, and [saveAsName]
 * gives a name typed without one the suffix it is written as — but a suffix typed over the type is not
 * a second opinion about the format.
 *
 * Swing rather than AWT is a deliberate exception to the window's own chrome: a file dialog is the
 * platform's surface, and only the Swing one has a type box to offer. `main` asks for the system look and
 * feel so it dresses like the Windows dialog beside it.
 */
internal object SaveAsPicker {

    /**
     * Show the save window for [tab] and answer the destination and the container, or null when it was
     * cancelled. It opens on the document's own container and its own folder: the common answer is "the
     * same file, somewhere else", and Save As should not make that a decision every time.
     */
    fun choose(window: Frame, tab: DesktopTab): Pair<File, DesktopNewDocumentFormat>? = onSwingThread {
        val initial = DesktopNewDocumentFormat.ofContainer(tab.document.container)
        val folder = tab.file.absoluteFile.parentFile?.takeIf { it.isDirectory }
        val chooser = if (folder != null) JFileChooser(folder) else JFileChooser()
        chooser.dialogTitle = "Save the document as"
        chooser.dialogType = JFileChooser.SAVE_DIALOG
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        // The three types are the only answers: an "all files" entry would name no container at all.
        chooser.isAcceptAllFileFilterUsed = false
        val types = DesktopNewDocumentFormat.entries.associateWith { format ->
            FileNameExtensionFilter(format.label, format.extension.removePrefix("."))
        }
        for (filter in types.values) chooser.addChoosableFileFilter(filter)
        followTypeBox(chooser, types)
        chooser.fileFilter = types.getValue(initial)
        val proposed = File(folder, tab.file.nameWithoutExtension + initial.extension)
        chooser.selectedFile = proposed
        if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) return@onSwingThread null
        val chosen = types.entries.firstOrNull { it.value === chooser.fileFilter }?.key ?: initial
        val target = chooser.selectedFile ?: return@onSwingThread null
        File(target.parentFile, saveAsName(target.name, chosen)) to chosen
    }

    /**
     * The name field following the type box: picking another type in the list rewrites the suffix of the
     * name already there, which is how the save windows of Word or Excel behave. A name with no suffix, or
     * one ending in something this window does not write, is left alone — the suffix is added when the
     * file is written instead, rather than a fragment of somebody's filename being cut off.
     */
    private fun followTypeBox(
        chooser: JFileChooser,
        types: Map<DesktopNewDocumentFormat, FileNameExtensionFilter>,
    ) {
        chooser.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY) { change ->
            val format = types.entries.firstOrNull { it.value === change.newValue }?.key ?: return@addPropertyChangeListener
            val current = chooser.selectedFile ?: return@addPropertyChangeListener
            val renamed = saveAsRenamed(current.name, format)
            if (renamed != current.name) chooser.selectedFile = File(current.parentFile, renamed)
        }
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

/**
 * Run [block] on the event thread — the one thread a Swing dialog may be shown from — and answer what it
 * returned. Compose's click handlers already run there; the hop is for the callers that do not.
 */
private fun <T> onSwingThread(block: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) return block()
    var answer: T? = null
    var failure: Throwable? = null
    EventQueue.invokeAndWait {
        runCatching(block).onSuccess { answer = it }.onFailure { failure = it }
    }
    failure?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return answer as T
}
