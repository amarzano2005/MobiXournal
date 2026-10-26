package com.mobixournal.desktop

import java.io.File

/**
 * The document a launch argument names — including the one Windows **handed over damaged**.
 *
 * Double-clicking a notebook (or "Open with") makes Windows append the path to the executable's command
 * line. When the registered command does not quote that placeholder, the path arrives **unquoted**, and
 * every Windows argv splitter then breaks it at its spaces — collapsing a run of them into one. A folder
 * named `09)  21-10-2026` (two spaces) reaches `main` as `09) 21-10-2026`, and the app is asked to open a
 * file that does not exist. The path is not the user's mistake, so the window puts it back together.
 *
 * Two recoveries, in order, and both only when the named file is missing:
 * 1. **[nearbyDocument]** finds the deepest folder of the path that does exist, and looks inside it for a
 *    file with the requested **name** — bounded in depth, and only when exactly one file matches, so it
 *    can never open a different document by accident;
 * 2. otherwise the launch reports the path it tried, in words, instead of a Java exception
 *    (`describeOpenFailure`).
 */
internal fun launchDocument(raw: String): File? {
    val cleaned = raw.trim().trim('"').removePrefix("file:").trimStart('/').let { path ->
        // A `file:` URL keeps its slashes as `/`; a Windows path needs its backslashes.
        if (path.length > 1 && path[1] == ':') path else raw.trim().trim('"')
    }
    val named = File(cleaned)
    if (named.isFile) return named
    return nearbyDocument(named)
}

/**
 * A file with [file]'s **name** inside the deepest folder of its path that exists — or null.
 *
 * This is the fallback for a launch whose line could not be read either (see [documentFromCommandLine]):
 * the deepest surviving folder is searched for the name, and the answer is refused unless exactly one
 * file carries it.
 *
 * The search is deliberately narrow: at most [LEVELS_UP] folders above the path are tried (a damaged path
 * differs in a name, not in its drive), at most [DEPTH] levels below it are walked, and a name that
 * matches in more than one place is refused rather than guessed. [VISIT_LIMIT] keeps a cloud mount from
 * turning a failed launch into a scan of the whole drive.
 */
internal fun nearbyDocument(
    file: File,
    depth: Int = DEPTH,
    levelsUp: Int = LEVELS_UP,
): File? {
    if (file.isFile) return file
    var directory = file.parentFile
    var climbed = 0
    while (directory != null && !directory.isDirectory && climbed < levelsUp) {
        directory = directory.parentFile
        climbed++
    }
    if (directory == null || !directory.isDirectory) return null

    val wanted = file.name
    val matches = mutableListOf<File>()
    var visited = 0

    fun walk(current: File, level: Int) {
        val children = runCatching { current.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (++visited > VISIT_LIMIT) return
            when {
                child.isFile && sameName(child.name, wanted) -> {
                    matches += child
                    // A second match makes the answer a guess; stop walking and refuse.
                    if (matches.size > 1) return
                }
                child.isDirectory && level < depth -> walk(child, level + 1)
            }
        }
    }

    walk(directory, 1)
    return matches.singleOrNull()
}

/**
 * A file name compared the way the damaged path compared it: **runs of spaces are the same run**.
 *
 * The squeeze that lost a folder's second space loses it in a file name too, so `notes  1.mobix` comes
 * in as `notes 1.mobix`. Nothing else is relaxed — no prefix, no other punctuation — because a fuzzy
 * match here is a wrong document opened.
 */
internal fun sameName(actual: String, wanted: String): Boolean =
    actual.equals(wanted, ignoreCase = true) ||
        collapseSpaces(actual).equals(collapseSpaces(wanted), ignoreCase = true)

/** [text] with every run of whitespace turned into one space, and the ends trimmed. */
private fun collapseSpaces(text: String): String = text.trim().replace(WHITESPACE_RUN, " ")

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * How deep below the surviving folder the search looks.
 *
 * Four, not one: a path can be damaged in more than one folder — the user's own notebook sits under
 * `…/Appunti/01) 📚 Teoria/09)  21-10-2026/Materiale/`, four levels below the last folder both spellings
 * agreed on — and a search that stopped sooner reported a document as missing while it was right there.
 */
private const val DEPTH = 6

/** How many folders above the requested path are tried — a damaged name is one, never six. */
private const val LEVELS_UP = 4

/** The most entries a failed launch may read while looking for the document. */
private const val VISIT_LIMIT = 4_000
