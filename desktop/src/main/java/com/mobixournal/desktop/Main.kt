package com.mobixournal.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File

/**
 * Runs the desktop preview: `./gradlew :desktop:run --args="udiff.xopp"` (or no argument to start
 * empty and pick a file from the toolbar). Packaging targets come from `desktop/build.gradle.kts`.
 */
fun main(args: Array<String>) {
    val initial = args.firstOrNull()?.let(::File)
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
