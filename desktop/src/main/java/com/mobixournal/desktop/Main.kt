package com.mobixournal.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File

/**
 * The document named on the command line, or null to start empty.
 *
 * `--args` splits its value on spaces, so a path that has one arrives in pieces: this machine keeps its
 * notebooks in `Il mio Drive/…`, and `--args="…/Il mio Drive/x.mobix"` handed `main` two arguments and a
 * `File("…/Il")` that does not exist. When the first piece is not itself a file, the pieces are put back
 * together and the joined path is tried — a real path with a space keeps working either way.
 */
internal fun initialDocument(args: Array<String>): File? {
    val first = args.firstOrNull() ?: return null
    val single = File(first)
    if (single.isFile) return single
    return File(args.joinToString(" ")).takeIf { it.isFile } ?: single
}

/**
 * Runs the desktop preview: `./gradlew :desktop:run --args="udiff.xopp"` (or no argument to start
 * empty and pick a file from the toolbar). Packaging targets come from `desktop/build.gradle.kts`.
 *
 * Because `:desktop:run` executes the compiled class directories, a **build while the window is open**
 * replaces files under the live JVM; close the window before recompiling, and reopen the document in a
 * fresh one if it reports "The preview has fallen behind" ([describeOpenFailure]).
 */
fun main(args: Array<String>) {
    val initial = initialDocument(args)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(width = 1100.dp, height = 1000.dp),
            title = "MobiXournal Desktop",
        ) {
            DesktopApp(initial)
        }
    }
}
