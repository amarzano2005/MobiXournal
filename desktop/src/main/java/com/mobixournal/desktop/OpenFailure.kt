package com.mobixournal.desktop

/**
 * What the window says when an open failed: a sentence a person can act on, and the detail under it.
 *
 * The shell used to show `exception.message`, which is the wrong thing in both directions: a
 * `NoClassDefFoundError` has *no* message (its `message` is the internal class name, so the user read
 * `com/mobixournal/desktop/DocumentLoader$openMobix$1` and concluded that their notebook was broken),
 * while the loader's own refusals — `unrecognised format`, `ZIP archive with no content.xml` — were
 * already the whole explanation. [describeOpenFailure] sorts the cases into those two groups so a
 * development accident cannot be reported as a damaged document.
 *
 * The copy is **English**, like the rest of the app's UI: the window is the same product as the tablet's
 * (`docs/ui-modernization.md` owns the rule).
 */
data class OpenFailure(
    /** One line: what happened. */
    val title: String,
    /** The sentence under it: why, and what to do. */
    val detail: String,
)

/**
 * The window's account of [failure].
 *
 * A `LinkageError` (`NoClassDefFoundError`, `NoSuchMethodError`, …) means the **code** of this window
 * was replaced while it was open — a Gradle build rewrote `build/classes` under the live JVM — so the
 * file that failed to open is innocent and reopening it in a fresh window works. That is a fact worth
 * saying out loud, because the alternative reading ("my notebook is corrupt") is the one a user
 * actually has, and it costs an hour of panic.
 *
 * An [UnsupportedOperationException] is the loader's own refusal and already explains itself; an
 * [OutOfMemoryError] on a very tall page gets its own line rather than a class name.
 */
fun describeOpenFailure(failure: Throwable): OpenFailure {
    val detail = failure.message?.takeIf { it.isNotBlank() } ?: "no detail available"
    return when (failure) {
        is LinkageError -> OpenFailure(
            "The preview has fallen behind",
            "The document is not at fault: this window was already open while the code was rebuilt, " +
                "and a class disappeared underneath it. Close the window, relaunch the preview " +
                "(`gradlew :desktop:run`), then open the same file again.",
        )
        is UnsupportedOperationException -> OpenFailure("Unsupported format", detail)
        is OutOfMemoryError -> OpenFailure(
            "Not enough memory",
            "This page is larger than the available memory: $detail",
        )
        else -> OpenFailure("Could not open the document", "${failure::class.simpleName}: $detail")
    }
}

/**
 * What the window says when a **save** failed. Same rule as an open — say what happened and what to
 * do — but a different set of causes: a destination it refuses because the file could not hold what
 * the document has, a folder it may not write (a read-only share, a file the user does not own), and
 * the disk being full. The document is untouched in every one of these cases, which is worth saying:
 * the write is staged and moved, so a failed save leaves the file that was there.
 */
fun describeSaveFailure(failure: Throwable): OpenFailure {
    val detail = failure.message?.takeIf { it.isNotBlank() } ?: "no detail available"
    return when (failure) {
        is DesktopSaves.SaveRefused -> OpenFailure("This document cannot be saved there", detail)
        is java.io.IOException -> OpenFailure(
            "Could not write the file",
            "$detail — the document on disk was left as it was.",
        )
        is OutOfMemoryError -> OpenFailure("Not enough memory", "This document is larger than the available memory: $detail")
        is LinkageError -> OpenFailure(
            "The preview has fallen behind",
            "The window was open while the code was rebuilt. Close it, relaunch, then save again.",
        )
        else -> OpenFailure("Could not save the document", "${failure::class.simpleName}: $detail")
    }
}
