package com.mobixournal.desktop.settings

import java.io.File
import java.util.Properties

/**
 * The notebooks the window has opened lately, most recent first — the entry screen's list.
 *
 * One key, `recent.files`, holding absolute paths separated by newlines (`Properties` escapes and unescapes
 * them, so a path keeps its own characters). It exists because the window now opens on a screen rather than
 * on a blank page: the sheets a reader had open yesterday are the first thing they want back, and asking them
 * to walk a file picker for a file the window already knows is the kind of forgetting a desktop app is not
 * allowed to do.
 *
 * An entry whose file has gone is dropped when the list is **read**, not when it is written: a notebook on a
 * drive that is not mounted today must not silently delete the fact that it was opened, and one that was
 * deleted in a file manager must not leave a card that opens nothing.
 */
internal class RecentFilesStore(
    private val properties: Properties,
    private val save: () -> Unit,
) {

    /** The notebooks that still exist, most recent first, at most [LIMIT] of them. */
    var files: List<File>
        get() = properties.getProperty(KEY_RECENT)
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.map(::File)
            ?.filter { it.isFile }
            ?.take(LIMIT)
            .orEmpty()
        set(value) {
            val kept = value.map { it.absolutePath }.distinct().take(LIMIT)
            if (kept.isEmpty()) {
                properties.remove(KEY_RECENT)
            } else {
                properties.setProperty(KEY_RECENT, kept.joinToString("\n"))
            }
            save()
        }

    /**
     * Puts [file] in front of the list.
     *
     * Opened twice it is one entry — the list is a set of sheets, not a log of visits — and the oldest falls
     * off the end, because a card past [LIMIT] is a card nobody scrolls to.
     */
    fun record(file: File) {
        val absolute = file.absoluteFile
        files = listOf(absolute) + files.filter { it.absolutePath != absolute.absolutePath }
    }

    companion object {

        /** The list's own key in `desktop.properties`. */
        const val KEY_RECENT = "recent.files"

        /** Six cards: one row that fits under the new sheet on a 3440 px desk as well as on a laptop. */
        const val LIMIT = 6
    }
}
